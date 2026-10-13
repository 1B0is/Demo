package se233.work_space.service;

import se233.work_space.exception.InvalidZipException;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZipProcessor — Extracts a user-dropped .zip file and keeps only image files (.jpg/.jpeg/.png).
 * Extraction goes to an OS temp directory.
 */
public class ZipProcessor {

    /**
     * Extracts a zip file and returns the list of image files found inside.
     * @throws InvalidZipException if the zip cannot be opened or read
     */
    public static List<File> extractFiles(File zipFile) throws InvalidZipException {
        List<File> extractedImages = new ArrayList<>();

        try {
            // Create an OS temp dir to hold the extracted files
            Path tempDir = Files.createTempDirectory("vector_unzip_");

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry zipEntry = zis.getNextEntry();

                while (zipEntry != null) {
                    String fileName = zipEntry.getName().toLowerCase();

                    // Only take image files, ignore directories
                    if (!zipEntry.isDirectory() &&
                            (fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".jpeg"))) {

                        // Use only the file name (strip inner path in zip) to prevent path traversal
                        File newFile = new File(tempDir.toFile(), new File(zipEntry.getName()).getName());

                        // Copy file data out of the zip
                        Files.copy(zis, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        extractedImages.add(newFile);
                    }
                    zipEntry = zis.getNextEntry();
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new InvalidZipException("Could not extract file: " + e.getMessage());
        }

        return extractedImages;
    }
}