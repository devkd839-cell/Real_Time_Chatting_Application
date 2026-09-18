# 🔒 Secure Real-Time Chat Application

A client-server desktop chat application built entirely in Java, demonstrating socket programming, multithreading, Swing GUI, user authentication, password hashing, and AES-GCM message encryption.

> **This is a college placement project.** It is designed to be completely understandable and explainable in a technical interview. Every major component is documented in the source code.

---

## Features

| Feature | Implementation |
|---|---|
| Real-time private messaging | Java TCP Sockets |
| Multiple simultaneous clients | Thread pool (ExecutorService) |
| Desktop GUI | Java Swing (dark theme) |
| User registration & login | Custom protocol over sockets |
| Password security | SHA-256 on wire, never plaintext |
| Message encryption | AES-256-GCM (authenticated encryption) |
| Online user list | Server broadcast on connect/disconnect |
| Graceful logout & disconnect | Cleanup in ClientHandler |
| Input validation | Both client-side and server-side |

---

## Architecture

```
                 ┌──────────────────────────────┐
                 │         ChatServer            │
                 │  • ServerSocket (port 12345)  │
                 │  • ExecutorService threadpool  │
                 │  • ServerUserManager (auth)   │
                 │  • ConcurrentHashMap (online) │
                 │  • AES key management         │
                 └──────────────┬───────────────┘
                                │  TCP / port 12345
              ┌─────────────────┼──────────────────┐
              │                 │                  │
     ┌────────▼──────┐ ┌────────▼──────┐ ┌────────▼──────┐
     │  ClientHandler│ │  ClientHandler│ │  ClientHandler│
     │  (Thread 1)   │ │  (Thread 2)   │ │  (Thread 3)   │
     └────────┬──────┘ └────────┬──────┘ └────────┬──────┘
              │                 │                  │
     ┌────────▼──────┐ ┌────────▼──────┐ ┌────────▼──────┐
     │   ChatClient  │ │   ChatClient  │ │   ChatClient  │
     │   (Krishna)   │ │   (Rahul)     │ │   (Amit)      │
     │   Swing GUI   │ │   Swing GUI   │ │   Swing GUI   │
     └───────────────┘ └───────────────┘ └───────────────┘
```

---

## Project Structure

```
SecureChatApplication/
│
├── src/
│   ├── server/
│   │   ├── ChatServer.java          ← Main server, accept loop, thread pool
│   │   ├── ClientHandler.java       ← One thread per connected client
│   │   └── ServerUserManager.java   ← Registration, login, file persistence
│   │
│   ├── client/
│   │   ├── ChatClient.java          ← Socket connection, receive thread, encryption
│   │   ├── LoginFrame.java          ← Login window (Swing)
│   │   ├── RegisterFrame.java       ← Registration window (Swing)
│   │   └── ChatFrame.java           ← Main chat window (Swing)
│   │
│   ├── security/
│   │   ├── EncryptionUtil.java      ← AES-256-GCM encrypt/decrypt, key management
│   │   └── PasswordUtil.java        ← SHA-256 hashing, PBKDF2 utilities
│   │
│   ├── model/
│   │   ├── User.java                ← User data (username, salt, hash)
│   │   └── ChatMessage.java         ← Message data (sender, recipient, content, timestamp)
│   │
│   └── util/
│       ├── Constants.java           ← All configuration values in one place
│       └── MessageProtocol.java     ← Build and parse protocol messages
│
├── data/
│   ├── users.dat                    ← Serialized user database (auto-created)
│   └── chat.key                     ← AES encryption key (auto-created)
│
├── out/                             ← Compiled .class files (after javac)
├── README.md
└── .gitignore
```

---

## Class Responsibilities

### Server Side

**`ChatServer.java`**
The entry point for the server. Opens a `ServerSocket` on port 12345, runs an infinite accept loop, and submits each new client connection to an `ExecutorService` thread pool. Maintains a `ConcurrentHashMap` of online users and broadcasts the online list whenever someone connects or disconnects.

**`ClientHandler.java`**
Implements `Runnable`. One instance runs in its own thread for each connected client. Reads lines from the client's socket in a loop, parses them using `MessageProtocol`, and routes them to the correct handler (login, register, message, logout). The `sendMessage()` method is `synchronized` to prevent garbled output when multiple threads write to the same client simultaneously.

**`ServerUserManager.java`**
Manages all registered user accounts. Stores them in a `ConcurrentHashMap<String, User>` and persists the map to `data/users.dat` using Java Object Serialization. Handles registration (with duplicate-username detection) and login verification.

### Client Side

**`ChatClient.java`**
The networking backbone. Opens a `Socket` to the server, receives the shared AES key, starts a background receive thread, and exposes `sendRegister()`, `sendLogin()`, `sendChatMessage()`, and `sendLogout()` methods. Uses `SwingUtilities.invokeLater()` to safely update the GUI from the background thread.

