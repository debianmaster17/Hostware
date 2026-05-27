package com.hostware.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.editor.RawEditor;
import com.hostware.model.LogEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class LogTab {

    private final MontoyaApi api;
    private final List<LogEntry> entries;

    private DefaultTableModel logModel;
    private JTable logTable;
    private RawEditor requestEditor;
    private RawEditor responseEditor;
    private JLabel statusMsg;

    public LogTab(
        MontoyaApi api,
        List<LogEntry> entries) {
        this.api = api;
        this.entries = entries;
    }

    public JPanel build() {
        JPanel panel = new JPanel(
            new BorderLayout(0, 6));
        panel.setBorder(
            BorderFactory.createEmptyBorder(
                10, 10, 10, 10));

        String[] cols = {
            "Time", "IP", "Method",
            "Path", "User-Agent", "Source"};
        logModel = new DefaultTableModel(
            cols, 0) {
            public boolean isCellEditable(
                int r, int c) {
                return false;
            }
        };

        logTable = new JTable(logModel);
        logTable.setAutoCreateRowSorter(true);
        logTable.setSelectionMode(
            ListSelectionModel.SINGLE_SELECTION);
        logTable.getSelectionModel()
            .addListSelectionListener(
                e -> showDetail());

        // Right click context menu
        logTable.addMouseListener(
            new MouseAdapter() {
                @Override
                public void mousePressed(
                    MouseEvent e) {
                    handleClick(e);
                }
                @Override
                public void mouseReleased(
                    MouseEvent e) {
                    handleClick(e);
                }
                private void handleClick(
                    MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        int row =
                            logTable.rowAtPoint(
                                e.getPoint());
                        if (row >= 0) {
                            logTable
                                .setRowSelectionInterval(
                                    row, row);
                            showContextMenu(
                                e.getComponent(),
                                e.getX(),
                                e.getY());
                        }
                    }
                }
            });

        // Row coloring by source
        logTable.setDefaultRenderer(
            Object.class,
            new javax.swing.table
                .DefaultTableCellRenderer() {
                @Override
                public Component
                    getTableCellRendererComponent(
                        JTable table,
                        Object value,
                        boolean isSelected,
                        boolean hasFocus,
                        int row, int column) {
                    Component c =
                        super
                        .getTableCellRendererComponent(
                            table, value,
                            isSelected,
                            hasFocus, row, column);
                    if (!isSelected) {
                        int mr = table
                            .convertRowIndexToModel(
                                row);
                        Object src =
                            table.getModel()
                            .getValueAt(mr, 5);
                        if ("Collaborator"
                            .equals(src)) {
                            c.setBackground(
                                new Color(
                                    45, 60, 45));
                        } else if ("Interactsh"
                            .equals(src)) {
                            c.setBackground(
                                new Color(
                                    40, 50, 70));
                        } else {
                            c.setBackground(
                                table
                                .getBackground());
                        }
                    }
                    return c;
                }
            });

        JScrollPane tableScroll =
            new JScrollPane(logTable);
        tableScroll.setPreferredSize(
            new Dimension(0, 200));

        requestEditor = api.userInterface()
            .createRawEditor();
        responseEditor = api.userInterface()
            .createRawEditor();

        JPanel reqPanel = new JPanel(
            new BorderLayout());
        reqPanel.setBorder(
            BorderFactory.createTitledBorder(
                "Request Detail"));
        reqPanel.add(
            requestEditor.uiComponent(),
            BorderLayout.CENTER);

        JPanel respPanel = new JPanel(
            new BorderLayout());
        respPanel.setBorder(
            BorderFactory.createTitledBorder(
                "Response Detail"));
        respPanel.add(
            responseEditor.uiComponent(),
            BorderLayout.CENTER);

        JSplitPane detailSplit = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            reqPanel, respPanel);
        detailSplit.setResizeWeight(0.5);

        JSplitPane mainSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            tableScroll, detailSplit);
        mainSplit.setDividerLocation(200);

        statusMsg = new JLabel(" ");
        statusMsg.setForeground(
            new Color(0, 150, 0));
        statusMsg.setFont(
            new Font("Arial", Font.ITALIC, 11));

        JPanel btnPanel = new JPanel(
            new FlowLayout(FlowLayout.LEFT));

        JButton refreshBtn =
            new JButton("Refresh");
        refreshBtn.setToolTipText(
            "Refresh the log table");
        refreshBtn.addActionListener(
            e -> logTable.repaint());

        JButton clearBtn =
            new JButton("Clear Log");
        clearBtn.setToolTipText(
            "Clear all log entries");
        clearBtn.addActionListener(e -> {
            logModel.setRowCount(0);
            entries.clear();
            clearEditors();
        });

        JButton exportBtn =
            new JButton("Export Log");
        exportBtn.setToolTipText(
            "Export log to CSV file");
        exportBtn.addActionListener(
            e -> exportLog());

        JLabel localLeg =
            new JLabel("  ■ Local");
        localLeg.setForeground(Color.GRAY);
        JLabel collabLeg =
            new JLabel("  ■ Collaborator");
        collabLeg.setForeground(
            new Color(0, 180, 0));
        JLabel interactshLeg =
            new JLabel("  ■ Interactsh");
        interactshLeg.setForeground(
            new Color(100, 150, 255));

        btnPanel.add(refreshBtn);
        btnPanel.add(clearBtn);
        btnPanel.add(exportBtn);
        btnPanel.add(
            Box.createHorizontalStrut(20));
        btnPanel.add(localLeg);
        btnPanel.add(collabLeg);
        btnPanel.add(interactshLeg);

        JPanel bottom = new JPanel(
            new BorderLayout());
        bottom.add(btnPanel, BorderLayout.CENTER);
        bottom.add(statusMsg, BorderLayout.SOUTH);

        panel.add(mainSplit, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private void showContextMenu(
        Component comp, int x, int y) {
        int viewRow = logTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow =
            logTable.convertRowIndexToModel(
                viewRow);
        if (modelRow < 0
            || modelRow >= entries.size())
            return;

        LogEntry entry = entries.get(modelRow);
        boolean isLocal =
            "Local".equals(entry.source)
            && entry.host != null
            && !entry.host.isEmpty()
            && entry.port > 0;

        JPopupMenu menu = new JPopupMenu();

        if (isLocal) {
            JMenuItem sendRepeater =
                new JMenuItem(
                    "Send to Repeater");
            sendRepeater.addActionListener(
                e -> sendToRepeater(entry));
            menu.add(sendRepeater);

            JMenuItem sendIntruder =
                new JMenuItem(
                    "Send to Intruder");
            sendIntruder.addActionListener(
                e -> sendToIntruder(entry));
            menu.add(sendIntruder);

            menu.addSeparator();

            JMenuItem addScope =
                new JMenuItem("Add to Scope");
            addScope.addActionListener(
                e -> addToScope(entry));
            menu.add(addScope);

            menu.addSeparator();
        }

        JMenuItem copyUrl =
            new JMenuItem("Copy URL");
        copyUrl.addActionListener(e -> {
            String url = "http://"
                + entry.ip + entry.path;
            copyToClipboard(url);
            flash("URL copied.",
                new Color(0, 150, 0));
        });
        menu.add(copyUrl);

        JMenuItem copyIp =
            new JMenuItem("Copy IP");
        copyIp.addActionListener(e -> {
            copyToClipboard(entry.ip);
            flash("IP copied.",
                new Color(0, 150, 0));
        });
        menu.add(copyIp);

        JMenuItem copyReq =
            new JMenuItem("Copy Raw Request");
        copyReq.addActionListener(e -> {
            copyToClipboard(entry.rawRequest);
            flash("Request copied.",
                new Color(0, 150, 0));
        });
        menu.add(copyReq);

        menu.show(comp, x, y);
    }

    private void sendToRepeater(
        LogEntry entry) {
        try {
            HttpRequest req =
                HttpRequest.httpRequest(
                    burp.api.montoya.http
                    .HttpService.httpService(
                        entry.host,
                        entry.port,
                        false),
                    ByteArray.byteArray(
                        entry.rawRequest
                        .getBytes()));
            api.repeater().sendToRepeater(
                req,
                "Hostware - " + entry.path);
            flash("Sent to Repeater.",
                new Color(0, 150, 0));
        } catch (Exception e) {
            api.logging().logToError(
                "Send to Repeater failed: "
                + e.getMessage());
            flash(
                "Failed to send to Repeater.",
                Color.RED);
        }
    }

    private void sendToIntruder(
        LogEntry entry) {
        try {
            HttpRequest req =
                HttpRequest.httpRequest(
                    burp.api.montoya.http
                    .HttpService.httpService(
                        entry.host,
                        entry.port,
                        false),
                    ByteArray.byteArray(
                        entry.rawRequest
                        .getBytes()));
            api.intruder().sendToIntruder(req);
            flash("Sent to Intruder.",
                new Color(0, 150, 0));
        } catch (Exception e) {
            api.logging().logToError(
                "Send to Intruder failed: "
                + e.getMessage());
            flash(
                "Failed to send to Intruder.",
                Color.RED);
        }
    }

    private void addToScope(LogEntry entry) {
        try {
            String url = (entry.port == 443
                ? "https" : "http")
                + "://"
                + entry.host + ":"
                + entry.port
                + entry.path;
            api.scope().includeInScope(url);
            flash("Added to scope.",
                new Color(0, 150, 0));
        } catch (Exception e) {
            api.logging().logToError(
                "Add to scope failed: "
                + e.getMessage());
            flash("Failed to add to scope.",
                Color.RED);
        }
    }

    private void copyToClipboard(String text) {
        StringSelection sel =
            new StringSelection(text);
        Toolkit.getDefaultToolkit()
            .getSystemClipboard()
            .setContents(sel, null);
    }

    public void addEntry(LogEntry entry) {
        SwingUtilities.invokeLater(() -> {
            entries.add(entry);
            logModel.addRow(new Object[]{
                entry.timestamp,
                entry.ip,
                entry.method,
                entry.path,
                entry.userAgent,
                entry.source});
        });
    }

    private void showDetail() {
        int viewRow = logTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow =
            logTable.convertRowIndexToModel(
                viewRow);
        if (modelRow >= 0
            && modelRow < entries.size()) {
            LogEntry e = entries.get(modelRow);
            requestEditor.setContents(
                ByteArray.byteArray(
                    e.rawRequest.getBytes()));
            responseEditor.setContents(
                ByteArray.byteArray(
                    e.rawResponse.getBytes()));
        }
    }

    private void clearEditors() {
        requestEditor.setContents(
            ByteArray.byteArray(new byte[0]));
        responseEditor.setContents(
            ByteArray.byteArray(new byte[0]));
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",")
            || val.contains("\"")
            || val.contains("\n")) {
            return "\""
                + val.replace("\"", "\"\"")
                + "\"";
        }
        return val;
    }

    private void exportLog() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(
            new File("hostware_log.csv"));
        if (fc.showSaveDialog(null)
            == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter pw =
                new PrintWriter(
                    fc.getSelectedFile())) {
                pw.println("Time,IP,Method,"
                    + "Path,User-Agent,Source");
                for (LogEntry e : entries) {
                    pw.println(
                        escapeCsv(e.timestamp)
                        + ","
                        + escapeCsv(e.ip)
                        + ","
                        + escapeCsv(e.method)
                        + ","
                        + escapeCsv(e.path)
                        + ","
                        + escapeCsv(e.userAgent)
                        + ","
                        + escapeCsv(e.source));
                }
                flash("Log exported.",
                    new Color(0, 150, 0));
            } catch (IOException e) {
                flash("Export failed: "
                    + e.getMessage(), Color.RED);
            }
        }
    }

    private void flash(
        String msg, Color color) {
        statusMsg.setForeground(color);
        statusMsg.setText(msg);
        Timer t = new Timer(2500,
            e -> statusMsg.setText(" "));
        t.setRepeats(false);
        t.start();
    }
}