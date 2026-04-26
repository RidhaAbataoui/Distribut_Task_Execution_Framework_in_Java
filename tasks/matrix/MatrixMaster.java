import java.io.*;
import java.util.*;

/**
 * Matrix Master Task — Handles SPLIT and MERGE for distributed matrix
 * operations.
 *
 * Supported operations: -product (multiply), -sum (add)
 *
 * Usage:
 * java -jar MatrixMaster.jar split <numSlaves> [-product|-sum] <fileA> <fileB>
 * java -jar MatrixMaster.jar merge <result_files...>
 *
 * Chunk file format:
 * ROWS_OF_A (the slice assigned to this slave)
 * ---
 * B_PART (full B for -product, matching rows of B for -sum)
 */
public class MatrixMaster {

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
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SPLIT
    // args: split <numSlaves> [-product|-sum] <fileA> <fileB>
    // ─────────────────────────────────────────────────────────────
    private static void split(String[] args) throws IOException {
        int numSlaves = Integer.parseInt(args[1]);

        // Optional operation flag (default: -product)
        String operation = "-product";
        int fileStart = 2;
        if (args.length > 2 && args[2].startsWith("-")) {
            operation = args[2];
            fileStart = 3;
        }

        if (args.length < fileStart + 2) {
            System.err.println("Missing input files.");
            printUsage();
            System.exit(1);
        }

        String fileA = args[fileStart];
        String fileB = args[fileStart + 1];

        System.out.println("[MatrixMaster] Operation  : " + operation);
        System.out.println("[MatrixMaster] Splitting for " + numSlaves + " slave(s)...");

        int[][] matrixA = readMatrix(fileA);
        int[][] matrixB = readMatrix(fileB);

        System.out.println("[MatrixMaster] Matrix A   : " + matrixA.length + " row(s) × "
                + (matrixA.length > 0 ? matrixA[0].length : 0) + " col(s)");
        System.out.println("[MatrixMaster] Matrix B   : " + matrixB.length + " row(s) × "
                + (matrixB.length > 0 ? matrixB[0].length : 0) + " col(s)");

        int totalRows = matrixA.length;
        int rowsPerSlave = totalRows / numSlaves;
        int remainder = totalRows % numSlaves;

        int startRow = 0;
        int chunksCreated = 0;

        for (int i = 0; i < numSlaves; i++) {
            int count = rowsPerSlave + (i < remainder ? 1 : 0);
            if (startRow >= totalRows)
                break;

            int endRow = Math.min(startRow + count - 1, totalRows - 1);
            String chunkFile = "chunk_" + i + ".txt";

            try (PrintWriter pw = new PrintWriter(new FileWriter(chunkFile))) {
                // Write assigned rows of A
                for (int r = startRow; r <= endRow; r++) {
                    pw.println(rowToString(matrixA[r]));
                }
                pw.println("---");

                if ("-sum".equals(operation)) {
                    // ── SUM: each slave gets MATCHING rows of B ──
                    // (A[startRow..endRow] + B[startRow..endRow])
                    for (int r = startRow; r <= endRow; r++) {
                        if (r < matrixB.length) {
                            pw.println(rowToString(matrixB[r]));
                        }
                    }
                } else {
                    // ── PRODUCT: each slave gets FULL matrix B ──
                    // (partial_A × full_B)
                    for (int[] row : matrixB) {
                        pw.println(rowToString(row));
                    }
                }
            }

            // Print exactly which rows of A go to this slave
            StringBuilder rowPreview = new StringBuilder();
            for (int r = startRow; r <= endRow; r++) {
                rowPreview.append("  Slave ").append(i).append(" ← A[row ").append(r).append("] = [")
                        .append(rowToString(matrixA[r])).append("]\n");
            }
            System.out.print(rowPreview);
            System.out.println("[MatrixMaster] Created " + chunkFile
                    + " (slave " + i + " → A rows " + startRow + "–" + endRow + ")");
            startRow += count;
            chunksCreated++;
        }

        System.out.println("[MatrixMaster] Split complete — " + chunksCreated + " chunk(s) created.");
    }

    // ─────────────────────────────────────────────────────────────
    // MERGE — concatenates partial results into Result.txt
    // Works for both -product and -sum (slaves return rows)
    // ─────────────────────────────────────────────────────────────
    private static void merge(String[] args) throws IOException {
        int fileCount = args.length - 1;
        System.out.println("[MatrixMaster] Merging " + fileCount + " result(s)...");

        List<String> allRows = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            try (BufferedReader br = new BufferedReader(new FileReader(args[i]))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty())
                        allRows.add(line);
                }
            }
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter("Result.txt"))) {
            for (String row : allRows)
                pw.println(row);
        }

        System.out.println("[MatrixMaster] Result.txt — " + allRows.size() + " rows.");
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private static int[][] readMatrix(String filename) throws IOException {
        List<int[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                String[] parts = line.split("\\s+");
                int[] row = new int[parts.length];
                for (int i = 0; i < parts.length; i++)
                    row[i] = Integer.parseInt(parts[i]);
                rows.add(row);
            }
        }
        return rows.toArray(new int[0][]);
    }

    private static String rowToString(int[] row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.length; i++) {
            if (i > 0)
                sb.append(" ");
            sb.append(row[i]);
        }
        return sb.toString();
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar MatrixMaster.jar split <numSlaves> [-product|-sum] <fileA> <fileB>");
        System.out.println("  java -jar MatrixMaster.jar merge <result_files...>");
    }
}
