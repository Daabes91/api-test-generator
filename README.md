# API Test Generator & Runner

This project packages a Spring Boot backend and static UI for turning cURL commands into executable Cucumber features. QAs can now stay inside the UI to generate test cases, edit them, and run them without switching to Postman or a terminal.

## Quick Start

1. **Run the app**: `mvn spring-boot:run`
2. **Sign in**: open `http://localhost:8080/` and use the credentials shown on the login page.
3. **Paste a cURL** export (or pick a template) and complete the wizard fields.
4. **Generate**: submit the form to create a feature under `src/test/resources/features/generated/` (datasets land in `src/test/resources/datasets/`).
5. **Review**: tweak assertions, add scenarios, or download the file from the preview panel.
6. **Run from the UI**:
   - Click **Run generated test** to launch a Maven/Cucumber job targeting the generated feature.
   - Watch the live status banner; it auto-polls until the run finishes.
   - Open **Runs history** to browse recent jobs, with inline links to their logs.
   - Failed runs surface the scenario name and first error line right in the status panel (logs stay available).
   - Use the **Run** button next to any scenario to execute just that scenario.
7. **Debug**: view the job log (streamed from `target/ui-runs/<jobId>/run.log`) or rerun after editing the feature.

## Test Runner Details

- Jobs execute `mvn -q test -Dtest=runner.RunCucumberTest` with optional filters for the generated feature.
- Results, timestamps, and exit codes are tracked in memory by `TestRunService` and exposed via:
  - `POST /api/tests/run`
  - `GET /api/tests`
  - `GET /api/tests/{id}`
  - `GET /api/tests/{id}/log`
- Logs are stored under `target/ui-runs/<jobId>/run.log` for easy retrieval.

## Tips for QAs

- Use the **hand-off summary** button to copy form settings into a ticket.
- Upload CSV/XLSX datasets to exercise multiple rows; the generator previews mismatched headers and JSON types.
- The run history keeps the latest 12 jobs; rerun or inspect logs directly from the drawer.
- Remember to click **Save changes** if you edit the feature preview before re-running.

