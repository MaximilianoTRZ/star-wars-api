package dev.starwars.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.jayway.jsonpath.JsonPath;
import dev.starwars.support.WebSliceTestConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// The login rate limit is covered by LoginRateLimitIT; here it would block the repeated logins
@WebMvcTest(controllers = AuthController.class, properties = "app.security.login-rate-limit.capacity=1000")
@Import(WebSliceTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MeterRegistry registry;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        given(userDetailsService.loadUserByUsername("demo"))
                .willReturn(User.withUsername("demo")
                        .password(passwordEncoder.encode("demo123"))
                        .roles("USER")
                        .build());
        given(userDetailsService.loadUserByUsername("nobody")).willThrow(new UsernameNotFoundException("x"));
    }

    private MvcTestResult login(String body) {
        return mvc.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    @Test
    @DisplayName("valid credentials return a 1 h Bearer token")
    void loginOk() {
        MvcTestResult result = login("{\"username\":\"demo\",\"password\":\"demo123\"}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
        assertThat(result).bodyJson().extractingPath("$.expiresIn").isEqualTo(3600);
        assertThat(result).bodyJson().extractingPath("$.accessToken").asString().contains(".");
    }

    @Test
    @DisplayName("logs a success with the user and a failure without username, password or token")
    void logsLoginOutcome(CapturedOutput output) throws Exception {
        MvcTestResult ok = login("{\"username\":\"demo\",\"password\":\"demo123\"}");
        login("{\"username\":\"typed-secret\",\"password\":\"wrong-secret\"}");

        String token = JsonPath.read(ok.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
        assertThat(output)
                .containsPattern("INFO.*Login succeeded for demo")
                .containsPattern("WARN.*Login failed from ")
                .doesNotContain("demo123")
                .doesNotContain("typed-secret")
                .doesNotContain("wrong-secret")
                .doesNotContain(token);
    }

    @Test
    @DisplayName("records login attempts by result")
    void recordsAttempts() {
        double successes = attempts("success");
        double failures = attempts("failure");

        login("{\"username\":\"demo\",\"password\":\"demo123\"}");
        login("{\"username\":\"demo\",\"password\":\"wrong\"}");

        assertThat(attempts("success")).isEqualTo(successes + 1);
        assertThat(attempts("failure")).isEqualTo(failures + 1);
    }

    private double attempts(String result) {
        return registry.get("auth.login.attempts")
                .tag("result", result)
                .counter()
                .count();
    }

    @Test
    @DisplayName("wrong password: 401 ProblemDetail")
    void wrongPassword() {
        MvcTestResult result = login("{\"username\":\"demo\",\"password\":\"wrong\"}");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/invalid-credentials");
        assertThat(result).bodyJson().extractingPath("$.timestamp").isNotNull();
    }

    @Test
    @DisplayName("unknown user: same 401 and same message as a wrong password")
    void unknownUserSameMessage() throws Exception {
        String wrongPassword = withoutVolatileFields(login("{\"username\":\"demo\",\"password\":\"wrong\"}"));
        MvcTestResult unknownUser = login("{\"username\":\"nobody\",\"password\":\"wrong\"}");

        assertThat(unknownUser).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(withoutVolatileFields(unknownUser)).isEqualTo(wrongPassword);
    }

    private static String withoutVolatileFields(MvcTestResult result) throws Exception {
        return result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("\"(timestamp|requestId)\":\"[^\"]+\"", "");
    }

    @Test
    @DisplayName("body without password: 400 listing the field in errors")
    void missingPassword() {
        MvcTestResult result = login("{\"username\":\"demo\"}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/validation-error");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");
    }

    @Test
    @DisplayName("unreadable body: 400")
    void malformedBody() {
        assertThat(login("{not json")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
