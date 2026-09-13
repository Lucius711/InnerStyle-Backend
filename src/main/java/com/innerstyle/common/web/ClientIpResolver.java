package com.innerstyle.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the "real" client IP for rate limiting and audit logging.
 *
 * <p>{@code X-Forwarded-For} is NOT trusted directly: this deployment's nginx sets it with
 * {@code $proxy_add_x_forwarded_for}, which APPENDS the observed peer to whatever value the
 * client already sent, rather than replacing it — so a client can prepend an arbitrary IP and
 * have it survive as the first (and previously trusted) entry. {@code X-Real-IP} is set with
 * {@code proxy_set_header X-Real-IP $remote_addr}, which always REPLACES the header with nginx's
 * own view of the peer, so it cannot be spoofed by a client talking to nginx. Only nginx can
 * reach the backend (it is the sole container on the docker-compose network with a published
 * port; the backend only {@code expose}s its port internally), so nginx is the sole trusted
 * source of {@code X-Real-IP} here.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
