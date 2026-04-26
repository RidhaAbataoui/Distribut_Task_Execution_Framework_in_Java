import java.io.*;
import java.util.*;

/**
 * Encrypt Master Task — Handles SPLIT and MERGE for distributed text
 * encryption.
 *
 * Supported encryption methods (passed as option to the worker):
 * -caesar <shift> Caesar cipher (letter shift)
 * -vigenere <key> Vigenère cipher (keyword-based)
 *
 * Usage:
 * java -jar EncryptMaster.jar split <numSlaves> <-caesar|-vigenere>
 * <key_or_shift> <input_file>
 * java -jar EncryptMaster.jar merge <result_files...>
 *
 * Split strategy: divide input file into roughly equal line-groups,
 * one chunk per slave. Each slave encrypts its chunk independently.
 * Merge strategy: concatenate chunk results in order → encrypted.txt
 */
public class EncryptMaster {

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];
        try {
            switch (command) {
                case "split":
                    split(args);
                    break;
                case "merge":
                    merge(args);
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("[EncryptMaster] Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SPLIT — divide input file into line-based chunks
    // args: split <numSlaves> <-caesar|-vigenere> <key> <inputFile>
    // ─────────────────────────────────────────────────────────────
    private static void split(String[] args) throws IOException {
        if (args.length < 5) {
            System.err.println("[EncryptMaster] Usage: split <numSlaves> <-caesar|-vigenere> <key> <inputFile>");
            System.exit(1);
        }

        int numSlaves = Integer.parseInt(args[1]);
        String method = args[2]; // -caesar or -vigenere
        String key = args[3]; // shift number or keyword
        String inputFile = args[4];

        System.out.println("[EncryptMaster] Method  : " + method + " (key/shift = " + key + ")");
        System.out.println("[EncryptMaster] Splitting '" + inputFile + "' into " + numSlaves + " chunk(s)...");

        // Read all lines
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }

        int totalLines = lines.size();
        if (totalLines == 0) {
            // Edge case: empty file — create one empty chunk
            try (PrintWriter pw = new PrintWriter(new FileWriter("chunk_0.txt"))) {
                pw.println("");
            }
            System.out.println("[EncryptMaster] Warning: empty input file. Created chunk_0.txt.");
            return;
        }

        int linesPerChunk = Math.max(1, (int) Math.ceil((double) totalLines / numSlaves));
        int chunkIndex = 0;
        int lineIndex = 0;

        while (lineIndex < totalLines) {
            int end = Math.min(lineIndex + linesPerChunk, totalLines);
            String chunkFile = "chunk_" + chunkIndex + ".txt";

            try (PrintWriter pw = new PrintWriter(new FileWriter(chunkFile))) {
                for (int i = lineIndex; i < end; i++) {
                    pw.println(lines.get(i));
                }
            }

            System.out.println("[EncryptMaster] Created " + chunkFile
                    + " (lines " + lineIndex + "–" + (end - 1) + ")");
            lineIndex = end;
            chunkIndex++;
        }

        System.out.println("[EncryptMaster] Split complete — " + chunkIndex + " chunk(s) created.");
    }

    // ─────────────────────────────────────────────────────────────
    // MERGE — concatenate partial results into encrypted.txt
    // args: merge <result_files...>
    // ─────────────────────────────────────────────────────────────
    private static void merge(String[] args) throws IOException {
        int count = args.length - 1;
        System.out.println("[EncryptMaster] Merging " + count + " result(s) into encrypted.txt...");

        try (PrintWriter pw = new PrintWriter(new FileWriter("encrypted.txt"))) {
            for (int i = 1; i < args.length; i++) {
                File f = new File(args[i]);
                if (!f.exists()) {
                    System.err.println("[EncryptMaster] Warning: result file not found: " + args[i]);
                    continue;
                }
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        pw.println(line);
                    }
                }
            }
        }

        System.out.println("[EncryptMaster] Merge complete. Output: encrypted.txt");
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar EncryptMaster.jar split <numSlaves> <-caesar|-vigenere> <key> <inputFile>");
        System.out.println("  java -jar EncryptMaster.jar merge <result_files...>");
    }
}
