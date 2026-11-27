# Demonstration and handover

## Five-minute demonstration

1. Start the application from the project folder; open `/timetable?date=2026-11-26`.
2. Point out the sample-data notice. Show the chronologically ordered class times, module codes and rooms. CSV file order is deliberately different.
3. Select 27 November in the date form. Use a day link, Next week, Previous week and Today.
4. Select 28 November: a successful empty-day message appears.
5. In another browser request `/timetable?date=2026-02-30`: correction guidance appears.
6. To demonstrate a data failure safely, stop the app and restart with `TIMETABLE_CSV` pointing to a nonexistent file. The page shows an error, not “no classes”. Remove that environment override and restart to recover.
7. Optionally edit a room in the CSV, refresh to show the updated value without restarting, then restore the sample data.
8. Show `./gradlew test` (Windows: `.\gradlew.bat test`) and the generated HTML report.

## Student/group activities still requiring real participation

The source code, design notes and recorded technical tests can be used in the project. They do not substitute for attendance, personal reflection, genuine user feedback, teammate review or a group demonstration.

- Review and understand the code, especially routes, validation and tests.
- With teammates, agree branch policy and integration versions; integrate all features and perform real integration tests.
- Copy suitable design/test notes into GitHub Wiki when ready. No remote Wiki content has been changed by this delivery.
- Write each weekly Portfolio from actual work and learning. Do not retroactively invent dates, meetings or feedback.
- Present the working feature at the end of semester.
- Optional: run a small UX session using the protocol below. Two prescribed rounds for the Final Project have not been treated as a Mini Project requirement.

## Optional UX protocol (not executed)

Ask a consenting classmate to use the demo. Explain that this tests the interface, not the participant. Use 26 November 2026 as the starting date.

| Task | Record |
| --- | --- |
| Find the first class's room on the selected day | Completion, elapsed time, wrong turns, comments |
| Find the last class's end time on 27 November | Same observations |
| Find classes on 3 December using week navigation | Same observations |
| Explain the 28 November no-classes state | Whether understood correctly |

Record real observations, issues, chosen improvements and a retest. No participant sessions or results are claimed in this package.

## Suggested portfolio prompts

- What requirement did I implement, and why does it help the persona?
- How do the happy-path and sad-path tests demonstrate it?
- What did I learn about Kotlin types, Ktor routing and Pebble templates?
- What failed during development, how was it fixed, and what evidence supports the result?
- What will I review with my teammates before integration?

## GitHub rule

Show changed files and the proposed commit message to the user before each commit. Commit and push only after explicit approval. Do not reuse an old directory-setup approval for this implementation. The package contains no Git repository or commit history.
