package util;

/**
 * MessageProtocol.java
 * --------------------
 * Translator between Java objects and the plain-text strings that
 * travel across the TCP socket.
 *
 * PROTOCOL (each message is one '\n'-terminated line, fields separated by '|')
 * ─────────────────────────────────────────────────────────────────────────────
 * Client → Server:
 *   LOGIN    |username|sha256HashHex
 *   REGISTER |username|sha256HashHex
 *   MESSAGE  |recipient|encryptedBase64
 *   USER_LIST
 *   LOGOUT
 *
 * Server → Client:
 *   SUCCESS|detail
 *   FAILURE|reason
 *   INCOMING|sender|encryptedBase64
 *   ONLINE_USERS|user1,user2,...
 *   SERVER_SHUTDOWN
 *
 * NOTE: Salt is no longer part of the wire protocol. The server stores
 * the SHA-256 hash directly (no per-user salt). This keeps the protocol
 * simple and consistent between registration and login.
 */
public class MessageProtocol {

    // ----------------------------------------------------------------
    // BUILD methods  (Java → wire string)
    // ----------------------------------------------------------------

    /**
     * Build a LOGIN request.
     *   Wire format:  "LOGIN|username|sha256HashHex"
     *
     * @param username       the username
     * @param passwordHashHex SHA-256 hash of the password, hex-encoded
     * @return wire string
     */
    public static String buildLogin(String username, String passwordHashHex) {
        return Constants.CMD_LOGIN
                + Constants.SEPARATOR + username
                + Constants.SEPARATOR + passwordHashHex;
    }

    /**
     * Build a REGISTER request.
     *   Wire format:  "REGISTER|username|sha256HashHex"
     *
     * @param username        chosen username
     * @param passwordHashHex SHA-256 hash of the password, hex-encoded
     * @return wire string
     */
    public static String buildRegister(String username, String passwordHashHex) {
        return Constants.CMD_REGISTER
                + Constants.SEPARATOR + username
                + Constants.SEPARATOR + passwordHashHex;
    }

    /**
     * Build a MESSAGE request (client sending a private message).
     *
     * @param recipient      username of the intended recipient
     * @param encryptedBody  AES-GCM encrypted message, Base64-encoded
     * @return  "MESSAGE|recipient|encryptedBody"
     */
    public static String buildMessage(String recipient, String encryptedBody) {
        return Constants.CMD_MESSAGE
                + Constants.SEPARATOR + recipient
                + Constants.SEPARATOR + encryptedBody;
    }

    /**
     * Build a USER_LIST request (client asking for the online-user list).
     *
     * @return  "USER_LIST"
     */
    public static String buildUserListRequest() {
        return Constants.CMD_USER_LIST;
    }

    /**
     * Build a LOGOUT request.
     *
     * @return  "LOGOUT"
     */
    public static String buildLogout() {
        return Constants.CMD_LOGOUT;
    }

    // ----------------------------------------------------------------
    // Server-side BUILD methods  (server → client)
    // ----------------------------------------------------------------

    /**
     * Build a SUCCESS response.
     *
     * @param detail  a human-readable detail string, e.g. "Login successful"
     * @return  "SUCCESS|detail"
     */
    public static String buildSuccess(String detail) {
        return Constants.RESP_SUCCESS + Constants.SEPARATOR + detail;
    }

    /**
     * Build a FAILURE response.
     *
     * @param reason  a human-readable reason, e.g. "Invalid username or password"
     * @return  "FAILURE|reason"
     */
    public static String buildFailure(String reason) {
        return Constants.RESP_FAILURE + Constants.SEPARATOR + reason;
    }

    /**
     * Build an INCOMING message (server pushing a message to a recipient).
     *
     * @param sender         username of the message author
     * @param encryptedBody  the encrypted message body, Base64-encoded
     * @return  "INCOMING|sender|encryptedBody"
     */
    public static String buildIncoming(String sender, String encryptedBody) {
        return Constants.RESP_INCOMING
                + Constants.SEPARATOR + sender
                + Constants.SEPARATOR + encryptedBody;
    }

    /**
     * Build an ONLINE_USERS response.
     *
     * @param userListCsv  comma-separated list of usernames, e.g. "Krishna,Rahul,Amit"
     * @return  "ONLINE_USERS|Krishna,Rahul,Amit"
     */
    public static String buildOnlineUsers(String userListCsv) {
        return Constants.RESP_ONLINE_USERS + Constants.SEPARATOR + userListCsv;
    }

    /**
     * Build a SERVER_SHUTDOWN notification.
     *
     * @return  "SERVER_SHUTDOWN"
     */
    public static String buildServerShutdown() {
        return Constants.RESP_SERVER_SHUTDOWN;
    }

    // ----------------------------------------------------------------
    // PARSE methods  (wire string → parts array)
    // ----------------------------------------------------------------

    /**
     * Split a raw protocol line into its fields.
     *
     * We use a limit of 3 so that the message body (which may itself
     * contain '|' if the Base64 encoder ever produced one, which it
     * won't for standard Base64, but defensive coding is good) is
     * never split accidentally.
     *
     * Example:
     *   input:  "MESSAGE|Rahul|U2FsdGVkX1+abc123..."
     *   output: ["MESSAGE", "Rahul", "U2FsdGVkX1+abc123..."]
     *
     * @param rawLine  the raw line received from the socket (without trailing \n)
     * @return         array of String fields
     */
    public static String[] parse(String rawLine) {
        if (rawLine == null) {
            return new String[0];
        }
        // Trim to remove any accidental whitespace or \r (Windows line endings)
        return rawLine.trim().split(Constants.SEPARATOR_REGEX, 4);
    }

    /**
     * Extract the command (the first field) from a raw line.
     *
     * @param rawLine  raw protocol line
     * @return         the command string, e.g. "LOGIN", or empty string if invalid
     */
    public static String getCommand(String rawLine) {
        String[] parts = parse(rawLine);
        if (parts.length == 0) return "";
        return parts[0];
    }

    /**
     * Validate that a parsed message has at least the expected number of fields.
     *
     * @param parts    result of parse()
     * @param expected minimum number of fields required
     * @return         true if parts.length >= expected, false otherwise
     */
    public static boolean hasFields(String[] parts, int expected) {
        return parts != null && parts.length >= expected;
    }

    // Private constructor — utility class, never instantiated
    private MessageProtocol() {}
}
