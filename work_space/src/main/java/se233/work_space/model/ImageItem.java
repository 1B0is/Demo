package se233.work_space.model;

import javafx.scene.image.Image;

import java.io.File;

//# ข้อมูลต่อ 1 ภาพ: ไฟล์ต้นฉบับ, จำนวนสีที่ detect ได้, settings ของภาพนั้น, ผล SVG, สถานะ (รอ/กำลังทำ/เสร็จ/error)
public class ImageItem {
    public enum Status { PENDING, PROCESSING, DONE, ERROR}

    private final File source;
    private final AppSettings settings = new AppSettings();
    private int detectedColor = -1; //-1 = ยังไม่ได้ detect
    private String svg;
    private Status status = Status.PENDING;
    private String errorMessage;

    public ImageItem(File source) { this.source = source;}

    public File getSource() {return this.source;}
    public String getName() {return this.source.getName();}
    public AppSettings getSettings() {return this.getSettings();}

    public int getDetectedColor() {return this.detectedColor;}
    public void setDetectedColor(int n) {this.detectedColor = n;}

    public String getSvg() {return svg;}
    public void setSvg(String svg) {this.svg = svg;}

    public Status getStatus() {return status;}
    public void setStatus(Status s) {this.status = s;}

    public String getErrorMessage() {return errorMessage;}
    public void setErrorMessage(String m) {this.errorMessage = m;}
}
