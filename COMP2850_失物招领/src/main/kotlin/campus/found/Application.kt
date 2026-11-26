package campus.found

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.ClasspathLoader
import java.io.IOException
import java.io.StringWriter
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Path
import java.sql.SQLException
import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BASE = "/lost-found"
private const val MAX_BODY = MAX_PHOTO + 128 * 1024
private val templateEngine =
    PebbleEngine.Builder()
        .loader(ClasspathLoader().apply { prefix = "lost-found/templates/" })
        .autoEscaping(true)
        .build()
private val acceptedFields = setOf("name", "date", "tags", "place", "key")

private fun today() = LocalDate.now(ZoneId.of("Europe/London"))

private fun tidy(s: String) = Normalizer.normalize(s.trim(), Normalizer.Form.NFKC)

private fun encoded(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

private fun browseUrl(q: String, page: Int) = "$BASE?q=" + encoded(q) + "&page=$page"

private suspend fun ApplicationCall.render(
    template: String,
    model: Map<String, Any?> = emptyMap(),
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val output = StringWriter()
    templateEngine.getTemplate(template).evaluate(output, model)
    respondText(output.toString(), ContentType.Text.Html.withCharset(Charsets.UTF_8), status)
}

private suspend fun ApplicationCall.problem(p: Problem) =
    render("error.peb", mapOf("problem" to p), p.status)

private suspend fun ApplicationCall.form(
    fields: Map<String, String> = emptyMap(),
    error: Problem? = null,
) {
    val data =
        mapOf("name" to "", "tags" to "", "place" to "", "date" to today().toString()) + fields
    render(
        "form.peb",
        mapOf(
            "form" to data,
            "key" to
                (fields["key"]?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
                    ?: UUID.randomUUID().toString()),
            "today" to today().toString(),
            "error" to error,
        ),
        error?.status ?: HttpStatusCode.OK,
    )
}

private suspend fun ApplicationCall.listItems(store: Items, partial: Boolean) {
    val q = tidy(request.queryParameters["q"].orEmpty())
    if (q.length > 100) throw InputProblem(Problem.QUERY)
    val page =
        (request.queryParameters["page"] ?: "1").toIntOrNull()?.takeIf { it > 0 }
            ?: throw InputProblem(Problem.PAGE)
    val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }.distinct()
    val result = withContext(Dispatchers.IO) { store.search(words, page) }
    if (page > 1 && (page - 1L) * Items.PAGE_SIZE >= result.total) throw InputProblem(Problem.PAGE)
    val context =
        mapOf(
            "q" to q,
            "items" to result.items,
            "total" to result.total,
            "prev" to if (page > 1) browseUrl(q, page - 1) else null,
            "next" to
                if (page.toLong() * Items.PAGE_SIZE < result.total) browseUrl(q, page + 1)
                else null,
        )
    render(if (partial) "results.peb" else "index.peb", context)
}

private fun id(call: ApplicationCall) =
    call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 } ?: throw InputProblem(Problem.MISSING)

private fun ApplicationCall.checkFormOrigin() {
    val origin = request.header(HttpHeaders.Origin)
    if (
        origin != null &&
            runCatching {
                    URI(origin)
                        .rawAuthority
                        .equals(request.header(HttpHeaders.Host), ignoreCase = true)
                }
                .getOrDefault(false)
                .not()
    )
        throw InputProblem(Problem.ORIGIN)
}

