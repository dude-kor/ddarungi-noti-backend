# 따릉이 알리미 – Backend

Spring Boot 3.3 / Java 21 (virtual threads) service behind the 따릉이 알리미 mobile app
(the `ddarungi-noti-mobile` repository). It ingests Seoul bike-station availability, caches it in Redis, and
sends commute push notifications at the exact minute users asked for.

```
Seoul Open API ──(RestClient + circuit breaker, 1000-row chunks)──► Redis (60 s, Redisson lock)
                                                                      │
 @Scheduled :45 ──► RabbitMQ push.exchange ──► worker waits for T:00 ──► FCM HTTP v1
                                                                      │
 REST /api/v1 (X-Device-Token) ◄────────────────────── MySQL (devices, stations, schedules, favourites)
```

API contract: [`docs/API.md`](docs/API.md).

## Run locally

```bash
docker compose up -d          # MySQL, Redis, RabbitMQ, WireMock (Seoul API mock on :8089)
./gradlew bootRun             # http://localhost:8080
./gradlew test
```

Run the app itself in Docker too: `docker compose --profile app up --build`.

WireMock simulates the Seoul API. The key selects the behaviour: `LOCAL_KEY` (default) returns sample stations,
`ERROR_KEY` → HTTP 500, `SLOW_KEY` → 5 s delay (trips the 3 s read timeout and the circuit breaker):

```bash
SEOUL_API_KEY=SLOW_KEY ./gradlew bootRun
```

## Configuration

| Variable | Default | |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_USER` / `MYSQL_PASSWORD` | `localhost` / `ddarungi` / `ddarungi` | |
| `REDIS_HOST`, `RABBIT_HOST` | `localhost` | |
| `SEOUL_API_BASE_URL` | `http://localhost:8089` | production: `http://openapi.seoul.go.kr:8088` |
| `SEOUL_API_KEY` | `LOCAL_KEY` | Seoul Open Data Plaza key |
| `FCM_ENABLED`, `FCM_CREDENTIALS_PATH` | `false`, – | Firebase service-account JSON for real pushes |

`spring.jpa.hibernate.ddl-auto=update` is for local development. Introduce Flyway and switch to `validate` before
production.

## CI/CD (GitHub Actions)

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | push to `main`, pull requests | `./gradlew build` (tests) on Java 21 · validates `docker-compose.yml` and the WireMock JSON · builds the Docker image |
| `docker-publish.yml` | push to `main`, tags `v*.*.*` | pushes `ghcr.io/<owner>/<repo>` (`main`, `sha-…`, semver tags) |

No secrets are needed: publishing uses the built-in `GITHUB_TOKEN`. In the repository settings, allow
*Actions → Workflow permissions → Read and write* once, so the first push to GHCR can create the package.
Dependabot keeps Gradle, GitHub Actions and the base images current.

Deploying the image (and providing the secrets above) is left to your platform.

## Layout

```
src/main/java/kr/ddarungi/noti/
  config/        properties, RestClient, RabbitMQ, web
  seoul/         bikeListHist client (chunked, circuit-breaker guarded)
  cache/         Redis cache-aside + Redisson lock + stale fallback
  notification/  T-15s dispatcher, push worker, FCM sender
  domain/        JPA entities and repositories
  service/       use cases
  web/           controllers, DTOs, X-Device-Token interceptor, error handling
wiremock/        Seoul API mappings
```
