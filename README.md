# Star Wars API

API REST sobre [SWAPI](https://www.swapi.tech) que expone People, Films, Starships y Vehicles con paginación uniforme, filtros por ID y/o nombre y autenticación JWT. Java 21 + Spring Boot 4.1.

El diseño, las fases de implementación y las decisiones están en el [RFC](docs/RFC.md) y en los [ADRs](docs/adr/).

## Qué es

## Demo

## Quick start

### Con Docker

```bash
docker build -t star-wars-api .
docker run -p 8080:8080 star-wars-api
curl http://localhost:8080/actuator/health
```

### Con Maven

```bash
./mvnw spring-boot:run
curl http://localhost:8080/actuator/health
```

## Configuración

## Uso

## Seguridad

## Arquitectura

## Tests

```bash
./mvnw verify
```

Corre los tests unitarios y de integración, y las verificaciones de calidad.

## Calidad

`./mvnw verify` falla si:

- el formato no coincide con palantir-java-format (`./mvnw spotless:apply` lo corrige),
- la cobertura de líneas baja del 80% (reporte en `target/site/jacoco/index.html`),
- se rompe una regla de arquitectura de `ArchitectureTest`.

## Decisiones de diseño

| ADR | Decisión |
|---|---|
| [001](docs/adr/ADR-001.md) | Monolito modular en capas |
| [002](docs/adr/ADR-002.md) | Paginación propia y uniforme |
| [003](docs/adr/ADR-003.md) | Listados con resumen; detalle solo en `/{id}` |
| [004](docs/adr/ADR-004.md) | JWT stateless con OAuth2 Resource Server nativo |
| [005](docs/adr/ADR-005.md) | Usuarios en H2 en memoria |
| [006](docs/adr/ADR-006.md) | Spring Boot 4.1.2 con Java 21 |
| [007](docs/adr/ADR-007.md) | Caché en la capa cliente, por decorator |
| [008](docs/adr/ADR-008.md) | `RestClient` sincrónico + virtual threads |
| [009](docs/adr/ADR-009.md) | WireMock con fixtures reales |
| [010](docs/adr/ADR-010.md) | Render free tier con Docker |
| [011](docs/adr/ADR-011.md) | Resilience4j para retry y circuit breaker |
| [012](docs/adr/ADR-012.md) | `wiremock-standalone` en tests |
| [013](docs/adr/ADR-013.md) | Errores con Problem Details (RFC 9457) |
| [014](docs/adr/ADR-014.md) | Calidad de código desde la Fase 0 |
| [015](docs/adr/ADR-015.md) | Dashboard público con Grafana Cloud y scrape autenticado |
| [016](docs/adr/ADR-016.md) | GraphQL y MCP como adaptadores del mismo `CatalogService` |
| [017](docs/adr/ADR-017.md) | Swagger UI y GraphiQL públicos en producción |

## Licencia

[MIT](LICENSE)
