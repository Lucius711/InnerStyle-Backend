package com.innerstyle.meshy.controller;

import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.UpstreamServiceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Same-origin proxy for remote images so the browser can run client-side NSFW moderation
 * (nsfwjs) on an image provided by URL — third-party hosts usually omit CORS headers, which
 * taints the canvas and blocks pixel reads. The server fetches the bytes (no CORS) and streams
 * them back same-origin. Guarded against SSRF: http(s) only, no redirects, no private hosts.
 */
@Slf4j
@Tag(name = "Common - 3D Generation (MeshyAI)")
@RestController
@RequestMapping("/common/3d")
public class ImageProxyController {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private static final long MAX_BYTES = 20L * 1024 * 1024;

    @GetMapping("/moderation/image-proxy")
    @Operation(summary = "Proxy a remote image (same-origin) so the client can run NSFW moderation on it")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String url) {
        URI uri = validate(url);
        try {
            HttpResponse<byte[]> resp = HTTP.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new UpstreamServiceException("meshy.upstreamError");
            }
            String contentType = resp.headers().firstValue("content-type")
                .orElse("application/octet-stream");
            if (!contentType.toLowerCase().startsWith("image/")) {
                throw new BadRequestException("validation.image.invalid");
            }
            byte[] body = resp.body();
            if (body.length > MAX_BYTES) {
                throw new BadRequestException("validation.image.tooLarge");
            }
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(body);
        } catch (IOException e) {
            log.warn("Image proxy fetch failed: {}", e.getMessage());
            throw new UpstreamServiceException("meshy.upstreamError");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException("meshy.upstreamError");
        }
    }

    /** Validate scheme + reject private / loopback / link-local hosts (SSRF guard). */
    private URI validate(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (RuntimeException e) {
            throw new BadRequestException("validation.image.invalid");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BadRequestException("validation.image.invalid");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BadRequestException("validation.image.invalid");
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                    || addr.isMulticastAddress()) {
                    throw new BadRequestException("validation.image.invalid");
                }
            }
        } catch (UnknownHostException e) {
            throw new BadRequestException("validation.image.invalid");
        }
        return uri;
    }
}
