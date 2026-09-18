package client;

import security.EncryptionUtil;
import security.PasswordUtil;
import util.Constants;
import util.MessageProtocol;

import javax.crypto.SecretKey;
import javax.swing.*;
import java.io.*;
import java.net.Socket;

/**
 * ChatClient.java
 * ---------------
 * Networking backbone for the chat client.
 *
 * Uses the ServerCallback interface instead of a concrete LoginFrame
 * reference, which means:
 *   - LoginFrame implements ServerCallback (for login)
 *   - A tiny RegisterCallback lambda/class handles registration
 *   - No fake LoginFrame subclass, no accidental hidden windows
 */
public class ChatClient {

    private Socket         socket;
    private PrintWriter    writer;
    private BufferedReader reader;
    private SecretKey      sharedKey;

    // Callback target for auth responses (login or register)
    private ServerCallback callback;

    // Chat window — set after successful login
    private ChatFrame chatFrame;

    private String  loggedInUsername;
    private boolean connected     = false;
    private boolean authenticated = false;

    // ----------------------------------------------------------------
    // CONNECTION
    // ----------------------------------------------------------------

    /**
     * Connect to the server, receive the shared AES key, start the
     * receive thread, and register the callback target.
     *
     * @param callback  who to notify on success/failure (LoginFrame or RegisterCallback)
     * @return          true if the TCP connection succeeded
     */
    public boolean connect(ServerCallback callback) {
        this.callback = callback;
        try {
            socket = new Socket(Constants.HOST, Constants.PORT);

            writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));

            // First line the server sends: "KEY|<base64AesKey>"
            String keyLine = reader.readLine();
            if (keyLine == null || !keyLine.startsWith("KEY|")) {
                showConnectionError("Server did not send encryption key.");
                disconnect();
                return false;
            }
            sharedKey = EncryptionUtil.base64ToKey(keyLine.substring(4));
            connected = true;

