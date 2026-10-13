package se233.work_space.model;

import javafx.scene.paint.Color;

//(จัดการเมื่อไฟล์ที่ลากเข้ามาเป็น .zip เพื่อแตกไฟล์)
//เก็บค่าตั้งของแต่ละภาพ: detail, โหมดสี, จำนวนสี, ลบพื้นหลัง
public class AppSettings {
    //ค่าเริ่มต้นตรงตามโจทย์: Medium, Custom, 2 สี
    public static final int DEFAULT_COLORS = 2;
    public static final int MAX_CUSTOM_COLORS = 5;

    public enum DetailLevel {LOW, MEDIUM, HIGH}
    public enum ColorMode { UNLIMITED, CUSTOM }

    private DetailLevel detailLevel = DetailLevel.MEDIUM;
    private ColorMode colorMode = ColorMode.CUSTOM;
    private int colorCount = DEFAULT_COLORS;
    private boolean removeBackground = true;
    private boolean flipHorizontal = false;   // พลิกซ้าย-ขวา
    private boolean flipVertical = false;     // พลิกบน-ล่าง

    public DetailLevel getDetailLevel() { return detailLevel;}
    public void setDetailLevel(DetailLevel d) { this.detailLevel = d; }

    public ColorMode getColorMode() { return colorMode;}
    public void setColorMode (ColorMode m) { this.colorMode = m; }

    public int getColorCount() { return colorCount;}
    public void setColorCount (int n) {
        this.colorCount = Math.clamp(n, 1, MAX_CUSTOM_COLORS);
        //Math.clamp(value, min, max) จำกัดค่าให้อยู่ในช่วง min ถึง max ผลเหมือนกับ Math.max(1, Math.min(n, MAX)) ทุกกรณี เช่น n = 0 ได้ 1, n = 3 ได้ 3, n = 9 ได้ 5
    }

    public boolean isRemoveBackground() { return removeBackground;}
    public void setRemoveBackground(boolean b) {this.removeBackground = b;}

    public boolean isFlipHorizontal() { return flipHorizontal; }
    public void setFlipHorizontal(boolean b) { this.flipHorizontal = b; }

    public boolean isFlipVertical() { return flipVertical; }
    public void setFlipVertical(boolean b) { this.flipVertical = b; }

    // สำเนาค่าตั้ง (ใช้ทำ snapshot ก่อนส่งให้เธรดงาน)
    public AppSettings copy() {
        AppSettings c = new AppSettings();
        c.detailLevel = detailLevel;
        c.colorMode = colorMode;
        c.colorCount = colorCount;
        c.removeBackground = removeBackground;
        c.flipHorizontal = flipHorizontal;
        c.flipVertical = flipVertical;
        return c;
    }
}
