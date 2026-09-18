package util;

/**
 * Constants.java
 * --------------
 * A single place for every "magic number" and "magic string" used
 * across the application.
 *
 * WHY THIS MATTERS:
 *   If you need to change the server port, you change it HERE and
 *   every other class automatically picks up the new value.
 *   Without this file you would have to hunt through dozens of
 *   classes to find every place the port number was hard-coded.
 *
 * All fields are:
 *   public  – any class can read them
 *   static  – you access them as Constants.PORT, not new Constants().PORT
 *   final   – the value cannot be changed at runtime (true constants)
 */
public class Constants {

    // ----------------------------------------------------------------
    // NETWORK SETTINGS
    // ----------------------------------------------------------------

    /**
     * The TCP port the ChatServer listens on.
     * Port 12345 is in the "ephemeral / user" range and is unlikely
     * to conflict with a well-known service.
     * Change this if another program on your machine uses 12345.
     */
    public static final int PORT = 12345;

    /**
     * The hostname or IP address clients connect to.
     * "localhost" means the server is running on the SAME machine.
     * To run across a real network, change this to the server's IP,
     * e.g. "192.168.1.10"
     */
    public static final String HOST = "localhost";

    /**
     * How long (in milliseconds) a socket waits for data before timing out.
     * 0 means "wait forever" — fine for a chat application where a user
     * might just be idle for a while.
     */
    public static final int SOCKET_TIMEOUT_MS = 0;


    // ----------------------------------------------------------------
    // MESSAGE PROTOCOL COMMANDS
    // These are the "verbs" used in the text protocol between client
    // and server.  Every message on the wire starts with one of these.
    // ----------------------------------------------------------------

    /** Client wants to log in.
     *  Full format:  LOGIN|username|hashedPasswordHex  */
    public static final String CMD_LOGIN    = "LOGIN";

    /** Client wants to register a new account.
     *  Full format:  REGISTER|username|hashedPasswordHex|saltHex  */
    public static final String CMD_REGISTER = "REGISTER";

    /** Client is sending a private message to another user.
     *  Full format:  MESSAGE|recipient|encryptedBase64Content  */
    public static final String CMD_MESSAGE  = "MESSAGE";

    /** Client requests the current list of online users.
     *  Full format:  USER_LIST  (no extra fields)  */
    public static final String CMD_USER_LIST = "USER_LIST";

    /** Client is logging out gracefully.
     *  Full format:  LOGOUT  */
    public static final String CMD_LOGOUT   = "LOGOUT";


    // ----------------------------------------------------------------
    // SERVER RESPONSES
    // The server sends these back to the client.
    // ----------------------------------------------------------------

    /** Login or registration succeeded. */
    public static final String RESP_SUCCESS = "SUCCESS";

    /** Login or registration failed. */
    public static final String RESP_FAILURE = "FAILURE";

    /** Server is pushing a message to a recipient client.
     *  Full format:  INCOMING|sender|encryptedBase64Content  */
    public static final String RESP_INCOMING = "INCOMING";

    /** Server is pushing an updated online-user list.
     *  Full format:  ONLINE_USERS|user1,user2,user3  */
    public static final String RESP_ONLINE_USERS = "ONLINE_USERS";

    /** Server informing the client that the server is shutting down. */
    public static final String RESP_SERVER_SHUTDOWN = "SERVER_SHUTDOWN";


    // ----------------------------------------------------------------
    // PROTOCOL SEPARATOR
    // ----------------------------------------------------------------

    /**
     * The character used to separate fields inside a protocol message.
     * Example:  LOGIN|Krishna|abc123def456
     *
     * We use the pipe character "|" because it is unlikely to appear
     * in a username or Base64-encoded ciphertext.
     *
     * NOTE: If a field might contain "|" (e.g. the message body),
     * MessageProtocol.split() uses a limit so only the first N-1
     * separators are split — the remainder is kept intact.
     */
    public static final String SEPARATOR = "|";

    /**
     * Regex-escaped version of SEPARATOR, needed by String.split()
     * because "|" is a special regex character meaning "OR".
     */
    public static final String SEPARATOR_REGEX = "\\|";


    // ----------------------------------------------------------------
    // SECURITY SETTINGS
    // ----------------------------------------------------------------

    /** AES key size in bits. 256-bit AES is the strongest standard size. */
    public static final int AES_KEY_SIZE = 256;

    /** GCM authentication tag length in bits. 128 is the recommended value. */
    public static final int GCM_TAG_LENGTH = 128;

    /** GCM initialisation vector (nonce) length in bytes. 12 bytes = 96 bits,
     *  which is the recommended IV size for AES-GCM. */
    public static final int GCM_IV_LENGTH = 12;

    /** PBKDF2 iteration count.
     *  Higher = harder for an attacker to brute-force, but slower to compute.
     *  310,000 is the 2023 OWASP recommendation for PBKDF2-HMAC-SHA256. */
    public static final int PBKDF2_ITERATIONS = 310_000;

    /** Output length of the PBKDF2 derived key, in bits. */
    public static final int PBKDF2_KEY_LENGTH = 256;

    /** Length of the random salt generated at registration time, in bytes. */
    public static final int SALT_LENGTH = 16;


    // ----------------------------------------------------------------
    // FILE PATHS
    // ----------------------------------------------------------------

    /**
     * Path to the file where registered users are persisted.
     * Relative to wherever you launch the JVM from.
     * The server reads this file on startup and writes to it on
     * every new registration.
     */
    public static final String USERS_FILE = "data/users.dat";


    // ----------------------------------------------------------------
    // GUI COLOURS  (used by Swing classes for the dark theme)
    // ----------------------------------------------------------------

    // Dark background for panels and frames
    public static final java.awt.Color COLOR_BG_DARK     = new java.awt.Color(30,  30,  40);
    // Slightly lighter background for input areas
    public static final java.awt.Color COLOR_BG_MEDIUM   = new java.awt.Color(45,  45,  60);
    // Accent colour for buttons and borders
    public static final java.awt.Color COLOR_ACCENT      = new java.awt.Color(88, 101, 242);
    // Foreground text colour
    public static final java.awt.Color COLOR_FG_TEXT     = new java.awt.Color(220, 220, 230);
    // Dimmer text for labels / hints
    public static final java.awt.Color COLOR_FG_DIM      = new java.awt.Color(140, 140, 160);
    // Green dot for "online" indicator
    public static final java.awt.Color COLOR_ONLINE_DOT  = new java.awt.Color(67, 181, 129);
    // Red for error messages
    public static final java.awt.Color COLOR_ERROR       = new java.awt.Color(240,  71,  71);
    // Green for success messages
    public static final java.awt.Color COLOR_SUCCESS     = new java.awt.Color(67, 181, 129);


    // ----------------------------------------------------------------
    // MISC
    // ----------------------------------------------------------------

    /** Application title shown in window title bars. */
    public static final String APP_TITLE = "SecureChat";

    /**
     * Private constructor — prevents anyone from creating an instance
     * of this class with  new Constants().
     * There is no reason to instantiate a constants-only class.
     */
    private Constants() {}
}