private suspend fun ApplicationCall.report(store: Items) {
    checkFormOrigin()
    if (!request.contentType().match(ContentType.MultiPart.FormData))
        throw InputProblem(Problem.FORM)
    if ((request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0L) > MAX_BODY)
        throw InputProblem(Problem.BODY)
    val fields = mutableMapOf<String, String>()
    var image: Photo? = null
    var sawFile = false
    var parts = 0
    try {
        receiveMultipart(formFieldLimit = MAX_BODY.toLong()).forEachPart { part ->
            try {
                if (++parts > 7) throw InputProblem(Problem.FORM)
                when (part) {
                    is PartData.FormItem -> {
                        val field = part.name
                        if (
                            field == null ||
                                field !in acceptedFields ||
                                fields.containsKey(field) ||
                                part.value.length > 4096
                        )
                            throw InputProblem(Problem.FORM)
                        fields[field] = tidy(part.value)
                    }
                    is PartData.FileItem -> {
                        if (part.name != "photo" || sawFile) throw InputProblem(Problem.FORM)
                        sawFile = true
                        if (!part.originalFileName.isNullOrBlank()) {
                            val bytes =
                                withContext(Dispatchers.IO) {
                                    part.provider().toInputStream().use {
                                        it.readNBytes(MAX_PHOTO + 1)
                                    }
                                }
                            image = withContext(Dispatchers.Default) { processPhoto(bytes) }
                        }
                    }
                    else -> throw InputProblem(Problem.FORM)
                }
            } finally {
                part.dispose()
            }
        }
        val key =
            fields["key"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw InputProblem(Problem.FORM)
        val name = fields["name"].orEmpty()
        if (name.isEmpty() || name.length > 100) throw InputProblem(Problem.NAME)
        val date =
            fields["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: throw InputProblem(Problem.DATE)
        if (date.year !in 1..9999) throw InputProblem(Problem.DATE)
        if (date > today()) throw InputProblem(Problem.FUTURE)
        val place = fields["place"].orEmpty()
        if (place.length > 120) throw InputProblem(Problem.PLACE)
        val tags =
            fields["tags"]
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase(Locale.ROOT) }
        if (tags.size > 8 || tags.any { it.length > 24 }) throw InputProblem(Problem.TAGS)
        val created =
            withContext(Dispatchers.IO) { store.save(key, name, date, place, tags, image) }
        response.header(HttpHeaders.Location, "$BASE/items/$created")
        respond(HttpStatusCode.SeeOther)
    } catch (failure: InputProblem) {
        form(fields, failure.problem)
    } catch (_: IOException) {
        form(fields, Problem.FORM)
    }
}

fun Application.lostFoundModule(directory: Path) {
    val store = Items(directory)
    install(StatusPages) {
        exception<InputProblem> { call, ex -> call.problem(ex.problem) }
        exception<BadRequestException> { call, _ -> call.problem(Problem.FORM) }
        exception<SQLException> { call, ex ->
            this@lostFoundModule.log.error("Lost & found database error", ex)
            call.problem(Problem.STORAGE)
        }
        exception<Throwable> { call, ex ->
            if (ex is CancellationException) throw ex
            this@lostFoundModule.log.error("Lost & found request failed", ex)
            call.problem(Problem.INTERNAL)
        }
        status(HttpStatusCode.NotFound) { call, _ -> call.problem(Problem.ROUTE) }
    }
    routing {
        get("/") { call.respondRedirect(BASE) }
        lostFoundRoutes(store)
    }
}

fun Route.lostFoundRoutes(store: Items) {
    staticResources("/lost-found/assets", "lost-found/assets")
    route(BASE) {
        get { call.listItems(store, false) }
        get("/results") { call.listItems(store, true) }
        get("/new") { call.form() }
        post("/items") { call.report(store) }
        get("/items/{id}") {
            val key = id(call)
            val item =
                withContext(Dispatchers.IO) { store.get(key) }
                    ?: throw InputProblem(Problem.MISSING)
            call.render(
                "detail.peb",
                mapOf(
                    "item" to item,
                    "tagLinks" to item.tags.map { mapOf("label" to it, "url" to browseUrl(it, 1)) },
                ),
            )
        }
        get("/items/{id}/delete") {
            val key = id(call)
            val (item, token) = withContext(Dispatchers.IO) { store.deletionForm(key) }
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.response.header("X-Frame-Options", "DENY")
            call.render("delete.peb", mapOf("item" to item, "token" to token.toString()))
        }
        post("/items/{id}/delete") {
            call.checkFormOrigin()
            val key = id(call)
            val raw =
                call.receiveParameters()["token"] ?: throw InputProblem(Problem.DELETE_CONFIRMATION)
            val token =
                runCatching { UUID.fromString(raw) }.getOrNull()
                    ?: throw InputProblem(Problem.DELETE_CONFIRMATION)
            withContext(Dispatchers.IO) { store.delete(key, token) }
            call.response.header(HttpHeaders.Location, BASE)
            call.respond(HttpStatusCode.SeeOther)
        }
        get("/items/{id}/photo") {
            val key = id(call)
            val photo = withContext(Dispatchers.IO) { store.photo(key) }
            call.response.header("X-Content-Type-Options", "nosniff")
            call.respondBytes(photo.bytes, ContentType.parse(photo.type))
        }
    }
}

fun main(args: Array<String>) {
    val port = (System.getenv("PORT") ?: "8080").toInt()
    val data = Path.of(System.getenv("LOST_FOUND_DATA") ?: "data")
    if (args.contentEquals(arrayOf("--load-examples"))) {
        println(
            if (Items(data).importExamples()) "Campus examples added."
            else "Campus examples already imported."
        )
        return
    }
    embeddedServer(CIO, host = "127.0.0.1", port = port) { lostFoundModule(data) }
        .start(wait = true)
}
