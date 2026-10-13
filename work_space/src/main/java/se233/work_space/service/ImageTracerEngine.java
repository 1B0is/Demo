package se233.work_space.service;

import javafx.scene.image.Image;
import se233.work_space.exception.CorruptedImageException;
import se233.work_space.exception.TracerNotFoundException;
import se233.work_space.model.AppSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;

/**
 * ImageTracerEngine — Thin wrapper for "convert 1 image → save SVG".
 * Used during batch export; each thread calls convertAndSave().
 */
public class ImageTracerEngine {

    private static final VectorEngine engine = new VectorEngine();

    public static Image generatePreview(File imageFile, String detail, int colors, boolean removeBg)
            throws TracerNotFoundException, CorruptedImageException {
        try {
            return new Image(new FileInputStream(imageFile));
        } catch (Exception e) {
            throw new CorruptedImageException("Cannot process the image file.");
        }
    }

    /**
     * Converts 1 image → SVG using its own AppSettings, saves it into outputDir.
     */
    public static void convertAndSave(File inputFile, File outputDir, AppSettings settings) throws Exception {
        // 1. Trace directly using the image's specific settings
        VectorEngine.TraceResult result = engine.trace(inputFile, settings);

        // 2. Write SVG to disk
        String outputFileName = inputFile.getName().replaceFirst("[.][^.]+$", "") + "_vectorized.svg";
        File outputFile = new File(outputDir, outputFileName);

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(result.svg());
        }
    }
}