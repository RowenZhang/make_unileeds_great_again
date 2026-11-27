# Verification record

Date: 26 November 2026. Environment: Windows, Temurin JDK 25.0.4.1, Gradle 9.2.1, Kotlin 2.3.21, Ktor 3.5.2.

## Actual automated execution

Command: `gradlew.bat build --offline --no-daemon` (after initial dependency download).

Result: **BUILD SUCCESSFUL**, 16 tests, 0 failures, 0 errors, 0 skipped. The final JUnit run timestamp is `2026-11-26T20:53:24.531Z`. `installDist` also completed successfully, and its Netty launcher served the browser checks below.

The portable HTML report is copied into `docs/test-report/index.html`. Re-running tests creates a new report under `build/reports/tests/test/`; the copied report records this delivery only.

Coverage:

1. Today's date, chronological order and required fields.
2. Selected-date filtering.
3. Valid empty day distinct from failure.
4. Missing file: HTTP 503 without internal paths/stack traces.
5. Malformed row: HTTP 503, no partial schedule.
6. Invalid calendar date: HTTP 400.
7. Blank date: HTTP 400.
8. HTML in data safely escaped.
9. Edited data read again without restart; no-store header.
10. Stylesheet served, CSV not public, root redirects.
11. Earliest/latest supported date boundaries.
12. Previous/next week URLs and Today destination.
13. Invalid header, missing field, invalid time, reversed interval, invalid date and quoted-field rejection.
14. Header-only empty dataset.
15. UTF-8 BOM and blank lines.
16. London summer-time date across UTC midnight.

An initial compiler issue in the template model's nullable values was corrected. An initial test expected whitespace between separate time elements; the assertion was corrected to check their semantic elements independently. The final complete run passed. Sandbox restrictions on local sockets required running Gradle and the preview server outside the sandbox; this was an environment limitation, not an application feature change.

## Actual browser checks

The real Netty application was opened at `http://127.0.0.1:8080/timetable` in the in-app browser.

| Check | Observed result |
| --- | --- |
| 26 November populated view | Three classes, ordered 09:00 / 11:00 / 14:00; times, codes, rooms visible |
| Desktop viewport 1280 × 900 | Full-page visual review passed; document width 1265, viewport 1280, no horizontal overflow |
| Enter 28 November and submit form | Selected day changed; zero classes and explicit no-classes message |
| Today link | Returned to 26 November 2026 at `/timetable` |
| Mobile viewport 390 × 844 | Full-page visual review passed; document and viewport width both 390; controls wrap and cards remain readable |
| Next week link | Selected 3 December 2026 and displayed its three classes |
| Invalid date 30 February | Visible date-correction guidance, no class cards |

The temporary viewport override was reset after testing. This is browser viewport testing, not a claim of testing on a physical phone. It is not a participant UX study. Missing-file/malformed-file responses were covered with the Ktor test host; no real participant feedback is claimed.

## Remaining verification after group integration

Test combined routes, shared template/plugin configuration, navigation and real deployment working directory. Check the target classroom/demo browser and device. There is no school API or true school synchronisation in this MVP.
