package se233.work_space.exception;
//# ภาพเสีย/อ่านไม่ได้
public class CorruptedImageException extends Exception {
    public CorruptedImageException(String message) { super(message); }
    public CorruptedImageException(String message, Throwable cause) { super(message, cause); }
}
