import java.io.*;
import java.util.*;

/**
 * Matrix Worker Task — Processes one chunk for distributed matrix
 * multiplication.
 *
 * Usage:
 * java -jar MatrixWorker.jar -product chunk_0.txt
 *
 * Input format (chunk file):
 * rows_of_A
 * ---
 * full_matrix_B
 *
 * Output: Result.txt (partial product — only the assigned rows)
 */
public class MatrixWorker {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -jar MatrixWorker.jar <-product|-sum> <chunk_file>");
            System.exit(1);
        }

        String operation = args[0];
        String chunkFile = args[1];

        try {
            System.out.println("[MatrixWorker] Processing chunk: " + chunkFile);

            // Parse chunk file: rows of A, then "---", then full B
            List<int[]> rowsA = new ArrayList<>();
            List<int[]> rowsB = new ArrayList<>();
            boolean readingB = false;

            try (BufferedReader br = new BufferedReader(new FileReader(chunkFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.equals("---")) {
                        readingB = true;
                        continue;
                    }
                    if (line.isEmpty())
                        continue;

                    String[] parts = line.split("\\s+");
                    int[] row = new int[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        row[i] = Integer.parseInt(parts[i]);
                    }
                    if (readingB)
                        rowsB.add(row);
                    else
                        rowsA.add(row);
                }
            }

            int[][] partA = rowsA.toArray(new int[0][]);
            int[][] matB = rowsB.toArray(new int[0][]);

            int[][] result;
            switch (operation) {
                case "-product":
                    System.out.println("[MatrixWorker] Computing partial product ("
                            + partA.length + " rows × " + matB[0].length + " cols)...");
                    result = multiply(partA, matB);
                    break;
                case "-sum":
                    System.out.println("[MatrixWorker] Computing partial sum...");
                    result = add(partA, matB);
                    break;
                default:
                    System.err.println("Unknown operation: " + operation);
                    System.exit(1);
                    return;
            }

            // Write partial result
            try (PrintWriter pw = new PrintWriter(new FileWriter("Result.txt"))) {
                for (int[] row : result) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < row.length; i++) {
                        if (i > 0)
                            sb.append(" ");
                        sb.append(row[i]);
                    }
                    pw.println(sb.toString());
                }
            }

            System.out.println("[MatrixWorker] Partial result written (" + result.length + " rows)");

        } catch (Exception e) {
            System.err.println("[MatrixWorker] Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int[][] multiply(int[][] a, int[][] b) {
        int rowsA = a.length, colsA = a[0].length;
        int colsB = b[0].length;

        int[][] result = new int[rowsA][colsB];
        for (int i = 0; i < rowsA; i++) {
            for (int j = 0; j < colsB; j++) {
                for (int k = 0; k < colsA; k++) {
                    result[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return result;
    }

    private static int[][] add(int[][] a, int[][] b) {
        int[][] result = new int[a.length][a[0].length];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                result[i][j] = a[i][j] + b[i][j];
            }
        }
        return result;
    }
}
