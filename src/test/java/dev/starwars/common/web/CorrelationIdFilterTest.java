package dev.starwars.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("reuses a valid incoming X-Request-Id in the MDC and the response")
    void propagatesIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(seenInMdc).hasValue("abc-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("generates a UUID when the header is missing and clears the MDC afterwards")
    void generatesId() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {});

        assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER)))
                .isNotNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "with space", "line\nbreak", "<script>"})
    @DisplayName("replaces unsafe incoming values with a generated ID")
    void rejectsUnsafeIds(String incoming) {
        assertThat(CorrelationIdFilter.resolve(incoming)).isNotEqualTo(incoming);
    }

    @Test
    @DisplayName("replaces values longer than 64 characters")
    void rejectsLongIds() {
        String tooLong = "a".repeat(65);

        assertThat(CorrelationIdFilter.resolve(tooLong)).isNotEqualTo(tooLong);
    }
}
