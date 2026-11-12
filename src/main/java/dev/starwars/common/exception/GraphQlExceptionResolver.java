package dev.starwars.common.exception;

import dev.starwars.catalog.domain.ResourceNotFoundException;
import dev.starwars.swapi.SwapiClientException;
import dev.starwars.swapi.UpstreamUnavailableException;
import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Translates exceptions thrown by GraphQL queries into GraphQL errors, the counterpart of
 * {@link GlobalExceptionHandler} for REST.
 *
 * <p>Each error carries the same {@code type} as the REST {@code ProblemDetail} and the {@code requestId} of
 * the {@code X-Request-Id} header in its {@code extensions}, so a failed query can be traced in the logs.
 * Unexpected errors never expose internal messages.
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(GraphQlExceptionResolver.class);

    /** Classifications that Spring's {@link ErrorType} does not cover. */
    enum UpstreamErrorType implements ErrorClassification {
        /** SWAPI is down, slow, rate limited or the circuit is open (503 in REST). */
        UNAVAILABLE,
        /** SWAPI answered something that cannot be processed (502 in REST). */
        BAD_GATEWAY
    }

    private final ProblemDetailFactory problems;
    private final CircuitRetryAfter circuitRetryAfter;

    public GraphQlExceptionResolver(ProblemDetailFactory problems, CircuitRetryAfter circuitRetryAfter) {
        this.problems = problems;
        this.circuitRetryAfter = circuitRetryAfter;
        // Resolve with the request's thread-locals (MDC, security), also when the query ran on another thread
        setThreadLocalContextAware(true);
    }

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        return switch (ex) {
            case ResourceNotFoundException notFound ->
                error(
                        env,
                        ErrorType.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "resource-not-found",
                        "Resource not found",
                        notFound.getMessage());
            case ConstraintViolationException invalid -> validationError(env, invalid);
            case UpstreamUnavailableException unavailable ->
                unavailable(
                        env, "SWAPI did not respond correctly. Try again in a few seconds.", unavailable.retryAfter());
            case CallNotPermittedException circuitOpen ->
                unavailable(
                        env,
                        "SWAPI is failing repeatedly, so calls are paused. Try again later.",
                        circuitRetryAfter.of(circuitOpen));
            case SwapiClientException upstream -> {
                log.warn("Unexpected SWAPI response: {}", upstream.getMessage());
                yield error(
                        env,
                        UpstreamErrorType.BAD_GATEWAY,
                        HttpStatus.BAD_GATEWAY,
                        "upstream-error",
                        "Invalid SWAPI response",
                        "SWAPI returned a response that could not be processed.");
            }
            default -> {
                log.error("Unhandled error on GraphQL field {}", env.getField().getName(), ex);
                yield error(
                        env,
                        ErrorType.INTERNAL_ERROR,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "internal-error",
                        "Internal error",
                        "An unexpected error occurred.");
            }
        };
    }

    private GraphQLError validationError(DataFetchingEnvironment env, ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .sorted(Comparator.comparing(GraphQlExceptionResolver::argumentName))
                .map(violation -> Map.of("field", argumentName(violation), "message", violation.getMessage()))
                .toList();
        ProblemDetail problem = problems.create(
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Invalid parameters",
                "The request has invalid parameters.");
        return build(env, ErrorType.BAD_REQUEST, problem, Map.of("errors", errors));
    }

    private GraphQLError unavailable(DataFetchingEnvironment env, String detail, Optional<Duration> retryAfter) {
        ProblemDetail problem =
                problems.create(HttpStatus.SERVICE_UNAVAILABLE, "upstream-unavailable", "SWAPI unavailable", detail);
        Map<String, Object> extra = retryAfter
                .<Map<String, Object>>map(d -> Map.of("retryAfter", d.toSeconds()))
                .orElse(Map.of());
        return build(env, UpstreamErrorType.UNAVAILABLE, problem, extra);
    }

    private GraphQLError error(
            DataFetchingEnvironment env,
            ErrorClassification classification,
            HttpStatus status,
            String type,
            String title,
            String detail) {
        return build(env, classification, problems.create(status, type, title, detail), Map.of());
    }

    /**
     * Message from the problem's {@code detail}; {@code type}, {@code timestamp}, {@code requestId} and
     * {@code extra} as extensions.
     */
    private static GraphQLError build(
            DataFetchingEnvironment env,
            ErrorClassification classification,
            ProblemDetail problem,
            Map<String, ?> extra) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("type", String.valueOf(problem.getType()));
        if (problem.getProperties() != null) {
            extensions.putAll(problem.getProperties());
        }
        extensions.putAll(extra);
        return GraphqlErrorBuilder.newError(env)
                .errorType(classification)
                .message(problem.getDetail())
                .extensions(extensions)
                .build();
    }

    /** {@code people.size} → {@code size}. */
    private static String argumentName(ConstraintViolation<?> violation) {
        String name = "";
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name;
    }
}
