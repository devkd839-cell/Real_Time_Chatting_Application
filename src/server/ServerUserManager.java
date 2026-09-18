package server;

import model.User;
import security.PasswordUtil;
import util.Constants;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ServerUserManager.java
 * ----------------------
 * Manages all REGISTERED users — loading them from disk, saving new ones,
 * and verifying credentials at login time.
 *
 * This class handles PERSISTENCE (who has an account) as opposed to
 * ClientHandler which handles LIVE CONNECTIONS (who is online right now).
 *
 * STORAGE FORMAT
 * --------------
 * Users are stored in "data/users.dat" using Java Object Serialization.
 * The file holds a ConcurrentHashMap<String, User> serialized as a
 * single object.
 *
 * For a student project this is perfectly fine.
 * In a production system you would use a proper database (MySQL, PostgreSQL).
 *
 * THREAD SAFETY
 * -------------
 * ConcurrentHashMap allows multiple threads to read/write simultaneously
 * without explicit synchronized blocks.  This is important because the
 * server handles each client in a separate thread — two users could
 * register at almost the same instant.
 *
 * The saveUsers() method is synchronized to prevent two threads from
 * writing to the file at the same time, which would corrupt it.
 */
public class ServerUserManager {

    // Maps username (lowercase) → User object
    // ConcurrentHashMap is thread-safe for individual put/get operations
    private final ConcurrentHashMap<String, User> registeredUsers;

    // Path to the persistence file
    private final String usersFilePath;


    // ----------------------------------------------------------------
    // CONSTRUCTOR
    // ----------------------------------------------------------------

    public ServerUserManager() {
        this.usersFilePath  = Constants.USERS_FILE;
        this.registeredUsers = new ConcurrentHashMap<>();
        loadUsers();  // Load existing users from file on startup
    }


    // ----------------------------------------------------------------
    // REGISTRATION
    // ----------------------------------------------------------------

    /**
     * Register a new user.
     *
     * @param username        chosen username (will be lowercased)
     * @param passwordHashHex SHA-256 hash of the password, hex-encoded
     * @return  "SUCCESS|..." or "FAILURE|..."
     */
    public String registerUser(String username, String passwordHashHex) {

        if (username == null || username.trim().isEmpty()) {
            return "FAILURE|Username cannot be empty";
        }
        if (passwordHashHex == null || passwordHashHex.trim().isEmpty()) {
            return "FAILURE|Password cannot be empty";
        }

        String normalizedUsername = username.trim().toLowerCase();

        if (!normalizedUsername.matches("[a-z0-9_]{3,20}")) {
            return "FAILURE|Username must be 3-20 characters (letters, digits, underscore only)";
        }

        // Store user with empty salt — SHA-256 hash is the credential
        User newUser = new User(normalizedUsername, new byte[0], passwordHashHex);

        // putIfAbsent is atomic — safe under concurrent registrations
        User existing = registeredUsers.putIfAbsent(normalizedUsername, newUser);
        if (existing != null) {
            return "FAILURE|Username already exists. Please choose a different username.";
        }

        saveUsers();
        System.out.println("[UserManager] Registered new user: " + normalizedUsername);
        return "SUCCESS|Registration successful! You can now log in.";
    }


    // ----------------------------------------------------------------
    // LOGIN / AUTHENTICATION
    // ----------------------------------------------------------------

    /**
     * Authenticate a login attempt.
     *
     * The client sends the password it hashed locally.
     * We fetch the stored salt + hash for this username and
     * compare hashes — we NEVER receive or store the plaintext password.
     *
     * @param username        the username the user typed
     * @param passwordHashHex hex-encoded PBKDF2 hash that the client computed
     * @return  true if credentials are valid, false otherwise
     */
    public boolean authenticateUser(String username, String passwordHashHex) {

        if (username == null || passwordHashHex == null) return false;

        String normalizedUsername = username.trim().toLowerCase();
        User user = registeredUsers.get(normalizedUsername);

        if (user == null) {
            // Username does not exist
            return false;
        }

        // Compare the stored hash against the submitted hash using constant-time equals
        // (PasswordUtil.verifyPassword expects a plaintext password, but here the client
        //  already hashed it — so we compare hex strings directly via constantTimeEquals
        //  which is embedded in PasswordUtil.  We call hashPassword with an empty string
        //  trick? No — we compare the pre-hashed value directly.)
        //
        // DESIGN DECISION:
        // The client performs PBKDF2 locally before sending.
        // So both sides hold the same hash — we simply compare them.
        // This avoids sending the plaintext password over the socket at all.
        // (The socket is not TLS-protected in this student project, so keeping
        //  the plaintext password off the wire is an extra safety measure.)
        return PasswordUtil.constantTimeEqualsPublic(passwordHashHex, user.getPasswordHash());
    }

    /**
     * Return the normalised (lowercase) username for a given raw input.
     * Returns null if the user does not exist.
     */
    public String getNormalizedUsername(String username) {
        if (username == null) return null;
        String normalized = username.trim().toLowerCase();
        return registeredUsers.containsKey(normalized) ? normalized : null;
    }

    /**
     * Check whether a username is already registered.
     *
     * @param username  the username to check
     * @return          true if the username exists
     */
    public boolean userExists(String username) {
        if (username == null) return false;
        return registeredUsers.containsKey(username.trim().toLowerCase());
    }

    /**
     * Return the total number of registered users.
     */
    public int getRegisteredUserCount() {
        return registeredUsers.size();
    }


    // ----------------------------------------------------------------
    // PERSISTENCE
    // ----------------------------------------------------------------

    /**
     * Load users from the data file into memory.
     * Called once when the server starts.
     * If the file does not exist yet, we start with an empty map.
     */
    @SuppressWarnings("unchecked")
    private void loadUsers() {
        File file = new File(usersFilePath);

        if (!file.exists()) {
            System.out.println("[UserManager] No users file found. Starting fresh.");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            // Read the serialized ConcurrentHashMap back from the file
            ConcurrentHashMap<String, User> loaded =
                    (ConcurrentHashMap<String, User>) ois.readObject();
            registeredUsers.putAll(loaded);
            System.out.println("[UserManager] Loaded " + registeredUsers.size() +
                               " registered user(s) from " + usersFilePath);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[UserManager] Warning: Could not load users file: " + e.getMessage());
            // Not fatal — the server can still run with an empty user list
        }
    }

    /**
     * Save the current user map to the data file.
     * Called every time a new user registers.
     *
     * synchronized ensures only one thread writes the file at a time.
     * Without this, two simultaneous registrations could corrupt the file.
     */
    private synchronized void saveUsers() {
        File file = new File(usersFilePath);

        // Ensure the data/ directory exists
        file.getParentFile().mkdirs();

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            // Write the entire ConcurrentHashMap as a single serialized object
            oos.writeObject(new ConcurrentHashMap<>(registeredUsers));
            System.out.println("[UserManager] Saved " + registeredUsers.size() +
                               " user(s) to " + usersFilePath);
        } catch (IOException e) {
            System.err.println("[UserManager] ERROR: Could not save users file: " + e.getMessage());
        }
    }
}
