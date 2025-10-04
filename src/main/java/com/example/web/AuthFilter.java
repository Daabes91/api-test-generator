package com.example.web;

import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class AuthFilter implements Filter {

    private static boolean isWhitelisted(String path) {
        if (path == null) return false;
        return path.equals("/login")
                || path.equals("/error")
                || path.startsWith("/favicon")
                || path.startsWith("/assets/")
                || path.startsWith("/static/")
                || path.equals("/robots.txt")
                || path.matches("/.+\\.(css|js|png|jpg|svg|ico)")
                || path.startsWith("/api/template/"); // allow template download pre-auth if needed
    }

    private static boolean hasValidSession(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return false;
        for (Cookie c : cookies) {
            if ("SESSION_ID".equals(c.getName())) {
                String token = c.getValue();
                return token != null && AuthController.SESSIONS.containsKey(token);
            }
        }
        return false;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();

        // Allow login page/posts
        if (isWhitelisted(path) || (path.equals("/login") && ("GET".equals(req.getMethod()) || "POST".equals(req.getMethod())))) {
            chain.doFilter(request, response);
            return;
        }

        // Check session
        if (!hasValidSession(req)) {
            // For API calls, return 401 JSON; for pages, redirect to /login
            boolean wantsJson = path.startsWith("/api/") || path.startsWith("/ui/");
            if (wantsJson) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Unauthorized\"}");
            } else {
                resp.setStatus(302);
                resp.setHeader("Location", "/login");
            }
            return;
        }

        chain.doFilter(request, response);
    }
}
