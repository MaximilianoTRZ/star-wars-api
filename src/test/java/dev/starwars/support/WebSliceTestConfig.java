package dev.starwars.support;

import dev.starwars.auth.security.JwtConfig;
import dev.starwars.auth.security.SecurityConfig;
import dev.starwars.auth.service.AuthMetrics;
import dev.starwars.auth.service.TokenService;
import dev.starwars.common.exception.CircuitRetryAfter;
import dev.starwars.common.exception.ProblemDetailFactory;
import dev.starwars.common.web.ClientIpProperties;
import dev.starwars.common.web.ClientIpResolver;
import dev.starwars.config.ClockConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * What a {@code @WebMvcTest} needs to run the real security chain: JWT, 401/403 handlers,
 * {@link ProblemDetailFactory}, {@link TokenService} to issue tokens through login, and the login metrics.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClientIpProperties.class)
@Import({
    SecurityConfig.class,
    JwtConfig.class,
    ClockConfig.class,
    ProblemDetailFactory.class,
    CircuitRetryAfter.class,
    TokenService.class,
    AuthMetrics.class,
    ClientIpResolver.class
})
@ComponentScan(basePackageClasses = SecurityConfig.class)
public class WebSliceTestConfig {

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
