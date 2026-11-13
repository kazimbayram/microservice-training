# Resource and Song Services

Training application for storing MP3 resources and song metadata using Spring
Boot and Spring Data JPA, with a separate PostgreSQL database for each service.
Apache Tika extracts audio metadata without changing tag values, except for
converting duration from seconds to `mm:ss`.

This repository contains three independent Maven projects: Resource Service,
Song Service, and Eureka Server. Each business service includes its own web error handling and validation
code, and each can be built from its own directory. Both database containers
are defined in the root Compose file. Resource uploads store the original audio
and invoke Song Service to create metadata using the generated Resource ID.

## Structure

```text
project-root/
|-- compose.yaml
|-- .gitignore
|-- README.md
|-- eureka-server/
|   |-- pom.xml
|   |-- mvnw
|   |-- .mvn/wrapper/
|   |-- Dockerfile
|   `-- src/main/
|-- resource-service/
|   |-- pom.xml
|   |-- mvnw
|   |-- .mvn/wrapper/
|   |-- src/main/java/com/kazimbayram/elearn/resource_service/
|   |   |-- ResourceServiceApplication.java
|   |   |-- client/
|   |   |-- controller/
|   |   |-- dto/
|   |   |-- entity/
|   |   |-- exception/
|   |   |-- metadata/
|   |   |-- mapper/
|   |   |-- repository/
|   |   `-- service/
|   |-- src/main/java/com/kazimbayram/elearn/common/web/
|   |   |-- autoconfigure/
|   |   |-- dto/
|   |   |-- exception/
|   |   `-- validation/
|   |-- src/main/resources/application.yaml
|   `-- src/test/java/
`-- song-service/
    |-- pom.xml
    |-- mvnw
    |-- .mvn/wrapper/
    |-- src/main/java/com/kazimbayram/elearn/song_service/
    |   |-- SongServiceApplication.java
    |   |-- controller/
    |   |-- dto/
    |   |-- entity/
    |   |-- exception/
    |   |-- mapper/
    |   |-- repository/
    |   `-- service/
    |-- src/main/java/com/kazimbayram/elearn/common/web/
    |   |-- autoconfigure/
    |   |-- dto/
    |   |-- exception/
    |   `-- validation/
    |-- src/main/resources/application.yaml
    `-- src/test/java/
```

Controllers do not expose entities. GET returns binary audio directly, as
required by its API contract; DELETE returns a response DTO.
Services handle validation and transactions; mappers convert between metadata
DTOs and internal entities. Tests mirror the application packages.

The Maven projects have no root parent or shared Maven module. Each service has
its own Maven Wrapper and inherits directly from Spring Boot.

## Run locally

Requires JDK 21 and Docker with Docker Compose. On a fresh checkout, copy
`.env.example` to `.env`. The example contains training-only credentials matching
the applications' local defaults. `.env` is ignored by Git and Docker builds.
Start only the database containers, then start Eureka locally:

```sh
docker compose up -d --wait kb-resource-db kb-song-db
(cd eureka-server && ./mvnw spring-boot:run)
```

In separate terminals, start Song Service and Resource Service:

```sh
(cd song-service && ./mvnw spring-boot:run)
(cd resource-service && ./mvnw spring-boot:run)
```

Alternatively, run each application's main class from IntelliJ, starting
`EurekaServerApplication` first. No profiles or configuration edits are needed.
Eureka is available at `http://localhost:8761`. Both services register using their
`spring.application.name`; Resource Service resolves `song-service` through
Eureka and uses Spring Cloud LoadBalancer for outbound Song API calls.
Eureka itself is the registry, not a request proxy or load balancer.
Wait until both applications appear as `UP` before running API requests.

