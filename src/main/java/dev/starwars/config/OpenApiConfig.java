package dev.starwars.config;

import dev.starwars.auth.security.DemoUserProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document served at {@code /v3/api-docs} and rendered by Swagger UI at {@code /swagger-ui.html}.
 *
 * <p>Declares the bearer JWT scheme (enables the Authorize button) and adds the standard
 * {@code ProblemDetail} error responses to every API operation, so controllers only document
 * their happy path.
 *
 * <p>With {@code app.docs.show-demo-credentials} on, the demo user's credentials of the running profile are
 * shown in the description and prefilled in the login request, so the login works as is in every environment.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";
    static final String PROBLEM_SCHEMA = "ProblemDetail";
    static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String LOGIN_SCHEMA = "LoginRequest";

    private final DemoUserProperties demoUser;
    private final boolean showDemoCredentials;

    OpenApiConfig(
            DemoUserProperties demoUser,
            @Value("${app.docs.show-demo-credentials:false}") boolean showDemoCredentials) {
        this.demoUser = demoUser;
        this.showDemoCredentials = showDemoCredentials;
    }

    @Bean
    OpenAPI openApi(ObjectProvider<BuildProperties> buildProperties) {
        String version = buildProperties.stream()
                .map(BuildProperties::getVersion)
                .findFirst()
                .orElse("dev");
        return new OpenAPI()
                .info(new Info()
                        .title("Star Wars API")
                        .version(version)
                        .description(description())
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    private String description() {
        String credentials = showDemoCredentials
                ? "**Demo user:** `%s` / `%s` (already filled in the login request body).%n%n"
                        .formatted(demoUser.username(), demoUser.password())
                : "";
        return """
                People, films, starships and vehicles from [SWAPI](https://www.swapi.tech) \
                with uniform pagination and filters by id and/or name.

                %s**How to try it:**
                1. Open `POST /api/v1/auth/login` under **Auth** and press **Execute**.
                2. The returned `accessToken` is applied to **Authorize** automatically \
                (it can also be pasted there by hand).
                3. Call any catalog endpoint. Filters: `id` and/or `name` (`title` for films), \
                `page` starts at 1 and `size` goes from 1 to 100.

                The same catalog is available over GraphQL at [/graphiql](/graphiql), with the same token \
                in the `Authorization` header.""".formatted(credentials);
    }

    /** Prefills the login request with the demo user of the running profile. */
    @Bean
    OpenApiCustomizer demoLoginExampleCustomizer() {
        return openApi -> {
            if (!showDemoCredentials) {
                return;
            }
            Schema<?> login = openApi.getComponents().getSchemas().get(LOGIN_SCHEMA);
            if (login == null || login.getProperties() == null) {
                return;
            }
            login.getProperties().get("username").example(demoUser.username());
            login.getProperties().get("password").example(demoUser.password());
        };
    }

    /**
     * Adds the error responses each operation can return, as {@code application/problem+json}.
     *
     * <p>The {@code ProblemDetail} schema is registered here and not in the {@link OpenAPI} bean:
     * springdoc rebuilds the component schemas from the scanned types and drops the ones declared
     * on the bean, which leaves the {@code $ref} unresolved.
     */
    @Bean
    OpenApiCustomizer problemResponsesCustomizer() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
            addProblemResponses(openApi);
        };
    }

    private static void addProblemResponses(OpenAPI openApi) {
        openApi.getPaths().forEach((path, item) -> {
            if (!path.startsWith("/api/v1/")) {
                return;
            }
            for (Operation operation : item.readOperations()) {
                ApiResponses responses = operation.getResponses();
                addProblem(responses, "400", "Invalid parameters");
                if (path.equals(LOGIN_PATH)) {
                    addProblem(responses, "401", "Invalid username or password");
                    addProblem(responses, "429", "Too many login attempts from this client");
                    continue;
                }
                addProblem(responses, "401", "Missing, invalid or expired token");
                addProblem(responses, "403", "Token without the catalog:read scope");
                if (path.endsWith("/{id}")) {
                    addProblem(responses, "404", "Resource not found");
                }
                addProblem(responses, "502", "SWAPI returned an unexpected response");
                addProblem(responses, "503", "SWAPI is unavailable");
            }
        });
    }

    private static void addProblem(ApiResponses responses, String status, String description) {
        Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA);
        responses.putIfAbsent(
                status,
                new ApiResponse()
                        .description(description)
                        .content(new Content()
                                .addMediaType(
                                        org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        new MediaType().schema(ref))));
    }

    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("Error as defined by RFC 9457")
                .addProperty("type", new StringSchema().example("/problems/resource-not-found"))
                .addProperty("title", new StringSchema().example("Resource not found"))
                .addProperty("status", new IntegerSchema().example(404))
                .addProperty("detail", new StringSchema().example("No people found with id 9999"))
                .addProperty("instance", new StringSchema().example("/api/v1/people/9999"))
                .addProperty("timestamp", new StringSchema().example("2026-11-11T20:00:00Z"));
    }
}
