import java.io.*;

/**
 * Caesar Cipher Encryption Task
 *
 * Usage:
 * java -jar Encrypt.jar <shift> <input_file>
 *
 * Example:
 * java -jar Encrypt.jar 3 message.txt
 *
 * Output: encrypted.txt
 */
public class Encrypt {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -jar Encrypt.jar <shift> <input_file>");
            System.exit(1);
        }

        int shift = Integer.parseInt(args[0]);
        String inputFile = args[1];

        try {
            System.out.println("Reading file: " + inputFile);
            String content = readFile(inputFile);

            System.out.println("Applying Caesar cipher with shift = " + shift + "...");
            String encrypted = caesarCipher(content, shift);

            writeFile("encrypted.txt", encrypted);

            System.out.println("Original:  " + content.trim());
            System.out.println("Encrypted: " + encrypted.trim());
            System.out.println("Result written to encrypted.txt");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String caesarCipher(String text, int shift) {
        StringBuilder result = new StringBuilder();
        // Normalize shift to 0-25 range
        shift = ((shift % 26) + 26) % 26;

        for (char c : text.toCharArray()) {
            if (Character.isUpperCase(c)) {
                result.append((char) ('A' + (c - 'A' + shift) % 26));
            } else if (Character.isLowerCase(c)) {
                result.append((char) ('a' + (c - 'a' + shift) % 26));
            } else {
                result.append(c); // non-alphabetic characters unchanged
            }
        }
        return result.toString();
    }

    private static String readFile(String filename) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static void writeFile(String filename, String content) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.print(content);
        }
    }
}
