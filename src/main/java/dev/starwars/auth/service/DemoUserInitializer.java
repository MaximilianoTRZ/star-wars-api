package dev.starwars.auth.service;

import dev.starwars.auth.domain.Role;
import dev.starwars.auth.domain.UserAccount;
import dev.starwars.auth.domain.UserAccountRepository;
import dev.starwars.auth.security.DemoUserProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the demo user at startup if it does not exist. Idempotent. */
@Component
public class DemoUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserInitializer.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final DemoUserProperties demoUser;

    public DemoUserInitializer(
            UserAccountRepository repository, PasswordEncoder passwordEncoder, DemoUserProperties demoUser) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.demoUser = demoUser;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.existsByUsername(demoUser.username())) {
            return;
        }
        repository.save(new UserAccount(demoUser.username(), passwordEncoder.encode(demoUser.password()), Role.USER));
        log.info("Demo user '{}' created", demoUser.username());
    }
}
