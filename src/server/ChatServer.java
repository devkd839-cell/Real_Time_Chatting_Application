package server;

import security.EncryptionUtil;
import util.Constants;
import util.MessageProtocol;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ChatServer.java
 * ---------------
 * The main server class.  Run this first, before any clients connect.
 *
 * RESPONSIBILITIES
 * ----------------
 * 1. Start a ServerSocket on a configured port
 * 2. Accept incoming client connections in a loop
 * 3. Create a ClientHandler for each new connection
 * 4. Submit each handler to a thread pool (ExecutorService)
 * 5. Maintain a map of currently online users (username → ClientHandler)
 * 6. Provide methods for ClientHandler to:
 *      - add/remove clients
 *      - look up a specific client by username
 *      - broadcast the online-user list to everyone
 * 7. Handle server shutdown gracefully
 *
 * HOW MULTIPLE CLIENTS WORK
 * --------------------------
 * ServerSocket.accept() blocks (waits) until a new client connects.
 * When a client connects, accept() returns a new Socket for that client.
 * We wrap it in a ClientHandler and submit it to the ExecutorService.
 * The ExecutorService picks an available thread from the pool and
 * calls clientHandler.run() on it.
 * The main thread immediately loops back to accept() to wait for the
 * next client — so accepting new clients never waits for existing ones.
 *
 * THREAD POOL (ExecutorService)
 * -----------------------------
 * newCachedThreadPool() creates threads on demand and reuses idle ones.
 * This is fine for a student project.  In production with thousands of
 * clients you would use a fixed thread pool or NIO (non-blocking I/O).
 *
 * THREAD SAFETY
 * -------------
 * onlineClients uses ConcurrentHashMap so ClientHandlers running on
 * different threads can safely add/remove/look up clients.
 */
public class ChatServer {

    // The ServerSocket that listens for incoming connections
    private ServerSocket serverSocket;

    // Thread pool — each accepted client gets a thread from this pool
    private final ExecutorService threadPool;

    // Maps username → ClientHandler for every currently online user
    // ConcurrentHashMap handles concurrent access from multiple threads
    private final ConcurrentHashMap<String, ClientHandler> onlineClients;

    // Manages registered users (loading/saving/authenticating)
    private final ServerUserManager userManager;

    // The shared AES-256 encryption key used by all clients
    // (loaded from data/chat.key or generated fresh on first run)
    private final SecretKey sharedKey;

    // Flag used to stop the accept loop on shutdown
    private volatile boolean running = false;


    // ----------------------------------------------------------------
    // CONSTRUCTOR
    // ----------------------------------------------------------------

    public ChatServer() {
        // newCachedThreadPool: grows as needed, reuses idle threads
        this.threadPool    = Executors.newCachedThreadPool();
        this.onlineClients = new ConcurrentHashMap<>();
        this.userManager   = new ServerUserManager();

        // Load or generate the shared AES key once at startup
        this.sharedKey     = EncryptionUtil.loadOrCreateKey();
    }


    // ----------------------------------------------------------------
    // START / ACCEPT LOOP
    // ----------------------------------------------------------------

