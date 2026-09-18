package security;

import util.Constants;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * EncryptionUtil.java
 * -------------------
 * Handles all message encryption and decryption using AES-GCM.
 *
 * ---------------------------------------------------------------
 * WHAT IS AES?
 * ---------------------------------------------------------------
 * Advanced Encryption Standard — the most widely used symmetric
 * encryption algorithm in the world.  "Symmetric" means the SAME
 * key is used to both encrypt and decrypt.
 *
 * We use a 256-bit key (the strongest AES variant).
 *
 * ---------------------------------------------------------------
 * WHAT IS GCM (Galois/Counter Mode)?
 * ---------------------------------------------------------------
 * GCM is the MODE in which AES operates.  It provides:
 *   1. CONFIDENTIALITY  — the message content is hidden
 *   2. INTEGRITY        — if the ciphertext is tampered with,
 *                         decryption will FAIL (throw an exception)
 *                         rather than silently return garbage
 *
 * This combination is called "Authenticated Encryption".
 * AES-GCM is the recommended mode for new applications.
 * Older modes like AES-CBC do not provide integrity protection.
 *
 * ---------------------------------------------------------------
 * WHAT IS AN IV / NONCE?
 * ---------------------------------------------------------------
 * IV = Initialisation Vector (also called a nonce = number used once).
 * It is a random 12-byte value generated FRESH for every single
 * encryption operation.
 *
 * WHY? — AES-GCM is a stream cipher.  If you encrypt two different
 * messages with the SAME key AND the SAME IV, an attacker can XOR
 * the ciphertexts and extract information about the plaintexts.
 * A unique IV per message prevents this completely.
 *
 * The IV does NOT need to be secret — we prepend it to the ciphertext
 * so the receiver can use it during decryption.
 *
 * ---------------------------------------------------------------
 * KEY MANAGEMENT IN THIS APPLICATION (important for interviews!)
 * ---------------------------------------------------------------
 * This application uses a SERVER-HELD SYMMETRIC KEY approach:
 *
 *   - One AES key is generated when the server first starts.
 *   - That key is saved to "data/chat.key" (Base64-encoded).
 *   - Every client receives this key from the server after
 *     successful login (sent over the socket connection).
 *   - All messages are encrypted with this shared key before
 *     being sent through the socket, and decrypted on receipt.
 *
 * WHAT THIS PROVIDES:
 *   Transport-level message confidentiality within the application.
 *   Messages travelling through the socket are not readable as
 *   plaintext even if the network traffic is captured.
 *
 * WHAT THIS DOES NOT PROVIDE:
 *   True end-to-end encryption.  The server holds the key and
 *   could theoretically decrypt messages in transit.
 *   True E2E would require each pair of clients to exchange keys
 *   directly (e.g., using Diffie-Hellman) without the server
 *   ever seeing the keys.  That is a more advanced architecture
 *   beyond the scope of this student project.
 *
 * This limitation is clearly documented and should be mentioned
 * honestly in an interview when discussing security trade-offs.
 *
 * ---------------------------------------------------------------
 * WIRE FORMAT
 * ---------------------------------------------------------------
 * encrypt() returns a single Base64 string with this layout:
 *
 *   [12 bytes IV][ciphertext + 16-byte GCM auth tag]
 *     → Base64 encode the whole thing
 *
 * decrypt() expects the same Base64 string, extracts the IV,
 * then decrypts+verifies the remainder.
 */
public class EncryptionUtil {

    // Algorithm identifiers used by the Java Cryptography Architecture (JCA)
    private static final String AES_ALGORITHM      = "AES";
    private static final String AES_GCM_CIPHER     = "AES/GCM/NoPadding";

    // Path where the server stores the generated AES key
    public static final String KEY_FILE_PATH       = "data/chat.key";

    // SecureRandom used for IV generation — same reasoning as in PasswordUtil
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();


    // ----------------------------------------------------------------
    // KEY MANAGEMENT
    // ----------------------------------------------------------------

