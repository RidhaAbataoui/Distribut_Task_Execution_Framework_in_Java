import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Filter Master Task — Handles SPLIT and MERGE for distributed image filtering.
 *
 * Usage:
 * java -jar FilterMaster.jar split <numSlaves> <-gray|-invert|-sepia>
 * <image_file>
 * java -jar FilterMaster.jar merge <result_files...>
 */
public class FilterMaster {

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

    private static void split(String[] args) throws IOException {
        if (args.length < 4) {
            printUsage();
            System.exit(1);
        }

        int numSlaves = Integer.parseInt(args[1]);
        String filter = args[2];
        String inputFile = args[3];

        System.out.println("[FilterMaster] Operation : " + filter);
        System.out.println("[FilterMaster] Splitting image " + inputFile + " into " + numSlaves + " chunks...");

        BufferedImage image = ImageIO.read(new File(inputFile));
        if (image == null) {
            throw new IOException("Could not read image: " + inputFile);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int rowsPerChunk = height / numSlaves;
        int remainder = height % numSlaves;

        int currentY = 0;
        for (int i = 0; i < numSlaves; i++) {
            int chunkHeight = rowsPerChunk + (i < remainder ? 1 : 0);
            if (currentY >= height)
                break;

            BufferedImage chunk = image.getSubimage(0, currentY, width, chunkHeight);
            String chunkFile = "chunk_" + i + ".png";
            ImageIO.write(chunk, "png", new File(chunkFile));

            System.out.println("[FilterMaster] Created " + chunkFile + " (rows " + currentY + " to "
                    + (currentY + chunkHeight - 1) + ")");
            currentY += chunkHeight;
        }

        System.out.println("[FilterMaster] Split complete.");
    }

    private static void merge(String[] args) throws IOException {
        System.out.println("[FilterMaster] Merging " + (args.length - 1) + " chunks...");

        List<BufferedImage> chunks = new ArrayList<>();
        int totalHeight = 0;
        int width = 0;

        for (int i = 1; i < args.length; i++) {
            BufferedImage chunk = ImageIO.read(new File(args[i]));
            if (chunk == null) {
                throw new IOException("Could not read chunk: " + args[i]);
            }
            chunks.add(chunk);
            totalHeight += chunk.getHeight();
            width = Math.max(width, chunk.getWidth());
        }

        BufferedImage result = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();

        int currentY = 0;
        for (BufferedImage chunk : chunks) {
            g.drawImage(chunk, 0, currentY, null);
            currentY += chunk.getHeight();
        }
        g.dispose();

        ImageIO.write(result, "png", new File("Result.png"));
        System.out.println("[FilterMaster] Merge complete. Saved to Result.png");
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar FilterMaster.jar split <numSlaves> <-gray|-invert|-sepia> <image_file>");
        System.out.println("  java -jar FilterMaster.jar merge <result_files...>");
    }
}
