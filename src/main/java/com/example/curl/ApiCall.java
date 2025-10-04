package com.example.curl;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiCall {
    public enum Method {GET, POST, PUT, PATCH, DELETE}

    private Method method;
    private String url;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String body;
    private String authType; // Bearer/Basic/None

    public Method getMethod() { return method; }
    public void setMethod(Method method) { this.method = method; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
}

