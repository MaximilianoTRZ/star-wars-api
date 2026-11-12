package dev.starwars.config;

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
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document served at {@code /v3/api-docs} and rendered by Swagger UI at {@code /swagger-ui.html}.
 *
 * <p>Declares the bearer JWT scheme (enables the Authorize button) and adds the standard
 * {@code ProblemDetail} error responses to every API operation, so controllers only document
 * their happy path.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";
    static final String PROBLEM_SCHEMA = "ProblemDetail";
    private static final String LOGIN_PATH = "/api/v1/auth/login";

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
                        .description("""
                                People, films, starships and vehicles from [SWAPI](https://www.swapi.tech) \
                                with uniform pagination and filters by id and/or name.

                                **How to authenticate:** call `POST /api/v1/auth/login` with the demo user, \
                                copy `accessToken` and paste it in **Authorize**.""")
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
