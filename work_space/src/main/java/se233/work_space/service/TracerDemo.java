package se233.work_space.service;

import se233.work_space.model.AppSettings;
import se233.work_space.model.ColorMode;
import se233.work_space.model.DetailLevel;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

// รันแยกจาก UI เพื่อดูว่าได้ SVG ไหม (ลบทิ้งทีหลังได้)
public class TracerDemo {
    public static void main(String[] args) throws Exception {

        File input = new File("C:/Users/LOQ/Downloads/download (4).jpg");
        System.out.println("path     = " + input.getAbsolutePath());
        System.out.println("exists   = " + input.exists());
        System.out.println("isFile   = " + input.isFile());
        System.out.println("canRead  = " + input.canRead());
        System.out.println("length   = " + input.length());

        // โฟลเดอร์ปลายทาง: C:\Users\LOQ\Downloads\svg_test (ต้องประกาศก่อนใช้)
        Path outDir = Path.of(System.getProperty("user.home"), "Downloads", "svg_test");
        Files.createDirectories(outDir);

        VectorEngine engine = new ImageTracerEngine();

        // 1) เทียบ 3 ระดับ Detail (Custom 2 สี ค่าเริ่มต้น)
        for (DetailLevel level : DetailLevel.values()) {
            AppSettings s = new AppSettings();
            s.setDetailLevel(level);
            long t = System.currentTimeMillis();
            String svg = engine.trace(input, s);
            Files.writeString(outDir.resolve("out_" + level + ".svg"), svg);
            System.out.println(level + ": " + svg.length() + " chars, "
                    + (System.currentTimeMillis() - t) + " ms");
        }

        // 2) Custom 5 สี
        AppSettings five = new AppSettings();
        five.setColorCount(5);
        Files.writeString(outDir.resolve("out_CUSTOM5.svg"), engine.trace(input, five));

        // 3) Unlimited
        AppSettings unlimited = new AppSettings();
        unlimited.setColorMode(ColorMode.UNLIMITED);
        Files.writeString(outDir.resolve("out_UNLIMITED.svg"), engine.trace(input, unlimited));

        System.out.println("Done. Files are in: " + outDir.toAbsolutePath());
    }
}