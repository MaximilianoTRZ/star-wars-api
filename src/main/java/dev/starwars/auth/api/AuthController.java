package dev.starwars.auth.api;

import dev.starwars.auth.service.TokenService;
import dev.starwars.auth.service.TokenService.IssuedToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login: validates username and password and returns a JWT. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Login and access tokens")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Log in",
            description = "Returns a JWT valid for 1 hour with the catalog:read scope. "
                    + "Send it as `Authorization: Bearer <token>`.")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        IssuedToken token = tokenService.issue(authentication.getName());
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds());
    }
}
