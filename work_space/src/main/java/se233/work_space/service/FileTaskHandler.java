package se233.work_space.service;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

public class FileTaskHandler {

    // ✅ เพิ่มพารามิเตอร์ flipH, flipV
    public static void startBatchProcess(
            List<File> imageFiles,
            File outputDir,
            String detail,
            int colors,
            boolean removeBg,
            boolean flipH,
            boolean flipV,
            Consumer<Double> onProgressUpdate,
            Runnable onComplete) {
        // ใช้ Task เพื่อป้องกันไม่ให้ UI หลักค้าง
        Task<Void> exportTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int totalFiles = imageFiles.size();
                AtomicInteger processedFiles = new AtomicInteger(0);

                // เช็คเงื่อนไข Parallel ตามโจทย์: Detail = Medium, Colors = 2 ถึง 5
                boolean isParallel = detail.equals("Medium") && colors > 2 && colors <= 5;

                if (isParallel) {
                    // ประมวลผลแบบขนาน (Parallel)
                    System.out.println("🚀 Parallel mode working .....");
                    ExecutorService executor = Executors.newFixedThreadPool(
                            Runtime.getRuntime().availableProcessors());

                    for (File file : imageFiles) {
                        executor.submit(() -> {
                            try {
                                System.out.println("⏳ Converting : " + file.getName());

                                // ✅ ส่ง flipH, flipV ต่อ
                                ImageTracerEngine.convertAndSave(
                                        file, outputDir, detail, colors, removeBg, flipH, flipV);

                                System.out.println("✅ Success saved: " + file.getName());
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
                    // ประมวลผลแบบทีละรูป (Sequential)
                    for (File file : imageFiles) {
                        try {
                            System.out.println("⏳ Converting : " + file.getName());

                            // ✅ ส่ง flipH, flipV ต่อ
                            ImageTracerEngine.convertAndSave(
                                    file, outputDir, detail, colors, removeBg, flipH, flipV);

                            System.out.println("✅ Done : " + file.getName());
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