package se233.work_space.service;

import javafx.scene.image.Image;
import se233.work_space.exception.CorruptedImageException;
import se233.work_space.exception.TracerNotFoundException;

import java.io.File;
import java.io.FileInputStream;

public class ImageTracerEngine {

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

    // เมธอดสำหรับแปลงและเซฟเป็นไฟล์เวกเตอร์จริงๆ
    public static void convertAndSave(File inputFile, File outputDir, String detail, int colors, boolean removeBg) throws Exception {
        // จำลองเวลาการประมวลผล (Processing time)
        Thread.sleep(1500);

        // สร้างไฟล์ SVG เปล่าๆ จำลองเป็นผลลัพธ์
        String outputFileName = inputFile.getName().replaceFirst("[.][^.]+$", "") + "_vectorized.svg";
        File outputFile = new File(outputDir, outputFileName);
        outputFile.createNewFile();
    }
}