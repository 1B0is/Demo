package se233.work_space.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import se233.work_space.model.AppSettings;
import se233.work_space.model.AppSettings.ColorMode;
import se233.work_space.model.AppSettings.DetailLevel;
import se233.work_space.model.ImageItem;
import se233.work_space.model.ImageSession;
import se233.work_space.service.VectorEngine;
import se233.work_space.service.FileTaskHandler;
import se233.work_space.view.ZoomableImagePane;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MainController — Main screen. Manages:
 *   - All controls (detail, colors, removeBg, flip)
 *   - Both previews (original + vector)
 *   - 250ms debounce before re-trace
 *   - Parallel preprocessing at load time (rubric M5)
 *   - Batch export
 */
public class MainController {

    // ---------- FXML bindings ----------
    @FXML private StackPane originalImageContainer;
    @FXML private StackPane vectorImageContainer;
    @FXML private Label imageStatusLabel;
    @FXML private ComboBox<String> detailLevelCombo;
    @FXML private RadioButton colorUnlimitedRadio;
    @FXML private RadioButton colorCustomRadio;
    @FXML private Spinner<Integer> customColorSpinner;
    @FXML private CheckBox removeBgCheckbox;
    @FXML private ProgressBar progressBar;
    @FXML private ToggleButton flipHButton;
    @FXML private ToggleButton flipVButton;

    // ---------- State ----------
    private ToggleGroup colorToggleGroup;
    private ZoomableImagePane originalPane;
    private ZoomableImagePane vectorPane;
    private ImageSession session;
    private final VectorEngine engine = new VectorEngine();

