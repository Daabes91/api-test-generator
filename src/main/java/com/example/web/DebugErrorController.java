package com.example.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class DebugErrorController implements org.springframework.boot.web.servlet.error.ErrorController {
    private final ErrorAttributes errorAttributes;

    public DebugErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping(value = "/error", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleErrorHtml(HttpServletRequest request) {
        var attrs = getErrorAttributes(request, true);
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'>")
            .append("<title>Error</title>")
            .append("<style>body{font-family:system-ui,Segoe UI,Roboto,Helvetica,Arial;margin:24px} pre{background:#f6f8fa;padding:12px;white-space:pre-wrap;border-radius:8px} code{background:#f0f0f0;padding:2px 4px;border-radius:3px}</style>")
            .append("</head><body>");
        html.append("<h2>Application Error</h2>");
        html.append("<div><b>Timestamp:</b> <code>").append(escape(String.valueOf(attrs.getOrDefault("timestamp", Instant.now())))).append("</code></div>");
        html.append("<div><b>Status:</b> <code>").append(escape(String.valueOf(attrs.get("status")))).append("</code></div>");
        html.append("<div><b>Error:</b> <code>").append(escape(String.valueOf(attrs.get("error")))).append("</code></div>")
            .append("<div><b>Message:</b> <code>").append(escape(String.valueOf(attrs.get("message")))).append("</code></div>")
            .append("<div><b>Path:</b> <code>").append(escape(String.valueOf(attrs.get("path")))).append("</code></div>");
        Object trace = attrs.get("trace");
        if (trace != null) {
            html.append("<h3>Trace</h3><pre>").append(escape(String.valueOf(trace))).append("</pre>");
        }
        Object ex = attrs.get("exception");
        if (ex != null) {
            html.append("<div><b>Exception:</b> <code>").append(escape(String.valueOf(ex))).append("</code></div>");
        }
        // request info
        html.append("<h3>Request</h3>");
        html.append("<div><b>Method:</b> <code>").append(escape(request.getMethod())).append("</code></div>");
        html.append("<div><b>Query:</b> <code>").append(escape(request.getQueryString())).append("</code></div>");
        html.append("<p><a href='/'>&larr; Back</a></p>");
        html.append("</body></html>");
        int status = (attrs.get("status") instanceof Integer i) ? i : 500;
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html.toString());
    }

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleErrorJson(HttpServletRequest request) {
        var attrs = getErrorAttributes(request, true);
        int status = (attrs.get("status") instanceof Integer i) ? i : 500;
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.putAll(attrs);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private Map<String, Object> getErrorAttributes(HttpServletRequest request, boolean includeTrace) {
        var opts = includeTrace ? ErrorAttributeOptions.of(ErrorAttributeOptions.Include.STACK_TRACE) : ErrorAttributeOptions.defaults();
        return errorAttributes.getErrorAttributes(new ServletWebRequest(request), opts);
    }

    private static String escape(String s){
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
