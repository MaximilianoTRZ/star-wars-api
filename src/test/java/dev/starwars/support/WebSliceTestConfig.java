package dev.starwars.support;

import dev.starwars.auth.security.JwtConfig;
import dev.starwars.auth.security.SecurityConfig;
import dev.starwars.auth.service.TokenService;
import dev.starwars.common.exception.ProblemDetailFactory;
import dev.starwars.config.ClockConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * What a {@code @WebMvcTest} needs to run the real security chain: JWT, 401/403 handlers,
 * {@link ProblemDetailFactory} and {@link TokenService} to issue tokens through login.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfig.class, JwtConfig.class, ClockConfig.class, ProblemDetailFactory.class, TokenService.class})
@ComponentScan(basePackageClasses = SecurityConfig.class)
public class WebSliceTestConfig {}
