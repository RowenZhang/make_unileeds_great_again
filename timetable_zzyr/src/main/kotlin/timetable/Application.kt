package timetable

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.pebbletemplates.pebble.loader.ClasspathLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    timetableModule(Path.of(System.getenv("TIMETABLE_CSV") ?: "data/timetable.csv"))
}

fun Application.timetableModule(csvPath: Path, clock: Clock = Clock.system(ZoneId.of("Europe/London"))) {
    install(Pebble) { loader(ClasspathLoader().apply { prefix = "templates" }); autoEscaping(true) }
    routing {
        get("/") { call.respondRedirect("/timetable") }
        staticResources("/timetable/static", "static")
        get("/timetable") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val today = LocalDate.now(clock)
            val requested = call.request.queryParameters["date"]
            val date = try { requested?.let(::parseDate) ?: today } catch (_: IllegalArgumentException) { null }
                catch (_: DateTimeException) { null }
            var status = HttpStatusCode.OK
            var error: String? = null
            var entries = emptyList<TimetableEntry>()
            if (date == null) {
                status = HttpStatusCode.BadRequest
                error = "Choose a valid date in YYYY-MM-DD format."
            } else {
                try {
                    entries = withContext(Dispatchers.IO) { readTimetable(csvPath) }
                } catch (_: IOException) {
                    status = HttpStatusCode.ServiceUnavailable
                    error = "We couldn't load your timetable. Please try again later."
                    log.warn("Timetable file could not be read")
                } catch (_: IllegalArgumentException) {
                    status = HttpStatusCode.ServiceUnavailable
                    error = "We couldn't load your timetable. Please try again later."
                    log.warn("Timetable file contains invalid data")
                } catch (_: DateTimeException) {
                    status = HttpStatusCode.ServiceUnavailable
                    error = "We couldn't load your timetable. Please try again later."
                    log.warn("Timetable file contains invalid dates or times")
                }
            }
            val selected = date ?: today
            val classes = classesOn(entries, selected)
            val monday = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val days = (0L..6L).map { offset ->
                val day = monday.plusDays(offset)
                mapOf("date" to day.toString(), "name" to day.format(DateTimeFormatter.ofPattern("EEE", Locale.UK)),
                    "number" to day.dayOfMonth, "selected" to (day == selected), "supported" to (day.year in 1..9999))
            }
            val cards = classes.map { entry -> mapOf("code" to entry.moduleCode, "name" to entry.moduleName,
                "type" to entry.type, "start" to entry.startTime.toString(), "end" to entry.endTime.toString(), "room" to entry.room) }
            val model = mapOf(
                "date" to selected.toString(), "heading" to selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.UK)),
                "isToday" to (selected == today), "days" to days, "classes" to cards, "count" to classes.size,
                "previousWeek" to (selected.minusWeeks(1).takeIf { it.year in 1..9999 }?.toString() ?: ""),
                "nextWeek" to (selected.plusWeeks(1).takeIf { it.year in 1..9999 }?.toString() ?: ""),
                "error" to (error ?: ""),
                "range" to if (entries.isEmpty()) "" else "${entries.minOf { it.date }} to ${entries.maxOf { it.date }}"
            )
            call.respond(status, PebbleContent("timetable.peb", model))
        }
    }
}