**`LoginFrame.java`**
The first window shown. Uses `SwingWorker` to connect and authenticate on a background thread so the GUI stays responsive. Callbacks from `ChatClient` (`onLoginSuccess`, `showError`, `showSuccess`) update the UI.

**`RegisterFrame.java`**
The registration window. Validates input client-side before sending to the server. Uses an adapter pattern (`RegisterCallbackAdapter`) to receive server responses.

**`ChatFrame.java`**
The main chat window. Left panel: `JList` of online users (updates in real time). Right panel: `JTextPane` with styled coloured messages. Bottom: message input + Send button. Messages are encrypted before sending and decrypted on receipt.

### Security

**`EncryptionUtil.java`**
AES-256-GCM encryption and decryption. Each message gets a fresh 12-byte random IV (nonce). The IV is prepended to the ciphertext and the whole thing is Base64-encoded for safe text transmission. GCM mode provides both confidentiality and integrity — tampered messages fail decryption.

**`PasswordUtil.java`**
Password hashing utilities. Provides PBKDF2WithHmacSHA256 (for demonstration and future use), SHA-256 for the wire credential, salt generation using `SecureRandom`, and constant-time comparison to resist timing attacks.

---

## How It Works — Complete Message Flow

```
Krishna types "Hello Rahul!" and clicks SEND
         │
         ▼
ChatFrame.sendMessage()
         │
         ▼
ChatClient.sendChatMessage("rahul", "Hello Rahul!")
         │
         ▼  AES-256-GCM encrypt with shared key
         │  generate random 12-byte IV
         │  produce Base64(IV + ciphertext)
         │
         ▼
PrintWriter.println("MESSAGE|rahul|<Base64ciphertext>")
         │
         ▼  TCP Socket  ────────────────────────────────►
                                                         │
                                              ChatServer (port 12345)
                                                         │
                                              ClientHandler (Krishna's thread)
                                                         │
                                              parse:  parts[1] = "rahul"
                                                      parts[2] = <ciphertext>
                                                         │
                                              lookup rahul's ClientHandler
                                                         │
                                              rahul.sendMessage("INCOMING|krishna|<ciphertext>")
                                                         │
                                         TCP Socket  ◄───┘
                                                         │
                                              Rahul's receive thread
                                                         │
                                              ChatClient.handleIncomingMessage()
                                                         │
                                              AES-256-GCM decrypt → "Hello Rahul!"
                                                         │
                                              SwingUtilities.invokeLater()
                                                         │
                                              ChatFrame.displayMessage("krishna", "Hello Rahul!")
                                                         │
                                              Message appears in Rahul's chat window ✓
```

---

## Security Design

### Password Handling
- Plaintext passwords **never** leave the client machine and are **never** stored anywhere.
- At registration and login, the password is hashed using **SHA-256** before being sent over the socket.
- The server stores only the hash — it cannot recover the original password.

### Message Encryption
- All chat messages are encrypted using **AES-256 in GCM mode** before being sent.
- A fresh **12-byte random IV (nonce)** is generated for every single message.
- GCM provides **authenticated encryption** — if a message is tampered with in transit, decryption throws an exception and the message is rejected.
- The encryption key is a 256-bit AES key stored in `data/chat.key`.

### Security Limitations (important to mention in interviews)

| Limitation | What it means | Production solution |
|---|---|---|
| No TLS on the socket | The AES key is sent in plaintext during the handshake | Use `SSLSocket` / TLS |
| Server-held shared key | The server could theoretically decrypt messages | True E2E with per-pair Diffie-Hellman key exchange |
| SHA-256 for login (no salt) | Weaker than salted PBKDF2; vulnerable to rainbow tables if the hash database is leaked | Add a GET_SALT round-trip before login |
| No message history | Messages are lost when clients disconnect | Store encrypted messages in a database |
| Single server | No redundancy | Distributed architecture |

**Always be honest about these limitations in an interview. Knowing the trade-offs is what distinguishes a thoughtful developer.**

---

## Installation & Running

