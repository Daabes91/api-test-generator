package com.example.web;

import com.example.support.TestRunService;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.IOException;

@Component
@Order(1)
public class AuthFilter implements Filter {

    private final TestRunService testRunService;

    public AuthFilter(TestRunService testRunService) {
        this.testRunService = testRunService;
    }

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

    private static final Pattern JOB_PATH = Pattern.compile("^/api/tests/([A-Za-z0-9\-]+)(?:/log)?$");

    private String extractRunId(String path) {
        if (path == null) return null;
        Matcher m = JOB_PATH.matcher(path);
        return m.find() ? m.group(1) : null;
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
            String runToken = req.getHeader("X-Run-Token");
            String runId = extractRunId(path);
            if (runToken != null && runId != null && testRunService.validateAccessToken(runId, runToken)) {
                chain.doFilter(request, response);
                return;
            }
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
