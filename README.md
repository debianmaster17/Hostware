# Hostware

> **Hostware is a Burp Suite extension providing an integrated exploit server and OOB detection platform for web application penetration testing.**

---
## What It Does

Hostware gives you a **fully controllable HTTP server inside Burp** — serve any payload, capture every request, and detect blind out-of-band callbacks, all without leaving the suite.

| Problem                                        | Hostware                                   |
| ---------------------------------------------- | ------------------------------------------ |
| Need a quick exploit server for XSS/CSRF?      | Built-in, one click                        |
| Testing SSRF but have no VPS right now?        | ngrok + local mode                         |
| Blind OOB injection with no Burp Pro?          | Interactsh built in                        |
| Want to see exactly what the target sent back? | Live access log with full request/response |

---
## Features

### Exploit Server

- **Local and external hosting modes** — bind to `127.0.0.1` or expose via your VPS/ngrok
- **Three-panel live editor** — edit `HEAD` and `BODY` separately; the full HTTP response updates in real time
- **Multi-slot tabs** — run multiple exploit variants simultaneously, close the ones you don't need
- **Quick Payload templates** — XSS, SSRF probes, XXE file read, JWT JWKS, open redirect, CORS exploit
- **Copy URL / Copy Host** — one click to grab exactly what you need for the payload
- **Open in Browser** — instantly preview your exploit in the default browser
- **Context menu integration** — right-click any request in Burp → _Send to Exploit Server_

### OASTForge (OOB Detection)

- **Burp Collaborator** (Pro) — generate payloads, poll for DNS/HTTP/SMTP interactions, auto-stops after 5 consecutive errors
- **Interactsh** (free) — full RSA-OAEP + AES-CTR encrypted session, works with `oast.pro` or any self-hosted server
- Both run **simultaneously** — cover all bases on a single engagement

### Access Log

- Live table of every inbound request — timestamp, IP, method, path, user-agent, source
- Click any row to view the **full raw request and response**
- **Export to CSV** for reporting
- Tab pulses orange on new activity so you never miss a hit

### General

- **Settings persistence** — port, path, host, scheme survive Burp restarts
- Works with **Burp Suite Community and Professional**

---

## Installation

### Requirements

- Burp Suite (Community or Professional) with **Montoya API** support (2022.8.1+)
- Java 11+

### From Releases

1. Download `Hostware.jar` from [Releases](https://github.com/debianmaster17/Hostware/releases)
2. In Burp: **Extensions → Installed → Add**
3. Set **Extension type** to `Java`, select the jar, click **Next**
4. The **Hostware** tab appears in the suite

### Build from Source

bash

```bash
git clone https://github.com/debianmaster17/Hostware.git
cd Hostware
./gradlew jar
# Output: build/libs/Hostware.jar
```

---

## Quick Start

### Serving an Exploit

1. Go to the **Exploit Server** tab
2. Select **Local** mode (or **External** if you have a VPS/ngrok tunnel)
3. Set your port and path (defaults: `8081`, `/exploit`)
4. Edit **HEAD** and **BODY** — the HTTP Request preview updates live
5. Click **Start Server** (dot turns green)
6. Click **Copy URL** and inject it into your target
7. Watch hits appear in the **Access Log** tab

### Blind OOB Testing

1. Go to **OASTForge**
2. **Collaborator (Pro):** Click **Generate Collaborator** → **Start Polling**
3. **Interactsh (Free):** Click **Register Session** → **Start Polling**
4. Copy the payload and inject into the target parameter
5. Interactions appear in the **Access Log** in real time

### From the Context Menu

Right-click any request in Proxy, Repeater, or anywhere else:  
**Hostware → Send to Exploit Server**  
The request loads into a new slot automatically.

---

## Use Cases

**Stored XSS** — serve a `<script>` that calls back to your exploit server, confirm execution via the access log.

**SSRF** — point the vulnerable parameter at your exploit server URL; see exactly what internal service hit you and with what headers.

**Blind XXE** — use the OASTForge Interactsh payload as your exfil endpoint; no VPS required.

**CORS misconfiguration** — craft a malicious CORS exploit page in the body editor and serve it locally.

**JWT algorithm confusion / JWKS spoofing** — use the JWT JWKS quick payload, host the fake JWKS endpoint, confirm the target fetches it.

---

## Architecture

```
Hostware (BurpExtension)
├── ui/
│   ├── MainTab          - Suite tab registration, tab management
│   ├── ExploitServerTab - Server config, exploit slots, button panel
│   ├── OOBTab           - Collaborator + Interactsh panels
│   └── LogTab           - Access log table + detail viewer
├── server/
│   ├── ExploitServer    - ServerSocket lifecycle, thread pool
│   └── RequestHandler   - Per-connection HTTP parsing and response
├── collaborator/
│   └── CollaboratorManager - Payload generation, polling, backoff
├── interactsh/
│   └── InteractshManager   - RSA/AES session, registration, polling
├── model/
│   ├── ExploitSlot      - HEAD/BODY editors + live sync
│   └── LogEntry         - Immutable log record
└── util/
    ├── PrefsUtil        - Persistent settings
    └── ClipboardUtil    - System clipboard helper
```


---

## Contributing

PRs and issues welcome. If you find a bug, please include:

- Burp Suite version
- Java version (`java -version`)
- Steps to reproduce
- Burp extension output log (Extensions → Hostware → Output)

---

## Author

**Alpay Ibrahimli**  
[github.com/debianmaster17](https://github.com/debianmaster17)

---

## License

MIT
