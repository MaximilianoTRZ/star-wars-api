package dev.starwars.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    @DisplayName("logs method, matched route, status and duration, without the query string")
    void logsAccessLine(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/people/1");
        request.setQueryString("secret=value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/people/{id}");
            ((MockHttpServletResponse) res).setStatus(200);
        });

        assertThat(output).containsPattern("INFO .*GET /api/v1/people/\\{id} 200 \\d+ms");
        assertThat(output).doesNotContain("secret=value");
    }

    @Test
    @DisplayName("falls back to the request path when no route matched")
    void fallsBackToPath(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                new MockHttpServletRequest("GET", "/nope"),
                response,
                (req, res) -> ((MockHttpServletResponse) res).setStatus(404));

        assertThat(output).contains("GET /nope 404");
    }

    @Test
    @DisplayName("logs 5xx responses as WARN")
    void serverErrorIsWarn(CapturedOutput output) throws Exception {
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/films"),
                new MockHttpServletResponse(),
                (req, res) -> ((MockHttpServletResponse) res).setStatus(503));

        assertThat(output).containsPattern("WARN .*GET /api/v1/films 503");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/livez", "/readyz", "/actuator/prometheus"})
    @DisplayName("skips health probes and Actuator")
    void skipsProbes(String path, CapturedOutput output) throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), (req, res) -> {});

        assertThat(output).doesNotContain(path);
    }
}
