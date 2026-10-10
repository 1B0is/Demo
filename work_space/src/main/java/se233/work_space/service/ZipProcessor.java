package se233.work_space.service;

import se233.work_space.exception.InvalidZipException;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipProcessor {

    public static List<File> extractFiles(File zipFile) throws InvalidZipException {
        List<File> extractedImages = new ArrayList<>();

        try {
            // สร้างโฟลเดอร์ชั่วคราวสำหรับเก็บไฟล์ที่แตกออกมา
            Path tempDir = Files.createTempDirectory("vector_unzip_");

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry zipEntry = zis.getNextEntry();

                while (zipEntry != null) {
                    String fileName = zipEntry.getName().toLowerCase();

                    // คัดกรองเฉพาะไฟล์ที่เป็นรูปภาพ .jpg หรือ .png
                    if (!zipEntry.isDirectory() && (fileName.endsWith(".jpg") || fileName.endsWith(".png"))) {
                        // ป้องกันปัญหาการแตกไฟล์ที่ซ้อนโฟลเดอร์ลึกๆ โดยดึงมาแค่ชื่อไฟล์
                        File newFile = new File(tempDir.toFile(), new File(zipEntry.getName()).getName());

                        // คัดลอกข้อมูลไฟล์จาก zip ลงโฟลเดอร์ชั่วคราว
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