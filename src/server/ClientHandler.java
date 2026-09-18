package server;

import security.EncryptionUtil;
import util.Constants;
import util.MessageProtocol;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;

/**
 * ClientHandler.java
 * ------------------
 * One instance of this class is created for EACH connected client.
 * It runs in its own thread so multiple clients can be handled
 * simultaneously without blocking each other.
 *
 * LIFECYCLE
 * ---------
 * 1. Constructor receives the client's Socket and a reference to ChatServer.
 * 2. run() is called by the thread — it loops forever reading lines from
 *    the client socket.
 * 3. Each line is a protocol message (e.g. "LOGIN|...", "MESSAGE|...", etc.)
 * 4. handleMessage() dispatches to the appropriate handler method.
 * 5. When the client disconnects (socket closes / null line received),
 *    the loop ends and cleanup() is called.
 *
 * THREAD SAFETY
 * -------------
 * sendMessage() is synchronized so that if two server threads try to write
 * to this client's socket at the same time (e.g. two users both sending a
 * message to this client simultaneously), the writes don't interleave and
 * corrupt the stream.
 */
public class ClientHandler implements Runnable {

    // The TCP socket connecting this client to the server
    private final Socket socket;

    // Reference to the server so we can ask it to route messages,
    // broadcast user lists, etc.
    private final ChatServer server;

    // Streams for reading from / writing to the socket
    private BufferedReader reader;
    private PrintWriter   writer;

    // The username of the logged-in user for this connection.
    // null until the client successfully authenticates.
    private String username;

    // Whether this client has successfully logged in
    private boolean authenticated = false;

    // The AES key shared across all clients (provided by the server)
    private final SecretKey sharedKey;


    // ----------------------------------------------------------------
    // CONSTRUCTOR
    // ----------------------------------------------------------------

    public ClientHandler(Socket socket, ChatServer server, SecretKey sharedKey) {
        this.socket    = socket;
        this.server    = server;
        this.sharedKey = sharedKey;
    }


    // ----------------------------------------------------------------
    // RUNNABLE ENTRY POINT
    // ----------------------------------------------------------------

    /**
     * This method runs in a separate thread for each client.
     * It reads lines from the socket in a loop until the connection closes.
     */
    @Override
    public void run() {
        try {
            // Set up a BufferedReader to read text lines from the socket's InputStream.
            // InputStreamReader converts the raw bytes to characters using UTF-8.
            // BufferedReader adds buffering and the readLine() convenience method.
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));

            // PrintWriter wraps the socket's OutputStream.
            // autoFlush=true means each println() is sent immediately without
            // needing an explicit flush() call.
            writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // Step 1: Send the shared AES key to the client.
            // The client will use this key to encrypt/decrypt all messages.
            //
            // SECURITY NOTE: In this application, the key is sent in plaintext
            // over the socket because we do not have TLS.  This means the key
            // is visible if someone intercepts the network traffic.
            // In a production system you would use TLS (SSL sockets) to protect
            // the key exchange, or implement a proper Diffie-Hellman handshake.
            writer.println("KEY|" + EncryptionUtil.keyToBase64(sharedKey));

            System.out.println("[Server] New connection from: " + socket.getInetAddress());

