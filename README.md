# Resource and Song Services

Training application for storing MP3 resources and song metadata using Spring
Boot and Spring Data JPA, with a separate PostgreSQL database for each service.
Apache Tika extracts audio metadata without changing tag values, except for
converting duration from seconds to `mm:ss`.

This repository contains two independent Maven projects: Resource Service and
Song Service. Each service includes its own web error handling and validation
code, and each can be built from its own directory. Both database containers
are defined in the root Compose file. Resource uploads store the original audio
and invoke Song Service to create metadata using the generated Resource ID.

## Structure

```text
project-root/
|-- compose.yaml
|-- .gitignore
|-- README.md
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
Start only the database containers, then run services locally:

```sh
docker compose up -d --wait kb-resource-db kb-song-db
(cd resource-service && ./mvnw spring-boot:run)
```

In a second terminal, start Song Service:

```sh
(cd song-service && ./mvnw spring-boot:run)
```

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
so source-only changes reuse the dependency layer. Both builds skip test
compilation and execution. From the repository root, build and start everything:

```sh
docker compose up -d --build
```

Compose automatically reads `.env` and injects database and service configuration.
All containers use the default Compose network and logical service names for
communication. Database health checks gate application startup. No application
image registry or manual image build/load is required. Stop local application
processes before starting Compose to avoid port conflicts.

Use `http://localhost:8081` and `http://localhost:8082` for container API
requests (or the ports set by `RESOURCE_SERVICE_PORT` and `SONG_SERVICE_PORT`).
A read-only smoke check is:

```sh
curl -i http://localhost:8081/resources/ABC
curl -i http://localhost:8082/songs/ABC
```

Both should return `400` with the invalid-ID JSON error. Check startup with
`docker compose logs resource-service song-service`. Stop the stack with
`docker compose down`. To run the supplied Postman collection, set
`resource_service_url` to `http://localhost:8081` and `song_service_url` to
`http://localhost:8082`, and select the sample MP3 for both binary upload requests.

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
bytes, and calls `POST /songs` at `http://localhost:8082` (override using
`SONG_SERVICE_URL`). Title, artist, album, and release year are mapped to the Song
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

Run unit and HTTP contract tests without a database:

```sh
(cd song-service && ./mvnw -Dtest=SongControllerTests,SongServiceTests test)
```

Resource Service's existing upload and metadata tests reference a
`resource_service.support.Mp3Fixture` helper that is not present in the
repository, so Maven cannot compile its test sources until that helper is added.

Run all tests, including the existing Song Service HTTP/persistence integration
tests, with both databases running.
Song integration tests roll back their database changes:

```sh
(cd song-service && ./mvnw test)
```

Build either service independently from its directory with
`./mvnw clean package -Dmaven.test.skip=true` to skip the existing test-source
compilation issue noted above. The executable JAR is generated in that
service's `target/` directory.
