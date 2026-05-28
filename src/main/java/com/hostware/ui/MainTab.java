package com.hostware.ui;

import burp.api.montoya.MontoyaApi;
import com.hostware.model.LogEntry;
import com.hostware.util.PrefsUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainTab {

    private final MontoyaApi api;
    private final boolean isBurpPro;
    private final PrefsUtil prefs;

    private ExploitServerTab exploitServerTab;
    private OOBTab oobTab;
    private LogTab logTab;
    private JTabbedPane outerTabs;

    private final List<LogEntry> entries =
        new CopyOnWriteArrayList<>();

    public MainTab(
        MontoyaApi api,
        boolean isBurpPro,
        PrefsUtil prefs) {
        this.api = api;
        this.isBurpPro = isBurpPro;
        this.prefs = prefs;
    }

    public void register() {
        logTab = new LogTab(api, entries);

        exploitServerTab = new ExploitServerTab(
            api, prefs,
            entry -> logTab.addEntry(entry),
            this::pulseNotification);

        oobTab = new OOBTab(
            api, isBurpPro,
            entry -> logTab.addEntry(entry),
            this::pulseNotification);

        outerTabs = new JTabbedPane();
        outerTabs.addTab("Exploit Server",
            exploitServerTab.build());
        outerTabs.addTab("OASTForge",
            oobTab.build());
        outerTabs.addTab("Access Log",
            logTab.build());
        outerTabs.addTab("About",
            buildAboutTab());

        api.userInterface()
            .registerSuiteTab("Hostware",
                outerTabs);
    }

    private void pulseNotification() {
        SwingUtilities.invokeLater(() -> {
            int logIdx = 2;
            String title =
                    outerTabs.getTitleAt(logIdx);
            if (!title.contains(" ●")) {
                outerTabs.setTitleAt(logIdx,
                        title + " ●");
                Timer t = new Timer(2000,
                        e -> outerTabs.setTitleAt(
                                logIdx, title));
                t.setRepeats(false);
                t.start();
            }
        });
    }

    // Send raw request to exploit server
    public void sendToExploitServer(
        String rawRequest) {
        if (exploitServerTab != null) {
            exploitServerTab
                .receiveRequest(rawRequest);
            // Switch to Exploit Server tab
            SwingUtilities.invokeLater(() ->
                outerTabs.setSelectedIndex(0));
        }
    }

    // Get active collaborator payload
    public String getCollabPayload() {
        if (oobTab != null)
            return oobTab.getCollabPayload();
        return "";
    }

    // Get active interactsh payload
    public String getInteractshPayload() {
        if (oobTab != null)
            return oobTab.getInteractshPayload();
        return "";
    }

    private JPanel buildAboutTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setEditable(false);
        textPane.setOpaque(false);
        textPane.setFocusable(false);
        textPane.setBackground(new Color(0, 0, 0, 0));
        textPane.setCursor(null);
        textPane.setHighlighter(null);

        textPane.setText(
                "<html><body style='font-family:Arial;font-size:13px;'>"
                        + "<h1 style='text-align:center;font-size:24px;'>Hostware</h1>"
                        + "<p style='text-align:center;color:gray;'>Version 1.0.0</p>"
                        + "<p style='text-align:center;'>"
                        + "Hostware is a Burp Suite extension providing an integrated exploit "
                        + "server and OOB detection platform for web application penetration testing.</p>"
                        + "<h2 style='font-size:16px;'>Features</h2><ul>"
                        + "<li>Local and external exploit server</li>"
                        + "<li>Three-panel exploit editor: HTTP Request, HEAD, BODY</li>"
                        + "<li>OASTForge: Collaborator + Interactsh OOB detection</li>"
                        + "<li>Live access log with request/response viewer</li>"
                        + "<li>Quick payload templates</li>"
                        + "<li>Export log to CSV</li>"
                        + "<li>Settings persistence</li>"
                        + "</ul>"
                        + "<h2 style='font-size:16px;'>Quick Start</h2><ol>"
                        + "<li>Go to <b>Exploit Server</b> tab</li>"
                        + "<li>Edit HEAD and BODY — HTTP Request updates live</li>"
                        + "<li>Click <b>Start Server</b></li>"
                        + "<li>Copy URL and inject into target</li>"
                        + "<li>Monitor in <b>Access Log</b></li>"
                        + "<li>For OOB: go to <b>OASTForge</b> tab</li>"
                        + "</ol>"
                        + "<p>GitHub: https://github.com/debianmaster17/Hostware</p>"
                        + "<p style='color:gray;'>Built by: Alpay Ibrahimli</p>"
                        + "</body></html>");

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(null);
        scrollPane.setFocusable(false);

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    public void shutdown() {
        if (exploitServerTab != null)
            exploitServerTab.shutdown();
        if (oobTab != null)
            oobTab.shutdown();
    }
}