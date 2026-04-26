import java.io.*;
import java.util.*;

/**
 * Encrypt Worker Task — Encrypts one text chunk for distributed encryption.
 *
 * Supported methods:
 * -caesar <shift> Caesar cipher (shift 0–25)
 * -vigenere <key> Vigenère cipher (alphabetic keyword)
 *
 * Usage:
 * java -jar EncryptWorker.jar -caesar <shift> <chunk_file>
 * java -jar EncryptWorker.jar -vigenere <key> <chunk_file>
 *
 * Output: Result.txt (encrypted content of the chunk)
 */
public class EncryptWorker {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java -jar EncryptWorker.jar <-caesar|-vigenere> <key/shift> <chunk_file>");
            System.exit(1);
        }

        String method = args[0]; // -caesar or -vigenere
        String key = args[1]; // shift value or keyword
        String chunkFile = args[2];

        try {
            System.out.println("[EncryptWorker] Method    : " + method);
            System.out.println("[EncryptWorker] Key/Shift : " + key);
            System.out.println("[EncryptWorker] Chunk     : " + chunkFile);

            // Read chunk
            List<String> lines = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(chunkFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                }
            }

            // Encrypt each line
            List<String> encryptedLines = new ArrayList<>();
            for (String line : lines) {
                String enc;
                switch (method) {
                    case "-caesar":
                        int shift = Integer.parseInt(key);
                        enc = caesarCipher(line, shift);
                        break;
                    case "-vigenere":
                        enc = vigenereCipher(line, key);
                        break;
                    default:
                        System.err.println("[EncryptWorker] Unknown method: " + method);
                        System.exit(1);
                        return;
                }
                encryptedLines.add(enc);
            }

            // Write result
            try (PrintWriter pw = new PrintWriter(new FileWriter("Result.txt"))) {
                for (String line : encryptedLines) {
                    pw.println(line);
                }
            }

            System.out.println("[EncryptWorker] Chunk encrypted successfully (" + lines.size() + " line(s)).");

        } catch (Exception e) {
            System.err.println("[EncryptWorker] Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Caesar Cipher — shifts each letter by <shift> positions
    // Example: shift=3 → A→D, B→E, z→c
    // ─────────────────────────────────────────────────────────────
    private static String caesarCipher(String text, int shift) {
        // Normalise shift to 0-25 range (handles negatives too)
        shift = ((shift % 26) + 26) % 26;
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isUpperCase(c)) {
                result.append((char) ('A' + (c - 'A' + shift) % 26));
            } else if (Character.isLowerCase(c)) {
                result.append((char) ('a' + (c - 'a' + shift) % 26));
            } else {
                result.append(c); // digits, spaces, punctuation unchanged
            }
        }
        return result.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // Vigenère Cipher — uses a repeating keyword to shift letters
    // Example: key="KEY" → A→K, B→O, C→C (shifts 10, 4, 24 repeating)
    // Only alphabetic characters are shifted; others are kept as-is.
    // The key index advances ONLY on alphabetic characters.
    // ─────────────────────────────────────────────────────────────
    private static String vigenereCipher(String text, String key) {
        if (key == null || key.isEmpty()) {
            return text; // no key → no change
        }
        key = key.toUpperCase();
        StringBuilder result = new StringBuilder();
        int keyIndex = 0;

        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                int shift = key.charAt(keyIndex % key.length()) - 'A';
                if (Character.isUpperCase(c)) {
                    result.append((char) ('A' + (c - 'A' + shift) % 26));
                } else {
                    result.append((char) ('a' + (c - 'a' + shift) % 26));
                }
                keyIndex++;
            } else {
                result.append(c); // non-letter unchanged
            }
        }
        return result.toString();
    }
}