The database is `resource_db`, hosted by a dedicated `postgres:16-alpine`
container. The HTTP service runs at `http://localhost:8081`. PostgreSQL is exposed
on host port 5433 and retains port 5432 inside its container. Set `DB_PORT` to
override the host port. Song Service has its own PostgreSQL container with
database `song_db`, exposed on host port 5434 (override with `SONG_DB_PORT`).
Song Service runs at `http://localhost:8082`.
Each database has separate credentials and ephemeral storage. Resource Service
defaults to username `resource_db_username` and password `resource_db_password`;
Song Service defaults to username `song_db_username` and password
`song_db_password`. Override these training-only defaults with
`RESOURCE_DB_USERNAME` / `RESOURCE_DB_PASSWORD` and
`SONG_DB_USERNAME` / `SONG_DB_PASSWORD` in `.env` and, for local applications,
their process environment. With the supplied `.env`, no local overrides are
needed. Spring does not load Compose's `.env`, so local runs use localhost
defaults; do not export the container database URLs into your local shell.
`DB_PORT` and `SONG_DB_PORT` configure published host ports, not container ports.

PostgreSQL creates each database using `POSTGRES_DB` and executes its mounted
`init-scripts/*/init.sql` to create tables. Scripts do not create databases.
Hibernate schema management is disabled (`ddl-auto=none`); Spring SQL
initialization and migration tools are not used. The Resource table uses an OID
column to match the entity's PostgreSQL large-object mapping.
Database storage uses `tmpfs`: **all database data is lost when its container
stops or restarts**. SQL initialization runs again on the empty storage.
Existing Module 1 named volumes are no longer mounted and are not deleted.
Changing a database name in `.env` also requires updating the corresponding
JDBC URL.

## Run services in Docker

Each standalone service has a two-stage Dockerfile: Eclipse Temurin 21 JDK
Alpine builds the application, and Temurin 21 JRE Alpine runs only the packaged JAR.
Maven configuration is copied and dependencies downloaded before source files,
so source-only changes reuse the dependency layer. All builds skip test
compilation and execution. From the repository root, build and start everything:

```sh
docker compose up -d --build --scale song-service=2
```

Compose automatically reads `.env` and injects database and service configuration.
All containers use the default Compose network and logical service names for
communication. Database, Eureka, and Song Service health checks gate dependent startup. No application
image registry or manual image build/load is required. Stop local application
processes before starting Compose to avoid port conflicts.

With `--scale song-service=2`, Compose starts one Eureka Server, one Resource
Service, Traefik, and two Song Service instances sharing the same Song database.
Eureka is published at `http://localhost:8761`. Traefik publishes the single
API address `http://localhost:8080` with service-specific base paths:
`/resources` routes to Resource Service, while `/songs` is load-balanced across
healthy Song replicas discovered through its Docker provider. Each router strips
its service prefix once, preserving the application endpoint path and query
parameters: `/resources/resources/1` becomes `/resources/1`, and `/songs/songs/1`
becomes `/songs/1`. Neither application publishes host
ports; both are reachable through Traefik or within the default Compose network.
Both instances listen on 8082 internally and register distinct container
hostnames/instance IDs and reachable container IP addresses.
Override host ports with `EUREKA_SERVER_PORT` and `API_PORT` in `.env`.
Without `--scale`, Compose starts one Song
Service instance. Additional replicas do not require additional host ports.
Do not set a fixed `container_name`, which would prevent scaling.

Traefik mounts `/var/run/docker.sock` read-only to discover containers. Docker
API access is privileged even with a read-only socket mount; this setup is for
the local demo on a trusted host. The Traefik dashboard is not enabled, and
containers are not routed unless explicitly labeled.
Traefik disables SELinux container labeling to access the socket on the
SELinux-enabled Docker-compatible engine used for this project; other
containers retain their default isolation.

Use `http://localhost:8080/resources` as the Resource API base URL and
`http://localhost:8080/songs` as the Song API base URL. Append the application
endpoint (`/resources` or `/songs`) to these base URLs. The earlier unprefixed
gateway URLs no longer map to the application endpoints.
A read-only smoke check is:

