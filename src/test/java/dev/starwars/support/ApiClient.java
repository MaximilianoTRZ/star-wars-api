package dev.starwars.support;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** Minimal HTTP client for integration tests that never throws on error statuses. */
public final class ApiClient {

    private final RestClient restClient;

    public ApiClient(int port) {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
                .build();
    }

    public String login(String username, String password) {
        ResponseEntity<String> response =
                post("/api/v1/auth/login", "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password));
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Login failed: " + response.getStatusCode());
        }
        return JsonPath.read(response.getBody(), "$.accessToken");
    }

    public ResponseEntity<String> post(String uri, String json) {
        return restClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .toEntity(String.class);
    }

    /** Posts a GraphQL query to {@code /graphql}; {@code token} may be {@code null}. */
    public ResponseEntity<String> graphql(String query, String token) {
        return restClient
                .post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (token != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                })
                .body(Map.of("query", query))
                .retrieve()
                .toEntity(String.class);
    }

    public ResponseEntity<String> getWithBasicAuth(String uri, String username, String password) {
        return restClient
                .get()
                .uri(uri)
                .headers(headers -> headers.setBasicAuth(username, password))
                .retrieve()
                .toEntity(String.class);
    }

    public ResponseEntity<String> get(String uri, String token) {
        return restClient
                .get()
                .uri(uri)
                .headers(headers -> {
                    if (token != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                })
                .retrieve()
                .toEntity(String.class);
    }
}