            startReceiveThread();
            System.out.println("[Client] Connected to " + Constants.HOST + ":" + Constants.PORT);
            return true;

        } catch (IOException e) {
            showConnectionError("Unable to connect to server.\n"
                    + "Is the server running?\n\nDetail: " + e.getMessage());
            return false;
        }
    }

    // ----------------------------------------------------------------
    // RECEIVE THREAD
    // ----------------------------------------------------------------

    private void startReceiveThread() {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    processIncoming(line);
                }
            } catch (IOException e) {
                if (connected) {
                    SwingUtilities.invokeLater(this::handleServerDisconnect);
                }
            }
        });
        t.setDaemon(true);
        t.setName("ChatClient-ReceiveThread");
        t.start();
    }

    // ----------------------------------------------------------------
    // INCOMING MESSAGE DISPATCHER
    // ----------------------------------------------------------------

    private void processIncoming(String rawLine) {
        String[] parts = MessageProtocol.parse(rawLine);
        if (parts.length == 0) return;

        switch (parts[0]) {

            case Constants.RESP_SUCCESS:
                handleSuccess(parts.length > 1 ? parts[1] : "");
                break;

            case Constants.RESP_FAILURE:
                handleFailure(parts.length > 1 ? parts[1] : "Unknown error");
                break;

            case Constants.RESP_INCOMING:
                if (parts.length >= 3) handleIncomingMessage(parts[1], parts[2]);
                break;

            case Constants.RESP_ONLINE_USERS:
                handleOnlineUsers(parts.length > 1 ? parts[1] : "");
                break;

            case Constants.RESP_SERVER_SHUTDOWN:
                SwingUtilities.invokeLater(this::handleServerDisconnect);
                break;

            default:
                System.out.println("[Client] Unknown server message: " + parts[0]);
        }
    }

    // ----------------------------------------------------------------
    // RESPONSE HANDLERS
    // ----------------------------------------------------------------

    private void handleSuccess(String detail) {
        if (!authenticated) {
            if (detail.contains("Login successful")) {
                // ── Login succeeded ──
                authenticated = true;
                SwingUtilities.invokeLater(() -> {
                    callback.onLoginSuccess(loggedInUsername);
                    chatFrame = new ChatFrame(this, loggedInUsername);
                    chatFrame.setVisible(true);
                });
            } else {
                // ── Registration succeeded ──
                SwingUtilities.invokeLater(() -> callback.showSuccess(detail));
            }
        }
        // Ignore SUCCESS after login (e.g. logout ack) — handled by disconnect
    }

    private void handleFailure(String reason) {
        SwingUtilities.invokeLater(() -> {
            if (!authenticated) {
                callback.showError(reason);
            } else if (chatFrame != null) {
                chatFrame.showError(reason);
            }
        });
    }

    private void handleIncomingMessage(String sender, String encBase64) {
        if (chatFrame == null || sharedKey == null) return;
        try {
            String plaintext = EncryptionUtil.decrypt(encBase64, sharedKey);
            SwingUtilities.invokeLater(() -> chatFrame.displayMessage(sender, plaintext));
        } catch (Exception e) {
            System.err.println("[Client] Decrypt failed from " + sender + ": " + e.getMessage());
            SwingUtilities.invokeLater(() ->
                    chatFrame.displayMessage(sender, "[Could not decrypt message]"));
        }
    }

    private void handleOnlineUsers(String csv) {
        if (chatFrame == null) return;
        String[] users = csv.isEmpty() ? new String[0] : csv.split(",");
        SwingUtilities.invokeLater(() -> chatFrame.updateOnlineUsers(users));
    }

    private void handleServerDisconnect() {
        connected     = false;
        authenticated = false;
        String msg = "Connection to server lost.\nThe server may have shut down.";
        if (chatFrame != null) {
            chatFrame.showError(msg);
            chatFrame.dispose();
            chatFrame = null;
        }
        if (callback != null) {
            callback.showError(msg);
            callback.resetForReconnect();
        }
        disconnect();
    }

    // ----------------------------------------------------------------
    // SEND METHODS
    // ----------------------------------------------------------------

    /**
     * Register: hash password with SHA-256 client-side, send to server.
     * Server stores the SHA-256 hash — plaintext never leaves this machine.
     */
    public void sendRegister(String username, String password) {
        String hashHex = sha256Hex(password);
        sendLine(MessageProtocol.buildRegister(username, hashHex));
    }

    public void sendLogin(String username, String password) {
        this.loggedInUsername = username.trim().toLowerCase();
        sendLine(MessageProtocol.buildLogin(username, sha256Hex(password)));
    }

    /** Encrypt and send a private message. */
    public void sendChatMessage(String recipient, String plaintext) {
        if (sharedKey == null) return;
        try {
            String encrypted = EncryptionUtil.encrypt(plaintext, sharedKey);
            sendLine(MessageProtocol.buildMessage(recipient, encrypted));
        } catch (Exception e) {
            System.err.println("[Client] Encrypt failed: " + e.getMessage());
            if (chatFrame != null) {
                SwingUtilities.invokeLater(() ->
                        chatFrame.showError("Could not encrypt message."));
            }
        }
    }

    /** Logout gracefully. */
    public void sendLogout() {
        if (connected) sendLine(MessageProtocol.buildLogout());
        connected     = false;
        authenticated = false;
        disconnect();
    }

    /** Request the current online-user list. */
    public void requestUserList() {
        sendLine(MessageProtocol.buildUserListRequest());
    }

    // ----------------------------------------------------------------
    // LOW-LEVEL
    // ----------------------------------------------------------------

    private void sendLine(String line) {
        if (writer != null && connected) writer.println(line);
    }

    public void disconnect() {
        connected = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    private String sha256Hex(String input) {
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            return PasswordUtil.bytesToHex(md.digest(input.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private void showConnectionError(String msg) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null, msg,
                        "Connection Error", JOptionPane.ERROR_MESSAGE));
    }

    // ----------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------

    public boolean isConnected()     { return connected;       }
    public boolean isAuthenticated() { return authenticated;   }
    public String  getUsername()     { return loggedInUsername; }
}
