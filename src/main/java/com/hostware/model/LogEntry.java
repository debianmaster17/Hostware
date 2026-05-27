package com.hostware.model;

public class LogEntry {
    public final String timestamp;
    public final String ip;
    public final String method;
    public final String path;
    public final String userAgent;
    public final String source;
    public final String rawRequest;
    public final String rawResponse;
    public final String host;
    public final int port;

    public LogEntry(
        String timestamp,
        String ip,
        String method,
        String path,
        String userAgent,
        String source,
        String rawRequest,
        String rawResponse,
        String host,
        int port) {
        this.timestamp = timestamp;
        this.ip = ip;
        this.method = method;
        this.path = path;
        this.userAgent = userAgent;
        this.source = source;
        this.rawRequest = rawRequest;
        this.rawResponse = rawResponse;
        this.host = host;
        this.port = port;
    }

    // OOB entries have no host/port
    public LogEntry(
        String timestamp,
        String ip,
        String method,
        String path,
        String userAgent,
        String source,
        String rawRequest,
        String rawResponse) {
        this(timestamp, ip, method, path,
            userAgent, source,
            rawRequest, rawResponse, "", 0);
    }
}