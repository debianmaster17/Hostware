package com.hostware.server;

import burp.api.montoya.MontoyaApi;
import com.hostware.model.ExploitSlot;
import com.hostware.model.LogEntry;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

public class RequestHandler implements Runnable {

    private final Socket client;
    private final MontoyaApi api;
    private final String configuredPath;
    private final ExploitSlot activeSlot;
    private final Consumer<LogEntry> onRequest;
    private final String serverHost;
    private final int serverPort;

    public RequestHandler(
        Socket client,
        MontoyaApi api,
        String configuredPath,
        ExploitSlot activeSlot,
        Consumer<LogEntry> onRequest,
        String serverHost,
        int serverPort) {
        this.client = client;
        this.api = api;
        this.configuredPath = configuredPath;
        this.activeSlot = activeSlot;
        this.onRequest = onRequest;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    @Override
    public void run() {
        try {
            client.setSoTimeout(30000);

            BufferedReader in =
                new BufferedReader(
                    new InputStreamReader(
                        client.getInputStream(),
                        StandardCharsets.UTF_8));
            OutputStream out =
                client.getOutputStream();

            StringBuilder raw =
                new StringBuilder();
            String line;
            String method = "";
            String path = "";
            String userAgent = "";
            boolean first = true;
            int contentLength = 0;

            while ((line = in.readLine()) != null
                && !line.isEmpty()) {
                raw.append(line).append("\n");
                if (first) {
                    String[] parts =
                        line.split(" ");
                    if (parts.length >= 2) {
                        method = parts[0];
                        path = parts[1];
                    }
                    first = false;
                }
                if (line.toLowerCase()
                    .startsWith("user-agent:")) {
                    userAgent =
                        line.substring(11).trim();
                }
                if (line.toLowerCase()
                    .startsWith(
                        "content-length:")) {
                    try {
                        contentLength =
                            Integer.parseInt(
                                line.substring(15)
                                .trim());
                    } catch (
                        NumberFormatException e) {
                        contentLength = 0;
                    }
                }
            }

            if (contentLength > 0) {
                StringBuilder bodyBuilder =
                    new StringBuilder();
                int remaining = contentLength;
                char[] buf = new char[4096];
                while (remaining > 0) {
                    int read = in.read(
                        buf, 0,
                        Math.min(
                            buf.length,
                            remaining));
                    if (read == -1) break;
                    bodyBuilder.append(
                        buf, 0, read);
                    remaining -= read;
                }
                raw.append("\n")
                    .append(bodyBuilder
                        .toString());
            }

            String ip = client.getInetAddress()
                .getHostAddress();
            String ts =
                new SimpleDateFormat("HH:mm:ss")
                .format(new Date());

            String responseStr;

            if (path.equals(configuredPath)
                || path.equals("/")) {
                String body = activeSlot.getBody();
                String head =
                    activeSlot.getHead().trim();
                String[] headLines =
                    head.split("\r\n|\n");
                String statusLine = headLines[0];

                StringBuilder headers =
                    new StringBuilder();
                for (int i = 1;
                    i < headLines.length; i++) {
                    if (!headLines[i].trim()
                        .isEmpty()) {
                        headers.append(
                            headLines[i].trim())
                            .append("\r\n");
                    }
                }
                headers.append("Content-Length: ")
                    .append(
                        body.getBytes(
                            StandardCharsets.UTF_8)
                        .length)
                    .append("\r\n");

                responseStr = statusLine + "\r\n"
                    + headers.toString()
                    + "\r\n" + body;
            } else {
                responseStr =
                    "HTTP/1.1 404 Not Found\r\n"
                    + "Connection: close\r\n\r\n"
                    + "Not Found";
            }

            out.write(responseStr.getBytes(
                StandardCharsets.UTF_8));
            out.flush();
            client.close();

            LogEntry entry = new LogEntry(
                ts, ip, method, path,
                userAgent, "Local",
                raw.toString(), responseStr,
                serverHost, serverPort);

            onRequest.accept(entry);

        } catch (IOException e) {
            api.logging().logToError(
                "Request error: "
                + e.getMessage());
        }
    }
}