package dev.starwars.common.exception;

import dev.starwars.catalog.mcp.ToolErrorMessages;
import dev.starwars.swapi.SwapiClientException;
import dev.starwars.swapi.UpstreamUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Same public texts as the {@code detail} of the REST and GraphQL errors; unexpected errors are logged, not exposed. */
@Component
public class PublicToolErrorMessages implements ToolErrorMessages {

    private static final Logger log = LoggerFactory.getLogger(PublicToolErrorMessages.class);

    @Override
    public String describe(RuntimeException ex) {
        return switch (ex) {
            case UpstreamUnavailableException unavailable ->
                "SWAPI did not respond correctly. Try again in a few seconds.";
            case CallNotPermittedException circuitOpen ->
                "SWAPI is failing repeatedly, so calls are paused. Try again later.";
            case SwapiClientException upstream -> {
                log.warn("Unexpected SWAPI response: {}", upstream.getMessage());
                yield "SWAPI returned a response that could not be processed.";
            }
            default -> {
                log.error("Unhandled error in an MCP tool", ex);
                yield "An unexpected error occurred.";
            }
        };
    }
}
