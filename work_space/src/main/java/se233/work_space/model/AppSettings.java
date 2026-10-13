package se233.work_space.model;

/**
 * AppSettings — Holds per-image settings: detail level, color mode, color count,
 * background removal, and flip flags. Each image has its own settings instance.
 */
public class AppSettings {
    // Defaults per project spec: Custom mode, 2 colors
    public static final int DEFAULT_COLORS = 2;
    public static final int MAX_CUSTOM_COLORS = 5;

    public enum DetailLevel {LOW, MEDIUM, HIGH}
    public enum ColorMode {UNLIMITED, CUSTOM}

    private DetailLevel detailLevel = DetailLevel.MEDIUM;
    private ColorMode colorMode = ColorMode.CUSTOM;
    private int colorCount = DEFAULT_COLORS;
    private boolean removeBackground = true;
    private boolean flipHorizontal = false;   // Flip left <-> right (extra feature)
    private boolean flipVertical = false;     // Flip top <-> bottom (extra feature)

    // --- Standard getters/setters ---

    public DetailLevel getDetailLevel() { return detailLevel; }
    public void setDetailLevel(DetailLevel d) { this.detailLevel = d; }

    public ColorMode getColorMode() { return colorMode; }
    public void setColorMode(ColorMode m) { this.colorMode = m; }

    public int getColorCount() { return colorCount; }

    /** Clamp color count to the range 1..MAX_CUSTOM_COLORS automatically */
    public void setColorCount(int n) {
        this.colorCount = Math.clamp(n, 1, MAX_CUSTOM_COLORS);
    }

    public boolean isRemoveBackground() { return removeBackground; }
    public void setRemoveBackground(boolean b) { this.removeBackground = b; }

    public boolean isFlipHorizontal() { return flipHorizontal; }
    public void setFlipHorizontal(boolean b) { this.flipHorizontal = b; }

    public boolean isFlipVertical() { return flipVertical; }
    public void setFlipVertical(boolean b) { this.flipVertical = b; }

    /**
     * Deep copy of all settings — used to snapshot before sending to background thread.
     */
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