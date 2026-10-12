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

    public static void startBatchProcess(
            List<File> imageFiles,
            File outputDir,
            String detail,
            int colors,
            boolean removeBg,
            Consumer<Double> onProgressUpdate,
            Runnable onComplete) {

        Task<Void> exportTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int totalFiles = imageFiles.size();
                AtomicInteger processedFiles = new AtomicInteger(0);

                // เช็คเงื่อนไข Parallel ตามโจทย์: Detail = Medium, Colors = 2 ถึง 5
                boolean isParallel = detail.equals("Medium") && colors > 0 && colors <= 5;

                if (isParallel) {
                    // ประมวลผลแบบขนาน (Parallel)
                    ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

                    // ตัวอย่างสำหรับบล็อก else (ประมวลผลแบบทีละรูป)
                    for (File file : imageFiles) {
                        try {
                            // เพิ่มบรรทัดนี้เพื่อเช็คว่ามันเริ่มรันหรือยัง
                            System.out.println("⏳ กำลังเริ่มแปลงไฟล์: " + file.getName() + " (อาจใช้เวลาสักพัก...)");

                            ImageTracerEngine.convertAndSave(file, outputDir, detail, colors, removeBg);

                            // เพิ่มบรรทัดนี้เพื่อเช็คว่าเซฟเสร็จไหม
                            System.out.println("✅ เซฟไฟล์สำเร็จลงโฟลเดอร์: " + file.getName());

                            updateProgressSafe(processedFiles.incrementAndGet(), totalFiles, onProgressUpdate);
                        } catch (Exception e) {
                            System.out.println("❌ เกิดข้อผิดพลาดกับไฟล์: " + file.getName());
                            e.printStackTrace();
                        }
                    }
                    executor.shutdown();
                    executor.awaitTermination(1, TimeUnit.HOURS);

                } else {
                    // ประมวลผลแบบทีละรูป (Sequential)
                    for (File file : imageFiles) {
                        try {
                            ImageTracerEngine.convertAndSave(file, outputDir, detail, colors, removeBg);
                            updateProgressSafe(processedFiles.incrementAndGet(), totalFiles, onProgressUpdate);
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

    // เมธอดสำหรับอัปเดต Progress Bar บน UI
    private static void updateProgressSafe(int current, int total, Consumer<Double> onProgressUpdate) {
        double progress = (double) current / total;
        Platform.runLater(() -> onProgressUpdate.accept(progress));
    }


}