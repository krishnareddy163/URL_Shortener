package com.example.shortener.api;

import com.example.shortener.domain.LinkStats;
import com.example.shortener.domain.ShortLink;
import com.example.shortener.service.ErrorCode;
import com.example.shortener.service.RateLimiter;
import com.example.shortener.service.ShortenerException;
import com.example.shortener.service.ShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/** HTTP adapter for link creation, redirection, and statistics. */
@RestController
public class LinkController {
    private final ShortenerService service;
    private final RateLimiter rateLimiter;
    private final ClientKeyResolver clientKeys;
    private final String configuredBaseUrl;

    public LinkController(ShortenerService service, RateLimiter rateLimiter, ClientKeyResolver clientKeys,
                          @Value("${shortener.base-url:}") String configuredBaseUrl) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.clientKeys = clientKeys;
        this.configuredBaseUrl = configuredBaseUrl;
    }

    @PostMapping(path = "/api/v1/links", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request,
                                               HttpServletRequest httpRequest) {
        if (!rateLimiter.tryAcquire(clientKeys.keyFor(httpRequest))) {
            throw new ShortenerException(ErrorCode.RATE_LIMITED, "Too many requests; retry later");
        }
        ShortenerService.CreateResult result = service.create(request.url(), request.customAlias(), request.expiresAt());
        LinkResponse body = LinkResponse.of(result.link(), baseUrl());
        return result.created()
                ? ResponseEntity.created(URI.create(body.shortUrl())).body(body)
                : ResponseEntity.ok(body);
    }

    @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        ShortLink link = service.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(link.url())).build();
    }

    @GetMapping(path = "/api/v1/links/{code}/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public LinkStats stats(@PathVariable String code) {
        return service.stats(code);
    }

    private String baseUrl() {
        if (!configuredBaseUrl.isBlank()) {
            return configuredBaseUrl;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
