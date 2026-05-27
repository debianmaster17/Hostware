package com.hostware.util;

import burp.api.montoya.MontoyaApi;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;

public class PrefsUtil {

    private final MontoyaApi api;

    public PrefsUtil(MontoyaApi api) {
        this.api = api;
    }

    public String load(
        String key, String def) {
        try {
            String val = api.persistence()
                .preferences()
                .getString(key);
            return val != null ? val : def;
        } catch (Exception e) {
            return def;
        }
    }

    public void save(
        String key, String value) {
        try {
            api.persistence()
                .preferences()
                .setString(key, value);
        } catch (Exception e) {
            api.logging().logToError(
                "Pref save error: "
                + e.getMessage());
        }
    }

    public DocumentListener autoSave(
        String key, JTextField field) {
        return new DocumentListener() {
            public void insertUpdate(
                DocumentEvent e) { save(); }
            public void removeUpdate(
                DocumentEvent e) { save(); }
            public void changedUpdate(
                DocumentEvent e) { save(); }
            void save() {
                PrefsUtil.this.save(
                    key,
                    field.getText().trim());
            }
        };
    }
}