package campus.found

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.Statement
import java.time.LocalDate
import java.util.UUID

data class Photo(val bytes: ByteArray, val type: String)

data class Item(
    val id: Long,
    val name: String,
    val date: LocalDate,
    val place: String,
    val tags: List<String>,
    val hasPhoto: Boolean,
    val example: Boolean,
)

data class Page(val items: List<Item>, val total: Long)

class Items(directory: Path) {
    private val url: String

    init {
        Files.createDirectories(directory)
        url =
            "jdbc:h2:file:" +
                directory
                    .toAbsolutePath()
                    .normalize()
                    .resolve("lost-found")
                    .toString()
                    .replace('\\', '/')
        connect().use { db ->
            db.createStatement().use { sql ->
                val resource = checkNotNull(javaClass.getResource("/lost-found/schema.sql"))
                val schema = resource.openStream().bufferedReader().use { it.readText() }
                schema.split(';').filter { it.isNotBlank() }.forEach { sql.executeUpdate(it) }
            }
        }
    }

    private fun connect(): Connection = DriverManager.getConnection(url, "sa", "")

    private val columns = "id, item_name, found_date, collection_point, tags, photo_type, example"

    private fun item(rs: ResultSet) =
        Item(
            rs.getLong("id"),
            rs.getString("item_name"),
            rs.getDate("found_date").toLocalDate(),
            rs.getString("collection_point"),
            rs.getString("tags").split(',').filter { it.isNotBlank() },
            rs.getString("photo_type") != null,
            rs.getBoolean("example"),
        )

    fun save(
        key: UUID,
        name: String,
        date: LocalDate,
        place: String,
        tags: List<String>,
        photo: Photo?,
    ): Long =
        connect().use { db ->
            val sql =
                "INSERT INTO found_item(submission_key,item_name,found_date,collection_point,tags,photo_type,photo) VALUES(?,?,?,?,?,?,?)"
            try {
                db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setObject(1, key)
                    ps.setString(2, name)
                    ps.setObject(3, date)
                    ps.setString(4, place)
                    ps.setString(5, tags.joinToString(","))
                    ps.setString(6, photo?.type)
                    ps.setBytes(7, photo?.bytes)
                    ps.executeUpdate()
                    ps.generatedKeys.use { keys ->
                        check(keys.next())
                        keys.getLong("id")
                    }
                }
            } catch (error: SQLIntegrityConstraintViolationException) {
                if (error.sqlState != "23505") throw error
                db.prepareStatement("SELECT id FROM found_item WHERE submission_key=?").use { ps ->
                    ps.setObject(1, key)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) throw error
                        rs.getLong(1)
                    }
                }
            }
        }

    fun search(words: List<String>, page: Int): Page =
        connect().use { db ->
            db.autoCommit = false
            db.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            val where =
                if (words.isEmpty()) ""
                else
                    words.joinToString(" AND ", prefix = " WHERE ") {
                        "(LOCATE(LOWER(?), LOWER(item_name))>0 OR LOCATE(LOWER(?), LOWER(tags))>0)"
                    }
            fun bind(ps: java.sql.PreparedStatement) = words.forEachIndexed { index, word ->
                ps.setString(2 * index + 1, word)
                ps.setString(2 * index + 2, word)
            }
            val count =
                db.prepareStatement("SELECT COUNT(*) FROM found_item$where").use { ps ->
                    bind(ps)
                    ps.executeQuery().use { rs ->
                        rs.next()
                        rs.getLong(1)
                    }
                }
            val rows =
                db.prepareStatement(
                        "SELECT $columns FROM found_item$where ORDER BY found_date DESC,id DESC LIMIT ? OFFSET ?"
                    )
                    .use { ps ->
                        bind(ps)
                        ps.setInt(2 * words.size + 1, PAGE_SIZE)
                        ps.setLong(2 * words.size + 2, (page - 1L) * PAGE_SIZE)
                        ps.executeQuery().use { rs ->
                            buildList { while (rs.next()) add(item(rs)) }
                        }
                    }
            db.commit()
            Page(rows, count)
        }

    fun get(id: Long): Item? =
        connect().use { db ->
            db.prepareStatement("SELECT $columns FROM found_item WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) item(rs) else null }
            }
        }

    fun deletionForm(id: Long): Pair<Item, UUID> =
        connect().use { db ->
            db.prepareStatement("SELECT $columns, delete_token FROM found_item WHERE id=?").use { ps
                ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) throw InputProblem(Problem.MISSING)
                    item(rs) to rs.getObject("delete_token", UUID::class.java)
                }
            }
        }

    fun delete(id: Long, token: UUID) {
        connect().use { db ->
            db.prepareStatement("DELETE FROM found_item WHERE id=? AND delete_token=?").use { ps ->
                ps.setLong(1, id)
                ps.setObject(2, token)
                if (ps.executeUpdate() == 0) {
                    if (get(id) == null) throw InputProblem(Problem.MISSING)
                    throw InputProblem(Problem.DELETE_CONFIRMATION)
                }
            }
        }
    }

    fun importExamples(): Boolean =
        connect().use { db ->
            db.autoCommit = false
            try {
                db.createStatement().use { sql ->
                    try {
                        sql.executeUpdate(
                            "INSERT INTO example_import(batch) VALUES ('campus-2026-11')"
                        )
                    } catch (error: SQLIntegrityConstraintViolationException) {
                        if (error.sqlState != "23505") throw error
                        db.rollback()
                        return false
                    }
                    val examples = checkNotNull(javaClass.getResource("/lost-found/examples.sql"))
                    val insert = examples.openStream().bufferedReader().use { it.readText() }
                    sql.executeUpdate(insert)
                }
                db.commit()
                true
            } catch (error: Exception) {
                db.rollback()
                throw error
            }
        }

    fun photo(id: Long): Photo =
        connect().use { db ->
            db.prepareStatement("SELECT photo_type,photo FROM found_item WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) throw InputProblem(Problem.MISSING)
                    val type = rs.getString(1) ?: throw InputProblem(Problem.NO_PHOTO)
                    Photo(rs.getBytes(2), type)
                }
            }
        }

    companion object {
        const val PAGE_SIZE = 24
    }
}
