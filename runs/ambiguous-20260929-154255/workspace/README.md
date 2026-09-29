# shortener-service

URL shortener with link creation, redirects, and click analytics (Spring Boot 3, Java 25, H2 with Flyway).

## Run

```sh
mvn spring-boot:run
curl -s -X POST localhost:8080/api/v1/links -H 'Content-Type: application/json' -d '{"url":"https://example.com/docs"}'
curl -si localhost:8080/<code>
curl -s localhost:8080/api/v1/links/<code>/stats
```

## Test

```sh
mvn verify
```

Unit tests cover `CodeGenerator`, `UrlValidator`, `RateLimiter` and `ShortenerService`. `LinkApiIntegrationTest` exercises the HTTP contract through MockMvc against a real H2 database.

## Documentation

- API contract: [openapi.yaml](openapi.yaml)
- Design and decisions: [docs/design.md](docs/design.md)
- Operations: [docs/operations.md](docs/operations.md)
- Release notes: [docs/release-notes.md](docs/release-notes.md)
