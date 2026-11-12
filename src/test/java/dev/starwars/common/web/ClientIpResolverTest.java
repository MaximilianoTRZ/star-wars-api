package dev.starwars.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.7");
        return request;
    }

    @Test
    @DisplayName("uses the configured edge header when present")
    void usesHeader() {
        var request = request();
        request.addHeader("CF-Connecting-IP", " 203.0.113.7 ");

        assertThat(new ClientIpResolver(new ClientIpProperties("CF-Connecting-IP")).resolve(request))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("falls back to the remote address when the header is missing")
    void fallsBackWhenHeaderMissing() {
        assertThat(new ClientIpResolver(new ClientIpProperties("CF-Connecting-IP")).resolve(request()))
                .isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("without a configured header, client headers are ignored")
    void ignoresHeadersWhenNotConfigured() {
        var request = request();
        request.addHeader("CF-Connecting-IP", "198.51.100.9");

        assertThat(new ClientIpResolver(new ClientIpProperties(null)).resolve(request))
                .isEqualTo("10.0.0.5");
    }
}
