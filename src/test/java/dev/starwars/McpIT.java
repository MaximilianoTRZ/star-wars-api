package dev.starwars;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.starwars.support.ApiClient;
import dev.starwars.support.Fixtures;
import dev.starwars.support.TestTokens;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** MCP over Streamable HTTP against the running app, with the SDK client, a real login and a stubbed SWAPI. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class McpIT {

    private static final String TEST_SECRET = "test-secret-0123456789-0123456789-0123456789";
    private static final String TOOLS_LIST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

    @RegisterExtension
    static WireMockExtension swapi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void swapiProperties(DynamicPropertyRegistry registry) {
        registry.add("app.swapi.base-url", () -> swapi.baseUrl() + "/api");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    private ApiClient api;
    private McpSyncClient client;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        circuitBreakers.circuitBreaker("swapi").reset();
        api = new ApiClient(port);
        String token = api.login("demo", "test-password");
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
                .build();
        client =
                McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
        client.initialize();
    }

    @AfterEach
    void tearDown() {
        client.closeGracefully();
    }

    @Test
    @DisplayName("an MCP client lists the eight read-only catalog tools")
    void listsTools() {
        assertThat(client.listTools().tools())
                .extracting(Tool::name)
                .containsExactlyInAnyOrder(
                        "search_people",
                        "get_person",
                        "search_films",
                        "get_film",
                        "search_starships",
                        "get_starship",
                        "search_vehicles",
                        "get_vehicle");
        assertThat(client.listTools().tools())
                .allSatisfy(
                        tool -> assertThat(tool.annotations().readOnlyHint()).isTrue());
    }

    @Test
    @DisplayName("search_people and get_person answer with SWAPI data as JSON")
    void callsTools() {
        swapi.stubFor(get(urlEqualTo("/api/people?name=skywalker"))
                .willReturn(okJson(Fixtures.swapi("people-search-skywalker.json"))));
        swapi.stubFor(get(urlEqualTo("/api/people/1")).willReturn(okJson(Fixtures.swapi("people-1.json"))));

        CallToolResult search = client.callTool(new CallToolRequest("search_people", Map.of("name", "skywalker")));
        CallToolResult detail = client.callTool(new CallToolRequest("get_person", Map.of("id", "1")));

        assertThat(search.isError()).isFalse();
        assertThat(text(search)).contains("Luke Skywalker", "Anakin Skywalker", "\"totalElements\":3");
        assertThat(text(detail)).contains("\"birthYear\":\"19BBY\"");
    }

    @Test
    @DisplayName("/mcp without a token returns 401 ProblemDetail")
    void requiresToken() {
        ResponseEntity<String> response = api.post("/mcp", TOOLS_LIST);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("/problems/unauthorized");
    }

    @Test
    @DisplayName("/mcp with a token without the catalog:read scope returns 403")
    void requiresScope() {
        String token = TestTokens.sign(TEST_SECRET, "star-wars-api", Instant.now(), Duration.ofMinutes(5), null);

        assertThat(api.postWithToken("/mcp", TOOLS_LIST, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }
}
