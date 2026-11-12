package dev.starwars.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves the IP of the client that made the request, for the login rate limit and the logs.
 *
 * <p>On Render the request goes through Cloudflare and then Render's own proxy, so the connection's remote
 * address is a proxy. {@code X-Forwarded-For} cannot be trusted either: its first value is whatever the client
 * sent, and its last ones are proxies. Cloudflare sets {@code CF-Connecting-IP} to the real client IP and
 * overwrites any value the client sends, so in {@code prod} that header is the source. Locally no header is
 * configured and the remote address is used, so nobody can pick their own IP by sending a header.
 */
@Component
public class ClientIpResolver {

    private final String header;

    public ClientIpResolver(ClientIpProperties properties) {
        this.header = properties.header();
    }

    public String resolve(HttpServletRequest request) {
        if (StringUtils.hasText(header)) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                return value.strip();
            }
        }
        return request.getRemoteAddr();
    }
}
