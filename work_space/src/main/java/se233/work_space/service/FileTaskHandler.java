package se233.work_space.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import java.io.File;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * FileTaskHandler — Handles batch export in the background.
 * Chooses parallel (ThreadPool) or sequential based on:
 *   parallel when detail = Medium AND colors is 2..5 (per project spec).
 */
public class FileTaskHandler {

    /**
     * Starts exporting all images.
     * @param flipH, flipV MUST be forwarded to convertAndSave, otherwise SVG won't flip.
     */
    public static void startBatchProcess(
            List<File> imageFiles,
            File outputDir,
            String detail,
            int colors,
            boolean removeBg,
            boolean flipH,
            boolean flipV,
            Consumer<Double> onProgressUpdate,   // Progress bar callback
            Runnable onComplete) {                // Completion callback

        Task<Void> exportTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int totalFiles = imageFiles.size();
                AtomicInteger processedFiles = new AtomicInteger(0);

                // Parallel condition per spec: Medium + Custom 2..5 colors
                boolean isParallel = detail.equals("Medium") && colors > 2 && colors <= 5;

                if (isParallel) {
                    // ---- Parallel mode ----
                    System.out.println("🚀 Parallel mode working .....");
                    ExecutorService executor = Executors.newFixedThreadPool(
                            Runtime.getRuntime().availableProcessors());

                    for (File file : imageFiles) {
                        executor.submit(() -> {
                            try {
                                ImageTracerEngine.convertAndSave(
                                        file, outputDir, detail, colors, removeBg, flipH, flipV);
                                updateProgressSafe(processedFiles.incrementAndGet(),
                                        totalFiles, onProgressUpdate);
                            } catch (Exception e) {
                                System.out.println("❌ Issues on files: " + file.getName());
                                e.printStackTrace();
                            }
                        });
                    }
                    executor.shutdown();
                    executor.awaitTermination(1, TimeUnit.HOURS);

                } else {
                    // ---- Sequential mode ----
                    for (File file : imageFiles) {
                        try {
                            ImageTracerEngine.convertAndSave(
                                    file, outputDir, detail, colors, removeBg, flipH, flipV);
                            updateProgressSafe(processedFiles.incrementAndGet(),
                                    totalFiles, onProgressUpdate);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                return null;
            }

            @Override
            protected void succeeded() {
                // onComplete must run on JavaFX thread (it touches UI)
                Platform.runLater(onComplete);
            }
        };
        new Thread(exportTask).start();
    }

    /** Updates the progress bar on JavaFX thread (thread-safe) */
    private static void updateProgressSafe(int current, int total, Consumer<Double> onProgressUpdate) {
        double progress = (double) current / total;
        Platform.runLater(() -> onProgressUpdate.accept(progress));
    }
}