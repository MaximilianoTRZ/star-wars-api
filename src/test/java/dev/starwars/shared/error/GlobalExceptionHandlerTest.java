package dev.starwars.shared.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.starwars.config.ClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(ErrorTestController.class)
@Import({GlobalExceptionHandler.class, ProblemDetailFactory.class, ClockConfig.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    private static void assertProblem(ResultActions result, int status, String type, String instance) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.instance").value(instance))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
        if (type != null) {
            result.andExpect(jsonPath("$.type").value("/problems/" + type));
        }
    }

    @Test
    @DisplayName("ResourceNotFoundException is 404 resource-not-found")
    void resourceNotFound() throws Exception {
        var result = mvc.perform(get("/test/errors/not-found"));

        assertProblem(result, 404, "resource-not-found", "/test/errors/not-found");
        result.andExpect(jsonPath("$.detail").value("No people found with id 9999"));
    }

    @Test
    @DisplayName("SwapiClientException is 502 upstream-error")
    void upstreamError() throws Exception {
        assertProblem(
                mvc.perform(get("/test/errors/upstream-error")), 502, "upstream-error", "/test/errors/upstream-error");
    }

    @Test
    @DisplayName("UpstreamUnavailableException is 503 with the SWAPI Retry-After")
    void upstreamUnavailableWithRetryAfter() throws Exception {
        var result = mvc.perform(get("/test/errors/rate-limited"));

        assertProblem(result, 503, "upstream-unavailable", "/test/errors/rate-limited");
        result.andExpect(header().string("Retry-After", "30"));
    }

    @Test
    @DisplayName("UpstreamUnavailableException without Retry-After is 503 without the header")
    void upstreamUnavailableWithoutRetryAfter() throws Exception {
        var result = mvc.perform(get("/test/errors/upstream-down"));

        assertProblem(result, 503, "upstream-unavailable", "/test/errors/upstream-down");
        result.andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    @DisplayName("an unexpected exception is 500 without internal details")
    void unexpected() throws Exception {
        var result = mvc.perform(get("/test/errors/unexpected"));

        assertProblem(result, 500, "internal-error", "/test/errors/unexpected");
        result.andExpect(content().string(not(containsString("secret internal detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    @DisplayName("an invalid body is 400 validation-error with the field")
    void invalidBody() throws Exception {
        var result = mvc.perform(post("/test/errors/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\"}"));

        assertProblem(result, 400, "validation-error", "/test/errors/body");
        result.andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    @DisplayName("an invalid parameter is 400 validation-error with the parameter")
    void invalidParameter() throws Exception {
        var result = mvc.perform(get("/test/errors/param").param("size", "50"));

        assertProblem(result, 400, "validation-error", "/test/errors/param");
        result.andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("must be less than or equal to 10"));
    }

    @Test
    @DisplayName("a parameter of the wrong type is 400 from Spring MVC, with timestamp and instance")
    void typeMismatch() throws Exception {
        assertProblem(mvc.perform(get("/test/errors/param").param("size", "abc")), 400, null, "/test/errors/param");
    }

    @Test
    @DisplayName("an unreadable body is 400 from Spring MVC")
    void unreadableBody() throws Exception {
        assertProblem(
                mvc.perform(post("/test/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json")),
                400,
                null,
                "/test/errors/body");
    }

    @Test
    @DisplayName("an unknown route is 404 from Spring MVC")
    void unknownRoute() throws Exception {
        assertProblem(mvc.perform(get("/test/errors/nope")), 404, null, "/test/errors/nope");
    }

    @Test
    @DisplayName("an unsupported method is 405 from Spring MVC")
    void methodNotAllowed() throws Exception {
        assertProblem(mvc.perform(post("/test/errors/not-found")), 405, null, "/test/errors/not-found");
    }
}
