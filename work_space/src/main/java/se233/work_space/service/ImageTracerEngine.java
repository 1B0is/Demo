package se233.work_space.service;

import javafx.scene.image.Image;
import se233.work_space.exception.CorruptedImageException;
import se233.work_space.exception.TracerNotFoundException;
import se233.work_space.model.AppSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;

public class ImageTracerEngine {

    // ประกาศ VectorEngine สำหรับใช้แปลงไฟล์
    private static final VectorEngine engine = new VectorEngine();

    // เมธอดสำหรับสร้างภาพพรีวิวส่งให้ UI (อ้างอิงจากที่คุณเรียกใช้ใน MainController)
    public static Image generatePreview(File imageFile, String detail, int colors, boolean removeBg)
            throws TracerNotFoundException, CorruptedImageException {

        try {
            // ในโปรเจกต์จริง ต้องรัน CommandLine (เช่น Potrace) ตรงนี้
            // แต่เพื่อการพรีวิว ให้โหลดภาพเดิมขึ้นมาแสดงก่อน
            return new Image(new FileInputStream(imageFile));
        } catch (Exception e) {
            throw new CorruptedImageException("Cannot process the image file.");
        }
    }

    // ✅ เพิ่มพารามิเตอร์ flipH, flipV
    public static void convertAndSave(File inputFile, File outputDir, String detail, int colors,
                                      boolean removeBg, boolean flipH, boolean flipV) throws Exception {
        // 1. แปลงค่าพารามิเตอร์ที่รับมาให้อยู่ในรูปแบบ AppSettings
        AppSettings settings = new AppSettings();

        if (detail != null) {
            settings.setDetailLevel(AppSettings.DetailLevel.valueOf(detail.toUpperCase()));
        } else {
            settings.setDetailLevel(AppSettings.DetailLevel.MEDIUM);
        }
        // เช็คว่าผู้ใช้เลือก Unlimited (-1) หรือ Custom (มีจำนวนสี)
        if (colors > 0) {
            settings.setColorMode(AppSettings.ColorMode.CUSTOM);
            settings.setColorCount(colors);
        } else {
            settings.setColorMode(AppSettings.ColorMode.UNLIMITED);
        }
        settings.setRemoveBackground(removeBg);
        settings.setFlipHorizontal(flipH);   // ✅ เพิ่ม
        settings.setFlipVertical(flipV);     // ✅ เพิ่ม

        // 2. ส่งภาพและการตั้งค่าไปให้ VectorEngine ประมวลผลเป็น SVG
        VectorEngine.TraceResult result = engine.trace(inputFile, settings);

        // 3. กำหนดชื่อไฟล์และเขียน String SVG ที่ได้ลงในไฟล์ปลายทาง
        String outputFileName = inputFile.getName().replaceFirst("[.][^.]+$", "") + "_vectorized.svg";
        File outputFile = new File(outputDir, outputFileName);

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(result.svg());
        }
    }
}