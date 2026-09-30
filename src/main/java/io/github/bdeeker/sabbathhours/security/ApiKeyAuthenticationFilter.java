package io.github.bdeeker.sabbathhours.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request that carries the correct {@value #HEADER} header as the "writer".
 * A missing or wrong key leaves the request anonymous; the authorization rules then decide.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String WRITER_ROLE = "WRITER";

    private final byte[] expectedKey;

    /** @param writeKey the configured key, or null when writes are disabled */
    public ApiKeyAuthenticationFilter(String writeKey) {
        this.expectedKey = writeKey == null ? null : writeKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (expectedKey != null && presented != null && matches(presented)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "api-key", null, List.of(new SimpleGrantedAuthority("ROLE_" + WRITER_ROLE)));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    /** Constant-time comparison, so response timing reveals nothing about how much of a guess was right. */
    private boolean matches(String presented) {
        return MessageDigest.isEqual(expectedKey, presented.getBytes(StandardCharsets.UTF_8));
    }
}
