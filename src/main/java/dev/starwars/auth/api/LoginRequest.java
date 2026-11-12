package dev.starwars.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Login credentials. */
public record LoginRequest(
        @Schema(example = "demo") @NotBlank @Size(max = 50) String username,

        @Schema(example = "demo123") @NotBlank @Size(max = 100)
        String password) {

    /** Keeps the password out of logs. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
