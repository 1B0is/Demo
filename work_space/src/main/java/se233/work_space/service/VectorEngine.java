package se233.work_space.service;

import se233.work_space.exception.CorruptedImageException;
import se233.work_space.model.AppSettings;

import java.io.File;

//# interface: แปลงภาพ 1 ไฟล์ + settings เป็น SVG (เปลี่ยน tracer ได้โดยไม่กระทบส่วนอื่น)
public interface VectorEngine {
    String trace(File image, AppSettings settings) throws CorruptedImageException;
}
