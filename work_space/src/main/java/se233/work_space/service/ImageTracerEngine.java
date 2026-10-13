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

    /**
     * Creates a preview image (currently returns the original image — not used yet).
     */
    public static Image generatePreview(File imageFile, String detail, int colors, boolean removeBg)
            throws TracerNotFoundException, CorruptedImageException {
        try {
            return new Image(new FileInputStream(imageFile));
        } catch (Exception e) {
            throw new CorruptedImageException("Cannot process the image file.");
        }
    }

    /**
     * Converts 1 image → SVG, saves it into outputDir.
     * Output name: <original-name>_vectorized.svg
     */
    public static void convertAndSave(File inputFile, File outputDir, String detail, int colors,
                                      boolean removeBg, boolean flipH, boolean flipV) throws Exception {

        // 1. Convert parameters into AppSettings
        AppSettings settings = new AppSettings();

        if (detail != null) {
            settings.setDetailLevel(AppSettings.DetailLevel.valueOf(detail.toUpperCase()));
        } else {
            settings.setDetailLevel(AppSettings.DetailLevel.MEDIUM);
        }

        if (colors > 0) {
            settings.setColorMode(AppSettings.ColorMode.CUSTOM);
            settings.setColorCount(colors);
        } else {
            settings.setColorMode(AppSettings.ColorMode.UNLIMITED);
        }
        settings.setRemoveBackground(removeBg);
<<<<<<< Updated upstream
        settings.setFlipHorizontal(flipH);   // MUST be set, otherwise flip is lost on export
        settings.setFlipVertical(flipV);     // (bug we hit)
=======
        settings.setFlipHorizontal(flipH);
        settings.setFlipVertical(flipV);
>>>>>>> Stashed changes

        // 2. Trace
        VectorEngine.TraceResult result = engine.trace(inputFile, settings);

        // 3. Write SVG to disk
        String outputFileName = inputFile.getName().replaceFirst("[.][^.]+$", "") + "_vectorized.svg";
        File outputFile = new File(outputDir, outputFileName);

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(result.svg());
        }
    }
}