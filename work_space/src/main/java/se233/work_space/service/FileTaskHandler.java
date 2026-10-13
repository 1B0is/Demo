package se233.work_space.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import se233.work_space.model.AppSettings;
import se233.work_space.model.ImageItem;

import java.io.File;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * FileTaskHandler — Handles batch export in the background using individual ImageItem settings.
 */
public class FileTaskHandler {

    public static void startBatchProcess(
            List<ImageItem> items,
            File outputDir,
            Consumer<Double> onProgressUpdate,   // Progress bar callback
            Runnable onComplete) {                // Completion callback

        Task<Void> exportTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int totalFiles = items.size();
                AtomicInteger processedFiles = new AtomicInteger(0);

                // Check parallel condition using the first item's settings as reference
                boolean isParallel = false;
                if (!items.isEmpty()) {
                    AppSettings ref = items.get(0).getSettings();
                    isParallel = ref.getDetailLevel() == AppSettings.DetailLevel.MEDIUM
                            && ref.getColorCount() >= 2
                            && ref.getColorCount() <= 5;
                }

                if (isParallel) {
                    // ---- Parallel mode ----
                    System.out.println("🚀 Parallel mode working .....");
                    ExecutorService executor = Executors.newFixedThreadPool(
                            Runtime.getRuntime().availableProcessors());

                    for (ImageItem item : items) {
                        executor.submit(() -> {
                            try {
                                File file = item.getSource();
                                AppSettings settingsSnapshot = item.getSettings().copy();

                                ImageTracerEngine.convertAndSave(file, outputDir, settingsSnapshot);
                                updateProgressSafe(processedFiles.incrementAndGet(),
                                        totalFiles, onProgressUpdate);
                            } catch (Exception e) {
                                System.out.println("❌ Issues on files: " + item.getName());
                                e.printStackTrace();
                            }
                        });
                    }
                    executor.shutdown();
                    executor.awaitTermination(1, TimeUnit.HOURS);

                } else {
                    // ---- Sequential mode ----
                    for (ImageItem item : items) {
                        try {
                            File file = item.getSource();
                            AppSettings settingsSnapshot = item.getSettings().copy();

                            ImageTracerEngine.convertAndSave(file, outputDir, settingsSnapshot);
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
                Platform.runLater(onComplete);
            }
        };
        new Thread(exportTask).start();
    }

    private static void updateProgressSafe(int current, int total, Consumer<Double> onProgressUpdate) {
        double progress = (double) current / total;
        Platform.runLater(() -> onProgressUpdate.accept(progress));
    }
}