    /**
     * Start the server on the configured port and begin accepting clients.
     * This method blocks indefinitely (runs the accept loop).
     */
    public void start() {
        try {
            // ServerSocket binds to the port and begins listening for connections.
            // The OS queues up to 50 incoming connection requests by default.
            serverSocket = new ServerSocket(Constants.PORT);
            running = true;

            System.out.println("╔══════════════════════════════════════════╗");
            System.out.println("║        SECURE CHAT SERVER STARTED        ║");
            System.out.println("╠══════════════════════════════════════════╣");
            System.out.println("║  Port    : " + Constants.PORT + "                          ║");
            System.out.println("║  Users   : " + userManager.getRegisteredUserCount()
                               + " registered                       ║");
            System.out.println("║  Waiting for connections...              ║");
            System.out.println("╚══════════════════════════════════════════╝");

            // Accept loop — runs until the server is shut down
            while (running) {
                try {
                    // accept() BLOCKS here until a client connects.
                    // When a client connects, it returns a new Socket
                    // dedicated to that client.
                    Socket clientSocket = serverSocket.accept();

                    System.out.println("[Server] Accepted connection from: " +
                                       clientSocket.getInetAddress().getHostAddress());

                    // Create a handler for this specific client
                    ClientHandler handler = new ClientHandler(clientSocket, this, sharedKey);

                    // Submit to the thread pool — the pool assigns a thread
                    // and calls handler.run() on it asynchronously.
                    // The main thread immediately returns here to accept the next client.
                    threadPool.submit(handler);

                } catch (IOException e) {
                    // If the server is shutting down, the serverSocket will be closed
                    // and accept() will throw an IOException — that's expected.
                    if (running) {
                        System.err.println("[Server] Error accepting client: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("[Server] FATAL: Could not start server on port " +
                               Constants.PORT + ": " + e.getMessage());
            System.err.println("[Server] Is another process using port " + Constants.PORT + "?");
        } finally {
            shutdown();
        }
    }


    // ----------------------------------------------------------------
    // SHUTDOWN
    // ----------------------------------------------------------------

    /**
     * Gracefully shut down the server.
     * Notifies all connected clients, then closes resources.
     */
    public void shutdown() {
        running = false;
        System.out.println("[Server] Shutting down...");

        // Notify all connected clients that the server is going down
        broadcastToAll(MessageProtocol.buildServerShutdown());

        // Shut down the thread pool — wait for running tasks to finish
        threadPool.shutdownNow();

        // Close the server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error closing server socket: " + e.getMessage());
        }

        System.out.println("[Server] Server stopped.");
    }


    // ----------------------------------------------------------------
    // CLIENT REGISTRY  (called by ClientHandler)
    // ----------------------------------------------------------------

    /**
     * Register a newly authenticated client.
     * Called by ClientHandler after successful login.
     *
     * @param username  the authenticated username (lowercase)
     * @param handler   the ClientHandler for this connection
     */
    public void addClient(String username, ClientHandler handler) {
        onlineClients.put(username, handler);
        System.out.println("[Server] Online: " + getOnlineUserListCSV() +
                           " (" + onlineClients.size() + " user(s))");
    }

    /**
     * Remove a client when they log out or disconnect.
     * Called by ClientHandler.cleanup().
     *
     * @param username  the username to remove
     */
    public void removeClient(String username) {
        onlineClients.remove(username);
        System.out.println("[Server] " + username + " disconnected. Online: " +
                           onlineClients.size() + " user(s)");
    }

    /**
     * Look up a connected client by username.
     *
     * @param username  the target username (lowercase)
     * @return          the ClientHandler, or null if the user is not online
     */
    public ClientHandler getClient(String username) {
        return onlineClients.get(username);
    }

    /**
     * Check whether a given username is currently connected.
     *
     * @param username  the username to check
     * @return          true if the user is online
     */
    public boolean isUserOnline(String username) {
        return username != null && onlineClients.containsKey(username);
    }

    /**
     * Return a comma-separated list of all online usernames.
     * Example: "amit,krishna,rahul"
     *
     * @return CSV string of online usernames
     */
    public String getOnlineUserListCSV() {
        if (onlineClients.isEmpty()) return "";
        return String.join(",", onlineClients.keySet());
    }


    // ----------------------------------------------------------------
    // BROADCAST METHODS
    // ----------------------------------------------------------------

    /**
     * Send the current online-user list to EVERY connected client.
     * Called whenever a user logs in or disconnects so all clients
     * can update their "Online Users" panel in real time.
     */
    public void broadcastOnlineUserList() {
        String message = MessageProtocol.buildOnlineUsers(getOnlineUserListCSV());
        broadcastToAll(message);
    }

    /**
     * Send a message to every currently connected client.
     *
     * @param message  the protocol message string to send
     */
    private void broadcastToAll(String message) {
        // Iterate over a snapshot of values to avoid ConcurrentModificationException
        // if a client disconnects while we're iterating
        List<ClientHandler> snapshot = new ArrayList<>(onlineClients.values());
        for (ClientHandler handler : snapshot) {
            handler.sendMessage(message);
        }
    }


    // ----------------------------------------------------------------
    // GETTER
    // ----------------------------------------------------------------

    /**
     * Provide access to the user manager for ClientHandler.
     */
    public ServerUserManager getUserManager() {
        return userManager;
    }


    // ----------------------------------------------------------------
    // MAIN METHOD  — entry point to start the server
    // ----------------------------------------------------------------

    /**
     * Run this main() to start the chat server.
     *
     * Compile and run instructions:
     *
     *   From the SecureChatApplication/ directory:
     *
     *   Compile:
     *     javac -d out src/model/*.java src/util/*.java src/security/*.java src/server/*.java
     *
     *   Run:
     *     java -cp out server.ChatServer
     */
    public static void main(String[] args) {
        // Add a shutdown hook so Ctrl+C triggers a graceful shutdown
        ChatServer server = new ChatServer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Shutdown signal received.");
            server.shutdown();
        }));

        // Start the server — this call blocks until the server stops
        server.start();
    }
}