            // Step 2: Read and process messages from this client in a loop.
            String line;
            // readLine() returns null when the client closes the connection
            while ((line = reader.readLine()) != null) {
                handleMessage(line.trim());
            }

        } catch (IOException e) {
            // IOException is normal when a client disconnects unexpectedly
            // (e.g. user closes the window without clicking Logout)
            if (authenticated) {
                System.out.println("[Server] Client " + username +
                                   " disconnected unexpectedly: " + e.getMessage());
            }
        } finally {
            // Always clean up, whether the loop ended normally or via exception
            cleanup();
        }
    }


    // ----------------------------------------------------------------
    // MESSAGE DISPATCHER
    // ----------------------------------------------------------------

    /**
     * Parse a raw protocol line and call the appropriate handler.
     * Unknown or malformed messages are silently ignored (with a log entry).
     *
     * @param rawLine  the raw text line received from the socket
     */
    private void handleMessage(String rawLine) {
        if (rawLine == null || rawLine.isEmpty()) return;

        // Split the line into fields using our protocol separator
        String[] parts = MessageProtocol.parse(rawLine);
        if (parts.length == 0) return;

        String command = parts[0];

        // Route to the correct handler based on the command
        switch (command) {

            case Constants.CMD_REGISTER:
                handleRegister(parts);
                break;

            case Constants.CMD_LOGIN:
                handleLogin(parts);
                break;

            case Constants.CMD_MESSAGE:
                // Only authenticated users may send messages
                if (authenticated) {
                    handleChatMessage(parts);
                } else {
                    sendMessage(MessageProtocol.buildFailure("You must log in first."));
                }
                break;

            case Constants.CMD_USER_LIST:
                if (authenticated) {
                    sendOnlineUserList();
                }
                break;

            case Constants.CMD_LOGOUT:
                handleLogout();
                break;

            default:
                // Log unknown commands — don't crash
                System.out.println("[Server] Unknown command from " +
                                   (username != null ? username : "unauthenticated") +
                                   ": " + command);
        }
    }


    // ----------------------------------------------------------------
    // HANDLER METHODS
    // ----------------------------------------------------------------

    /**
     * Handle a REGISTER request.
     *
     * Wire format: REGISTER|username|sha256HashHex
     * parts[0] = "REGISTER"
     * parts[1] = username
     * parts[2] = sha256HashHex
     */
    private void handleRegister(String[] parts) {
        if (!MessageProtocol.hasFields(parts, 3)) {
            sendMessage(MessageProtocol.buildFailure("Invalid registration request."));
            return;
        }

        String uname   = parts[1];
        String hashHex = parts[2];

        String result = server.getUserManager().registerUser(uname, hashHex);
        sendMessage(result);
    }

    /**
     * Handle a LOGIN request.
     *
     * Wire format: LOGIN|username|sha256HashHex
     * parts[0] = "LOGIN"
     * parts[1] = username
     * parts[2] = sha256HashHex
     */
    private void handleLogin(String[] parts) {
        if (!MessageProtocol.hasFields(parts, 3)) {
            sendMessage(MessageProtocol.buildFailure("Invalid login request."));
            return;
        }

        String uname   = parts[1];
        String hashHex = parts[2];

        // Check if someone is already logged in on this connection
        if (authenticated) {
            sendMessage(MessageProtocol.buildFailure("Already logged in as " + username));
            return;
        }

        // Ask the user manager to verify credentials
        boolean valid = server.getUserManager().authenticateUser(uname, hashHex);

        if (!valid) {
            System.out.println("[Server] Failed login attempt for: " + uname);
            sendMessage(MessageProtocol.buildFailure("Invalid username or password."));
            return;
        }

        // Normalise the username (lowercase) for consistency
        String normalizedName = server.getUserManager().getNormalizedUsername(uname);

        // Check if this user is already logged in from another connection
        if (server.isUserOnline(normalizedName)) {
            sendMessage(MessageProtocol.buildFailure(
                    "User '" + normalizedName + "' is already logged in."));
            return;
        }

        // Authentication passed — mark this handler as authenticated
        this.username      = normalizedName;
        this.authenticated = true;

        // Register this handler with the server's active-client map
        server.addClient(normalizedName, this);

        System.out.println("[Server] User logged in: " + username);
        sendMessage(MessageProtocol.buildSuccess("Login successful! Welcome, " + username + "."));

        // Broadcast the updated online user list to ALL connected clients
        server.broadcastOnlineUserList();
    }

    /**
     * Handle a MESSAGE request (private message from one user to another).
     *
     * Expected format: MESSAGE|recipient|encryptedBase64
     * parts[0] = "MESSAGE"
     * parts[1] = recipient username
     * parts[2] = encrypted message body (Base64)
     *
     * The server DOES NOT decrypt the message — it simply forwards the
     * encrypted body to the recipient.  The recipient's client decrypts it.
     * This means the server never sees the plaintext message content.
     */
    private void handleChatMessage(String[] parts) {
        if (!MessageProtocol.hasFields(parts, 3)) {
            sendMessage(MessageProtocol.buildFailure("Invalid message format."));
            return;
        }

        String recipient      = parts[1].trim().toLowerCase();
        String encryptedBody  = parts[2];

        // Validate: sender cannot message themselves
        if (recipient.equals(username)) {
            sendMessage(MessageProtocol.buildFailure("You cannot send a message to yourself."));
            return;
        }

        // Validate: recipient must be online
        ClientHandler recipientHandler = server.getClient(recipient);
        if (recipientHandler == null) {
            sendMessage(MessageProtocol.buildFailure(
                    "User '" + recipient + "' is not online."));
            return;
        }

        // Forward the INCOMING message to the recipient.
        // The format the recipient receives: INCOMING|senderName|encryptedBody
        recipientHandler.sendMessage(
                MessageProtocol.buildIncoming(username, encryptedBody));

        System.out.println("[Server] Routed message: " + username + " → " + recipient);
    }

    /**
     * Handle a LOGOUT request.
     * The client is saying goodbye gracefully.
     */
    private void handleLogout() {
        System.out.println("[Server] User logged out: " +
                           (username != null ? username : "unauthenticated"));
        sendMessage(MessageProtocol.buildSuccess("Logged out successfully."));
        cleanup();
    }


    // ----------------------------------------------------------------
    // SEND METHOD  (synchronized for thread safety)
    // ----------------------------------------------------------------

    /**
     * Send a line of text to this client's socket.
     *
     * synchronized: if two threads (e.g. the server routing two different
     * incoming messages to this client simultaneously) call sendMessage()
     * at the same time, the synchronized keyword ensures one waits for
     * the other — their writes don't get interleaved.
     *
     * @param message  the text line to send (should NOT contain '\n')
     */
    public synchronized void sendMessage(String message) {
        if (writer != null && !socket.isClosed()) {
            writer.println(message);  // println appends '\n' and flushes (autoFlush=true)
        }
    }


    // ----------------------------------------------------------------
    // HELPER METHODS
    // ----------------------------------------------------------------

    /**
     * Send the current online-user list to THIS client only.
     * Used to refresh the client's user list panel on demand.
     */
    private void sendOnlineUserList() {
        String csv = server.getOnlineUserListCSV();
        sendMessage(MessageProtocol.buildOnlineUsers(csv));
    }

    /**
     * Clean up resources when the client disconnects.
     * Called from the finally block in run(), or from handleLogout().
     */
    private void cleanup() {
        // Remove this client from the server's active-client map
        if (username != null && authenticated) {
            server.removeClient(username);
            // Broadcast updated online list to all remaining clients
            server.broadcastOnlineUserList();
            authenticated = false;
        }

        // Close all I/O streams and the socket
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[Server] Error closing resources: " + e.getMessage());
        }
    }


    // ----------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------

    /** Returns the authenticated username, or null if not logged in. */
    public String getUsername() {
        return username;
    }

    /** Returns true if this client has successfully authenticated. */
    public boolean isAuthenticated() {
        return authenticated;
    }
}
