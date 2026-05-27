package com.hostware.collaborator;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.Interaction;
import com.hostware.model.LogEntry;

import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

public class CollaboratorManager {

    private final MontoyaApi api;
    private CollaboratorClient client;
    private Timer poller;
    private boolean active = false;
    private int pollErrorCount = 0;
    private static final int MAX_POLL_ERRORS = 5;

    private final Consumer<LogEntry> onInteraction;
    private final Runnable onStart;
    private final Runnable onStop;
    private final Consumer<String> onError;

    public CollaboratorManager(
        MontoyaApi api,
        Consumer<LogEntry> onInteraction,
        Runnable onStart,
        Runnable onStop,
        Consumer<String> onError) {
        this.api = api;
        this.onInteraction = onInteraction;
        this.onStart = onStart;
        this.onStop = onStop;
        this.onError = onError;
    }

    public String generatePayload() {
        try {
            if (client == null)
                client = api.collaborator()
                    .createClient();
            CollaboratorPayload payload =
                client.generatePayload();
            return "https://" + payload.toString();
        } catch (Exception e) {
            onError.accept(
                "Collaborator error: "
                + e.getMessage());
            return null;
        }
    }

    public void startPolling() {
        if (client == null) {
            onError.accept(
                "Generate a payload first.");
            return;
        }
        pollErrorCount = 0;
        poller = new Timer(5000, e -> poll());
        poller.start();
        active = true;
        SwingUtilities.invokeLater(onStart);
        api.logging().logToOutput(
            "Collaborator polling started.");
    }

    public void stopPolling() {
        if (poller != null) poller.stop();
        active = false;
        SwingUtilities.invokeLater(onStop);
        api.logging().logToOutput(
            "Collaborator polling stopped.");
    }

    private void poll() {
        if (client == null) return;
        try {
            List<Interaction> interactions =
                client.getAllInteractions();

            // Reset error count on success
            pollErrorCount = 0;

            for (Interaction i : interactions) {
                String ts =
                    new SimpleDateFormat(
                        "HH:mm:ss")
                    .format(new Date());
                String type =
                    i.type().toString();
                String ip =
                    i.clientIp() != null
                    ? i.clientIp().toString()
                    : "unknown";

                StringBuilder detail =
                    new StringBuilder();
                detail.append("Type: ")
                    .append(type).append("\n");
                detail.append("Client IP: ")
                    .append(ip).append("\n");
                detail.append("Time: ")
                    .append(ts).append("\n");

                i.httpDetails().ifPresent(
                    http -> {
                        detail.append(
                            "\n--- HTTP Request"
                            + " ---\n");
                        detail.append(
                            http.requestResponse()
                            .request().toString());
                    });

                i.dnsDetails().ifPresent(
                    dns -> {
                        detail.append(
                            "\n--- DNS ---\n");
                        detail.append("Query: ")
                            .append(dns.query()
                            .toString());
                    });

                // Build response detail
                StringBuilder responseDetail =
                    new StringBuilder();
                responseDetail.append(
                    "=== Collaborator"
                    + " Interaction ===\n\n");
                responseDetail.append("Type: ")
                    .append(type).append("\n");
                responseDetail.append(
                    "Client IP: ")
                    .append(ip).append("\n");
                responseDetail.append("Time: ")
                    .append(ts).append("\n");

                i.httpDetails().ifPresent(
                    http -> {
                        responseDetail.append(
                            "\n--- HTTP Response"
                            + " ---\n");
                        if (http.requestResponse()
                            .response() != null) {
                            responseDetail.append(
                                http
                                .requestResponse()
                                .response()
                                .toString());
                        } else {
                            responseDetail.append(
                                "[No HTTP response"
                                + " captured]");
                        }
                    });

                i.dnsDetails().ifPresent(
                    dns -> {
                        responseDetail.append(
                            "\n--- DNS Query"
                            + " Type ---\n");
                        responseDetail.append(
                            dns.queryType()
                            .toString());
                    });

                LogEntry entry = new LogEntry(
                    ts, ip, type,
                    "OOB", "-",
                    "Collaborator",
                    detail.toString(),
                    responseDetail.toString());

                SwingUtilities.invokeLater(() ->
                    onInteraction.accept(entry));

                api.logging().logToOutput(
                    "Collaborator hit: "
                    + type + " from " + ip);
            }
        } catch (Exception e) {
            pollErrorCount++;
            api.logging().logToError(
                "Poll error "
                + pollErrorCount + "/"
                + MAX_POLL_ERRORS + ": "
                + e.getMessage());
            if (pollErrorCount
                >= MAX_POLL_ERRORS) {
                stopPolling();
                SwingUtilities.invokeLater(
                    () -> onError.accept(
                        "Collaborator polling "
                        + "stopped after "
                        + MAX_POLL_ERRORS
                        + " consecutive errors."));
            }
        }
    }

    public boolean isActive() { return active; }

    public void shutdown() { stopPolling(); }
}