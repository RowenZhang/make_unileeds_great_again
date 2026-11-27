package timetable

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.jsoup.Jsoup
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.*
import kotlin.io.path.writeText
import kotlin.test.*

class TimetableTest {
    @TempDir lateinit var temp: Path
    private val clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneId.of("Europe/London"))
    private val header = "date,moduleCode,moduleName,type,startTime,endTime,room\n"
    private val rows = "2026-11-26,COMP2850,Software Engineering,Lab,14:00,16:00,Lab 2\n" +
        "2026-11-26,COMP2811,User Interfaces,Lecture,09:00,10:00,Room 1\n" +
        "2026-11-27,COMP2711,Algorithms,Seminar,11:00,12:00,Room 3\n"
    private fun csv(content: String = header + rows) = temp.resolve("timetable.csv").apply { writeText(content) }
    private fun checkPage(path: String, file: Path = csv(), verify: (HttpStatusCode, String) -> Unit) = testApplication {
        environment { config = MapApplicationConfig() }
        application { timetableModule(file, clock) }
        val response = client.get(path)
        verify(response.status, response.bodyAsText())
    }

    @Test fun `default today sorts classes and shows required fields`() = checkPage("/timetable") { status, html ->
        assertEquals(HttpStatusCode.OK, status)
        val doc = Jsoup.parse(html)
        assertEquals("2026-11-26", doc.selectFirst("input[name=date]")!!.attr("value"))
        val cards = doc.select(".class-card")
        assertEquals(2, cards.size)
        assertEquals(listOf("COMP2811", "COMP2850"), cards.map { it.select(".module-code").text() })
        assertEquals("09:00", cards[0].select(".time time").text())
        assertEquals("to 10:00", cards[0].select(".time span").text())
        assertContains(cards[0].text(), "Room 1")
        assertEquals("get", doc.selectFirst("form")!!.attr("method"))
        assertEquals("/timetable", doc.selectFirst("form")!!.attr("action"))
        assertEquals(7, doc.select(".week-strip a").size)
        assertEquals("2026-11-26", doc.selectFirst(".week-strip [aria-current]")!!.attr("href").substringAfter("date="))
    }
    @Test fun `selected day filters other classes`() = checkPage("/timetable?date=2026-11-27") { status, html ->
        assertEquals(HttpStatusCode.OK, status)
        assertContains(html, "COMP2711")
        assertFalse(html.contains("COMP2850" + "</span>"))
        assertEquals(1, Jsoup.parse(html).select(".class-card").size)
    }
    @Test fun `empty day is successful and distinct from failure`() = checkPage("/timetable?date=2026-11-28") { status, html ->
        assertEquals(HttpStatusCode.OK, status)
        assertContains(html, "No classes scheduled")
        assertFalse(html.contains("Timetable unavailable"))
    }
    @Test fun `missing file returns helpful error without path`() = checkPage("/timetable", temp.resolve("private-missing.csv")) { status, html ->
        assertEquals(HttpStatusCode.ServiceUnavailable, status)
        assertContains(html, "Timetable unavailable")
        assertFalse(html.contains("No classes scheduled"))
        assertFalse(html.contains("private-missing"))
        assertFalse(html.contains("Exception"))
    }
    @Test fun `malformed row fails instead of silently omitting classes`() = checkPage("/timetable", csv(header + rows + "broken,row")) { status, html ->
        assertEquals(HttpStatusCode.ServiceUnavailable, status)
        assertTrue(Jsoup.parse(html).select(".class-card").isEmpty())
    }
    @Test fun `invalid calendar date is rejected`() = checkPage("/timetable?date=2026-02-30") { status, html ->
        assertEquals(HttpStatusCode.BadRequest, status)
        assertContains(html, "Choose a valid date")
    }
    @Test fun `blank date is rejected`() = checkPage("/timetable?date=") { status, _ -> assertEquals(HttpStatusCode.BadRequest, status) }
    @Test fun `html in data is escaped`() = checkPage("/timetable", csv(header + rows.replace("Software Engineering", "<script>alert(1)</script>"))) { status, html ->
        assertEquals(HttpStatusCode.OK, status)
        assertTrue(Jsoup.parse(html).select("script").isEmpty())
        assertContains(Jsoup.parse(html).select(".class-card").text(), "<script>alert(1)</script>")
    }
    @Test fun `data is reread after edit without restarting`() = testApplication {
        val file = csv()
        environment { config = MapApplicationConfig() }
        application { timetableModule(file, clock) }
        assertContains(client.get("/timetable").bodyAsText(), "Room 1")
        file.writeText(header + rows.replace("Room 1", "New Room"))
        val response = client.get("/timetable")
        assertContains(response.bodyAsText(), "New Room")
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }
    @Test fun `stylesheet served but csv remains private`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { timetableModule(csv(), clock) }
        assertEquals(HttpStatusCode.OK, client.get("/timetable/static/timetable.css").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/data/timetable.csv").status)
        val noRedirect = createClient { followRedirects = false }
        assertEquals("/timetable", noRedirect.get("/").headers[HttpHeaders.Location])
    }
    @Test fun `date boundaries render without exception`() {
        for (date in listOf("0001-01-01", "9999-12-31")) checkPage("/timetable?date=$date") { status, _ -> assertEquals(HttpStatusCode.OK, status) }
    }
    @Test fun `next week links retain selected weekday`() = checkPage("/timetable") { _, html ->
        val links = Jsoup.parse(html).select(".week-controls a").map { it.attr("href") }
        assertEquals(listOf("/timetable?date=2026-11-19", "/timetable?date=2026-12-03"), links)
        assertEquals("/timetable", Jsoup.parse(html).selectFirst("a.secondary")!!.attr("href"))
    }
    @Test fun `CSV validation rejects bad header missing fields bad time and reversed interval`() {
        for (content in listOf("", "wrong\n" + rows, header + rows.replace("Room 1", ""),
            header + rows.replace("09:00", "25:00"), header + rows.replace("09:00", "11:00"),
            header + rows.replace("2026-11-26", "2026-02-30"), header + rows.replace("Room 1", "\"Room 1\""))) {
            assertFails { readTimetable(csv(content)) }
        }
    }
    @Test fun `header only is valid empty dataset`() { assertTrue(readTimetable(csv(header)).isEmpty()) }
    @Test fun `BOM and blank lines supported`() { assertEquals(3, readTimetable(csv("\uFEFF" + header + rows + "\n")).size) }
    @Test fun `today uses Leeds timezone across midnight`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { timetableModule(csv(), Clock.fixed(Instant.parse("2026-06-01T23:30:00Z"), ZoneId.of("Europe/London"))) }
        assertEquals("2026-06-02", Jsoup.parse(client.get("/timetable").bodyAsText()).selectFirst("input[name=date]")!!.attr("value"))
    }
}
