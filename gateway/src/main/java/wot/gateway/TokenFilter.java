package wot.gateway;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bearer token on /things/** and /events/** (token: gateway.token). Dashboard files are public.
 * TODO (bonus): roles viewer / operator, 403.
 */
@Component
public class TokenFilter extends OncePerRequestFilter {

    private final String expectedToken;

    public TokenFilter(@Value("${gateway.token}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/things") || path.startsWith("/events"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = null;
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring("Bearer ".length()).trim();
        } else if (request.getRequestURI().equals("/events/stream")) {
            // EventSource cannot send headers
            token = request.getParameter("token");
        }

        if (token == null || !token.equals(expectedToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":401,\"error\":\"missing or invalid token\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
