package dev.starwars.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.starwars.auth.domain.Role;
import dev.starwars.auth.domain.UserAccount;
import dev.starwars.auth.domain.UserAccountRepository;
import dev.starwars.auth.security.DemoUserProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DemoUserInitializerTest {

    @Mock
    private UserAccountRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DemoUserInitializer initializer() {
        return new DemoUserInitializer(repository, passwordEncoder, new DemoUserProperties("demo", "demo123"));
    }

    @Test
    @DisplayName("creates the demo user with a hashed password")
    void createsDemoUser() {
        given(repository.existsByUsername("demo")).willReturn(false);
        given(passwordEncoder.encode("demo123")).willReturn("hash");

        initializer().run(new DefaultApplicationArguments());

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        then(repository).should().save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("demo");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("does nothing if the demo user already exists")
    void idempotent() {
        given(repository.existsByUsername("demo")).willReturn(true);

        initializer().run(new DefaultApplicationArguments());

        then(repository).should(never()).save(any());
    }
}