### Prerequisites
- **Java JDK 8 or later** — [Download](https://www.oracle.com/java/technologies/downloads/)
- No external libraries required — uses Java standard library only

### Step 1 — Get the project
```bash
# If using git:
git clone <your-repo-url>
cd SecureChatApplication

# Or just download and unzip, then open a terminal in SecureChatApplication/
```

### Step 2 — Compile all source files
Open a terminal in the `SecureChatApplication/` directory and run:

```bash
# Windows (PowerShell)
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse -Filter "*.java" -Path src | Select-Object -ExpandProperty FullName)

# Windows (Command Prompt)
for /r src %f in (*.java) do javac -encoding UTF-8 -d out "%f"

# Linux / macOS
find src -name "*.java" | xargs javac -encoding UTF-8 -d out
```

### Step 3 — Start the server
```bash
java -cp out server.ChatServer
```

You should see:
```
╔══════════════════════════════════════════╗
║        SECURE CHAT SERVER STARTED        ║
╠══════════════════════════════════════════╣
║  Port    : 12345                         ║
║  Users   : 0 registered                  ║
║  Waiting for connections...              ║
╚══════════════════════════════════════════╝
```

### Step 4 — Start a client (repeat for each user)
Open a **new terminal** for each client instance:

```bash
java -cp out client.LoginFrame
```

### Step 5 — Register and chat
1. Click **REGISTER** → enter username and password → click **CREATE ACCOUNT**
2. Go back to Login → enter credentials → click **LOGIN**
3. The chat window opens. Click a username in the left panel to select them as your recipient.
4. Type a message and press **Enter** or click **SEND**.

### Running multiple clients
Open multiple terminals and run `java -cp out client.LoginFrame` in each. Register different usernames and they will all appear in each other's online user list.

---

## Test Cases

| # | Test | Steps | Expected Result |
|---|---|---|---|
| 1 | Server startup | Run `java -cp out server.ChatServer` | "Server started" banner printed |
| 2 | Registration | Register username "krishna", password "test123" | "Registration successful" shown |
| 3 | Duplicate registration | Register "krishna" again | "Username already exists" shown |
| 4 | Login | Login as "krishna" / "test123" | Chat window opens |
| 5 | Wrong password | Login as "krishna" / "wrongpass" | "Invalid username or password" shown |
| 6 | Two-client messaging | Krishna sends "Hello" to Rahul | Rahul sees "[time] krishna → rahul: Hello" |
| 7 | Multiple clients | Connect 4 users simultaneously | All chat windows remain responsive |
| 8 | Encryption check | Intercept socket with a packet sniffer | Message body is Base64 ciphertext, not readable text |
| 9 | Logout | Click LOGOUT → confirm | User disappears from all online lists |
| 10 | Unexpected disconnect | Close client window | User removed from online list within seconds |

---

## Future Enhancements

- **Database integration** — Replace `users.dat` with MySQL + JDBC
- **Group chat rooms** — Broadcast messages to multiple recipients
- **Message history** — Persist encrypted messages to database
- **TLS transport** — Replace plain `Socket` with `SSLSocket` for secure key exchange
- **True end-to-end encryption** — Diffie-Hellman key exchange between clients
- **File sharing** — Send files through the chat
- **Profile pictures** — Avatar images in the user list
- **Read receipts** — "Seen" indicators
- **Desktop notifications** — System tray alerts for new messages
- **Better key management** — Per-session keys, key rotation

---

## Resume Bullet Points

```
• Built a multi-client secure real-time chat application in Java using TCP socket programming,
  supporting simultaneous connections via a thread pool (ExecutorService).

• Implemented AES-256-GCM message encryption with per-message random IVs and GCM
  authentication tags using Java Cryptography Architecture (JCA) APIs.

• Designed a client-server authentication system with password hashing (SHA-256 on wire,
  PBKDF2 utilities) ensuring plaintext passwords never leave the client.

• Created a multi-screen Java Swing GUI (login, registration, chat) following correct
  Event Dispatch Thread (EDT) practices using SwingWorker and SwingUtilities.invokeLater().

• Applied multithreading with one ClientHandler thread per client, synchronized shared
  resources using ConcurrentHashMap and synchronized methods.
```

---

## Technologies Used

| Category | Technology |
|---|---|
| Language | Java 8+ |
| Networking | `java.net.Socket`, `java.net.ServerSocket` |
| I/O | `BufferedReader`, `PrintWriter`, `InputStreamReader`, `OutputStreamWriter` |
| Multithreading | `Thread`, `ExecutorService`, `Executors.newCachedThreadPool()` |
| GUI | Java Swing — `JFrame`, `JPanel`, `JTextPane`, `JList`, `JPasswordField`, `SwingWorker` |
| Encryption | `javax.crypto.Cipher` (AES/GCM/NoPadding), `javax.crypto.KeyGenerator` |
| Password Security | `javax.crypto.SecretKeyFactory` (PBKDF2WithHmacSHA256), `java.security.MessageDigest` (SHA-256) |
| Random Generation | `java.security.SecureRandom` |
| Persistence | Java Object Serialization (`ObjectOutputStream` / `ObjectInputStream`) |
| Data Structures | `ConcurrentHashMap`, `DefaultListModel` |

---

*Project developed as a Java placement/interview demonstration project.*
*All security limitations are documented. This is not production-grade software.*
