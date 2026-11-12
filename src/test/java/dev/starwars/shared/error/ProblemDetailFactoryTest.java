package dev.starwars.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemDetailFactoryTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:15:30Z");

    private final ProblemDetailFactory factory = new ProblemDetailFactory(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("creates a problem with type, title, status, detail and timestamp")
    void createsProblem() {
        ProblemDetail problem =
                factory.create(HttpStatus.NOT_FOUND, "resource-not-found", "Resource not found", "No people");

        assertThat(problem.getType()).isEqualTo(URI.create("/problems/resource-not-found"));
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("No people");
        assertThat(problem.getProperties()).containsEntry("timestamp", "2026-11-12T10:15:30Z");
    }

    @Test
    @DisplayName("adds a timestamp to a problem built elsewhere")
    void addsTimestamp() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);

        factory.withTimestamp(problem);

        assertThat(problem.getProperties()).containsEntry("timestamp", NOW.toString());
    }

    @Test
    @DisplayName("keeps an existing timestamp")
    void keepsExistingTimestamp() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setProperty("timestamp", "2020-01-01T00:00:00Z");

        factory.withTimestamp(problem);

        assertThat(problem.getProperties()).containsEntry("timestamp", "2020-01-01T00:00:00Z");
    }
}
