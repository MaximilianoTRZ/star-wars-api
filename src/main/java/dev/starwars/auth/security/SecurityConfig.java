package dev.starwars.auth.security;

import dev.starwars.auth.service.AuthMetrics;
import dev.starwars.common.web.ClientIpResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API security filter chain (ADR-004).
 *
 * <ul>
 *   <li><b>Deny by default:</b> only login, the OpenAPI docs and the {@code /livez} and {@code /readyz} probes
 *       are public; any other route is rejected.</li>
 *   <li><b>Actuator:</b> its own chain. Locally it runs on a management port that is never published and is
 *       open; in {@code prod} it shares the public port and {@code /actuator/prometheus} requires HTTP Basic
 *       with the scrape credentials ({@link MetricsScrapeProperties}).</li>
 *   <li><b>Authorization:</b> {@code /api/v1/**} requires the {@code SCOPE_catalog:read} authority, taken
 *       from the JWT {@code scope} claim. Being authenticated is not enough.</li>
 *   <li><b>Stateless:</b> no HTTP session. CSRF is disabled because there are no cookies: the token travels
 *       in the {@code Authorization} header, which browsers never send on their own.</li>
 *   <li><b>Errors:</b> 401 and 403 return {@code ProblemDetail}, like the rest of the API.</li>
 *   <li><b>Logs:</b> the token's user goes to the MDC ({@link AuthenticatedUserFilter}).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties({MetricsScrapeProperties.class, LoginRateLimitProperties.class})
public class SecurityConfig {

    /** Scope issued by login and required by the catalog endpoints. */
    public static final String CATALOG_READ_SCOPE = "catalog:read";

    /** Role of the user that scrapes {@code /actuator/prometheus}. */
    static final String METRICS_ROLE = "METRICS";

    /**
     * Actuator endpoints. Locally they live on a management port that is never published, so they are open.
     * In {@code prod} they share the public port (Render publishes only one) and the Prometheus endpoint
     * requires HTTP Basic with the scrape credentials; health and info stay public.
     */
    @Bean
    @Order(1)
    SecurityFilterChain managementFilterChain(
            HttpSecurity http, MetricsScrapeProperties scrape, PasswordEncoder passwordEncoder) {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502 - no cookies, see the API chain
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (scrape.credentialsRequired()) {
            http.authenticationManager(metricsAuthenticationManager(scrape, passwordEncoder))
                    .httpBasic(Customizer.withDefaults())
                    .authorizeHttpRequests(auth -> auth.requestMatchers(EndpointRequest.to("prometheus"))
                            .hasRole(METRICS_ROLE)
                            .anyRequest()
                            .permitAll());
        } else {
            http.httpBasic(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }

    /** Authenticates only the scrape user; it is separate from the H2 users that log in to the API. */
    private static AuthenticationManager metricsAuthenticationManager(
            MetricsScrapeProperties scrape, PasswordEncoder passwordEncoder) {
        UserDetails scraper = User.withUsername(scrape.username())
                .password(passwordEncoder.encode(scrape.password()))
                .roles(METRICS_ROLE)
                .build();
        var provider = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(scraper));
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler) {
        // CSRF needs credentials that the browser attaches on its own (cookies). This API is stateless and the
        // token travels in the Authorization header, which browsers never send by themselves.
        http.csrf(AbstractHttpConfigurer::disable); // NOSONAR java:S4502 - no cookies, see comment above
        http.httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/livez", "/readyz")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/api/v1/**")
                        .hasAuthority("SCOPE_" + CATALOG_READ_SCOPE)
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(new AuthenticatedUserFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** Rate limit on login, applied as a servlet filter before the security chain. */
    @Bean
    LoginRateLimitFilter loginRateLimitFilter(
            LoginRateLimiter limiter,
            ProblemDetailResponseWriter writer,
            AuthMetrics metrics,
            ClientIpResolver clientIpResolver) {
        return new LoginRateLimitFilter(limiter, writer, metrics, clientIpResolver);
    }

    /**
     * Username/password authentication for login. {@link DaoAuthenticationProvider} returns the same error
     * for an unknown user and a wrong password, and hashes in both cases so response times do not reveal
     * which users exist.
     */
    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
