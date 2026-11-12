package dev.starwars.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the client IP comes from ({@code app.client-ip.*}).
 *
 * @param header request header set by a trusted edge proxy with the real client IP (in {@code prod},
 *     {@code CF-Connecting-IP}); empty to use the connection's remote address
 */
@ConfigurationProperties("app.client-ip")
public record ClientIpProperties(String header) {}
