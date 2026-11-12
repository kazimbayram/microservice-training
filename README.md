# Resource and Song Services

Training application for storing MP3 resources and song metadata using Spring
Boot and Spring Data JPA, with a separate PostgreSQL database for each service.
Apache Tika extracts audio metadata without changing tag values, except for
converting duration from seconds to `mm:ss`.

This multi-module Maven project includes Resource Service, Song Service, and a
shared web starter. Both database containers are defined in the root Compose file.
Resource uploads store the original audio and invoke Song Service to create
metadata using the generated Resource ID.

## Structure

```text
project-root/
|-- compose.yaml
|-- pom.xml            Parent POM and module aggregation
|-- mvnw
|-- mvnw.cmd
|-- .mvn/wrapper/
|-- .gitignore
|-- README.md
|-- common-web-starter/
|   |-- pom.xml
|   |-- src/main/java/com/kazimbayram/elearn/common/web/
|   |   |-- autoconfigure/  Spring Boot auto-configuration
|   |   |-- dto/           Shared error responses
|   |   |-- exception/     Shared exceptions and global HTTP error handling
|   |   `-- validation/    Shared ID and CSV validation
|   |-- src/main/resources/META-INF/spring/
|   |   `-- org.springframework.boot.autoconfigure.AutoConfiguration.imports
|   `-- src/test/java/
|-- resource-service/
|   |-- pom.xml
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
|   |-- src/main/resources/application.yaml
|   `-- src/test/java/
`-- song-service/
    |-- pom.xml
    |-- src/main/java/com/kazimbayram/elearn/song_service/
    |   |-- SongServiceApplication.java
    |   |-- controller/
    |   |-- dto/
    |   |-- entity/
    |   |-- exception/
    |   |-- mapper/
    |   |-- repository/
    |   `-- service/
    |-- src/main/resources/application.yaml
    `-- src/test/java/
```

Controllers do not expose entities. GET returns binary audio directly, as
required by its API contract; DELETE returns a response DTO.
Services handle validation and transactions; mappers convert between metadata
DTOs and internal entities. Tests mirror the application packages.

The parent artifact is temporarily named `resource-service-parent`. Renaming the
root directory does not affect Maven. If you rename the parent's Maven
`artifactId`, also update the `<parent><artifactId>` in each module's `pom.xml`.
The Maven Wrapper and shared infrastructure files remain only at the root.

## Run locally

Requires JDK 21 and Docker with Docker Compose. Use the included Maven Wrapper.
Start the databases with the included training-only credential defaults:

```sh
docker compose up -d --wait
export RESOURCE_DB_URL=jdbc:postgresql://localhost:${DB_PORT:-5433}/resource_db
export SONG_DB_URL=jdbc:postgresql://localhost:${SONG_DB_PORT:-5434}/song_db
./mvnw install -DskipTests
./mvnw -pl resource-service spring-boot:run
```

In a second terminal with the same database environment variables, start Song
Service:

```sh
./mvnw -pl song-service spring-boot:run
```

The database is `resource_db`, hosted by a dedicated `postgres:16-alpine`
container. The HTTP service runs at `http://localhost:8081`. PostgreSQL is exposed
on host port 5433 and retains port 5432 inside its container. Set `DB_PORT` to
override the host port. Song Service has its own PostgreSQL container with
database `song_db`, exposed on host port 5434 (override with `SONG_DB_PORT`).
Song Service runs at `http://localhost:8082`.
Each database has separate credentials and a separate volume. Resource Service
defaults to username `resource_db_username` and password `resource_db_password`;
Song Service defaults to username `song_db_username` and password
`song_db_password`. Override these training-only defaults with
`RESOURCE_DB_USERNAME` / `RESOURCE_DB_PASSWORD` and
`SONG_DB_USERNAME` / `SONG_DB_PASSWORD` in both Compose and the application.
Set `RESOURCE_DB_URL` and `SONG_DB_URL` to the localhost URLs shown above when
running on the host. For applications inside the Compose network, use
`jdbc:postgresql://kb-resource-db:5432/resource_db` and
`jdbc:postgresql://kb-song-db:5432/song_db` instead. `DB_PORT` and `SONG_DB_PORT`
configure the published Docker host ports, not the container ports.
Credentials used on the first container startup initialize the database;
changing environment variables later does not change an existing database user.

Hibernate automatically creates and updates the schema with
`spring.jpa.hibernate.ddl-auto=update`. No migration tools or SQL initialization
scripts are used. Database data persists in the `resource-db-data` and
`song-db-data` Docker volumes. Stop the containers with `docker compose down`.

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

GET returns `404` when a resource does not exist. Invalid input returns `400`.
Unexpected server errors return `500` without exposing internal details.

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

## Shared web starter

Other Spring Boot MVC services can use the same validation and error contract by
adding this dependency (no component scanning changes or manual imports needed):

```xml
<dependency>
    <groupId>com.kazimbayram.elearn</groupId>
    <artifactId>common-web-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

The starter is a regular library JAR, not an executable application. It supplies
Spring Web, Bean Validation (`@Valid`, `@NotBlank`, etc.), shared error DTOs,
`RequestValidationException`, `NotFoundException`, `ConflictException`, and an auto-configured
`GlobalExceptionHandler` for servlet applications. Service-specific exceptions
can extend `NotFoundException`; service validation can throw
`new RequestValidationException("field", "Human-readable message")` or pass a map
of field errors. Annotated request DTO failures use the same validation response.
Unexpected failures are logged and return a generic `500` message.

Both services reuse `RequestIds.parse` and `RequestIds.parseCsv` for consistent
positive integer and 200-character CSV validation. Business logic, persistence,
mappers, and successful response DTOs remain in each service.
The starter has no database dependency.
Build/install the reactor before running an individual consuming module.

## Tests

Run unit and HTTP contract tests without a database:

```sh
./mvnw -Dtest=CommonWebStarterTests,ResourceControllerTests,ResourceUploadTests,ResourceServiceTests,AudioMetadataExtractorTests,SongMetadataMapperTests,SongControllerTests,SongServiceTests test
```

Run all tests, including the existing Song Service HTTP/persistence integration
tests, with both databases running and the URL environment overrides above.
Song integration tests roll back their database changes:

```sh
./mvnw test
```

Build all modules from the root with `./mvnw clean package`. The executable
service JARs are generated in `resource-service/target/` and `song-service/target/`.