    // Single-thread preview worker (daemon → doesn't block app close)
    private static final ExecutorService PREVIEW_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "preview-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile long previewVersion = 0;   // Discards stale task results
    private boolean syncing = false;            // true = setting UI values → listeners should ignore
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));

    /**
     * Called automatically after FXML loads.
     * - Sets combo/radio/spinner defaults
     * - Wires up listeners → onControlsChanged()
     */
    @FXML
    public void initialize() {
        detailLevelCombo.getItems().addAll("Low", "Medium", "High");
        detailLevelCombo.setValue("Medium");

        colorToggleGroup = new ToggleGroup();
        colorUnlimitedRadio.setToggleGroup(colorToggleGroup);
        colorCustomRadio.setToggleGroup(colorToggleGroup);
        colorCustomRadio.setSelected(true);   // Default = Custom
        customColorSpinner.disableProperty().bind(colorCustomRadio.selectedProperty().not());

        // Create panes and attach to containers
        originalPane = new ZoomableImagePane();
        vectorPane = new ZoomableImagePane();
        originalImageContainer.getChildren().add(originalPane);
        vectorImageContainer.getChildren().add(vectorPane);

        // After debounce (250ms) → run preview
        debounce.setOnFinished(e -> runPreview(false));

        // Wire up all listeners
        detailLevelCombo.valueProperty().addListener((o, a, b) -> onControlsChanged());
        colorToggleGroup.selectedToggleProperty().addListener((o, a, b) -> onControlsChanged());
        customColorSpinner.valueProperty().addListener((o, a, b) -> onControlsChanged());
        removeBgCheckbox.selectedProperty().addListener((o, a, b) -> onControlsChanged());
        flipHButton.selectedProperty().addListener((o, a, b) -> onControlsChanged());
        flipVButton.selectedProperty().addListener((o, a, b) -> onControlsChanged());
    }

    // ============================================================
    // Section 1: Receiving images from DropZone
    // ============================================================

    /**
     * Builds an ImageSession from a file list.
     * If multiple files are dropped, runs parallel preprocessing first
     * (detect colors for every file concurrently) — rubric M5.
     */
    public void setInitialFiles(List<File> files) {
        ImageSession s = new ImageSession();
        for (File f : files) s.add(new ImageItem(f));

        if (files.size() > 1) {
            preprocessInParallel(s);
        } else {
            setSession(s);
        }
    }

    /**
     * Detect colors for all files in parallel (background thread pool).
     * Shows progress bar while running. Once done → switches to session.
     */
    private void preprocessInParallel(ImageSession s) {
        progressBar.progressProperty().unbind();
        progressBar.setVisible(true);
        progressBar.setProgress(0.0);

        final int total = s.size();
        final AtomicInteger done = new AtomicInteger(0);

        // Thread pool sized to available CPU cores (cap at number of files)
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(total, Runtime.getRuntime().availableProcessors()));

        System.out.println("🚀 Preprocessing " + total + " files in parallel...");

        for (ImageItem item : s.getItems()) {
            pool.submit(() -> {
                try {
                    if (item.getDetectedColor() < 0) {
                        item.setDetectedColor(VectorEngine.detectColors(item.getSource()));
                        System.out.println("✅ Detected colors: " + item.getName()
                                + " → " + item.getDetectedColor());
                    }
                } catch (Exception e) {
                    System.out.println("❌ Detect failed: " + item.getName());
                    e.printStackTrace();
                } finally {
                    int current = done.incrementAndGet();
                    Platform.runLater(() -> progressBar.setProgress((double) current / total));
                }
            });
        }

        pool.shutdown();

        // Waiter thread: once all preprocessing finishes → switch to session on UI thread
        new Thread(() -> {
            try {
                pool.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException ignored) {}
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                setSession(s);
            });
        }, "preprocess-waiter").start();
    }

    public void setSession(ImageSession session) {
        this.session = session;
        showCurrent();
    }

    /** Shows current image: update label, load settings into controls, run preview */
    private void showCurrent() {
        if (session == null || session.isEmpty()) return;
        debounce.stop();   // Cancel any pending debounce
        ImageItem item = session.getCurrent();
        imageStatusLabel.setText("Image " + (session.getCurrentIndex() + 1) + " of " + session.size());
        loadSettingsIntoControls(item.getSettings());
        runPreview(true);
    }

    // ============================================================
    // Section 2: Sync between UI ↔ AppSettings
    // ============================================================

    /**
     * Loads settings → UI controls (won't trigger listeners because syncing = true).
     */
    private void loadSettingsIntoControls(AppSettings s) {
        syncing = true;
        detailLevelCombo.setValue(toLabel(s.getDetailLevel()));
        if (s.getColorMode() == ColorMode.CUSTOM) colorCustomRadio.setSelected(true);
        else colorUnlimitedRadio.setSelected(true);
        customColorSpinner.getValueFactory().setValue(s.getColorCount());
        removeBgCheckbox.setSelected(s.isRemoveBackground());
        flipHButton.setSelected(s.isFlipHorizontal());
        flipVButton.setSelected(s.isFlipVertical());
        syncing = false;
    }

    /** Max spinner value = min(detected colors, MAX_CUSTOM_COLORS) */
    private int colorCap(ImageItem item) {
        int d = item.getDetectedColor();
        if (d < 0) return AppSettings.MAX_CUSTOM_COLORS;   // Not detected yet → cap at MAX
        return Math.max(1, Math.min(AppSettings.MAX_CUSTOM_COLORS, d));
    }

    /** Sets spinner max = colorCap(item), initial = min(current, cap) */
    private void configureSpinner(ImageItem item) {
        int cap = colorCap(item);
        AppSettings s = item.getSettings();
        int v = Math.min(s.getColorCount(), cap);
        s.setColorCount(v);

        boolean prev = syncing;
        syncing = true;
        customColorSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, cap, v));
        syncing = prev;
    }

    /**
     * Called whenever the user changes any control.
     * - Syncs values into the current image's AppSettings
     * - Debounces 250ms before re-tracing (avoids excessive traces)
     */
    private void onControlsChanged() {
        if (syncing || session == null || session.isEmpty()) return;
        AppSettings s = session.getCurrent().getSettings();
        s.setDetailLevel(fromLabel(detailLevelCombo.getValue()));
        s.setColorMode(colorCustomRadio.isSelected() ? ColorMode.CUSTOM : ColorMode.UNLIMITED);
        Integer n = customColorSpinner.getValue();
        if (n != null) s.setColorCount(n);
        s.setRemoveBackground(removeBgCheckbox.isSelected());
        s.setFlipHorizontal(flipHButton.isSelected());
        s.setFlipVertical(flipVButton.isSelected());
        debounce.playFromStart();
    }

    /** enum → "Medium" */
    private static String toLabel(DetailLevel d) {
        String n = d.name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    /** "Medium" → enum */
    private static DetailLevel fromLabel(String label) {
        return label == null ? DetailLevel.MEDIUM : DetailLevel.valueOf(label.toUpperCase());
    }

    // ============================================================
    // Section 3: Preview (the core UI flow)
    // ============================================================

    /**
     * Runs preview in a background thread.
     * - imageChanged = true → loads original image + zoom fit
     * - imageChanged = false → just re-trace (user tweaked settings)
     */
    private void runPreview(boolean imageChanged) {
        if (session == null || session.isEmpty()) return;

        final ImageItem item = session.getCurrent();
        // Use .copy() (copies every field) — do NOT use the old copyOf()
        final AppSettings snapshot = item.getSettings().copy();
        final long myVersion = ++previewVersion;

        progressBar.progressProperty().unbind();
        progressBar.setVisible(true);
        progressBar.setProgress(0.0);

        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception {
                updateProgress(10, 100);
                Dimension d = VectorEngine.displaySize(item.getSource());

                if (imageChanged) {
                    // Load original image (display size) → show on left pane
                    Image orig = new Image(item.getSource().toURI().toString(),
                            d.width, d.height, false, true, false);
                    Platform.runLater(() -> {
                        if (myVersion == previewVersion) {
                            originalPane.setImage(orig);
                            originalPane.zoomToFit();
                        }
                    });
                }
                updateProgress(30, 100);

                // Detect colors if not yet done (usually already done by parallel preprocessing)
                if (item.getDetectedColor() < 0) {
                    item.setDetectedColor(VectorEngine.detectColors(item.getSource()));
                    Platform.runLater(() -> {
                        if (session != null && session.getCurrent() == item) configureSpinner(item);
                    });
                }
                snapshot.setColorCount(Math.min(snapshot.getColorCount(), colorCap(item)));

                if (myVersion != previewVersion) return null;   // A newer task exists → bail out

                updateProgress(60, 100);

                // Trace!
                VectorEngine.TraceResult r = engine.trace(item.getSource(), snapshot, d.width, d.height);
                item.setSvg(r.svg());

                updateProgress(90, 100);
                Image previewImg = toFxImage(r.preview());
                updateProgress(100, 100);
                return previewImg;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            if (myVersion != previewVersion || task.getValue() == null) return;
            vectorPane.setImage(task.getValue());
            if (imageChanged) vectorPane.zoomToFit();
            progressBar.setVisible(false);
        });

        task.setOnFailed(e -> {
            if (myVersion != previewVersion) return;
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);
            Throwable ex = task.getException();
            showError("Preview Error", ex == null ? "Unknown error" : ex.getMessage());
        });

        PREVIEW_POOL.submit(task);
    }

    /** BufferedImage (AWT) → JavaFX Image */
    private static Image toFxImage(BufferedImage bi) {
        int w = bi.getWidth(), h = bi.getHeight();
        int[] px = bi.getRGB(0, 0, w, h, null, 0, w);
        WritableImage out = new WritableImage(w, h);
        out.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        return out;
    }

    // ============================================================
    // Section 4: Export
    // ============================================================

    /**
     * Exports every image in the session as SVG.
     * 1. Open DirectoryChooser
     * 2. Collect all current settings (including flip!)
     * 3. Hand off to FileTaskHandler (parallel/sequential)
     */
    @FXML
    private void handleExportAll() {
        if (session == null || session.isEmpty()) {
            showError("Export Error", "No images to export.");
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Folder");
        Stage stage = (Stage) detailLevelCombo.getScene().getWindow();
        File outputDir = chooser.showDialog(stage);

        if (outputDir != null) {
            List<File> imageFiles = new ArrayList<>();
            for (ImageItem item : session.getItems()) {
                imageFiles.add(item.getSource());
            }

            // Read settings from UI
            String detail = detailLevelCombo.getValue();
            int colors = colorCustomRadio.isSelected() ? customColorSpinner.getValue() : -1;
            boolean removeBg = removeBgCheckbox.isSelected();
            boolean flipH = flipHButton.isSelected();   // Important! Don't forget
            boolean flipV = flipVButton.isSelected();

            progressBar.progressProperty().unbind();
            progressBar.setVisible(true);
            progressBar.setProgress(0.0);

            FileTaskHandler.startBatchProcess(
                    imageFiles, outputDir, detail, colors, removeBg,
                    flipH, flipV,
                    this::updateProgress, this::showSuccessDialog);
        }
    }

    /** Callback from FileTaskHandler */
    private void updateProgress(Double progress) {
        progressBar.setProgress(progress);
    }

    /** Callback when export finishes */
    private void showSuccessDialog() {
        progressBar.setVisible(false);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export Complete");
        alert.setHeaderText(null);
        alert.setContentText("All images have been successfully exported to SVG!");
        alert.showAndWait();
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ============================================================
    // Section 5: Zoom / Navigation
    // ============================================================

    @FXML private void handleZoomIn()  { originalPane.zoomIn();  vectorPane.zoomIn(); }
    @FXML private void handleZoomOut() { originalPane.zoomOut(); vectorPane.zoomOut(); }
    @FXML private void handleZoomFit() { originalPane.zoomToFit(); vectorPane.zoomToFit(); }

    @FXML
    private void handleBack() {
        if (session != null && session.hasPrevious()) { session.previous(); showCurrent(); }
    }

    @FXML
    private void handleNext() {
        if (session != null && session.hasNext()) { session.next(); showCurrent(); }
    }
}