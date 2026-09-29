package com.example.shortener.api;

import com.example.shortener.service.Sha256;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.HexFormat;

/** Derives an opaque rate-limit key from the remote address. The raw address is never stored or logged. */
@Component
public class ClientKeyResolver {

    public String keyFor(HttpServletRequest request) {
        return HexFormat.of().formatHex(Sha256.digest(request.getRemoteAddr()), 0, 16);
    }
}
