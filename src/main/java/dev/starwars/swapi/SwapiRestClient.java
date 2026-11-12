package dev.starwars.swapi;

import dev.starwars.swapi.SwapiResponses.DetailResponse;
import dev.starwars.swapi.SwapiResponses.Entity;
import dev.starwars.swapi.SwapiResponses.ListItem;
import dev.starwars.swapi.SwapiResponses.ListResponse;
import dev.starwars.swapi.SwapiResponses.SearchResponse;
import dev.starwars.swapi.UpstreamUnavailableException.Reason;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link SwapiClient} backed by {@link RestClient}. Always uses URI templates so that HTTP client
 * metrics are grouped by route instead of by value.
 */
public class SwapiRestClient implements SwapiClient {

    private static final Logger log = LoggerFactory.getLogger(SwapiRestClient.class);

    private final RestClient restClient;

    public SwapiRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public SwapiPage list(ResourceType type, int page, int limit) {
        if (!type.paginatedUpstream()) {
            List<SwapiEntity> all = toEntities(call(
                    type,
                    () -> restClient
                            .get()
                            .uri("/{path}", type.path())
                            .retrieve()
                            .body(SearchResponse.class)));
            return new SwapiPage(all, all.size(), 1);
        }
        ListResponse response = call(
                type,
                () -> restClient
                        .get()
                        .uri("/{path}?page={page}&limit={limit}", type.path(), page, limit)
                        .retrieve()
                        .body(ListResponse.class));
        if (response == null
                || response.results() == null
                || response.totalRecords() == null
                || response.totalPages() == null) {
            throw new SwapiClientException("Unexpected SWAPI response when listing " + type.path());
        }
        List<SwapiEntity> items =
                response.results().stream().map(item -> toEntity(type, item)).toList();
        return new SwapiPage(items, response.totalRecords(), response.totalPages());
    }

    @Override
    public List<SwapiEntity> search(ResourceType type, String term) {
        return toEntities(call(
                type,
                () -> restClient
                        .get()
                        .uri("/{path}?{param}={term}", type.path(), type.nameAttribute(), term)
                        .retrieve()
                        .body(SearchResponse.class)));
    }

    @Override
    public Optional<SwapiEntity> findById(ResourceType type, String id) {
        Optional<DetailResponse> response = callAllowingNotFound(
                type,
                () -> restClient
                        .get()
                        .uri("/{path}/{id}", type.path(), id)
                        .retrieve()
                        .body(DetailResponse.class));
        if (response.isEmpty()) {
            return Optional.empty();
        }
        Entity result = response.get().result();
        if (result == null) {
            throw new SwapiClientException("Unexpected SWAPI response for " + type.path() + "/" + id);
        }
        return Optional.of(toEntity(result));
    }

    /** Like {@link #call}, but a SWAPI 404 is returned as empty. */
    private <T> Optional<T> callAllowingNotFound(ResourceType type, Supplier<T> request) {
        try {
            T body = call(type, request);
            if (body == null) {
                throw new SwapiClientException("SWAPI returned an empty body for " + type.path());
            }
            return Optional.of(body);
        } catch (NotFoundUpstream e) {
            return Optional.empty();
        }
    }

    /**
     * Runs the request and translates {@link RestClient} exceptions into this package's exceptions.
     * A 404 is raised as {@link NotFoundUpstream}, which only {@link #callAllowingNotFound} catches;
     * in a listing or a search it is a contract error and surfaces as {@link SwapiClientException}.
     */
    private <T> T call(ResourceType type, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundUpstream(type, e);
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (e.getStatusCode().is5xxServerError() || status == HttpStatus.TOO_MANY_REQUESTS) {
                Reason reason = status == HttpStatus.TOO_MANY_REQUESTS ? Reason.RATE_LIMITED : Reason.SERVER_ERROR;
                logAttemptFailed(
                        type, reason, "SWAPI answered " + e.getStatusCode().value());
                throw new UpstreamUnavailableException(
                        "SWAPI answered " + e.getStatusCode().value(), reason, retryAfter(e.getResponseHeaders()), e);
            }
            throw new SwapiClientException("SWAPI answered " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            Reason reason = isReadTimeout(e) ? Reason.TIMEOUT : Reason.CONNECTION;
            logAttemptFailed(type, reason, e.getMessage());
            throw new UpstreamUnavailableException("SWAPI did not respond in time", reason, null, e);
        } catch (RestClientException e) {
            throw new SwapiClientException("Could not read the SWAPI response", e);
        }
    }

    /**
     * One failed attempt, at {@code DEBUG}: whether it is retried or ends the call is logged once by
     * {@link SwapiResilienceLogging}.
     */
    private static void logAttemptFailed(ResourceType type, Reason reason, String detail) {
        log.atDebug()
                .setMessage("SWAPI attempt failed for {} ({}): {}")
                .addArgument(type.path())
                .addArgument(reason)
                .addArgument(detail)
                .addKeyValue("swapi.resource", type.path())
                .addKeyValue("swapi.reason", reason.name())
                .log();
    }

    /** A connect timeout is a connection error; only a timeout waiting for the response counts as TIMEOUT. */
    private static boolean isReadTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static Duration retryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<SwapiEntity> toEntities(SearchResponse response) {
        if (response == null || response.result() == null) {
            throw new SwapiClientException("Unexpected SWAPI response: missing 'result'");
        }
        return response.result().stream().map(SwapiRestClient::toEntity).toList();
    }

    private static SwapiEntity toEntity(Entity entity) {
        if (entity.uid() == null) {
            throw new SwapiClientException("Unexpected SWAPI response: entity without 'uid'");
        }
        return new SwapiEntity(entity.uid(), entity.properties());
    }

    private static SwapiEntity toEntity(ResourceType type, ListItem item) {
        if (item.uid() == null) {
            throw new SwapiClientException("Unexpected SWAPI response: item without 'uid'");
        }
        return new SwapiEntity(item.uid(), item.name() == null ? Map.of() : Map.of(type.nameAttribute(), item.name()));
    }

    /** SWAPI 404. Outside {@link #findById} it behaves like any other {@link SwapiClientException}. */
    private static final class NotFoundUpstream extends SwapiClientException {
        NotFoundUpstream(ResourceType type, Throwable cause) {
            super("SWAPI answered 404 for " + type.path(), cause);
        }
    }
}
