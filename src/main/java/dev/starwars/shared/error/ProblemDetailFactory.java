package dev.starwars.shared.error;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds API errors as {@link ProblemDetail} (RFC 9457), always with a {@code timestamp}.
 *
 * <p>The only place where an error body is created, so every error has the same shape.
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

    /** Adds a {@code timestamp} if the problem does not have one yet. */
    public ProblemDetail withTimestamp(ProblemDetail problem) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey("timestamp")) {
            problem.setProperty("timestamp", Instant.now(clock).toString());
        }
        return problem;
    }
}
