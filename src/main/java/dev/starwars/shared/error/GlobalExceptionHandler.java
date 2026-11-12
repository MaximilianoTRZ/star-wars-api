package dev.starwars.shared.error;

import dev.starwars.catalog.domain.ResourceNotFoundException;
import dev.starwars.swapi.SwapiClientException;
import dev.starwars.swapi.UpstreamUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single place where exceptions are translated to HTTP. Every response is a {@link ProblemDetail}.
 *
 * <p>The base class already covers Spring MVC errors (type mismatch, unreadable body, unknown route,
 * unsupported method). This class adds the domain exceptions and the validation error format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI ABOUT_BLANK = URI.create("about:blank");

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(
                problems.create(HttpStatus.NOT_FOUND, "resource-not-found", "Resource not found", ex.getMessage()),
                request,
                HttpHeaders.EMPTY);
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    ResponseEntity<ProblemDetail> handleUpstreamUnavailable(
            UpstreamUnavailableException ex, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        ex.retryAfter().ifPresent(d -> headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(d.toSeconds())));
        return respond(
                problems.create(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "upstream-unavailable",
                        "SWAPI unavailable",
                        "SWAPI did not respond correctly. Try again in a few seconds."),
                request,
                headers);
    }

    @ExceptionHandler(SwapiClientException.class)
    ResponseEntity<ProblemDetail> handleSwapiClient(SwapiClientException ex, HttpServletRequest request) {
        log.warn("Unexpected SWAPI response: {}", ex.getMessage());
        return respond(
                problems.create(
                        HttpStatus.BAD_GATEWAY,
                        "upstream-error",
                        "Invalid SWAPI response",
                        "SWAPI returned a response that could not be processed."),
                request,
                HttpHeaders.EMPTY);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        return respond(
                problems.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "internal-error",
                        "Internal error",
                        "An unexpected error occurred."),
                request,
                HttpHeaders.EMPTY);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), String.valueOf(error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(),
                                String.valueOf(error.getDefaultMessage()))))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    /**
     * Every response built by the base class goes through here. Spring MVC errors get the same shape as the
     * rest: an explicit {@code type} derived from the status, a {@code timestamp}, an {@code instance} and a
     * {@code detail} that does not reveal framework internals.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            // Some handlers (unknown route, unsupported method) pass no body and let the base class
            // take it from the exception; take it here so it also gets a timestamp and an instance.
            body = errorResponse.getBody();
        }
        if (body instanceof ProblemDetail problem) {
            if (problem.getType() == null || ABOUT_BLANK.equals(problem.getType())) {
                problem.setType(URI.create("/problems/" + typeFor(statusCode)));
            }
            publicDetail(ex).ifPresent(problem::setDetail);
            problems.withTimestamp(problem);
            if (problem.getInstance() == null && request instanceof ServletWebRequest servlet) {
                problem.setInstance(URI.create(servlet.getRequest().getRequestURI()));
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    /** {@code 404} → {@code not-found}, {@code 405} → {@code method-not-allowed}. */
    static String typeFor(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null
                ? "http-" + status.value()
                : known.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Replaces Spring messages that mention internals (static resources, type conversion). */
    private static Optional<String> publicDetail(Exception ex) {
        if (ex instanceof NoResourceFoundException) {
            return Optional.of("No endpoint matches this path.");
        }
        if (ex instanceof TypeMismatchException mismatch && mismatch.getPropertyName() != null) {
            return Optional.of("Parameter '" + mismatch.getPropertyName() + "' has an invalid value.");
        }
        return Optional.empty();
    }

    private ProblemDetail validationProblem(List<FieldViolation> errors) {
        ProblemDetail problem = problems.create(
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Invalid parameters",
                "The request has invalid parameters.");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** One invalid field or parameter in a 400 response. */
    record FieldViolation(String field, String message) {}

    private static ResponseEntity<ProblemDetail> respond(
            ProblemDetail problem, HttpServletRequest request, HttpHeaders headers) {
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(problem.getStatus()).headers(headers).body(problem);
    }
}
