package dev.starwars.support;

import dev.starwars.auth.security.JwtConfig;
import dev.starwars.auth.security.SecurityConfig;
import dev.starwars.common.exception.ProblemDetailFactory;
import dev.starwars.config.ClockConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * What a {@code @WebMvcTest} needs to run the real security chain: JWT, 401/403 handlers and
 * {@link ProblemDetailFactory}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfig.class, JwtConfig.class, ClockConfig.class, ProblemDetailFactory.class})
@ComponentScan(basePackageClasses = SecurityConfig.class)
public class WebSliceTestConfig {}
