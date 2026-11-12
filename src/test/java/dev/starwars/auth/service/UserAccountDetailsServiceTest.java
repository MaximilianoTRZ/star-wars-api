package dev.starwars.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import dev.starwars.auth.domain.Role;
import dev.starwars.auth.domain.UserAccount;
import dev.starwars.auth.domain.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class UserAccountDetailsServiceTest {

    @Mock
    private UserAccountRepository repository;

    @InjectMocks
    private UserAccountDetailsService service;

    @Test
    @DisplayName("loads the user with its hash and role")
    void loadsUser() {
        given(repository.findByUsername("demo")).willReturn(Optional.of(new UserAccount("demo", "{hash}", Role.USER)));

        UserDetails user = service.loadUserByUsername("demo");

        assertThat(user.getUsername()).isEqualTo("demo");
        assertThat(user.getPassword()).isEqualTo("{hash}");
        assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("unknown user throws UsernameNotFoundException")
    void unknownUser() {
        given(repository.findByUsername("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody")).isInstanceOf(UsernameNotFoundException.class);
    }
}
