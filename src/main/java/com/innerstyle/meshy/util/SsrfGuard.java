package com.innerstyle.meshy.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Shared SSRF guard for any URL the backend fetches server-side on behalf of a request: http(s)
 * only, no private/loopback/link-local/multicast hosts. Used by {@code ImageProxyController} for
 * client-supplied URLs and by {@code MeshyTaskServiceImpl} for URLs arriving in Meshy webhook
 * payloads (finding TRUNG: webhook payloads were merged onto tasks with no URL validation at
 * all, unlike the image proxy).
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /** Returns true if the URL is http(s) with a resolvable, non-private/loopback/link-local host. */
    public static boolean isSafe(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (RuntimeException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                    || addr.isMulticastAddress()) {
                    return false;
                }
            }
        } catch (UnknownHostException e) {
            return false;
        }
        return true;
    }
}
