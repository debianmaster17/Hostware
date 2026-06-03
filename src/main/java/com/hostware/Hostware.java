package com.hostware;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import com.hostware.ui.MainTab;
import com.hostware.util.PrefsUtil;

import javax.swing.*;
import java.awt.Component;
import java.util.List;

public class Hostware implements BurpExtension {

    private MontoyaApi api;
    private MainTab mainTab;
    private boolean isBurpPro = false;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Hostware");
        api.logging().logToOutput(
            "Hostware v1.0 loading...");

        // Check Burp Pro
        try {
            CollaboratorClient test =
                api.collaborator().createClient();
            if (test != null) isBurpPro = true;
        } catch (Exception e) {
            isBurpPro = false;
        }

        api.logging().logToOutput(
            "Burp Pro: " + isBurpPro);

        PrefsUtil prefs = new PrefsUtil(api);
        mainTab = new MainTab(
            api, isBurpPro, prefs);

        // Register context menu
        api.userInterface()
            .registerContextMenuItemsProvider(
                new ContextMenuItemsProvider() {
                    @Override
                    public List<Component>
                        provideMenuItems(
                            ContextMenuEvent event) {
                        return buildMenu(event);
                    }
                });

        api.extension()
            .registerUnloadingHandler(
                () -> mainTab.shutdown());

        SwingUtilities.invokeLater(
            () -> mainTab.register());

        api.logging().logToOutput(
            "Hostware v1.0 loaded.");
    }

    private List<Component> buildMenu(
        ContextMenuEvent event) {

        JMenu hostwareMenu =
            new JMenu("Hostware");

        // ── Send to Exploit Server ──
        JMenuItem sendExploit = new JMenuItem(
            "Send to Exploit Server");
        sendExploit.addActionListener(e -> {
            final boolean[] sent = {false};
            event.messageEditorRequestResponse()
                    .ifPresent(editor -> {
                        try {
                            String raw = new String(
                                    editor.requestResponse()
                                            .request()
                                            .toByteArray()
                                            .getBytes());
                            mainTab.sendToExploitServer(raw);
                            sent[0] = true;
                        } catch (Exception ex) {
                            api.logging().logToError(
                                    "Send to exploit: "
                                            + ex.getMessage());
                        }
                    });

            if (!sent[0] && !event
                    .selectedRequestResponses()
                    .isEmpty()) {
                try {
                    String raw = new String(
                            event
                                    .selectedRequestResponses()
                                    .get(0)
                                    .request()
                                    .toByteArray()
                                    .getBytes());
                    mainTab.sendToExploitServer(raw);
                } catch (Exception ex) {
                    api.logging().logToError(
                            "Send to exploit: "
                                    + ex.getMessage());
                }
            }
        });
        hostwareMenu.add(sendExploit);
        return List.of(hostwareMenu);
    }
}