```sh
curl -i http://localhost:8080/resources/resources/ABC
curl -i http://localhost:8080/songs/songs/ABC
```

Both should return `400` with the invalid-ID JSON error. Check startup with
`docker compose logs resource-service song-service`. Stop the stack with
`docker compose down`. To run the supplied Postman collection, set
`resource_service_url` to `http://localhost:8080/resources` and
`song_service_url` to `http://localhost:8080/songs`,
and select the sample MP3 for both binary upload requests.

## Eureka discovery and demo

Spring Cloud 2025.1.2 is imported through its release BOM from Maven Central and
supports the project's Spring Boot 4.1.x version. Both clients use
`EUREKA_SERVER_URL` from Compose and default to `http://localhost:8761/eureka/`
when launched locally. There is no direct Song host/port fallback:
`SONG_SERVICE_URL` is no longer used. Resource Service's load-balanced
`RestClient.Builder` resolves the logical `http://song-service` URL for both
metadata creation and deletion, preserving the existing error contract.

The five-second heartbeat, registry fetch, and server response-cache intervals
and short client lease expiry are for this development/demo environment only.
Discovery is eventually consistent: a healthy container may not yet be visible
in every client's registry cache. Allow propagation before testing. A stopped
instance may remain cached briefly; requests during that interval can fail.
Writes are not automatically retried because creating metadata is not
idempotent. Tune these intervals and leases for production rather than copying
the demo values unchanged.

For the Docker demonstration, stop local Java processes to free ports, run
`docker compose down`, then `docker compose up -d --build --scale song-service=2 --wait`. Inspect
`docker compose ps` and `docker compose logs eureka-server resource-service song-service`.
Open `http://localhost:8761` and verify one `RESOURCE-SERVICE` and two
`SONG-SERVICE` instances are `UP`. Run the Postman collection with the URLs above.
Traefik routes external Resource requests and balances external Song requests
using Docker discovery. Resource Service
continues to perform client-side balancing through Eureka, bypassing Traefik;
both Song replicas register separately with their internal addresses. Stop the
Docker stack with `docker compose down` afterward.

For the local demonstration, start only `kb-resource-db` and `kb-song-db` in
Docker, then launch the three applications as described above. Verify the
registry and repeat the Postman tests against ports 8081 and 8082. A second local
Song process can be started with `--server.port=8083` to demonstrate balancing
locally as well. Keep local and Docker application runs separate so their
registrations and published ports do not conflict.

## API

### Resource Service (port 8081)

| Method | Path | Successful response |
|---|---|---|
| POST | `/resources` | `200`, `{"id":1}` |
| GET | `/resources/{id}` | `200`, original MP3 bytes, `Content-Type: audio/mpeg` |
| DELETE | `/resources?id=1,2` | `200`, `{"ids":[1,2]}` |

IDs must be positive 32-bit integers. DELETE requires a comma-separated string
of at most 200 characters, without spaces or empty entries. Missing resources
are ignored during deletion. Duplicate IDs are returned once in request order;
if no resources exist, the response is `{"ids":[]}`.

Resource deletion calls Song Service's `DELETE /songs?id=...` through
`SongApiClient` for the IDs of resources actually removed. Missing metadata is
ignored by Song Service. If no resources exist, no metadata deletion is requested.
Local deletions are flushed before the API call; a remote failure returns `500`
and rolls back the local transaction. As with uploads, this is not a distributed
transaction: a later local commit failure cannot restore deleted remote metadata.

GET returns `404` when a resource does not exist. Invalid input returns `400`.
Unexpected server errors return `500` without exposing internal details.

Invalid resource path IDs use a simple error response without `details`, for
example `{"errorMessage":"Invalid value 'ABC' for ID. Must be a positive integer","errorCode":"400"}`.

Upload raw binary MP3 data, not multipart form data:

```sh
curl -X POST http://localhost:8081/resources \
  -H 'Content-Type: audio/mpeg' --data-binary @song.mp3
```

