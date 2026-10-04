# Sabbath Hours API

[![CI](https://github.com/bdeeker/sabbath-hours/actions/workflows/ci.yml/badge.svg)](https://github.com/bdeeker/sabbath-hours/actions/workflows/ci.yml)

A REST API that returns **sunset-based Sabbath times** (Friday sunset to Saturday sunset) for any place on Earth, in that place's own time zone. Churches, schools, and apps can save a location once and pull its times for a website, a bulletin, or a calendar.

Built with **Java 21, Spring Boot 4.1, Spring Data JPA, Flyway, PostgreSQL, and Spring Security**.

```bash
curl "http://localhost:8080/api/v1/sabbath?latitude=39.0615&longitude=-76.9671&timeZone=America/New_York&date=2026-10-02"
```

```json
{
  "location": { "latitude": 39.0615, "longitude": -76.9671, "timeZone": "America/New_York" },
  "sabbath": {
    "friday": "2026-10-02",
    "saturday": "2026-10-03",
    "begins": { "type": "SUNSET", "time": "2026-10-02T18:49:00-04:00" },
    "ends":   { "type": "SUNSET", "time": "2026-10-03T18:47:00-04:00" },
    "summary": "Sabbath begins Friday, October 2 at 6:49 PM and ends Saturday, October 3 at 6:47 PM."
  }
}
```

Send `Accept-Language: es` and the summary comes back as *"El sábado comienza el viernes 2 de octubre a las 18:49 y termina el sábado 3 de octubre a las 18:47."* English, Spanish, French, and Portuguese are supported.

## Getting the hard cases right

Sunset times are easy to get roughly right and surprisingly easy to get wrong. This API handles the cases a naive implementation breaks on:

| Case | What goes wrong naively | What this API does |
| --- | --- | --- |
| **Fairbanks, Alaska in June** | Friday's sunset happens at 12:47 AM on Saturday's clock, so "the sunset on Friday's date" finds the wrong one | Defines a day's sunset as *the first sunset after that day's solar noon*, and returns Saturday 00:47 as the start of the Sabbath |
| **Kiritimati (UTC+14 at 157° W)** | The civil calendar runs a full day ahead of the sun | The same solar-noon rule picks the right solar day |
| **Tromsø in December** | The sun never rises, so a formula returns NaN or an invented time | Reports `POLAR_NIGHT` (or `POLAR_DAY`) with no time, rather than guessing at a religious rule |
| **Daylight saving time** | A raw offset like `-04:00` is wrong for half the year | Requires a real IANA zone ID and rejects raw offsets with a clear error |
| **Samoa, 30 December 2011** | The date never existed there | Reports `DATE_NOT_IN_ZONE` |
| **Sunday 12:20 AM in Fairbanks** | "It's Sunday, so it isn't Sabbath" | Still `IN_SABBATH`, because Saturday's sun hasn't set yet |

Times are published to the nearest minute, the way almanacs publish them. The "is it Sabbath now?" check uses those same published minutes, so the API never says the Sabbath has begun at 6:48:50 while showing a start of 6:49.

## How it's verified

- **Against the U.S. Naval Observatory.** Thirteen reference sunsets across 10 time zones, from their [Rise/Set/Transit API](https://aa.usno.navy.mil/data/RS_OneDay), including Silver Spring, St. Croix, Sydney, Nairobi, Jerusalem (on the Friday daylight saving time starts), Kiritimati, and Fairbanks. Every one rounds to the Observatory's exact published minute.
- **Against an independent sun-position model.** 5,000 random points and dates (latitudes to 66°, years 2000 to 2050) were checked against the [astral](https://github.com/sffjunkie/astral) library. At every computed time, astral puts the sun at the sunset angle of −0.833° to within 0.004°.
- **Randomized consistency test.** 20,000 random moments across 8 cities check the "is it Sabbath now?" answer against a simple, independent rule.
- **Security tests are mutation-tested.** Deliberately breaking the access rules or the key check makes the security tests fail, which proves they guard what they claim to.
- **Real PostgreSQL in CI.** The migration, schema validation, and database constraints run against PostgreSQL 17 on every push.

Accuracy is about one minute, the limit of the underlying NOAA/Meeus algorithm. Near the poles in summer the sun sinks very slowly, so a sunset there can round to a minute either side of other sources.

## API

All Sabbath endpoints take `latitude`, `longitude`, and `timeZone` (an IANA ID such as `America/New_York`). Interactive documentation is served at **`/swagger-ui.html`**.

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/sabbath?date=` | The Sabbath for the week of `date` (default: today at the location) |
| `GET /api/v1/sabbath/upcoming?from=&count=` | `count` consecutive Sabbaths (1 to 53, default 4), for bulletins and calendars |
| `GET /api/v1/sabbath/status?at=` | Whether the Sabbath is in progress at `at` (default: now), and which Sabbath |
| `GET /api/v1/locations` | Saved locations, paged (`page`, `size` up to 100) |
| `GET /api/v1/locations/{id}` | One saved location |
| `GET /api/v1/locations/{id}/sabbath` (also `/upcoming`, `/status`) | The same Sabbath lookups for a saved location |
| `POST /api/v1/locations` | Save a location. **Requires `X-API-Key`** |
| `PUT /api/v1/locations/{id}` | Replace a location. **Requires `X-API-Key`** |
| `DELETE /api/v1/locations/{id}` | Delete a location. **Requires `X-API-Key`** |

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details (`application/problem+json`), with each invalid field listed:

```json
{
  "title": "Invalid request",
  "status": 400,
  "detail": "One or more request parameters are invalid.",
  "errors": [
    { "field": "timeZone", "message": "must be an IANA time zone ID such as America/New_York, not a UTC offset" }
  ]
}
```

### Showing times on a church website

The read endpoints allow requests from any origin, so a web page can call them directly:

```html
<p id="sabbath-times">Loading Sabbath times...</p>
<script>
  fetch("https://your-host/api/v1/locations/YOUR-LOCATION-ID/sabbath", { headers: { "Accept-Language": "en" } })
    .then(response => response.json())
    .then(data => { document.getElementById("sabbath-times").textContent = data.sabbath.summary; });
</script>
```

## Running it

You need a Java 21 JDK. Maven is not required, because the Maven Wrapper downloads it (and checks its SHA-256).

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080` with an in-memory H2 database. To use PostgreSQL and enable writes, set:

| Environment variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | e.g. `jdbc:postgresql://localhost:5432/sabbath_hours` |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Database credentials |
| `SABBATH_API_WRITE_KEY` | Key for writes, at least 32 characters. **If unset, the API is read-only.** It fails closed |

Flyway creates the schema on startup.

```bash
./mvnw verify   # all tests
```

To also run the PostgreSQL integration tests locally, set `SABBATH_TEST_POSTGRES_URL`, `SABBATH_TEST_POSTGRES_USER`, and `SABBATH_TEST_POSTGRES_PASSWORD`. Point them at a disposable database, because the tests delete saved locations.

## Design notes

- **Layers:** `solar` (astronomy only, no Spring dependencies in the logic), `sabbath` (the week and status rules), `web` and `location` (HTTP, validation, persistence), and `security`.
- **Modern Java:** sealed interfaces and records for results (`SunsetOutcome` is either `Sunset` or `NoSunset`), with pattern-matching `switch` over them.
- **Time is injected.** A `Clock` bean means "today" and "now" are testable, and "today" is always the location's date, not the server's.
- **The database defends itself.** CHECK constraints repeat the API's validation, Hibernate validates the entity against the Flyway schema at startup, and `@Version` optimistic locking returns a 409 instead of silently losing a concurrent update.
- **Security:** stateless API-key authentication with a constant-time comparison. It fails closed when unconfigured, and refuses to start with a key under 32 characters. Reads are open with CORS, and every other route is denied by default.
- **Build hygiene:** every `javac` lint check is on and any warning fails the build. GitHub Actions are pinned to commit SHAs, and the Maven download is checksum-verified.

## Feedback

Questions and bug reports are welcome in [GitHub Issues](https://github.com/bdeeker/sabbath-hours/issues).

## License

[MIT](LICENSE) © 2026 Brian Deeker
