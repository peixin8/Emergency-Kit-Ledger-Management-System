package com.emergency.ledger;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class Trace extends OncePerRequestFilter {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    public static String id() { return CURRENT.get() == null ? "" : CURRENT.get(); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String trace = UUID.randomUUID().toString();
        CURRENT.set(trace);
        response.setHeader("X-Trace-Id", trace);
        try { chain.doFilter(request, response); } finally { CURRENT.remove(); }
    }
}
