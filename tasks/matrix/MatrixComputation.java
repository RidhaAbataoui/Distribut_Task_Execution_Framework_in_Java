import java.io.*;
import java.util.*;

/**
 * Matrix Computation Task
 *
 * Usage:
 * java -jar MatrixComputation.jar -product A.txt B.txt
 * java -jar MatrixComputation.jar -sum A.txt B.txt
 *
 * Input files format (space-separated values, one row per line):
 * 1 2 3
 * 4 5 6
 *
 * Output: Result.txt
 */
public class MatrixComputation {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java -jar MatrixComputation.jar <-product|-sum> <fileA> <fileB>");
            System.exit(1);
        }

        String operation = args[0];
        String fileA = args[1];
        String fileB = args[2];

        try {
            int[][] matrixA = readMatrix(fileA);
            int[][] matrixB = readMatrix(fileB);

            int[][] result;

            switch (operation) {
                case "-product":
                    System.out.println("Computing matrix product...");
                    result = multiply(matrixA, matrixB);
                    break;
                case "-sum":
                    System.out.println("Computing matrix sum...");
                    result = add(matrixA, matrixB);
                    break;
                default:
                    System.err.println("Unknown operation: " + operation);
                    System.exit(1);
                    return;
            }

            writeMatrix("Result.txt", result);
            System.out.println("Result written to Result.txt");
            printMatrix(result);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

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
                for (int i = 0; i < parts.length; i++) {
                    row[i] = Integer.parseInt(parts[i]);
                }
                rows.add(row);
            }
        }
        return rows.toArray(new int[0][]);
    }

    private static int[][] multiply(int[][] a, int[][] b) {
        int rowsA = a.length, colsA = a[0].length;
        int rowsB = b.length, colsB = b[0].length;

        if (colsA != rowsB) {
            throw new IllegalArgumentException(
                    "Cannot multiply: A columns (" + colsA + ") != B rows (" + rowsB + ")");
        }

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
        if (a.length != b.length || a[0].length != b[0].length) {
            throw new IllegalArgumentException("Matrices must have the same dimensions for addition.");
        }

        int[][] result = new int[a.length][a[0].length];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                result[i][j] = a[i][j] + b[i][j];
            }
        }
        return result;
    }

    private static void writeMatrix(String filename, int[][] matrix) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            for (int[] row : matrix) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0)
                        sb.append(" ");
                    sb.append(row[i]);
                }
                pw.println(sb.toString());
            }
        }
    }

    private static void printMatrix(int[][] matrix) {
        System.out.println("Result matrix:");
        for (int[] row : matrix) {
            StringBuilder sb = new StringBuilder("  ");
            for (int i = 0; i < row.length; i++) {
                if (i > 0)
                    sb.append("\t");
                sb.append(row[i]);
            }
            System.out.println(sb.toString());
        }
    }
}
