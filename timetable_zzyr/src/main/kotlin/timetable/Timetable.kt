package timetable

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class TimetableEntry(
    val date: LocalDate,
    val moduleCode: String,
    val moduleName: String,
    val type: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val room: String
)

private const val HEADER = "date,moduleCode,moduleName,type,startTime,endTime,room"
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

fun parseDate(value: String): LocalDate {
    require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value))
    return LocalDate.parse(value).also { require(it.year in 1..9999) }
}

/** Deliberately small CSV format: seven non-empty, unquoted fields per line. */
fun readTimetable(path: Path): List<TimetableEntry> {
    val lines = Files.readAllLines(path)
    require(lines.isNotEmpty() && lines.first().removePrefix("\uFEFF") == HEADER) { "Invalid CSV header" }
    return lines.drop(1).filter { it.isNotBlank() }.map { line ->
        require('"' !in line) { "Quoted CSV fields are not supported" }
        val fields = line.split(',').map(String::trim)
        require(fields.size == 7 && fields.all { it.isNotEmpty() }) { "Invalid CSV fields" }
        val start = LocalTime.parse(fields[4], timeFormat)
        val end = LocalTime.parse(fields[5], timeFormat)
        require(end > start) { "Class must end after its start" }
        TimetableEntry(parseDate(fields[0]), fields[1], fields[2], fields[3], start, end, fields[6])
    }
}

fun classesOn(entries: List<TimetableEntry>, date: LocalDate): List<TimetableEntry> =
    entries.filter { it.date == date }.sortedWith(compareBy({ it.startTime }, { it.endTime }, { it.moduleCode }))
