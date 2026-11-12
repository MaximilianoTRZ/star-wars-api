package dev.starwars.auth.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Demo user created at startup ({@code app.security.demo-user.*}).
 *
 * <p>An environment variable that is not set leaves the placeholder as a literal
 * ({@code ${DEMO_USER_PASSWORD}}) instead of failing, so it is rejected explicitly.
 *
 * @param username username
 * @param password plain-text password ({@code DEMO_USER_PASSWORD}); stored with BCrypt
 */
@Validated
@ConfigurationProperties("app.security.demo-user")
public record DemoUserProperties(
        @NotBlank String username, @NotBlank String password) {

    public DemoUserProperties {
        if (password != null && password.startsWith("${")) {
            throw new IllegalArgumentException(
                    "app.security.demo-user.password (DEMO_USER_PASSWORD) is not set: " + password);
        }
    }
}
