package dev.starwars;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.starwars.support.ApiClient;
import dev.starwars.support.Fixtures;
import dev.starwars.support.Logs;
import dev.starwars.support.TestTokens;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
@ExtendWith(OutputCaptureExtension.class)
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

    @Autowired
    private MeterRegistry meterRegistry;

    private ApiClient api;
    private McpSyncClient client;
    private String token;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        circuitBreakers.circuitBreaker("swapi").reset();
        api = new ApiClient(port);
        token = api.login("demo", "test-password");
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
    @DisplayName("SWAPI down is an isError result with the public message, not the internal one")
    void upstreamFailure() {
        swapi.stubFor(get(urlEqualTo("/api/films/1")).willReturn(aResponse().withStatus(500)));

        CallToolResult result = client.callTool(new CallToolRequest("get_film", Map.of("id", "1")));

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .isEqualTo("SWAPI did not respond correctly. Try again in a few seconds.")
                .doesNotContain("500");
    }

    @Test
    @DisplayName("tool calls count under channel mcp and their logs carry the request ID of the header")
    void metricsAndCorrelation(CapturedOutput output) {
        swapi.stubFor(get(urlEqualTo("/api/people?name=skywalker"))
                .willReturn(okJson(Fixtures.swapi("people-search-skywalker.json"))));
        swapi.stubFor(get(urlEqualTo("/api/starships/9")).willReturn(aResponse().withStatus(500)));
        double before = meterRegistry.find("catalog.queries").tag("channel", "mcp").counters().stream()
                .mapToDouble(Counter::count)
                .sum();

        client.callTool(new CallToolRequest("search_people", Map.of("name", "skywalker")));
        ResponseEntity<String> down = api.mcp(
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"get_starship\",\"arguments\":{\"id\":\"9\"}}}",
                token);

        assertThat(meterRegistry.find("catalog.queries").tag("channel", "mcp").counters().stream()
                        .mapToDouble(Counter::count)
                        .sum())
                .isEqualTo(before + 1);
        String requestId = down.getHeaders().getFirst("X-Request-Id");
        assertThat(down.getBody()).contains("\"isError\":true");
        assertThat(output).containsPattern("\\[" + requestId + "] .*SWAPI attempt 1/3 failed");
        Logs.awaitPattern(output, "\\[" + requestId + "] .*POST /mcp 200");
    }

    @Test
    @DisplayName("/mcp without a token returns 401 ProblemDetail")
    void requiresToken() {
        ResponseEntity<String> response = api.mcp(TOOLS_LIST, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("/problems/unauthorized");
    }

    @Test
    @DisplayName("/mcp with a token without the catalog:read scope returns 403")
    void requiresScope() {
        String token = TestTokens.sign(TEST_SECRET, "star-wars-api", Instant.now(), Duration.ofMinutes(5), null);

        assertThat(api.mcp(TOOLS_LIST, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }
}
