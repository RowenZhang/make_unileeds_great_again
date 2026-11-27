# Daily Timetable

My Daily Timetable feature for the COMP2850 UniLeeds replacement mini project.

I'm Zhengyunrui Zhang. I'm responsible for this feature within the group. The aim is to let students check their classes for a selected day, including times, module codes and rooms. I've kept it as a small, independently runnable Ktor application so we can integrate it with the other features later.

## Features

- Opens on today's date in the Europe/London time zone.
- Date picker, day links, previous/next week navigation and a Today button.
- Classes sorted by start time, with module code, name, type, start/end times and room.
- Separate messages for no classes, invalid dates and unavailable timetable data.
- Responsive layout for desktop and mobile screens.

## Tech stack

- Kotlin 2.3.21
- Ktor 3.5.2 with Netty
- Pebble templates and local CSS
- Gradle Wrapper 9.2.1
- Local CSV data
- Kotlin Test, JUnit 5, Ktor Test Host and Jsoup

The project targets Java 17 bytecode. The recorded build and tests used Temurin JDK 25.

## Running locally

Use JDK 17–25 and configure `JAVA_HOME` if necessary. The first run needs an internet connection to download dependencies. Gradle does not need to be installed separately.

Run commands from inside the `timetable_zzyr` folder so the application can find `data/timetable.csv`.

### Git Bash, macOS or Linux

```bash
./gradlew run
```

If the script does not have execute permission:

```bash
chmod +x gradlew
./gradlew run
```

### Windows PowerShell

```powershell
.\gradlew.bat run
```

Open [http://localhost:8080/timetable](http://localhost:8080/timetable). Keep the terminal running while using the application and press `Ctrl+C` to stop it. Gradle continues to show `EXECUTING` while the server is running; this is expected.

The folder can also be opened in IntelliJ IDEA as a Gradle project.

### Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | Server port |
| `TIMETABLE_CSV` | `data/timetable.csv` | Path to the timetable file |

For example, in Bash:

```bash
PORT=8081 ./gradlew run
```

Use an absolute path when setting `TIMETABLE_CSV`. The server binds to `127.0.0.1` by default.

## Sample data

I use local CSV data for the MVP. The sample contains fictional classes between **23 November and 4 December 2026**, with no classes at weekends. Rows are deliberately out of order so sorting can be checked.

For a populated example, open [26 November 2026](http://localhost:8080/timetable?date=2026-11-26). Dates outside the sample range show that no classes are scheduled in the demo data.

The CSV uses this header and field order:

```csv
date,moduleCode,moduleName,type,startTime,endTime,room
2026-11-26,COMP2850,Software Engineering,Lab,14:00,16:00,Computing Lab 2
```

Format rules:

- UTF-8, one class per line, seven non-empty fields.
- Dates use `YYYY-MM-DD`, with years from 0001 to 9999.
- Times use `HH:mm`; the end must be later than the start on the same day.
- Fields cannot contain commas, quotation marks or line breaks.
- Blank lines and an optional UTF-8 BOM are accepted.

The file is read on every timetable request, so edits appear after refreshing without restarting. If any record is invalid, the application shows an error instead of displaying a partial schedule. A file containing only the valid header represents an empty timetable.

## Project structure

```text
timetable_zzyr/
  build.gradle.kts
  settings.gradle.kts
  gradlew / gradlew.bat
  gradle/wrapper/
  data/timetable.csv
  src/main/kotlin/timetable/
    Application.kt
    Timetable.kt
  src/main/resources/
    application.conf
    logback.xml
    templates/timetable.peb
    static/timetable.css
  src/test/kotlin/timetable/
    TimetableTest.kt
  docs/
```

`Application.kt` configures the application, handles requests and passes the page model to Pebble. `Timetable.kt` contains the course data model, CSV validation, date filtering and sorting.

I kept these responsibilities in two Kotlin files because the feature is small. The page uses a standard HTML GET form and server-side rendering, so its main interactions work without JavaScript. CSS is served locally, and the CSV is outside the public resources.

## Routes

| Route | Behaviour |
| --- | --- |
| `GET /` | Redirects to `/timetable` |
| `GET /timetable` | Shows today in Europe/London |
| `GET /timetable?date=YYYY-MM-DD` | Shows the selected day |
| `GET /timetable/static/timetable.css` | Serves the stylesheet |

A valid request returns `200`, including days with no classes. Invalid dates return `400`. Missing, unreadable or malformed data returns `503` with a readable error message. Internal file paths and stack traces are not included in those messages.

## Tests and build

In Bash:

```bash
./gradlew test
./gradlew build
```

In PowerShell, use `.\gradlew.bat` instead of `./gradlew`.

Tests cover date filtering, sorting, required fields, empty dates, missing files, malformed records, invalid dates, HTML escaping and reading changed data without a restart. They also check date boundaries, week links, static resources and London time-zone behaviour.

The recorded verification run passed all **16 tests** and completed the build successfully. The generated report is at `build/reports/tests/test/index.html`; a copy of the recorded report is in `docs/test-report/index.html`.

Desktop and mobile layouts were checked in a browser at 1280 × 900 and 390 × 844. Details are in `docs/TEST_RESULTS.md`.

To create a local application distribution:

```bash
./gradlew installDist
```

Launchers are generated in `build/install/timetable_zzyr/bin/`. Run them from the project root, or set `TIMETABLE_CSV` to an absolute path.

## Scope and limitations

The original user story includes automatic timetable synchronisation. This version reads a local file; it does not connect to the university timetable service. All included classes and rooms are demonstration data.

For this MVP, I've left out login, individual student accounts, course editing, notifications, maps and calendar integration. The CSV format is limited to the documented fields. Real user feedback and testing after group integration are still outstanding.

## Group integration

This feature currently runs independently in `timetable_zzyr/`. When we combine the group's features, we'll need to agree dependency versions, shared navigation and application configuration.

The `timetableModule(csvPath)` function provides the feature's routes and template configuration. A shared application can call it with the agreed data path. If Pebble is already installed in the shared application, its setup needs to be consolidated so the plugin is installed only once. The template and static resources also need to be included.

## Development notes

- `docs/REQUIREMENTS_AND_DESIGN.md`: user story, acceptance criteria, wireframe and request sequence.
- `docs/TEST_RESULTS.md`: recorded automated and browser checks.
- `docs/HANDOVER.md`: demonstration steps, integration notes and proposed UX tasks.
- `开始使用.md`: Chinese setup instructions.

## References

- [Course guide: project creation](https://comp2850.github.io/ktor-guide/ktor/projtools)
- [Course guide: templates](https://comp2850.github.io/ktor-guide/ktor/templates)
- [Course guide: testing](https://comp2850.github.io/ktor-guide/ktor/testing)
- Mini Project specification and the group's Daily Timetable user story and acceptance criteria.

