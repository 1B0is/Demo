package se233.work_space.model;

import java.io.File;

/**
 * ImageItem — Data for a single image.
 * Holds: source file + per-image settings + result SVG + processing status.
 */
public class ImageItem {
    public enum Status { PENDING, PROCESSING, DONE, ERROR }

    private final File source;                 // Original file (jpg/png)
    private final AppSettings settings = new AppSettings();
    private int detectedColor = -1;            // -1 = colors not yet detected
    private String svg;                        // Latest SVG result
    private Status status = Status.PENDING;
    private String errorMessage;

    public ImageItem(File source) { this.source = source; }

    public File getSource() { return this.source; }
    public String getName() { return this.source.getName(); }
    public AppSettings getSettings() { return this.settings; }

    public int getDetectedColor() { return this.detectedColor; }
    public void setDetectedColor(int n) { this.detectedColor = n; }

    public String getSvg() { return svg; }
    public void setSvg(String svg) { this.svg = svg; }

    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String m) { this.errorMessage = m; }
}