# Star Wars API

[![CI](https://github.com/MaximilianoTRZ/star-wars-api/actions/workflows/ci.yml/badge.svg)](https://github.com/MaximilianoTRZ/star-wars-api/actions/workflows/ci.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=MaximilianoTRZ_star-wars-api&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=MaximilianoTRZ_star-wars-api)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=MaximilianoTRZ_star-wars-api&metric=coverage)](https://sonarcloud.io/summary/new_code?id=MaximilianoTRZ_star-wars-api)

> **Back-end JAVA Challenge**
>
> **Objetivo:**
> Desarrollar una aplicación Java21 que se integre con la API de Star Wars (https://www.swapi.tech/documentation). La aplicación debe listar People, Films, Starships y Vehicles de manera paginada, permitir filtrado por ID y/o nombre, y contar con un sistema de autenticación seguro. Además, la aplicación debe estar bien documentada y contener pruebas unitarias y de integración.
>
> **Requisitos:**
>
> 1. **Integración con la API de Star Wars:**
>     - La aplicación debe conectarse a la API de Star Wars y obtener datos de People, Films, Starships y Vehicles.
> 2. **Listado de Entidades:**
>     - Implementar funcionalidades para listar People, Films, Starships y Vehicles.
>     - La lista debe ser paginada para manejar grandes cantidades de datos.
> 3. **Filtrado:**
>     - Permitir el filtrado de resultados por ID y/o nombre.
> 4. **Autenticación Segura:**
>     - Implementar un sistema de login seguro.
>     - Manejo adecuado de autenticación y autorización para acceder a las listas.
> 5. **Documentación:**
>     - La aplicación debe estar bien documentada, incluyendo instrucciones para la configuración y uso del sistema.
>     - La documentación del código debe ser clara y detallada.
> 6. **Pruebas Unitarias y de Integración:**
>     - La aplicación debe contener pruebas unitarias para garantizar el correcto funcionamiento de las unidades de código.
>     - Incluir pruebas de integración para asegurar que los componentes trabajen correctamente juntos.
> 7. **Despliegue:**
>     - Se recomienda desplegar la aplicación en algún servidor gratuito, como Heroku o Vercel, para facilitar la prueba de la app.

API REST sobre [SWAPI](https://www.swapi.tech) que expone People, Films, Starships y Vehicles con paginación uniforme, filtros por ID y/o nombre y autenticación JWT. El mismo catálogo se consulta también por [GraphQL](#graphql) y desde clientes de IA como Claude, con un [servidor MCP](#mcp). Java 21 + Spring Boot 4.1.

El diseño, las fases de implementación y las decisiones están en el [RFC](docs/RFC.md) y en los [ADRs](docs/adr/).

## Qué es

| # | Requisito | Cómo se cumple |
|---|---|---|
| 1 | Integración con SWAPI | `SwapiClient` sobre `RestClient`, aislado detrás de una interfaz, con timeouts y mapeo de errores |
| 2 | Listado paginado | `PageResponse<T>` uniforme para los 4 recursos, aunque SWAPI no pagine films ni búsquedas |
| 3 | Filtrado por ID y/o nombre | `CatalogService` elige la estrategia según la combinación de filtros |
| 4 | Login seguro, autenticación y autorización | JWT HS256 emitido por `POST /api/v1/auth/login` y validado con OAuth2 Resource Server; scope `catalog:read`; contraseñas con BCrypt |
| 5 | Documentación | Este README, Swagger UI, Javadoc, el [RFC](docs/RFC.md) y los [ADRs](docs/adr/) |
| 6 | Tests unitarios y de integración | 245 tests: JUnit 5, Mockito, AssertJ, MockMvc, WireMock y `@SpringBootTest`. Ninguno llama a SWAPI real |
| 7 | Despliegue | Docker multi-stage en Render, con deploy automático desde `main` |

## Cómo se construyó

**El MVP ([Fase 1, `v1.0.0`](https://github.com/MaximilianoTRZ/star-wars-api/releases/tag/v1.0.0)) cumple todos los requisitos del challenge** (la tabla de arriba). A partir de ahí, el alcance se amplió por fases con prácticas que consideré necesarias para que una API así funcione bien en producción: tolerar un proveedor lento y con rate limit, ver qué pasa adentro y endurecer la seguridad, sin cambiar el contrato REST. Cada fase terminó con un release taggeado y desplegado en Render.

| Fase | Release | Qué se sumó | Por qué | Detalle |
|---|---|---|---|---|
| 0 | [`v0.1.0`](https://github.com/MaximilianoTRZ/star-wars-api/releases/tag/v0.1.0) | Esqueleto desplegable: CI, Docker, Render, SonarCloud, JaCoCo (80%), ArchUnit, Spotless, Dependabot | Calidad verificada desde el primer commit y deploy desde el día uno | [Calidad](#calidad) |
| 1 | [`v1.0.0`](https://github.com/MaximilianoTRZ/star-wars-api/releases/tag/v1.0.0) | **MVP:** cliente SWAPI, paginación uniforme, filtros por ID y/o nombre, login JWT con scope, errores Problem Details, OpenAPI, tests y colección Postman | Requisitos del challenge | [Uso](#uso), [Errores](#errores), [Seguridad](#seguridad) |
| 2 | [`v1.1.0`](https://github.com/MaximilianoTRZ/star-wars-api/releases/tag/v1.1.0) | Caché Caffeine, retry y circuit breaker sobre el cliente de SWAPI | SWAPI tarda entre 2 y 9 s desde Render y tiene rate limit | [Resiliencia y caché](#resiliencia-y-caché) |
| 3 | [`v1.2.0`](https://github.com/MaximilianoTRZ/star-wars-api/releases/tag/v1.2.0) | Métricas técnicas y de negocio, correlation ID, Prometheus y Grafana como código, dashboard público | Saber qué pasa en producción sin entrar al servidor | [Observabilidad](#observabilidad) |
| 4 | [`v1.3.0`](https://github.com/MaximilianoTRZ/star-wars-api/releases/tag/v1.3.0) | Rate limiting del login, logging de producción, notas de escalabilidad | Frenar fuerza bruta y diagnosticar desde los logs de Render | [Seguridad](#seguridad), [Logs](#logs-y-correlación), [Escalabilidad](#cómo-lo-escalaría) |
| 5 | [`v1.4.0`](https://github.com/MaximilianoTRZ/star-wars-api/releases/tag/v1.4.0) | GraphQL y GraphiQL sobre el mismo `CatalogService`, con la fila de alertas en el dashboard | Consultas con la forma que pide el cliente, sin duplicar lógica | [GraphQL](#graphql) |
| 6 | `v1.5.0` (en curso) | Servidor MCP para clientes de IA (Claude, Cursor) | Consultar el catálogo desde un asistente con el mismo JWT | [MCP](#mcp) |

El detalle de cada fase (work units, tests, decisiones SOLID y criterios de salida) está en el [RFC](docs/RFC.md); las decisiones de diseño, en los [ADRs](#decisiones-de-diseño).

## Demo

- **Swagger UI:** https://star-wars-api-ailb.onrender.com/swagger-ui.html
- **GraphiQL:** https://star-wars-api-ailb.onrender.com/graphiql
- **Servidor MCP:** https://star-wars-api-ailb.onrender.com/mcp (ver [MCP](#mcp))
- **Dashboard en vivo (Grafana Cloud):** https://magentaosprey3543.grafana.net/public-dashboards/c1eb26ffdec84e1fa902aa1efc509e6f
- **API:** https://star-wars-api-ailb.onrender.com/api/v1
- **Usuario demo:** `demo` / `6WOHUwBrXmIreNrDYxkq47Q5PuNV7mBq` (en local: `demo` / `demo123`)

### Probarlo en Render

**GraphQL, con todo precargado.** Este comando hace login con el usuario demo y abre GraphiQL con una query de ejemplo y el header `Authorization` ya completo; solo queda apretar ▶:

```bash
BASE=https://star-wars-api-ailb.onrender.com
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"6WOHUwBrXmIreNrDYxkq47Q5PuNV7mBq"}' | jq -r .accessToken)
open "$BASE/graphiql?path=/graphql#token=$TOKEN"   # en Linux: xdg-open
```

El token viaja en el fragmento de la URL (`#token=`), que el navegador no envía al servidor, y la página lo borra de la barra de direcciones al cargarlo. Sin terminal: abrir [GraphiQL](https://star-wars-api-ailb.onrender.com/graphiql), que ya trae la query de ejemplo, y reemplazar `<token from POST /api/v1/auth/login>` en el panel **Headers** por un token sacado de Swagger UI. Más detalle en [GraphQL](#graphql).

**REST desde Swagger UI:** ejecutar `POST /api/v1/auth/login` con el usuario demo, copiar el `accessToken`, pegarlo en **Authorize** y llamar a cualquier endpoint del catálogo.

**Desde Claude Code (MCP):** con el `TOKEN` del comando de arriba, `claude mcp add --transport http star-wars $BASE/mcp --header "Authorization: Bearer $TOKEN"` y preguntarle, por ejemplo, "¿qué naves aparecen en A New Hope?". Más detalle en [MCP](#mcp).

**Todo junto con Postman:** importar la [colección](docs/postman/star-wars-api.postman_collection.json) y el entorno [`render`](docs/postman/render.postman_environment.json), y correrla con el *Collection Runner*. El request **Login** guarda el `accessToken` en la variable `token` de la colección, y todos los demás requests (REST, GraphQL y MCP) lo usan como `Bearer {{token}}`: no hay que copiar ni pegar el token. Cada request trae sus aserciones, así que la corrida muestra qué pasó y qué falló. Ver [Uso](#uso) para correrla con newman.

Corre en el plan gratuito de Render (0,1 CPU, 512 MB), que duerme la instancia después de un rato sin tráfico. Con tan poca CPU la app tarda unos 170 s en arrancar (medido en el deploy de la Fase 5), así que si la instancia está dormida el primer request puede tardar unos 3 minutos. El scrape de Grafana Cloud cada 1 minuto la mantiene despierta. Además, SWAPI tarda entre 2 y 9 s por request desde Render; las respuestas quedan en caché una hora (ver [Resiliencia y caché](#resiliencia-y-caché)).

## Quick start

En local no hace falta configurar nada: el perfil por defecto trae valores de desarrollo.

### Con Docker Compose

```bash
docker compose up --build
```

Levanta la app en http://localhost:8080, Prometheus y Grafana (ver [Observabilidad](#observabilidad)).

### Con Docker

```bash
docker build -t star-wars-api .
docker run -p 8080:8080 star-wars-api
```

### Con Maven

```bash
./mvnw spring-boot:run
```

Después abrir http://localhost:8080/swagger-ui.html y hacer login con `demo` / `demo123`.

## Configuración

| Variable | Descripción | Default local | En `prod` |
|---|---|---|---|
| `JWT_SECRET` | Clave HMAC-SHA256 para firmar los tokens, mínimo 32 bytes | Valor de desarrollo | Obligatoria |
| `DEMO_USER_PASSWORD` | Contraseña del usuario `demo` | `demo123` | Obligatoria |
| `SPRING_PROFILES_ACTIVE` | Perfil activo | — | `prod` |
| `METRICS_USERNAME` | Usuario del scrape de `/actuator/prometheus` | — | `grafana` por defecto |
| `METRICS_PASSWORD` | Contraseña del scrape, mínimo 16 caracteres | — (en local el endpoint no pide credenciales) | Obligatoria |
| `GRAFANA_ADMIN_PASSWORD` | Admin de Grafana en el compose local | `admin` | — |

- En el perfil `prod`, si falta una variable o el secreto es corto, la app no arranca (fail fast).
- Para generar un secreto: `openssl rand -base64 48`.
- [`.env.example`](.env.example) muestra el formato. `docker compose` lee `.env` si existe.

## Uso

```bash
BASE=http://localhost:8080

# 1. Login
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}' | jq -r .accessToken)

# 2. Listar (page base 1, size entre 1 y 100, default 10)
curl -s "$BASE/api/v1/people?page=1&size=3" -H "Authorization: Bearer $TOKEN"
```

```json
{
  "content": [
    { "id": "1", "name": "Luke Skywalker" },
    { "id": "2", "name": "C-3PO" },
    { "id": "3", "name": "R2-D2" }
  ],
  "page": 1,
  "size": 3,
  "totalElements": 82,
  "totalPages": 28
}
```

```bash
# 3. Filtrar por nombre (contiene, sin distinguir mayúsculas). En films el filtro es "title"
curl -s "$BASE/api/v1/people?name=skywalker" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/v1/films?title=jedi" -H "Authorization: Bearer $TOKEN"

# 4. Filtrar por ID, o por ID y nombre a la vez
curl -s "$BASE/api/v1/starships?id=9" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/v1/people?id=1&name=luke" -H "Authorization: Bearer $TOKEN"

# 5. Detalle
curl -s "$BASE/api/v1/starships/9" -H "Authorization: Bearer $TOKEN"
```

```json
{
  "id": "9",
  "name": "Death Star",
  "model": "DS-1 Orbital Battle Station",
  "manufacturer": "Imperial Department of Military Research, Sienar Fleet Systems",
  "starshipClass": "Deep Space Mobile Battlestation",
  "costInCredits": "1000000000000",
  "length": "120000",
  "crew": "342,953",
  "passengers": "843,342",
  "cargoCapacity": "1000000000000",
  "hyperdriveRating": "4.0",
  "mglt": "10"
}
```

El mismo flujo está disponible como:

- **Colección de Postman** en [`docs/postman/`](docs/postman/): 61 requests con 190 aserciones y dos entornos, `local` y `render`. Se importan la colección y un entorno y se corre con el *Collection Runner*: el login guarda el token para el resto. Como pega contra SWAPI real, que tiene rate limit, conviene dejar una pausa entre requests (*Delay* en el runner). La carpeta "Rate limiting" va al final porque bloquea los logins durante hasta un minuto. Por línea de comandos:
  ```bash
  npx newman run docs/postman/star-wars-api.postman_collection.json \
    -e docs/postman/local.postman_environment.json --delay-request 500
  ```
- **Archivo `.http`** en [`docs/api-examples.http`](docs/api-examples.http), para IntelliJ o la extensión REST Client de VS Code.

### Endpoints

| Método y ruta | Parámetros | Respuesta |
|---|---|---|
| `POST /api/v1/auth/login` | body `{username, password}` | `{accessToken, tokenType, expiresIn}` |
| `GET /api/v1/people` | `page`, `size`, `id`, `name` | Página de `{id, name}` |
| `GET /api/v1/films` | `page`, `size`, `id`, `title` | Página de `{id, title}` |
| `GET /api/v1/starships` | `page`, `size`, `id`, `name` | Página de `{id, name}` |
| `GET /api/v1/vehicles` | `page`, `size`, `id`, `name` | Página de `{id, name}` |
| `GET /api/v1/{recurso}/{id}` | — | Detalle completo |

Reglas del contrato:

- Los listados devuelven un resumen; el detalle completo sale solo de `/{id}` ([ADR-003](docs/adr/ADR-003.md)).
- Una página fuera de rango devuelve `content: []` con los totales reales.
- Un ID inexistente en el listado devuelve una página vacía (200); en `/{id}` devuelve 404.

### Errores

Todos los errores siguen el formato `ProblemDetail` ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457), [ADR-013](docs/adr/ADR-013.md)) con `Content-Type: application/problem+json`:

```json
{
  "type": "/problems/resource-not-found",
  "title": "Resource not found",
  "status": 404,
  "detail": "No people found with id 9999",
  "instance": "/api/v1/people/9999",
  "timestamp": "2026-11-12T06:45:56Z",
  "requestId": "3f1c2a9e-7b4d-4e8a-9c1f-2d6b5e8a0f13"
}
```

`requestId` es el mismo valor del header `X-Request-Id` de la respuesta y de las líneas de log del request (ver [Logs y correlación](#logs-y-correlación)).

El campo `type` identifica el problema. Los de la API son:

| Status | `type` | Cuándo |
|---|---|---|
| 400 | `/problems/validation-error` | Un parámetro o un campo del body no cumple las validaciones (`size` > 100, `id` no numérico, `password` vacía); incluye `errors: [{field, message}]` |
| 401 | `/problems/unauthorized` | Falta el token, es inválido o está vencido; incluye `WWW-Authenticate: Bearer` |
| 401 | `/problems/invalid-credentials` | Login con usuario o contraseña incorrectos (mismo mensaje en ambos casos) |
| 403 | `/problems/forbidden` | El token no tiene el scope `catalog:read` |
| 404 | `/problems/resource-not-found` | El ID no existe en SWAPI |
| 429 | `/problems/too-many-requests` | Más de 5 intentos de login por minuto desde la misma IP; incluye `Retry-After` |
| 500 | `/problems/internal-error` | Error inesperado; la respuesta no expone detalles internos |
| 502 | `/problems/upstream-error` | SWAPI respondió algo que no se puede interpretar |
| 503 | `/problems/upstream-unavailable` | SWAPI caída, lenta (timeout de 10 s), con rate limit o con el circuito abierto; incluye `Retry-After` cuando se conoce |

Los errores genéricos de HTTP (ruta inexistente, método no soportado, body ilegible, tipo de parámetro inválido) usan `type: about:blank`, como indica el RFC 9457 cuando el problema no agrega nada al status, y su `title` es el del status.

## GraphQL

El mismo catálogo se consulta por GraphQL en `/graphql`, con los mismos filtros, la misma paginación, la misma caché y el mismo JWT que la API REST. El controller GraphQL delega en el mismo `CatalogService` que los controllers REST y devuelve los mismos DTOs, así que no hay lógica duplicada ([ADR-016](docs/adr/ADR-016.md)). El schema está en [`schema.graphqls`](src/main/resources/graphql/schema.graphqls).

**Desde GraphiQL** (https://star-wars-api-ailb.onrender.com/graphiql, o http://localhost:8080/graphiql en local). La página abre con la query de abajo en el editor y el header `Authorization` en el panel **Headers**:

1. Conseguir un token: con el comando de [Probarlo en Render](#probarlo-en-render) se abre GraphiQL con el token ya cargado; si no, hacer login en Swagger UI y pegar el `accessToken` en lugar de `<token from POST /api/v1/auth/login>`.
2. Ejecutar la query con ▶. El panel **Docs** muestra los tipos y los argumentos de cada query.

La página es la de Spring for GraphQL ([`graphiql/index.html`](src/main/resources/graphiql/index.html)), con la query de ejemplo y el header precargados.

```graphql
{
  people(name: "skywalker") {
    content { id name }
    totalElements
  }
  film(id: 1) { title episodeId director }
}
```

```json
{
  "data": {
    "people": {
      "content": [
        { "id": "1", "name": "Luke Skywalker" },
        { "id": "11", "name": "Anakin Skywalker" },
        { "id": "43", "name": "Shmi Skywalker" }
      ],
      "totalElements": 3
    },
    "film": { "title": "A New Hope", "episodeId": 4, "director": "George Lucas" }
  }
}
```

**Con curl:**

```bash
curl -s $BASE/graphql -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"query":"{ starship(id: 9) { name model mglt } }"}'
```

| Query | Argumentos | Devuelve |
|---|---|---|
| `people`, `starships`, `vehicles` | `page`, `size`, `id`, `name` | Página de `{id, name}` |
| `films` | `page`, `size`, `id`, `title` | Página de `{id, title}` |
| `person`, `film`, `starship`, `vehicle` | `id` | Detalle completo, o `null` si no existe |

**Errores:** como en cualquier API GraphQL, la respuesta es 200 con un arreglo `errors`. Cada error lleva en `extensions` el mismo `type` que el `ProblemDetail` de REST y el `requestId` del header `X-Request-Id`:

```json
{
  "errors": [{
    "message": "No people found with id 9999",
    "path": ["person"],
    "extensions": {
      "classification": "NOT_FOUND",
      "type": "/problems/resource-not-found",
      "requestId": "1e6ab5b0-143c-446b-9802-975601f6e9f3",
      "timestamp": "2026-11-12T10:18:35Z"
    }
  }],
  "data": { "person": null }
}
```

| `classification` | `type` | Cuándo |
|---|---|---|
| `BAD_REQUEST` | `/problems/validation-error` | Un argumento no cumple las validaciones (`size` > 100, `id` no numérico); incluye `errors: [{field, message}]` |
| `NOT_FOUND` | `/problems/resource-not-found` | El ID no existe en SWAPI |
| `UNAVAILABLE` | `/problems/upstream-unavailable` | SWAPI caída, lenta, con rate limit o con el circuito abierto; incluye `retryAfter` (segundos) cuando se conoce |
| `BAD_GATEWAY` | `/problems/upstream-error` | SWAPI respondió algo que no se puede interpretar |
| `INTERNAL_ERROR` | `/problems/internal-error` | Error inesperado, sin detalles internos |

Sin token o sin el scope `catalog:read`, `/graphql` responde 401 o 403 con `ProblemDetail`, antes de llegar a GraphQL. Una query con más de 5 campos raíz se rechaza sin ejecutarse (ver [Seguridad](#seguridad)).

## MCP

El catálogo también se expone como servidor [MCP](https://modelcontextprotocol.io) en `/mcp`, para que un cliente de IA (Claude Code, Claude Desktop, Cursor) lo consulte con tools. Las tools delegan en el mismo `CatalogService` que REST y GraphQL y devuelven los mismos DTOs como JSON. El servidor no llama a ningún modelo: el modelo lo pone el cliente ([ADR-016](docs/adr/ADR-016.md)).

- **Transporte:** Streamable HTTP *stateless* ([ADR-019](docs/adr/ADR-019.md)). No guarda sesión: cualquier réplica atiende cualquier llamada, y responde JSON plano.
- **Seguridad:** el mismo JWT y el scope `catalog:read`, en el header `Authorization`. Sin token, 401 antes de llegar a MCP.

| Tool | Argumentos | Devuelve |
|---|---|---|
| `search_people`, `search_starships`, `search_vehicles` | `name`, `id`, `page`, `size` (todos opcionales) | Página de `{id, name}` |
| `search_films` | `title`, `id`, `page`, `size` (todos opcionales) | Página de `{id, title}` |
| `get_person`, `get_film`, `get_starship`, `get_vehicle` | `id` | Detalle completo |

Todas son de solo lectura (`readOnlyHint`). Un ID inexistente devuelve un mensaje claro (`No people found with id 9999…`), y una entrada inválida o una falla de SWAPI devuelven un resultado con `isError` y el mismo texto público que el `detail` de REST, sin detalles internos.

**Desde Claude Code:**

```bash
BASE=https://star-wars-api-ailb.onrender.com   # o http://localhost:8080 (usuario demo / demo123)
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"6WOHUwBrXmIreNrDYxkq47Q5PuNV7mBq"}' | jq -r .accessToken)
claude mcp add --transport http star-wars $BASE/mcp --header "Authorization: Bearer $TOKEN"
```

Después, en Claude Code: "¿qué naves aparecen en A New Hope?" o "comparame el Halcón Milenario con la Estrella de la Muerte". `/mcp` dentro de Claude Code muestra el servidor y sus tools.

**Desde Claude Desktop:** los conectores remotos de Claude Desktop no permiten agregar headers, así que se usa el proxy [`mcp-remote`](https://www.npmjs.com/package/mcp-remote) en `claude_desktop_config.json`, reemplazando `<token>`:

```json
{
  "mcpServers": {
    "star-wars": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "https://star-wars-api-ailb.onrender.com/mcp",
               "--header", "Authorization: Bearer <token>"]
    }
  }
}
```

**Cuando el token vence** (1 hora), las tools responden 401. Hay que repetir el login y volver a configurar el header: en Claude Code, `claude mcp remove star-wars` y el `claude mcp add` de arriba; en Claude Desktop, reemplazar el token y reiniciar la app.

**Sin cliente de IA**, con curl (JSON-RPC 2.0):

```bash
curl -s $BASE/mcp -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_people","arguments":{"name":"skywalker"}}}'
```

```json
{"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"{\"content\":[{\"id\":\"1\",\"name\":\"Luke Skywalker\"},{\"id\":\"11\",\"name\":\"Anakin Skywalker\"},{\"id\":\"43\",\"name\":\"Shmi Skywalker\"}],\"page\":1,\"size\":10,\"totalElements\":3,\"totalPages\":1}"}],"isError":false}}
```

Con `"method":"tools/list"` devuelve las 8 tools con sus descripciones y el esquema de sus argumentos.

## Resiliencia y caché

SWAPI es gratuita, desde Render tarda entre 2 y 9 s y limita a 100 requests por IP. El cliente se arma como una cadena de decorators: `CachingSwapiClient → ResilientSwapiClient → SwapiRestClient` ([ADR-007](docs/adr/ADR-007.md), [ADR-011](docs/adr/ADR-011.md)).

**Caché (Caffeine, en memoria):**

| Cache | Clave | Qué guarda |
|---|---|---|
| `swapi-list` | recurso + página + límite | Páginas del listado |
| `swapi-search` | recurso + término (sin espacios y en minúsculas) | Resultado completo de una búsqueda; todas sus páginas salen de ahí |
| `swapi-detail` | recurso + id | Detalle de una entidad |

- Hasta 1000 entradas por cache, vencen a la hora de escritas.
- No se cachean errores ni los 404, así un fallo transitorio no queda fijo.

**Retry:** hasta 3 intentos con backoff exponencial (200 ms, 400 ms) ante 5xx, errores de conexión y 429. Ante un 429 se espera el `Retry-After` de SWAPI si es de hasta 2 s; si es mayor, se responde 503 con ese mismo `Retry-After`. Los read timeouts (10 s) y los 4xx no se reintentan.

**Circuit breaker:**

| Parámetro | Valor |
|---|---|
| Ventana | últimas 20 llamadas (mínimo 10 para evaluar) |
| Abre con | 50% de fallas, u 80% de llamadas de más de 8 s |
| Abierto durante | 30 s, después prueba con 3 llamadas |
| Cuenta como falla | 5xx, 429, timeout o error de conexión; un 4xx no |

**Si SWAPI se cae:** lo que ya está en caché se sigue sirviendo. El resto responde 503 `upstream-unavailable`: primero tras los reintentos y, cuando el circuito se abre, al instante y con `Retry-After: 30`, sin llamar a SWAPI.

### Prueba de carga

![Prueba de carga con Postman contra Render](docs/images/postman-load-test.png)

Prueba de performance de Postman contra Render: 4 usuarios virtuales durante 2 minutos, recorriendo listados, detalles y una búsqueda.

- 1548 requests, 12,9 req/s y 0% de errores.
- Latencia promedio de 298 ms, con p90 de 307 ms y p95 de 383 ms. Casi todo ese tiempo es red hasta Render: la API responde en milisegundos desde la caché (ver el p95 de 6,64 ms en el [dashboard](#dashboard)).
- Los primeros requests tardan entre 4 y 5 s (máximos de hasta 5,5 s): son los misses que van a SWAPI. Después del primer pasaje, todo sale de la caché y la latencia queda plana.

## Observabilidad

Métricas con Micrometer y Prometheus, dashboard de Grafana y logs con correlation ID. En local, `docker compose` levanta Prometheus y Grafana. En producción, Grafana Cloud lee las métricas de Render y publica el mismo dashboard ([ADR-015](docs/adr/ADR-015.md)).

### Dashboard en vivo

**[Abrir el dashboard público](https://magentaosprey3543.grafana.net/public-dashboards/c1eb26ffdec84e1fa902aa1efc509e6f)**: métricas reales de la app en Render, sin login. Grafana Cloud las lee cada 1 minuto; para ver movimiento, hacer algunos requests a la [demo](https://star-wars-api-ailb.onrender.com/swagger-ui.html) y refrescar.

### Levantar el stack

```bash
docker compose up --build
```

| Servicio | URL | Acceso |
|---|---|---|
| API | http://localhost:8080 | — |
| Grafana | http://localhost:3000 | Anónimo como `Viewer`; admin `admin` / `GRAFANA_ADMIN_PASSWORD` (default `admin`) |
| Prometheus | http://localhost:9090 | Alertas en http://localhost:9090/alerts |

El dashboard **Star Wars API** queda como home de Grafana, sin pasos manuales: datasource, dashboard y alertas están provisionados desde `monitoring/`. Para ver datos, hacer algunos requests a la API.

### Endpoint de métricas

- **Local:** Actuator vive en el puerto de management **8081**, que el compose no publica. Solo Prometheus lo alcanza por la red interna (`app:8081/actuator/prometheus`, cada 15 s), sin credenciales.
- **Render:** publica un solo puerto, así que en el perfil `prod` Actuator lo comparte con la API. `/actuator/prometheus` exige HTTP Basic con `METRICS_USERNAME` / `METRICS_PASSWORD`; Grafana Cloud lo lee cada 1 minuto con la integración Metrics Endpoint. Si falta `METRICS_PASSWORD`, la app no arranca.
- Los probes `/livez` y `/readyz` se sirven en el puerto principal para el health check de Render.

### Dashboard

Las capturas son de Render (Grafana Cloud) durante la prueba de carga de [Resiliencia y caché](#prueba-de-carga), con la caché ya caliente. El dashboard tiene 6 filas; la de alertas se describe en [Alertas](#alertas).

#### Resumen y Negocio

![Dashboard de Grafana: filas Resumen y Negocio](docs/images/grafana-summary-business.png)

| Widget | Qué muestra |
|---|---|
| Requests / s | Tráfico total de la API |
| 5xx rate | Porcentaje de respuestas 5xx: si sube, la API está fallando |
| p95 latency | Latencia que cumple el 95% de los requests. En la captura, 6,64 ms con el 100% de hit ratio; con la caché fría cada miss va a SWAPI y tarda segundos |
| SLO < 300 ms | Porcentaje de requests que responden en menos de 300 ms |
| Active users | Usuarios distintos que hicieron login en los últimos 15 min. La prueba de carga reusó un token, así que marca 0 |
| Cache hit ratio | Porcentaje de lecturas de SWAPI resueltas por la caché |
| SWAPI circuit | Estado del circuit breaker: `CLOSED` (normal), `OPEN` (SWAPI cortada) o `HALF_OPEN` (probando) |
| Queries by resource and channel | Consultas por segundo a cada recurso, separadas por canal (REST o GraphQL) |
| Filter usage | Cómo se filtra: sin filtro, por `id`, por `name` o por ambos |
| Top 10 entities | Entidades más vistas en el detalle, con el nombre que devuelve SWAPI |
| Empty searches | Porcentaje de búsquedas por nombre que no encontraron nada, por recurso |
| Page depth | Hasta qué página navegan (promedio y máximo por recurso) |
| Logins | Logins exitosos y fallidos; base de la alerta de fuerza bruta |

#### API

![Fila API](docs/images/grafana-api.png)

| Widget | Qué muestra |
|---|---|
| Request rate by endpoint | Tráfico por endpoint, agrupado por URI template: `/people/1` y `/people/2` son una sola serie. `UNKNOWN` agrupa los requests rechazados antes del ruteo, como un 401 sin token |
| Errors by status | Respuestas 4xx y 5xx por status. En la captura, los 404 de IDs inexistentes |
| Latency p50 / p95 / p99 | Latencia total. Se ve el pico inicial con la caché fría (SWAPI tarda entre 4 y 5 s desde Render) y cómo cae cuando la caché se llena |
| p95 latency by endpoint | La misma latencia, separada por endpoint |

#### SWAPI y resiliencia

![Fila SWAPI y resiliencia](docs/images/grafana-swapi-resilience.png)

| Widget | Qué muestra |
|---|---|
| SWAPI vs API latency (p95) | Latencia de SWAPI contra la de la API. Cuando la de la API queda por debajo, la caché está absorbiendo la latencia del proveedor |
| SWAPI responses by status | Respuestas de SWAPI por status. `CLIENT_ERROR` son llamadas sin respuesta (timeout o conexión cortada) |
| Cache gets | Hits y misses de cada cache (`swapi-list`, `swapi-search`, `swapi-detail`) |
| Retries | Llamadas a SWAPI según si necesitaron reintento y si terminaron bien |
| Circuit breaker calls | Llamadas exitosas, fallidas, ignoradas (4xx) y rechazadas con el circuito abierto |
| Circuit failure rate | Tasa de fallas y de llamadas lentas sobre la ventana de 20 llamadas; el circuito abre con 50% y 80%. En la captura, 10% de fallas de SWAPI que el retry absorbió |

#### JVM

![Fila JVM](docs/images/grafana-jvm.png)

| Widget | Qué muestra |
|---|---|
| Heap | Memoria usada, comprometida y máxima. En Render el máximo es 384 MiB: el 75% de los 512 MB del plan (`-XX:MaxRAMPercentage=75`) |
| GC pauses | Duración de las pausas del garbage collector |
| CPU | Uso de CPU del proceso y del sistema |

### Alertas

Provisionadas en Prometheus desde [`monitoring/alerts.yml`](monitoring/alerts.yml):

| Alerta | Condición |
|---|---|
| `SwapiCircuitOpen` | El circuito de SWAPI está abierto hace más de 1 min |
| `HighServerErrorRate` | Más de 5% de respuestas 5xx en 5 min |
| `FailedLoginSpike` | Más de 20 logins fallidos o bloqueados por rate limiting en 5 min |

![Alertas en Prometheus con FailedLoginSpike disparada](docs/images/prometheus-alerts.png)

La captura es del stack local después de una ráfaga de logins con contraseña incorrecta: `FailedLoginSpike` pasa a `FIRING` y las otras dos siguen inactivas.

**En producción:** en Render no corre Prometheus, así que no hay página `/alerts`. El estado de las tres alertas se ve en la fila **Alerts** del [dashboard público](https://magentaosprey3543.grafana.net/public-dashboards/c1eb26ffdec84e1fa902aa1efc509e6f), debajo de Resumen: un panel por alerta, con la misma expresión de `alerts.yml`, en verde (`OK`) o en rojo (`FIRING`). Muestra si la condición se cumple ahora, sin la espera (`for`) que aplica Prometheus antes de disparar. Para verla cambiar, alcanza con más de 20 logins con contraseña incorrecta en 5 minutos: `FailedLoginSpike` pasa a `FIRING` en el próximo scrape (cada 1 minuto).

![Fila Alerts del dashboard con FailedLoginSpike disparada](docs/images/grafana-alerts.png)

La captura es de la fila **Alerts** después de una ráfaga de logins con contraseña incorrecta: `FailedLoginSpike` en `FIRING` y las otras dos en `OK`.

### Métricas de negocio

| Métrica | Tipo | Tags | Pregunta que responde |
|---|---|---|---|
| `catalog.queries` | Counter | `resource`, `filter` (`none`, `id`, `name`, `id_and_name`), `channel` (`rest`, `graphql`, `mcp`) | ¿Qué se consulta, cómo se filtra y por qué canal? |
| `catalog.entity.views` | Counter | `resource`, `entity` | ¿Qué entidades son las más consultadas? |
| `catalog.searches.empty` | Counter | `resource` | ¿Qué porcentaje de búsquedas no encuentra nada? |
| `catalog.page.requested` | DistributionSummary | `resource` | ¿Hasta qué página navegan? |
| `auth.login.attempts` | Counter | `result` (`success`, `failure`, `blocked`) | ¿Hay intentos de fuerza bruta? |
| `auth.active.users` | Gauge | — | ¿Cuántos usuarios distintos hubo en 15 min? |

Se registran en `CatalogService` y `AuthController` a través de `CatalogMetrics` y `AuthMetrics`; los controllers no conocen Micrometer.

**Regla de cardinalidad:** cada combinación de tags es una serie distinta en Prometheus, así que los tags solo toman valores de un conjunto acotado. El texto que escribe el usuario nunca es un tag: una búsqueda por `name=luke` cuenta como `filter=name`, no como `name=luke`. `entity` se registra solo después de resolver una entidad con éxito, y su valor viene de SWAPI (unas 160 entidades), no del input. Por el mismo motivo las llamadas a SWAPI usan URI template, para que el tag `uri` de `http.client.requests` no tenga un valor por ID.

### Logs y correlación

- `CorrelationIdFilter` corre primero en la cadena: reusa el `X-Request-Id` del request si es corto y alfanumérico, o genera un UUID. Lo pone en el MDC y lo devuelve en el header `X-Request-Id`.
- El mismo ID aparece en cada línea de log del request y en el campo `requestId` de los errores, así una respuesta fallida se cruza con su log.
- En local los logs son texto legible; en `prod` son JSON con formato ECS, con `requestId`, `user`, `channel` (`graphql` o `mcp` según el adaptador) y los datos de cada evento como campos.
- Nunca se loguean contraseñas, tokens ni el header `Authorization`.
- Cada evento se loguea una vez, donde se decide ([ADR-018](docs/adr/ADR-018.md)):

| Evento | Nivel | Campos |
| --- | --- | --- |
| `GET /api/v1/people/{id} 200 1138ms` (una por request, sin probes ni Actuator) | `INFO`, `WARN` si es 5xx | `http.method`, `http.route`, `http.status`, `duration_ms`, `user` |
| `SWAPI attempt 1/3 failed (CONNECTION), retrying in 200ms` | `INFO` | `swapi.attempt`, `swapi.max_attempts`, `swapi.reason`, `swapi.wait_ms` |
| `SWAPI call succeeded on attempt 2` | `INFO` | `swapi.attempts` |
| `SWAPI call failed after 3 attempts (SERVER_ERROR)` / `... is not retried (TIMEOUT)` | `WARN` | `swapi.attempts`, `swapi.reason` |
| `SWAPI circuit breaker changed from CLOSED to OPEN` | `WARN` al abrir, `INFO` el resto | `circuit.from`, `circuit.to` |
| `Login succeeded for demo` | `INFO` | `user` |
| `Login failed from <ip>` (sin username) | `WARN` | `client.ip` |
| `Login blocked by rate limit for <ip>, retry in 42s` | `WARN` | `client.ip`, `retry_after_s` |
| `Request rejected as unauthenticated: <motivo>` / `Request forbidden` | `INFO` | `auth.failure` |

Para seguir una request en Render: copiar el `requestId` del error (o del header `X-Request-Id`) y buscarlo en **Logs**. Para más detalle, `LOGGING_LEVEL_DEV_STARWARS=DEBUG` en las variables de entorno muestra cada intento contra SWAPI (requiere reiniciar).

## Seguridad

- **Login:** `AuthenticationManager` + `DaoAuthenticationProvider`, con contraseñas en BCrypt guardadas en H2 ([ADR-005](docs/adr/ADR-005.md)). Usuario inexistente y contraseña incorrecta devuelven el mismo error.
- **Token:** JWT HS256 de 1 h con `iss`, `sub`, `iat`, `exp` y `scope: catalog:read`. Lo valida el OAuth2 Resource Server de Spring: firma, expiración e issuer ([ADR-004](docs/adr/ADR-004.md)).
- **Autorización:** los endpoints del catálogo, `/graphql` y `/mcp` exigen el scope `catalog:read`; estar autenticado no alcanza.
- **Deny by default:** solo son públicos el login, Swagger UI, el documento OpenAPI, GraphiQL y los probes `/livez` y `/readyz`. Cualquier otra ruta sin token responde 401.
- **Métricas:** en local Actuator vive en el puerto de management (8081), que no se publica. En Render comparte el puerto público y `/actuator/prometheus` exige HTTP Basic con un usuario propio, que no sirve para llamar a la API ([ADR-015](docs/adr/ADR-015.md)).
- **Rate limiting del login:** 5 intentos por minuto por IP (Bucket4j). El 6º responde 429 con `Retry-After` antes de verificar las credenciales, y cuenta como `auth.login.attempts{result="blocked"}`. En Render la IP sale de `CF-Connecting-IP`, que pone Cloudflare (delante de Render) y que el cliente no puede falsear; `X-Forwarded-For` se ignora porque su primer valor lo elige el cliente. En local se usa la IP de la conexión. Los buckets viven en memoria y vencen a los 10 min sin uso: con varias réplicas el límite no se comparte (ver [Cómo lo escalaría](#cómo-lo-escalaría)).
- **CSRF deshabilitado:** la API es stateless y no usa cookies. El token viaja en el header `Authorization`, que el navegador no envía por su cuenta, así que no hay superficie para CSRF.
- **Fail fast:** en `prod` la app no arranca sin `JWT_SECRET` (mínimo 32 bytes), `DEMO_USER_PASSWORD` ni `METRICS_PASSWORD` (mínimo 16 caracteres).
- **Swagger UI y GraphiQL públicos en producción** ([ADR-017](docs/adr/ADR-017.md)): las páginas cargan sin token, pero cada llamada al catálogo lo exige.
- **Límite de consultas GraphQL:** como máximo 5 campos raíz por query, alias incluidos. Cada uno puede ser una llamada a SWAPI, así que una sola query no puede multiplicar el tráfico contra su rate limit.

## Arquitectura

Monolito modular, organizado por feature y con dependencias en una sola dirección ([ADR-001](docs/adr/ADR-001.md)):

```
dev.starwars
├── auth/       login, JWT y usuarios (api · service · domain · security)
├── catalog/    controllers, GraphQL, tools MCP, CatalogService y Paginator (api · graphql · mcp · service · domain)
├── swapi/      cliente de SWAPI: el único paquete que conoce su formato
├── common/     manejo de errores (ProblemDetail y errores GraphQL) y correlation ID
└── config/     RestClient, OpenAPI, reloj
```

```mermaid
flowchart LR
    C[Cliente] -->|Bearer JWT| S[Security filter chain]
    C -->|POST /auth/login| A[AuthController] --> T[TokenService]
    S --> K[Controllers] --> CS[CatalogService]
    S --> M[MCP tools] --> CS
    S --> G[GraphQL controller] --> CS --> CC[CachingSwapiClient] --> R[ResilientSwapiClient] --> RC[SwapiRestClient] --> X[(swapi.tech)]
```

`ArchitectureTest` (ArchUnit) verifica en cada build que `api`, `graphql` y `mcp` no usen `swapi` ni repositorios, que `service` no dependa de ningún adaptador (`api`, `graphql`, `mcp`), que los adaptadores no dependan entre sí, que `swapi` no dependa de las features y que no haya ciclos.

## Tests

```bash
./mvnw verify
```

- Corre los unitarios (`*Test`, con Surefire) y los de integración (`*IT`, con Failsafe).
- El reporte de cobertura queda en `target/site/jacoco/index.html`; CI lo publica como artefacto. La cobertura de líneas es de 98%.

| Capa | Tipo | Herramientas |
|---|---|---|
| `Paginator`, `CatalogService`, `TokenService` | Unitario | JUnit 5, Mockito, AssertJ |
| Controllers, errores y seguridad | Slice | `@WebMvcTest`, `MockMvcTester`, `spring-security-test` |
| Queries y errores GraphQL | Slice | `@GraphQlTest`, `GraphQlTester` |
| Tools MCP | Unitario | JUnit 5, Mockito |
| `SwapiRestClient` | Integración con stub | WireMock |
| Flujo punta a punta (`CatalogFlowIT`, `SecurityIT`, `GraphQlIT`, `McpIT`) | `*IT` | `@SpringBootTest` con puerto aleatorio + WireMock; `McpIT` usa el cliente del MCP SDK |
| Caché, retry y circuit breaker (`SwapiResilienceIT`) | `*IT` | WireMock con escenarios y `CircuitBreakerRegistry` |
| Puerto de management y scrape protegido (`ManagementPortIT`, `MetricsScrapeIT`) | `*IT` | `@SpringBootTest` + `@LocalManagementPort` |

SWAPI se simula con WireMock usando respuestas reales guardadas en `src/test/resources/swapi/` ([ADR-009](docs/adr/ADR-009.md)). Para regenerar una:

```bash
curl -s "https://www.swapi.tech/api/people/1" | jq . > src/test/resources/swapi/people-1.json
```

## Calidad

`./mvnw verify` falla si:

- el formato no coincide con palantir-java-format (`./mvnw spotless:apply` lo corrige),
- la cobertura de líneas baja del 80%,
- se rompe una regla de arquitectura de `ArchitectureTest`.

En CI, además, cada push a `main` y cada PR se analizan en [SonarCloud](https://sonarcloud.io/summary/new_code?id=MaximilianoTRZ_star-wars-api) con el quality gate por defecto. Dependabot abre PRs semanales con las actualizaciones de Maven y de GitHub Actions.

![Quality gate de SonarCloud en verde](docs/images/sonarcloud-summary.png)

![Ratings de SonarCloud](docs/images/sonarcloud-overview.png)

Quality gate "Sonar way" en verde: 0 issues nuevos, 97,8% de cobertura en código nuevo (95,7% en total), 0% de duplicación y rating A en seguridad, confiabilidad y mantenibilidad. Dependabot mantiene las dependencias al día.

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
| [018](docs/adr/ADR-018.md) | Estrategia de logging para producción |
| [019](docs/adr/ADR-019.md) | Servidor MCP stateless con el JWT en un header |

## ¿Por qué no microservicios?

El dominio es uno solo: cuatro recursos de lectura que vienen del mismo proveedor, y el único estado propio son los usuarios. Separarlo en servicios agregaría latencia de red, despliegues coordinados, trazas distribuidas y fallas parciales, sin un beneficio concreto: no hay equipos distintos ni cargas que escalen distinto. Los límites entre módulos (`auth`, `catalog`, `swapi`) están marcados por paquetes con dependencias en una sola dirección, verificadas por ArchUnit, y el cliente de SWAPI está detrás de una interfaz: extraer un módulo más adelante no exige reescribir. El [RFC](docs/RFC.md#en-un-ecosistema-de-microservicios) lista lo que se sumaría en un ecosistema de varios servicios.

## Cómo lo escalaría

1. **Réplicas stateless detrás de un load balancer.** La app no guarda sesión: cualquier réplica valida cualquier token. Lo que hoy vive en memoria pasa a Redis para que lo compartan todas: la caché de SWAPI (Caffeine → Redis con el mismo TTL) y el rate limiting del login (Bucket4j tiene backend para Redis; `LoginRateLimiter` es la interfaz que se reemplaza).
2. **Identity Provider externo** (Keycloak, Auth0, Cognito). El Resource Server ya valida JWT estándar: alcanza con configurar `issuer-uri` en lugar de la clave HMAC y emitir los tokens afuera. El login y los usuarios en H2 desaparecen de la app.
3. **Sincronizar SWAPI a una base propia** (PostgreSQL o MongoDB) con un job periódico. Los datos son casi estáticos, así que la API deja de depender de la disponibilidad y el rate limit de SWAPI, y puede paginar y filtrar con índices en lugar de en memoria.
4. **Microservicios solo si aparece la necesidad:** equipos separados o cargas muy distintas entre auth y catálogo. En ese caso irían detrás de un API Gateway, con OpenTelemetry para las trazas y lo listado en la sección "En un ecosistema de microservicios" del RFC.

## Licencia

[MIT](LICENSE)