Non-`audio/mpeg` requests return `400` with a simple error such as
`{"errorMessage":"Invalid file format: application/json. Only MP3 files are allowed","errorCode":"400"}`.
Empty bodies and content without MPEG audio frames return `400`.

The upload service parses MP3 metadata with Apache Tika, stores the original
bytes, and calls `POST /songs` through the load-balanced `http://song-service`
discovery name. Title, artist, album, and release year are mapped to the Song
Service fields without trimming, truncation, defaults, or date normalization.
Only duration is converted from seconds to `mm:ss` (fractional seconds discarded).
The Song Service API accepts scalar fields, so the first extracted value is
used for each field. Missing tags are sent as null rather than fabricated values.
If Song Service rejects metadata or is unavailable, the upload returns `500`
and the local resource insert is rolled back. This simple synchronous flow is
not a distributed transaction: a later local commit/network failure cannot undo
an already successful remote metadata insert.

`SongApiClient` decodes downstream simple and validation errors into
`SongApiException`, retaining the HTTP status, `errorMessage`, `errorCode`, and
optional `details`. These details are logged by the global handler; downstream
failures still return a generic `500` to Resource Service callers. Malformed
downstream error responses are reported explicitly rather than treated as success.

### Song Service (port 8082)

| Method | Path | Successful response |
|---|---|---|
| POST | `/songs` | `200`, `{"id":1}` |
| GET | `/songs/{id}` | `200`, full song metadata |
| DELETE | `/songs?id=1,2` | `200`, `{"ids":[1,2]}` |

Example POST body (also the GET response):

```json
{
  "id": 1,
  "name": "We are the champions",
  "artist": "Queen",
  "album": "News of the world",
  "duration": "02:59",
  "year": "1977"
}
```

All fields are required. The Resource ID is used as the primary key, not
generated by Song Service. Names, artists, and albums must be nonblank and
1-100 characters long. Duration must match `mm:ss` with two-digit minutes and
seconds from `00` to `59`; year must be four digits from `1900` to `2099`.
Metadata values are stored and returned without trimming or normalization.

POST does not call Resource Service or require an existing resource. An existing
metadata ID returns `409` without modifying its record; invalid metadata returns
`400` with field details. GET validates a positive 32-bit ID and returns `404`
when no metadata exists. DELETE uses the same ID/CSV restrictions and missing-ID
and duplicate-ID behavior as Resource Service. Unexpected errors return `500`.

### Error contract

Simple error response:

```json
{
  "errorMessage": "Resource with ID=1 not found",
  "errorCode": "404"
}
```

Validation error response:

```json
{
  "errorMessage": "Validation error",
  "details": {
    "id": "ID must be a positive integer between 1 and 2147483647"
  },
  "errorCode": "400"
}
```

## Service-local web support

Both services use Bean Validation annotations `@PositiveId` and `@IdCsv`
for positive integer and 200-character CSV validation. Spring MVC validates
controller parameters and `@Validated` protects service entry points.
Path ID and CSV errors use the simple error response; request DTO constraint
errors include field details. CSV errors identify the invalid token or report
the actual string length. Missing metadata fields have required-field messages;
empty text fields have length/format messages. Business logic, persistence,
mappers, and successful response DTOs remain in each service. The web support
code is intentionally duplicated so either project can be built without the
other.

## Tests

Run all tests from the repository root:

```sh
(cd resource-service && ./mvnw test)
(cd song-service && ./mvnw test)
```

Integration tests activate the `test` profile and use an embedded, in-memory H2
database with a schema created and dropped by Hibernate. No PostgreSQL, Docker,
or Eureka server is required. Persistence tests roll back their changes.
H2 is a test-only dependency; normal application runs still use PostgreSQL.
These tests do not replace PostgreSQL-specific integration checks.

Build any service independently from its directory with
`./mvnw clean package -Dmaven.test.skip=true` to skip test compilation and
execution. The executable JAR is generated in that
service's `target/` directory.
