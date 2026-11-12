package dev.starwars.shared.error;

import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.ResourceNotFoundException;
import dev.starwars.swapi.SwapiClientException;
import dev.starwars.swapi.UpstreamUnavailableException;
import dev.starwars.swapi.UpstreamUnavailableException.Reason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Throws each exception that {@link GlobalExceptionHandler} maps. Test only. */
@RestController
@RequestMapping("/test/errors")
class ErrorTestController {

    record Body(@NotBlank String name) {}

    @GetMapping("/not-found")
    void notFound() {
        throw new ResourceNotFoundException(CatalogResource.PEOPLE, "9999");
    }

    @GetMapping("/upstream-error")
    void upstreamError() {
        throw new SwapiClientException("SWAPI answered 400");
    }

    @GetMapping("/rate-limited")
    void rateLimited() {
        throw new UpstreamUnavailableException("SWAPI answered 429", Reason.RATE_LIMITED, Duration.ofSeconds(30), null);
    }

    @GetMapping("/upstream-down")
    void upstreamDown() {
        throw new UpstreamUnavailableException("SWAPI answered 500", Reason.SERVER_ERROR, null, null);
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException("secret internal detail");
    }

    @GetMapping("/param")
    int param(@RequestParam @Max(10) int size) {
        return size;
    }

    @PostMapping("/body")
    String body(@Valid @RequestBody Body body) {
        return body.name();
    }
}
