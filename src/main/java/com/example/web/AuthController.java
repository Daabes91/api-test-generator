package com.example.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class AuthController {
    // Very simple in-memory session store
    public static final Map<String, String> SESSIONS = new ConcurrentHashMap<>();

    // Hard-coded credentials as requested
    private static final String EMAIL = "m.daabes@salla.sa";
    private static final String PASSWORD = "Salla@2025";

    @GetMapping("/login")
    public String loginPage() {
        return "login.html"; // served from static
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> doLogin(@RequestParam("email") String email,
                                     @RequestParam("password") String password,
                                     HttpServletResponse resp) {
        if (EMAIL.equals(email) && PASSWORD.equals(password)) {
            String token = UUID.randomUUID().toString();
            SESSIONS.put(token, email);
            Cookie c = new Cookie("SESSION_ID", token);
            c.setPath("/");
            c.setHttpOnly(true);
            // Optional: session cookie (no max-age)
            resp.addCookie(c);
            return ResponseEntity.status(302).location(URI.create("/")).build();
        }
        return ResponseEntity.status(302).location(URI.create("/login?error=1")).build();
    }

    @GetMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = "SESSION_ID", required = false) String token,
                                    HttpServletResponse resp) {
        if (token != null) {
            SESSIONS.remove(token);
            Cookie c = new Cookie("SESSION_ID", "");
            c.setPath("/");
            c.setMaxAge(0);
            resp.addCookie(c);
        }
        return ResponseEntity.status(302).location(URI.create("/login")).build();
    }

    @GetMapping(value = "/api/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> me(@CookieValue(value = "SESSION_ID", required = false) String token) {
        if (token != null) {
            String email = SESSIONS.get(token);
            if (email != null) return ResponseEntity.ok(java.util.Map.of("email", email));
        }
        return ResponseEntity.status(401).body(java.util.Map.of("ok", false, "error", "Unauthorized"));
    }
}
