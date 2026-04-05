# API Test Generator

Spring Boot app with a static front end that turns cURL commands into executable Cucumber features. The UI helps generate request coverage, attach datasets, edit the resulting feature file, and run the generated tests without leaving the browser.

## What It Does

- Parses cURL commands into structured API test scenarios
- Generates Cucumber feature files for happy-path and negative-path coverage
- Supports CSV/XLSX datasets for scenario outlines
- Runs generated tests from the UI and stores per-run logs
- Exposes a lightweight auth gate for demo deployments

## Quick Start

1. Start the app:

   ```bash
   ./mvnw spring-boot:run
   ```

2. Optionally set demo login credentials:

   ```bash
   export APP_LOGIN_EMAIL=demo@example.com
   export APP_LOGIN_PASSWORD=change-me
   ```

3. Open `http://localhost:8080/`
4. Paste a cURL command, adjust the generator options, and submit
5. Review the generated feature under `src/test/resources/features/generated/`
6. Run the generated test from the UI and inspect logs under `target/ui-runs/<run-id>/`

## Configuration

Example local environment values are in `.env.example`.

Test configuration defaults live in `src/test/resources/test.properties`. Replace the placeholder base URL and tokens with values from your own environment.

## Project Notes

- Build output and generated samples are ignored to keep the repository clean.
- The login flow uses environment-configured demo credentials instead of source-controlled secrets.
- GitHub auto-publish settings can be configured through `GITHUB_*` environment variables if you want generated artifacts pushed to another repository.
