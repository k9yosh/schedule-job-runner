# Real-time Spring Batch Job Monitor

This project provides a web backend for launching, monitoring, and viewing the history of Spring Batch jobs in real-time. It uses Spring Boot, Spring Batch, Spring WebFlux (for reactive streams), and an in-memory H2 database.

## Features

*   Launch Spring Batch jobs via a REST API.
*   View currently running jobs via a REST API (`/dashboard-snapshot`).
*   Stream real-time status updates for job executions using Server-Sent Events (`/stream`).
*   Retrieve recent job execution history (including completed, failed, stopped) for specific jobs (`/{jobName}/recent`).
*   Retrieve details for a specific job execution (`/execution/{id}`).
*   Includes several pre-configured simulated jobs for testing:
    *   `simulatedJob`, `simulatedJob2`, `simulatedJob3`: Simulate successful completion after a configurable duration.
    *   `simulatedJob4`: Simulates failure after a configurable duration.
    *   `simulatedJob5`: Simulates being stopped after a configurable duration.

## Prerequisites

*   **Java Development Kit (JDK) 21** or later.
*   An internet connection (for downloading Gradle dependencies on the first run).

## Building and Running

The project uses the Gradle wrapper, so you don't need to install Gradle separately.

1.  **Clone the repository:**
    ```bash
    git clone <your-repo-url>
    cd schedule-job-runnner 
    ```
2.  **Build the project:** (Optional, as `bootRun` builds automatically)
    ```bash
    ./gradlew build 
    ```
    (On Windows, use `gradlew.bat build`)

3.  **Run the application:**
    ```bash
    ./gradlew bootRun
    ```
    (On Windows, use `gradlew.bat bootRun`)

The application will start, and the API will be available at `http://localhost:8080` by default.

## API Endpoints

*   `POST /api/jobs/launch/{jobName}`: Launches the specified job.
    *   `{jobName}` can be `simulatedJob`, `simulatedJob2`, `simulatedJob3`, `simulatedJob4`, or `simulatedJob5`.
    *   Optional JSON body: `{"customJobName": "...", "durationInSeconds": ...}`
    *   Example: `curl -X POST -H "Content-Type: application/json" -d '{"durationInSeconds": 15}' http://localhost:8080/api/jobs/launch/simulatedJob`
*   `GET /api/jobs/stream`: Returns a Server-Sent Events stream of `BatchJobExecutionInfoDTO` updates for all jobs. Connect to this endpoint with a compatible client (e.g., JavaScript `EventSource`) to get real-time updates.
*   `GET /api/jobs/dashboard-snapshot`: Returns a JSON list of `BatchJobExecutionInfoDTO` for currently *running* job executions.
*   `GET /api/jobs/{jobName}/recent?count={n}`: Returns a JSON list of the last `n` (default 10) `BatchJobExecutionInfoDTO` for the specified `{jobName}`, including completed, failed, and stopped runs.
*   `GET /api/jobs/execution/{id}`: Returns the `BatchJobExecutionInfoDTO` for the specified job execution ID.

## Configuration

*   **Port:** Configured in `src/main/resources/application.properties` (defaults to 8080).
*   **Database:** Uses H2 in-memory database by default (`spring.batch.jdbc.initialize-schema=EMBEDDED`).
*   **CORS:** Enabled globally via `@CrossOrigin` on `JobController`, allowing requests from any origin.
