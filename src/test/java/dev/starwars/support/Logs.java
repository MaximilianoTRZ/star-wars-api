package dev.starwars.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.test.system.CapturedOutput;

/**
 * Assertions on captured logs written after the response. The access line of {@code RequestLoggingFilter} is
 * logged once the filter chain returns, which can be just after the client already has the response.
 */
public final class Logs {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private Logs() {}

    /** Waits until the output contains {@code regex}, then asserts it (failing with the full output). */
    public static void awaitPattern(CapturedOutput output, String regex) {
        Pattern pattern = Pattern.compile(regex);
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!pattern.matcher(output.getAll()).find() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(output).containsPattern(regex);
    }
}
