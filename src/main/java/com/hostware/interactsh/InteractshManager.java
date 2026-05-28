package com.hostware.interactsh;

import burp.api.montoya.MontoyaApi;
import com.hostware.model.LogEntry;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.SwingUtilities;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

public class InteractshManager {

    private final MontoyaApi api;
    private ScheduledExecutorService pollScheduler;
    private volatile boolean active = false;
    private KeyPair keyPair;
    private String correlationId = "";
    private String secretKey = "";
    private String server = "oast.pro";
    private String generatedPayload = "";
    private final Consumer<LogEntry> onInteraction;
    private final Runnable onStart;
    private final Runnable onStop;
    private final Consumer<String> onError;
    private final Consumer<String> onRegistered;

    public InteractshManager(MontoyaApi api, Consumer<LogEntry> onInteraction,
                             Runnable onStart, Runnable onStop,
                             Consumer<String> onError, Consumer<String> onRegistered) {
        this.api = api;
        this.onInteraction = onInteraction;
        this.onStart = onStart;
        this.onStop = onStop;
        this.onError = onError;
        this.onRegistered = onRegistered;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public void register() {
        new Thread(() -> {
            try {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                keyPair = gen.generateKeyPair();

                String corrId = generateCorrelationId();

                String pubKeyB64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                        .encodeToString(keyPair.getPublic().getEncoded());

                String pubKeyPem = "-----BEGIN PUBLIC KEY-----\n" + pubKeyB64 + "\n-----END PUBLIC KEY-----";
                String pubKeyEncoded = Base64.getEncoder()
                        .encodeToString(pubKeyPem.getBytes("UTF-8"));

                String secret = generateCorrelationId();
                String jsonBody = "{\"correlation-id\":\"" + corrId + "\",\"public-key\":\""
                        + pubKeyEncoded + "\",\"secret-key\":\"" + secret + "\"}";

                URI uri = new URI("https://" + server + "/register");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes("UTF-8"));
                }

                int status = conn.getResponseCode();
                if (status != 200) {
                    InputStream errStream = conn.getErrorStream() != null ?
                            conn.getErrorStream() : conn.getInputStream();
                    BufferedReader errBr = new BufferedReader(new InputStreamReader(errStream));
                    StringBuilder errSb = new StringBuilder();
                    String el;
                    while ((el = errBr.readLine()) != null) errSb.append(el);
                    throw new Exception("Server returned " + status + ": " + errSb.toString());
                }

                String nonce = generateNonce(13);
                correlationId = corrId;
                secretKey = secret;
                generatedPayload = corrId + nonce + "." + server;

                final String payload = generatedPayload;
                SwingUtilities.invokeLater(() -> onRegistered.accept(payload));
                api.logging().logToOutput("Interactsh registered: " + payload);

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> onError.accept("Registration failed: " + e.getMessage()));
                api.logging().logToError("Interactsh error: " + e.getMessage());
            }
        }).start();
    }

    public void startPolling() {
        if (correlationId.isEmpty()) {
            onError.accept("Register a session first.");
            return;
        }
        pollScheduler = Executors.newSingleThreadScheduledExecutor();
        pollScheduler.scheduleAtFixedRate(this::poll, 0, 5, TimeUnit.SECONDS);
        active = true;
        SwingUtilities.invokeLater(onStart);
        api.logging().logToOutput("Interactsh polling: " + generatedPayload);
    }

    public void stopPolling() {
        if (pollScheduler != null) {
            pollScheduler.shutdownNow();
            pollScheduler = null;
        }
        active = false;
        SwingUtilities.invokeLater(onStop);
        api.logging().logToOutput("Interactsh polling stopped.");
    }

    private void poll() {
        try {
            URI uri = new URI("https://" + server + "/poll?id=" + correlationId + "&secret=" + secretKey);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");

            int status = conn.getResponseCode();
            if (status != 200) return;

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            String resp = sb.toString();

            if (resp == null || resp.isEmpty() || resp.equals("{}") || resp.equals("null")) return;

            String aesKeyEnc = extractJson(resp, "aes_key");
            String dataArr = extractJsonArray(resp, "data");

            if (dataArr == null || dataArr.isEmpty() || dataArr.equals("null")) return;

            java.util.List<String> items = new java.util.ArrayList<>();
            int braceCount = 0;
            int startIdx = -1;
            boolean inQuotes = false;

            for (int i = 0; i < dataArr.length(); i++) {
                char c = dataArr.charAt(i);
                if (c == '"' && (i == 0 || dataArr.charAt(i - 1) != '\\')) {
                    inQuotes = !inQuotes;
                }
                if (!inQuotes) {
                    if (c == '{') {
                        if (braceCount == 0) startIdx = i;
                        braceCount++;
                    } else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0 && startIdx != -1) {
                            items.add(dataArr.substring(startIdx, i + 1));
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                for (String token : dataArr.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
                    items.add(token);
                }
            }

            for (String item : items) {
                String cleaned = item.trim();
                if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
                    cleaned = cleaned.substring(1, cleaned.length() - 1);
                }
                if (cleaned.isEmpty()) continue;

                String decrypted = null;
                try {
                    byte[] decryptedBytes = decrypt(cleaned, aesKeyEnc);
                    if (decryptedBytes.length > 1 && decryptedBytes[0] == 0x1f &&
                            (decryptedBytes[1] & 0xFF) == 0x8b) {
                        try (ByteArrayInputStream bais = new ByteArrayInputStream(decryptedBytes);
                             GZIPInputStream gzis = new GZIPInputStream(bais);
                             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = gzis.read(buffer)) > 0) {
                                baos.write(buffer, 0, len);
                            }
                            decrypted = baos.toString("UTF-8");
                        }
                    } else {
                        decrypted = new String(decryptedBytes, StandardCharsets.UTF_8);
                    }
                } catch (Exception e) {
                    decrypted = "{\"protocol\":\"unknown\"," +
                            "\"remote-address\":\"unknown\"," +
                            "\"note\":\"Interaction detected but could not be decrypted.\"," +
                            "\"raw-data\":\"" + cleaned + "\"}";
                    api.logging().logToError("Decrypt failed: " + e.getMessage());
                }

                if (decrypted == null || decrypted.isEmpty()) continue;

                String formatted = formatInteraction(decrypted);

                String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());

                String protocol = extractJson(decrypted, "protocol");
                String remoteAddr = extractJson(decrypted, "remote-address");
                String fullPath = extractJson(decrypted, "full-id");

                if (fullPath == null || fullPath.isEmpty()) fullPath = generatedPayload;
                if (protocol == null || protocol.isEmpty()) protocol = "OOB";
                if (remoteAddr == null || remoteAddr.isEmpty()) remoteAddr = "unknown";

                final String fTs = ts;
                final String fProto = protocol.toUpperCase();
                final String fAddr = remoteAddr;
                final String fPath = fullPath;
                final String fFormatted = formatted;

                SwingUtilities.invokeLater(() -> {
                    String reqPart = fFormatted;
                    String respPart = "[Interactsh " + fProto + "]";
                    int respIdx = fFormatted.indexOf("--- Raw Response ---");
                    if (respIdx != -1) {
                        reqPart = fFormatted.substring(0, respIdx).trim();
                        respPart = fFormatted.substring(respIdx).trim();
                    }

                    LogEntry entry = new LogEntry(fTs, fAddr, fProto, fPath, "-",
                            "Interactsh", reqPart, respPart);
                    onInteraction.accept(entry);
                    api.logging().logToOutput("Interactsh hit: " + fProto + " from " + fAddr);
                });
            }
        } catch (Exception e) {
            api.logging().logToError("Interactsh poll error: " + e.getMessage());
        }
    }

    private String prettyHtml(String html) {
        if (html == null) return "";
        // Insert newline before every tag
        return html
                .replaceAll("<(/)?(html|head|body|div|p|"
                                + "script|style|title|meta|link|span"
                                + "|table|tr|td|th|ul|ol|li|form"
                                + "|input|h[1-6]|br|hr)([^>]*)>",
                        "\n<$1$2$3>")
                .replaceAll("\n{2,}", "\n")
                .trim();
    }

    private String unescapeUnicode(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (i + 5 < s.length()
                    && s.charAt(i) == '\\'
                    && s.charAt(i + 1) == 'u') {
                try {
                    int cp = Integer.parseInt(
                            s.substring(i + 2, i + 6), 16);
                    sb.append((char) cp);
                    i += 6;
                } catch (NumberFormatException e) {
                    sb.append(s.charAt(i));
                    i++;
                }
            } else {
                sb.append(s.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    private String extractJson(String json, String key) {
        try {
            String searchWithSpace = "\"" + key + "\": \"";
            String searchNoSpace = "\"" + key + "\":";

            int start = json.indexOf(searchWithSpace);
            if (start != -1) {
                start += searchWithSpace.length();
            } else {
                start = json.indexOf(searchNoSpace);
                if (start == -1) return null;
                start += searchNoSpace.length();
                while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) {
                    start++;
                }
            }

            int end = start;
            if (start > 0 && json.charAt(start - 1) == '"') {
                end = json.indexOf("\"", start);
                while (end != -1 && json.charAt(end - 1) == '\\') {
                    end = json.indexOf("\"", end + 1);
                }
            } else {
                while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') {
                    end++;
                }
            }

            if (end == -1 || start == end) return null;

            String value = json.substring(start, end).replace("\\\"", "\"").trim();
            return value;
        } catch (Exception e) {
            api.logging().logToError("extractJson error for " + key + ": " + e.getMessage());
            return null;
        }
    }

    private String formatInteraction(String decrypted) {
        try {
            StringBuilder out = new StringBuilder();
            out.append("=== Interactsh Interaction ===\n\n");

            String protocol = extractJson(decrypted, "protocol");
            String remoteAddr = extractJson(decrypted, "remote-address");
            String timestamp = extractJson(decrypted, "timestamp");
            String qType = extractJson(decrypted, "q-type");
            String rawRequest = unescapeUnicode(
                    extractJson(decrypted, "raw-request"));
            String rawResponse = unescapeUnicode(
                    extractJson(decrypted, "raw-response"));
            String rawData = extractJson(decrypted, "raw-data");
            String note = extractJson(decrypted, "note");

            if (protocol != null) out.append("Protocol: ").append(protocol.toUpperCase()).append("\n");
            if (remoteAddr != null) out.append("Remote IP: ").append(remoteAddr).append("\n");
            if (timestamp != null) out.append("Timestamp: ").append(timestamp).append("\n");
            if (qType != null) out.append("DNS Type: ").append(qType).append("\n");
            if (note != null) out.append("\nNote: ").append(note).append("\n");

            out.append("\n");

            if (rawRequest != null && !rawRequest.isEmpty()) {
                out.append("--- Raw Request ---\n");
                String fixed = rawRequest
                        .replace("\\\\r\\\\n", "\n")
                        .replace("\\\\n", "\n")
                        .replace("\\\\r", "\n")
                        .replace("\\r\\n", "\n")
                        .replace("\\n", "\n")
                        .replace("\\r", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\t", "\t");
                int htmlIdx = fixed.toLowerCase()
                        .indexOf("<html");
                if (htmlIdx == -1)
                    htmlIdx = fixed.toLowerCase()
                            .indexOf("<!doctype");
                if (htmlIdx != -1) {
                    String beforeHtml =
                            fixed.substring(0, htmlIdx);
                    String htmlPart =
                            fixed.substring(htmlIdx);
                    fixed = beforeHtml
                            + prettyHtml(htmlPart);
                }
                out.append(fixed);
                out.append("\n");
            }

            if (rawResponse != null && !rawResponse.isEmpty()) {
                out.append("\n--- Raw Response ---\n");
                String fixed = rawResponse
                        .replace("\\\\r\\\\n", "\n")
                        .replace("\\\\n", "\n")
                        .replace("\\\\r", "\n")
                        .replace("\\r\\n", "\n")
                        .replace("\\n", "\n")
                        .replace("\\r", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\t", "\t");
                int htmlIdx = fixed.toLowerCase()
                        .indexOf("<html");
                if (htmlIdx == -1)
                    htmlIdx = fixed.toLowerCase()
                            .indexOf("<!doctype");
                if (htmlIdx != -1) {
                    String beforeHtml =
                            fixed.substring(0, htmlIdx);
                    String htmlPart =
                            fixed.substring(htmlIdx);
                    fixed = beforeHtml
                            + prettyHtml(htmlPart);
                }
                out.append(fixed);
                out.append("\n");
            }

            if (rawData != null && !rawData.isEmpty()) {
                out.append("\n--- DNS Data ---\n");
                String fixed = rawData
                        .replace("\\\\r\\\\n", "\n")
                        .replace("\\\\n", "\n")
                        .replace("\\\\r", "\n")
                        .replace("\\r\\n", "\n")
                        .replace("\\n", "\n")
                        .replace("\\r", "\n")
                        .replace("\\t", "\t");
                out.append(fixed);
                out.append("\n");
            }

            return out.toString();
        } catch (Exception e) {
            return "=== Interactsh Interaction ===\n\nParsing Fallback:\n" + decrypted;
        }
    }

    private byte[] decrypt(String encryptedData, String encryptedAesKey) throws Exception {
        if (keyPair == null) throw new Exception("No keypair.");

        byte[] encAesKeyBytes = Base64.getDecoder().decode(encryptedAesKey);
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        javax.crypto.spec.OAEPParameterSpec oaepSpec = new javax.crypto.spec.OAEPParameterSpec(
                "SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256,
                javax.crypto.spec.PSource.PSpecified.DEFAULT);
        rsaCipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), oaepSpec);
        byte[] aesKeyBytes = rsaCipher.doFinal(encAesKeyBytes);

        byte[] encDataBytes = Base64.getDecoder().decode(encryptedData);
        if (encDataBytes.length < 16) {
            throw new Exception("Ciphertext too short");
        }

        byte[] iv = new byte[16];
        byte[] cipherText = new byte[encDataBytes.length - 16];
        System.arraycopy(encDataBytes, 0, iv, 0, 16);
        System.arraycopy(encDataBytes, 16, cipherText, 0, cipherText.length);

        Cipher aesCipher = Cipher.getInstance("AES/CTR/NoPadding");
        javax.crypto.spec.IvParameterSpec spec = new javax.crypto.spec.IvParameterSpec(iv);
        SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, spec);

        return aesCipher.doFinal(cipherText);
    }

    private String generateCorrelationId() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        SecureRandom rng = new SecureRandom();
        for (int i = 0; i < 20; i++)
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    private String generateNonce(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        SecureRandom rng = new SecureRandom();
        for (int i = 0; i < length; i++)
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    private String extractJsonArray(String json, String key) {
        try {
            String search = "\"" + key + "\":[";
            int start = json.indexOf(search);
            if (start == -1) return null;
            start += search.length() - 1;
            int depth = 0;
            int end = start;
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            return json.substring(start + 1, end);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isActive() {
        return active;
    }

    public void shutdown() {
        stopPolling();
    }
}
