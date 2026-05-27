package com.hostware.util;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

public class ClipboardUtil {

    public static void copy(String text) {
        StringSelection sel =
            new StringSelection(text);
        Toolkit.getDefaultToolkit()
            .getSystemClipboard()
            .setContents(sel, null);
    }
}