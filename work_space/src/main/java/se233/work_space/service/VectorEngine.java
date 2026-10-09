package se233.work_space.service;

import javafx.concurrent.Task;
import se233.work_space.exception.CorruptedImageException;
import se233.work_space.lib.ImageTracer;
import se233.work_space.model.AppSettings.DetailLevel;
import se233.work_space.model.AppSettings.ColorMode;
import se233.work_space.model.AppSettings;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class VectorEngine {

    // ใช้แทนความหมาย "Unlimited"
    private static final int UNLIMITED_COLORS = 64;

    /**
     * เมธอดสำหรับสร้าง Background Task เพื่อนำไปรันใน Thread แยก (ไม่ทำให้ UI ค้าง)
     * Controller จะเรียกใช้เมธอดนี้เพื่อรับ Task กลับไปจัดการต่อ
     */
    public Task<String> createTraceTask(File image, AppSettings settings) {
        return new Task<>() {
            @Override
            protected String call() throws Exception {
                // เรียกใช้เมธอด trace ปกติ แต่การทำงานจะถูกโยนไปอยู่เบื้องหลัง
                return trace(image, settings);
            }
        };
    }

    /**
     * ลอจิกการแปลงภาพหลัก (Synchronous)
     * การทำงานส่วนนี้กินพลัง CPU และใช้เวลานาน จึงต้องถูกห่อใน Task เสมอเมื่อใช้กับ UI
     */
    public String trace(File image, AppSettings settings) throws CorruptedImageException {
        // 1) ตรวจว่าไฟล์มีอยู่จริง
        if (image == null || !image.isFile()) {
            throw new CorruptedImageException("File not found: " + image);
        }

        // 2) อ่านภาพ: ถ้าอ่านไม่ได้ ImageIO.read จะคืน null
        BufferedImage bitmap;
        try {
            bitmap = ImageIO.read(image);
        } catch (IOException e) {
            throw new CorruptedImageException("Cannot read image: " + image.getName(), e);
        }
        if (bitmap == null) {
            throw new CorruptedImageException("Not a valid JPG/PNG image: " + image.getName());
        }

        // 3) สั่ง trace
        try {
            String svg = ImageTracer.imageToSVG(bitmap, buildOptions(settings), null);
            if (svg == null || svg.isBlank()) {
                throw new CorruptedImageException("Tracing produced no output: " + image.getName());
            }
            return svg;
        } catch (CorruptedImageException e) {
            throw e; // โยน Exception เดิมกลับไปให้ Task ฝั่ง Controller รับรู้
        } catch (Exception e) {
            throw new CorruptedImageException("Failed to trace " + image.getName(), e);
        }
    }

    /**
     * แปลง AppSettings เป็น Options แบบ Hashmap สำหรับ ImageTracer
     */
    private HashMap<String, Float> buildOptions(AppSettings s) {
        HashMap<String, Float> o = new HashMap<>();

        switch (s.getDetailLevel()) {
            case LOW -> { o.put("ltres", 5f); o.put("qtres", 5f); o.put("pathomit", 32f); }
            case MEDIUM -> { o.put("ltres", 1f); o.put("qtres", 1f); o.put("pathomit", 8f); }
            case HIGH -> { o.put("ltres", 0.5f); o.put("qtres", 0.5f); o.put("pathomit", 1f); }
        }

        int colors = (s.getColorMode() == ColorMode.CUSTOM) ? s.getColorCount() : UNLIMITED_COLORS;
        o.put("numberofcolors", (float) colors);
        return o;
    }
}