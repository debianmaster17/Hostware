package com.hostware.ui;

import burp.api.montoya.MontoyaApi;
import com.hostware.collaborator.CollaboratorManager;
import com.hostware.interactsh.InteractshManager;
import com.hostware.model.LogEntry;
import com.hostware.util.ClipboardUtil;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class OOBTab {

    private final MontoyaApi api;
    private final boolean isBurpPro;
    private final Consumer<LogEntry> onInteraction;
    private Runnable onNewActivity;

    private CollaboratorManager collabManager;
    private InteractshManager interactshManager;

    private JLabel collabDot;
    private JButton toggleCollabBtn;
    private JTextField collabField;

    private JLabel interactshDot;
    private JButton toggleInteractshBtn;
    private JTextField interactshSessionField;
    private JTextField interactshServerField;

    private JLabel statusMsg;
    private volatile String storedCollabPayload
        = "";
    private volatile String
        storedInteractshPayload = "";

    public OOBTab(
        MontoyaApi api,
        boolean isBurpPro,
        Consumer<LogEntry> onInteraction,
        Runnable onNewActivity) {
        this.api = api;
        this.isBurpPro = isBurpPro;
        this.onInteraction = onInteraction;
        this.onNewActivity = onNewActivity;
    }

    public JPanel build() {
        JPanel panel = new JPanel(
            new BorderLayout(0, 8));
        panel.setBorder(
            BorderFactory.createEmptyBorder(
                10, 10, 10, 10));

        statusMsg = new JLabel(" ");
        statusMsg.setForeground(
            new Color(0, 150, 0));
        statusMsg.setFont(
            new Font("Arial", Font.ITALIC, 11));

        panel.add(buildCollabPanel(),
            BorderLayout.NORTH);
        panel.add(buildInteractshPanel(),
            BorderLayout.CENTER);
        panel.add(statusMsg,
            BorderLayout.SOUTH);

        return panel;
    }

    public String getCollabPayload() {
        return storedCollabPayload;
    }
    public String getInteractshPayload() {
        return storedInteractshPayload;
    }

    private JPanel buildCollabPanel() {
        JPanel panel = new JPanel(
            new BorderLayout(8, 4));
        panel.setBorder(
            BorderFactory.createTitledBorder(
                "Burp Collaborator (Pro Only)"));

        if (isBurpPro) {
            collabManager =
                new CollaboratorManager(
                    api,
                    entry -> {
                        onInteraction.accept(entry);
                        if (onNewActivity != null)
                            onNewActivity.run();
                    },
                    () -> {
                        collabDot.setForeground(
                            Color.GREEN);
                        toggleCollabBtn.setText(
                            "Stop Polling");
                        flash(
                            "Collaborator polling "
                            + "started.",
                            new Color(0, 150, 0));
                    },
                    () -> {
                        collabDot.setForeground(
                            Color.RED);
                        toggleCollabBtn.setText(
                            "Start Polling");
                        flash(
                            "Collaborator stopped.",
                            Color.GRAY);
                    },
                    err -> flash(err, Color.RED));

            collabField = new JTextField(
                "Click Generate to create payload");
            collabField.setEditable(false);
            collabField.setFont(new Font(
                "Monospaced", Font.PLAIN, 12));

            JPanel btns = new JPanel(
                new GridLayout(3, 1, 4, 4));

            JButton genBtn = new JButton(
                "Generate Collaborator");
            genBtn.setToolTipText(
                "Generate Collaborator payload");
            genBtn.addActionListener(e -> {
                String url =
                    collabManager.generatePayload();
                if (url != null) {
                    collabField.setText(url);
                    storedCollabPayload = url
                        .replace("https://", "")
                        .replace("http://", "")
                        .trim();
                    api.logging().logToOutput(
                        "Collab payload stored: "
                        + storedCollabPayload);
                    flash("Payload generated.",
                        new Color(0, 150, 0));
                }
            });

            JButton copyBtn =
                new JButton("Copy Host");
            copyBtn.setToolTipText(
                "Copy Collaborator host");
            copyBtn.addActionListener(e -> {
                if (!storedCollabPayload
                    .isEmpty()) {
                    ClipboardUtil.copy(
                        storedCollabPayload);
                    flash("Host copied.",
                        new Color(0, 150, 0));
                }
            });

            JPanel toggleRow = new JPanel(
                new FlowLayout(
                    FlowLayout.CENTER, 4, 0));
            collabDot = new JLabel("●");
            collabDot.setForeground(Color.RED);
            collabDot.setFont(new Font(
                "Arial", Font.BOLD, 14));
            toggleCollabBtn =
                new JButton("Start Polling");
            toggleCollabBtn.setToolTipText(
                "Start or stop polling");
            toggleCollabBtn.addActionListener(
                e -> {
                    if (collabManager.isActive())
                        collabManager.stopPolling();
                    else
                        collabManager.startPolling();
                });
            toggleRow.add(collabDot);
            toggleRow.add(toggleCollabBtn);

            btns.add(genBtn);
            btns.add(copyBtn);
            btns.add(toggleRow);

            panel.add(collabField,
                BorderLayout.CENTER);
            panel.add(btns, BorderLayout.EAST);
        } else {
            JLabel proLabel = new JLabel(
                "  🔒 Burp Suite Professional "
                + "required for Collaborator.");
            proLabel.setForeground(Color.RED);
            proLabel.setFont(new Font(
                "Arial", Font.BOLD, 13));
            panel.add(proLabel,
                BorderLayout.CENTER);
        }

        return panel;
    }

    private JPanel buildInteractshPanel() {
        JPanel panel = new JPanel(
            new BorderLayout(8, 4));
        panel.setBorder(
            BorderFactory.createTitledBorder(
                "Interactsh OOB (Free)"));

        interactshManager =
            new InteractshManager(
                api,
                entry -> {
                    onInteraction.accept(entry);
                    if (onNewActivity != null)
                        onNewActivity.run();
                },
                () -> {
                    interactshDot.setForeground(
                        Color.GREEN);
                    toggleInteractshBtn.setText(
                        "Stop Polling");
                    flash(
                        "Interactsh polling "
                        + "started.",
                        new Color(0, 150, 0));
                },
                () -> {
                    interactshDot.setForeground(
                        Color.RED);
                    toggleInteractshBtn.setText(
                        "Start Polling");
                    flash("Interactsh stopped.",
                        Color.GRAY);
                },
                err -> flash(err, Color.RED),
                payload -> {
                    storedInteractshPayload =
                        payload;
                    api.logging().logToOutput(
                        "Interactsh payload "
                        + "stored: " + payload);
                    interactshSessionField
                        .setText(payload);
                    flash("Registered: " + payload,
                        new Color(0, 150, 0));
                });

        JPanel center = new JPanel(
            new GridLayout(2, 2, 4, 4));
        center.add(
            new JLabel("Interactsh Server:"));
        interactshServerField =
            new JTextField("oast.pro", 20);
        interactshServerField.setToolTipText(
            "Interactsh server. Default: oast.pro");
        interactshServerField.getDocument()
            .addDocumentListener(
                new javax.swing.event
                    .DocumentListener() {
                    public void insertUpdate(
                        javax.swing.event
                        .DocumentEvent e) {
                        upd();
                    }
                    public void removeUpdate(
                        javax.swing.event
                        .DocumentEvent e) {
                        upd();
                    }
                    public void changedUpdate(
                        javax.swing.event
                        .DocumentEvent e) {
                        upd();
                    }
                    void upd() {
                        interactshManager
                            .setServer(
                            interactshServerField
                            .getText().trim());
                    }
                });
        center.add(interactshServerField);
        center.add(
            new JLabel("Generated Payload:"));
        interactshSessionField = new JTextField(
            "Click Register to generate", 20);
        interactshSessionField.setEditable(false);
        interactshSessionField.setFont(new Font(
            "Monospaced", Font.PLAIN, 11));
        center.add(interactshSessionField);

        JPanel btns = new JPanel(
            new GridLayout(3, 1, 4, 4));

        JButton regBtn =
            new JButton("Register Session");
        regBtn.setToolTipText(
            "Register Interactsh session");
        regBtn.addActionListener(
            e -> interactshManager.register());

        JButton copyBtn =
            new JButton("Copy Payload");
        copyBtn.setToolTipText(
            "Copy Interactsh payload");
        copyBtn.addActionListener(e -> {
            if (!storedInteractshPayload
                .isEmpty()) {
                ClipboardUtil.copy(
                    storedInteractshPayload);
                flash("Copied.",
                    new Color(0, 150, 0));
            }
        });

        JPanel toggleRow = new JPanel(
            new FlowLayout(
                FlowLayout.CENTER, 4, 0));
        interactshDot = new JLabel("●");
        interactshDot.setForeground(Color.RED);
        interactshDot.setFont(new Font(
            "Arial", Font.BOLD, 14));
        toggleInteractshBtn =
            new JButton("Start Polling");
        toggleInteractshBtn.setToolTipText(
            "Start or stop polling");
        toggleInteractshBtn.addActionListener(
            e -> {
                if (interactshManager.isActive())
                    interactshManager.stopPolling();
                else
                    interactshManager.startPolling();
            });
        toggleRow.add(interactshDot);
        toggleRow.add(toggleInteractshBtn);

        btns.add(regBtn);
        btns.add(copyBtn);
        btns.add(toggleRow);

        JLabel notice = new JLabel(
            "  ℹ Detects blind OOB pings. "
            + "Does not serve files.");
        notice.setForeground(Color.GRAY);
        notice.setFont(new Font(
            "Arial", Font.ITALIC, 11));

        panel.add(center, BorderLayout.CENTER);
        panel.add(btns, BorderLayout.EAST);
        panel.add(notice, BorderLayout.SOUTH);

        return panel;
    }

    private void flash(String msg, Color color) {
        statusMsg.setForeground(color);
        statusMsg.setText(msg);
        Timer t = new Timer(2500,
            e -> statusMsg.setText(" "));
        t.setRepeats(false);
        t.start();
    }

    public void shutdown() {
        if (collabManager != null)
            collabManager.shutdown();
        if (interactshManager != null)
            interactshManager.shutdown();
    }
}