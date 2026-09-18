package model;

/**
 * User.java
 * ---------
 * This class represents a registered user in the chat application.
 * It stores the username, a random salt, and the hashed password.
 *
 * IMPORTANT: We NEVER store the plaintext password.
 * Instead, we store a salt + hash produced by PasswordUtil.
 *
 * This class implements java.io.Serializable so that User objects
 * can be written to / read from a file for simple persistence.
 */
public class User implements java.io.Serializable {

    // serialVersionUID is required for Serializable classes.
    // It ensures that a saved file can be read back correctly
    // even if the class is loaded again later.
    private static final long serialVersionUID = 1L;

    // The unique name this user logs in with (e.g. "Krishna")
    private String username;

    // A random byte array generated once at registration time.
    // Adding a unique salt to each password prevents "rainbow table" attacks,
    // where an attacker pre-computes hashes for common passwords.
    private byte[] salt;

    // The result of hashing (password + salt) with PBKDF2WithHmacSHA256.
    // Stored as a hex string so it can be written to a text file easily.
    private String passwordHash;

    // Constructor called during registration
    public User(String username, byte[] salt, String passwordHash) {
        this.username     = username;
        this.salt         = salt;
        this.passwordHash = passwordHash;
    }

    // --- Getters ---
    // We only provide getters (no setters) because a User's credentials
    // should not change after registration in this simple application.

    public String getUsername() {
        return username;
    }

    public byte[] getSalt() {
        return salt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    // toString is used for debug logging — it deliberately omits
    // the password hash and salt so they are never accidentally printed.
    @Override
    public String toString() {
        return "User{username='" + username + "'}";
    }
}
