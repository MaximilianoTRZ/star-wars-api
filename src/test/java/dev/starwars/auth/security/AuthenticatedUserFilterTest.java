package dev.starwars.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.common.web.RequestLoggingFilter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthenticatedUserFilterTest {

    private final AuthenticatedUserFilter filter = new AuthenticatedUserFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("puts the authenticated user in the MDC and the request, and clears the MDC afterwards")
    void exposesUser() throws Exception {
        var authentication = new TestingAuthenticationToken("luke", null, "SCOPE_catalog:read");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequest request = new MockHttpServletRequest();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (req, res) -> seenInMdc.set(MDC.get(AuthenticatedUserFilter.MDC_KEY)));

        assertThat(seenInMdc).hasValue("luke");
        assertThat(request.getAttribute(RequestLoggingFilter.USER_ATTRIBUTE)).isEqualTo("luke");
        assertThat(MDC.get(AuthenticatedUserFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("leaves anonymous requests without a user")
    void ignoresAnonymous() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        AtomicReference<String> seenInMdc = new AtomicReference<>("unset");

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (req, res) -> seenInMdc.set(MDC.get(AuthenticatedUserFilter.MDC_KEY)));

        assertThat(seenInMdc.get()).isNull();
        assertThat(request.getAttribute(RequestLoggingFilter.USER_ATTRIBUTE)).isNull();
    }
}
