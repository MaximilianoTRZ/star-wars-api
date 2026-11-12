package dev.starwars.common.exception;

import dev.starwars.common.web.CorrelationIdFilter;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds API errors as {@link ProblemDetail} (RFC 9457), always with a {@code timestamp} and, inside a request,
 * the {@code requestId} that also appears in the logs and the {@code X-Request-Id} header.
 *
 * <p>The only place where an error body is created, so every error has the same shape. Used by
 * {@link GlobalExceptionHandler} and by the 401/403 handlers of the security filter chain, which do not go
 * through the {@code @RestControllerAdvice}.
 */
@Component
public class ProblemDetailFactory {

    private final Clock clock;

    public ProblemDetailFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param status HTTP status
     * @param type   problem identifier, published as {@code /problems/{type}}
     * @param title  short, stable summary of the problem
     * @param detail explanation specific to this occurrence
     */
    public ProblemDetail create(HttpStatusCode status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("/problems/" + type));
        problem.setTitle(title);
        return withTimestamp(problem);
    }

    /** Adds a {@code timestamp} and, inside a request, the current {@code requestId}, if missing. */
    public ProblemDetail withTimestamp(ProblemDetail problem) {
        if (!hasProperty(problem, "timestamp")) {
            problem.setProperty("timestamp", Instant.now(clock).toString());
        }
        String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (requestId != null && !hasProperty(problem, "requestId")) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }

    private static boolean hasProperty(ProblemDetail problem, String name) {
        return problem.getProperties() != null && problem.getProperties().containsKey(name);
    }

    /**
     * JSON shape of the problem, with extra properties at the same level as the standard fields. Used where
     * the response is written by hand, outside Spring MVC message converters.
     */
    public Map<String, Object> toMap(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(
                "type",
                problem.getType() == null ? "about:blank" : problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }
}
