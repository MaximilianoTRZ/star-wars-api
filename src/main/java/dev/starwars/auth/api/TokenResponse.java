package dev.starwars.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Access token issued by login.
 *
 * @param accessToken JWT for the {@code Authorization: Bearer <token>} header
 * @param tokenType   always {@code Bearer}
 * @param expiresIn   lifetime in seconds
 */
public record TokenResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "3600") long expiresIn) {}
