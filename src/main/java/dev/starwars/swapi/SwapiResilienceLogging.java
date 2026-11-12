package dev.starwars.swapi;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.event.RetryOnErrorEvent;
import io.github.resilience4j.retry.event.RetryOnIgnoredErrorEvent;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import io.github.resilience4j.retry.event.RetryOnSuccessEvent;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Logs the outcome of the SWAPI retry and circuit breaker ({@value ResilientSwapiClient#INSTANCE}), once per
 * decision, so a failed attempt that a retry recovered reads as such:
 *
 * <ul>
 *   <li>{@code INFO} each retry, with the attempt, the wait and the reason, and the success after retrying.</li>
 *   <li>{@code WARN} when the retries run out, and when an outage is not retried (read timeout or long
 *       {@code Retry-After}).</li>
 *   <li>{@code WARN} when the circuit opens; {@code INFO} for the other transitions.</li>
 * </ul>
 *
 * <p>Errors that are not outages (4xx, unreadable responses, an open circuit) are left to the exception
 * handler and the access line, so they are not logged twice.
 */
public final class SwapiResilienceLogging {

    private static final Logger log = LoggerFactory.getLogger(SwapiResilienceLogging.class);

    private SwapiResilienceLogging() {}

    /** Subscribes to the SWAPI retry when the registry creates it. */
    public static RegistryEventConsumer<Retry> retryEvents() {
        return new OnAdded<>(retry -> {
            if (!ResilientSwapiClient.INSTANCE.equals(retry.getName())) {
                return;
            }
            int maxAttempts = retry.getRetryConfig().getMaxAttempts();
            retry.getEventPublisher()
                    .onRetry(event -> onRetry(event, maxAttempts))
                    .onSuccess(SwapiResilienceLogging::onSuccess)
                    .onError(SwapiResilienceLogging::onError)
                    .onIgnoredError(SwapiResilienceLogging::onIgnoredError);
        });
    }

    /** Subscribes to the SWAPI circuit breaker when the registry creates it. */
    public static RegistryEventConsumer<CircuitBreaker> circuitBreakerEvents() {
        return new OnAdded<>(circuitBreaker -> {
            if (ResilientSwapiClient.INSTANCE.equals(circuitBreaker.getName())) {
                circuitBreaker.getEventPublisher().onStateTransition(SwapiResilienceLogging::onStateTransition);
            }
        });
    }

    static void onRetry(RetryOnRetryEvent event, int maxAttempts) {
        int failedAttempt = event.getNumberOfRetryAttempts();
        long waitMs = event.getWaitInterval().toMillis();
        String reason = reason(event.getLastThrowable());
        log.atInfo()
                .setMessage("SWAPI attempt {}/{} failed ({}), retrying in {}ms")
                .addArgument(failedAttempt)
                .addArgument(maxAttempts)
                .addArgument(reason)
                .addArgument(waitMs)
                .addKeyValue("swapi.attempt", failedAttempt)
                .addKeyValue("swapi.max_attempts", maxAttempts)
                .addKeyValue("swapi.reason", reason)
                .addKeyValue("swapi.wait_ms", waitMs)
                .log();
    }

    static void onSuccess(RetryOnSuccessEvent event) {
        int attempts = event.getNumberOfRetryAttempts() + 1;
        log.atInfo()
                .setMessage("SWAPI call succeeded on attempt {}")
                .addArgument(attempts)
                .addKeyValue("swapi.attempts", attempts)
                .log();
    }

    static void onError(RetryOnErrorEvent event) {
        int attempts = event.getNumberOfRetryAttempts();
        String reason = reason(event.getLastThrowable());
        log.atWarn()
                .setMessage("SWAPI call failed after {} attempts ({})")
                .addArgument(attempts)
                .addArgument(reason)
                .addKeyValue("swapi.attempts", attempts)
                .addKeyValue("swapi.reason", reason)
                .log();
    }

    static void onIgnoredError(RetryOnIgnoredErrorEvent event) {
        if (!(event.getLastThrowable() instanceof UpstreamUnavailableException)) {
            return;
        }
        String reason = reason(event.getLastThrowable());
        log.atWarn()
                .setMessage("SWAPI call failed and is not retried ({})")
                .addArgument(reason)
                .addKeyValue("swapi.attempts", 1)
                .addKeyValue("swapi.reason", reason)
                .log();
    }

    static void onStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        CircuitBreaker.State from = event.getStateTransition().getFromState();
        CircuitBreaker.State to = event.getStateTransition().getToState();
        log.atLevel(to == CircuitBreaker.State.OPEN ? Level.WARN : Level.INFO)
                .setMessage("SWAPI circuit breaker changed from {} to {}")
                .addArgument(from)
                .addArgument(to)
                .addKeyValue("circuit.from", from.name())
                .addKeyValue("circuit.to", to.name())
                .log();
    }

    private static String reason(Throwable error) {
        if (error instanceof UpstreamUnavailableException upstream) {
            return upstream.reason().name();
        }
        return error == null ? "UNKNOWN" : error.getClass().getSimpleName();
    }

    /** Runs an action on each entry the registry creates; replacements and removals are not used here. */
    private record OnAdded<T>(Consumer<T> action) implements RegistryEventConsumer<T> {

        @Override
        public void onEntryAddedEvent(EntryAddedEvent<T> event) {
            action.accept(event.getAddedEntry());
        }

        @Override
        public void onEntryRemovedEvent(EntryRemovedEvent<T> event) {
            // Nothing to unsubscribe: the entry is discarded with its publisher.
        }

        @Override
        public void onEntryReplacedEvent(EntryReplacedEvent<T> event) {
            action.accept(event.getNewEntry());
        }
    }
}