    /**
     * Generate a new 256-bit AES key.
     * The server calls this ONCE when no key file exists yet.
     *
     * @return a new SecretKey
     */
    public static SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGORITHM);
            // 256-bit key — requires the JCE Unlimited Strength policy,
            // which is included by default in JDK 8u161+ and all JDK 9+
            keyGen.init(Constants.AES_KEY_SIZE, SECURE_RANDOM);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES not available: " + e.getMessage(), e);
        }
    }

    /**
     * Serialize a SecretKey to a Base64 string for file storage.
     *
     * @param key  the AES SecretKey
     * @return     Base64-encoded string of the raw key bytes
     */
    public static String keyToBase64(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Deserialize a SecretKey from a Base64 string (read from file or socket).
     *
     * @param base64Key  Base64-encoded key string
     * @return           reconstructed SecretKey
     */
    public static SecretKey base64ToKey(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
        // SecretKeySpec wraps raw key bytes into a proper SecretKey object
        return new SecretKeySpec(keyBytes, AES_ALGORITHM);
    }

    /**
     * Load the AES key from the key file.
     * If the file does not exist, generate a new key and save it.
     *
     * @return the application's AES SecretKey
     */
    public static SecretKey loadOrCreateKey() {
        java.io.File keyFile = new java.io.File(KEY_FILE_PATH);

        if (keyFile.exists()) {
            // Key file already exists — read and return it
            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(new java.io.FileReader(keyFile))) {
                String base64Key = reader.readLine();
                System.out.println("[EncryptionUtil] Loaded existing AES key from " + KEY_FILE_PATH);
                return base64ToKey(base64Key);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Could not read key file: " + e.getMessage(), e);
            }
        } else {
            // No key file — generate a new key and save it
            SecretKey key = generateKey();
            try {
                // Make sure the data/ directory exists
                keyFile.getParentFile().mkdirs();
                try (java.io.PrintWriter writer =
                             new java.io.PrintWriter(new java.io.FileWriter(keyFile))) {
                    writer.println(keyToBase64(key));
                }
                System.out.println("[EncryptionUtil] Generated new AES key and saved to " + KEY_FILE_PATH);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Could not save key file: " + e.getMessage(), e);
            }
            return key;
        }
    }


    // ----------------------------------------------------------------
    // ENCRYPTION
    // ----------------------------------------------------------------

    /**
     * Encrypt a plaintext message using AES-GCM.
     *
     * Steps:
     *   1. Generate a fresh 12-byte random IV
     *   2. Initialize AES-GCM cipher in ENCRYPT mode with key + IV
     *   3. Encrypt the plaintext → produces ciphertext (includes GCM auth tag)
     *   4. Prepend the IV to the ciphertext
     *   5. Base64-encode the whole thing for safe transmission as text
     *
     * @param plaintext  the original message (e.g. "Hello Rahul!")
     * @param key        the AES SecretKey
     * @return           Base64(IV + ciphertext)  ready to send over the socket
     * @throws Exception if encryption fails
     */
    public static String encrypt(String plaintext, SecretKey key) throws Exception {
        // Step 1: Generate a unique IV for this message
        byte[] iv = new byte[Constants.GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);  // Fill with random bytes

        // Step 2: Set up the cipher
        Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER);

        // GCMParameterSpec tells the cipher the tag length and the IV
        GCMParameterSpec parameterSpec = new GCMParameterSpec(
                Constants.GCM_TAG_LENGTH,  // authentication tag size in bits
                iv                          // the nonce / IV
        );

        // Initialize for encryption using our key and the GCM parameters
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        // Step 3: Encrypt the plaintext bytes
        // getBytes("UTF-8") converts the String to bytes using UTF-8 encoding,
        // which supports all Unicode characters including emoji etc.
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

        // Step 4: Combine IV + ciphertext into one byte array
        // Layout: [iv (12 bytes)][ciphertext (variable) + GCM tag (16 bytes)]
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        // Step 5: Base64-encode so the result is a safe printable string
        // (raw bytes would break our pipe-separated text protocol)
        return Base64.getEncoder().encodeToString(combined);
    }


    // ----------------------------------------------------------------
    // DECRYPTION
    // ----------------------------------------------------------------

    /**
     * Decrypt a message that was encrypted by encrypt().
     *
     * Steps:
     *   1. Base64-decode to get the raw bytes
     *   2. Extract the IV (first 12 bytes)
     *   3. Extract the ciphertext (remaining bytes)
     *   4. Initialize AES-GCM cipher in DECRYPT mode with key + IV
     *   5. Decrypt — GCM automatically verifies the auth tag
     *   6. Convert decrypted bytes back to a UTF-8 String
     *
     * If the ciphertext has been tampered with, step 5 throws
     * AEADBadTagException — we treat that as a security failure.
     *
     * @param encryptedBase64  the Base64 string produced by encrypt()
     * @param key              the same AES SecretKey used to encrypt
     * @return                 the original plaintext message
     * @throws Exception       if decryption fails (wrong key, tampered data, etc.)
     */
    public static String decrypt(String encryptedBase64, SecretKey key) throws Exception {
        // Step 1: Decode from Base64
        byte[] combined = Base64.getDecoder().decode(encryptedBase64.trim());

        // Step 2: Extract the IV from the first GCM_IV_LENGTH bytes
        byte[] iv = new byte[Constants.GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, iv.length);

        // Step 3: Extract the ciphertext (everything after the IV)
        int ciphertextLength = combined.length - Constants.GCM_IV_LENGTH;
        byte[] ciphertext = new byte[ciphertextLength];
        System.arraycopy(combined, Constants.GCM_IV_LENGTH, ciphertext, 0, ciphertextLength);

        // Step 4: Set up the cipher for decryption
        Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(
                Constants.GCM_TAG_LENGTH,
                iv
        );
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        // Step 5: Decrypt and verify the GCM authentication tag.
        // If someone modified the ciphertext in transit, this will throw
        // javax.crypto.AEADBadTagException — the message is rejected.
        byte[] plaintextBytes = cipher.doFinal(ciphertext);

        // Step 6: Convert bytes back to String
        return new String(plaintextBytes, "UTF-8");
    }


    // Private constructor — utility class, never instantiated
    private EncryptionUtil() {}
}
