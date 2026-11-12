package dev.starwars.auth.security;

import dev.starwars.common.exception.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes a {@link ProblemDetail} from the security filters, which run outside Spring MVC. */
@Component
class ProblemDetailResponseWriter {

    private final ProblemDetailFactory problems;
    private final JsonMapper jsonMapper;

    ProblemDetailResponseWriter(ProblemDetailFactory problems, JsonMapper jsonMapper) {
        this.problems = problems;
        this.jsonMapper = jsonMapper;
    }

    void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String type,
            String title,
            String detail)
            throws IOException {
        ProblemDetail problem = problems.create(status, type, title, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), problems.toMap(problem));
    }
}
