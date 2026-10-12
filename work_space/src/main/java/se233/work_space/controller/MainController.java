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
import se233.work_space.service.FileTaskHandler; // <- เพิ่มการเรียกใช้ FileTaskHandler
import se233.work_space.view.ZoomableImagePane;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainController {

    @FXML private StackPane originalImageContainer;
    @FXML private StackPane vectorImageContainer;
    @FXML private Label imageStatusLabel;
    @FXML private ComboBox<String> detailLevelCombo;
    @FXML private RadioButton colorUnlimitedRadio;
    @FXML private RadioButton colorCustomRadio;
    @FXML private Spinner<Integer> customColorSpinner;
    @FXML private CheckBox removeBgCheckbox;
    @FXML private ProgressBar progressBar;

    private ToggleGroup colorToggleGroup;
    private ZoomableImagePane originalPane;
    private ZoomableImagePane vectorPane;

    private ImageSession session;
    private final VectorEngine engine = new VectorEngine();

    // thread เดียวสำหรับพรีวิว (daemon ไม่ขวางการปิดโปรแกรม)
    private static final ExecutorService PREVIEW_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "preview-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile long previewVersion = 0;   // ใช้ทิ้งผลของงานเก่าที่ช้ากว่า
    private boolean syncing = false;            // true ตอนเซ็ตค่าลงปุ่ม เพื่อไม่ให้ listener ทำงาน
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));

    @FXML
    public void initialize() {
        detailLevelCombo.getItems().addAll("Low", "Medium", "High");
        detailLevelCombo.setValue("Medium");

        colorToggleGroup = new ToggleGroup();
        colorUnlimitedRadio.setToggleGroup(colorToggleGroup);
        colorCustomRadio.setToggleGroup(colorToggleGroup);
        colorCustomRadio.setSelected(true);                       // ค่าเริ่มต้นตามโจทย์: Custom 2 สี
        customColorSpinner.disableProperty().bind(colorCustomRadio.selectedProperty().not());

        originalPane = new ZoomableImagePane();
        vectorPane = new ZoomableImagePane();
        originalImageContainer.getChildren().add(originalPane);
        vectorImageContainer.getChildren().add(vectorPane);

        debounce.setOnFinished(e -> runPreview(false));

        detailLevelCombo.valueProperty().addListener((o, a, b) -> onControlsChanged());
        colorToggleGroup.selectedToggleProperty().addListener((o, a, b) -> onControlsChanged());
        customColorSpinner.valueProperty().addListener((o, a, b) -> onControlsChanged());
        removeBgCheckbox.selectedProperty().addListener((o, a, b) -> onControlsChanged());
    }

    // ---------- รับภาพจากหน้า Drop Zone ----------
    public void setInitialFiles(List<File> files) {
        ImageSession s = new ImageSession();
        for (File f : files) s.add(new ImageItem(f));
        setSession(s);
    }

    public void setSession(ImageSession session) {
        this.session = session;
        showCurrent();
    }

    private void showCurrent() {
        if (session == null || session.isEmpty()) return;
        debounce.stop();
        ImageItem item = session.getCurrent();
        imageStatusLabel.setText("Image " + (session.getCurrentIndex() + 1) + " of " + session.size());
        loadSettingsIntoControls(item.getSettings());
        runPreview(true);
    }

    // ---------- ปุ่มตั้งค่า <-> AppSettings ของภาพปัจจุบัน ----------
    private void loadSettingsIntoControls(AppSettings s) {
        syncing = true;
        detailLevelCombo.setValue(toLabel(s.getDetailLevel()));
        if (s.getColorMode() == ColorMode.CUSTOM) colorCustomRadio.setSelected(true);
        else colorUnlimitedRadio.setSelected(true);
        customColorSpinner.getValueFactory().setValue(s.getColorCount());
        removeBgCheckbox.setSelected(s.isRemoveBackground());
        syncing = false;
    }

    private int colorCap(ImageItem item) {
        int d = item.getDetectedColor();
        if (d < 0) return AppSettings.MAX_CUSTOM_COLORS;
        return Math.max(1, Math.min(AppSettings.MAX_CUSTOM_COLORS, d));
    }

    private void configureSpinner(ImageItem item) {
        int cap = colorCap(item);
        AppSettings s = item.getSettings();
        int v = Math.min(s.getColorCount(), cap);
        s.setColorCount(v);
        boolean prev = syncing;
        syncing = true;
        customColorSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, cap, v));
        syncing = prev;
    }

    private void onControlsChanged() {
        if (syncing || session == null || session.isEmpty()) return;
        AppSettings s = session.getCurrent().getSettings();
        s.setDetailLevel(fromLabel(detailLevelCombo.getValue()));
        s.setColorMode(colorCustomRadio.isSelected() ? ColorMode.CUSTOM : ColorMode.UNLIMITED);
        Integer n = customColorSpinner.getValue();
        if (n != null) s.setColorCount(n);
        s.setRemoveBackground(removeBgCheckbox.isSelected());
        debounce.playFromStart();
    }

    private static String toLabel(DetailLevel d) {
        String n = d.name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    private static DetailLevel fromLabel(String label) {
        return label == null ? DetailLevel.MEDIUM : DetailLevel.valueOf(label.toUpperCase());
    }

    private static AppSettings copyOf(AppSettings src) {
        AppSettings c = new AppSettings();
        c.setDetailLevel(src.getDetailLevel());
        c.setColorMode(src.getColorMode());
        c.setColorCount(src.getColorCount());
        c.setRemoveBackground(src.isRemoveBackground());
        return c;
    }

    // ---------- พรีวิว (ทำเบื้องหลัง ไม่ให้ UI ค้าง) ----------
    private void runPreview(boolean imageChanged) {
        if (session == null || session.isEmpty()) return;

        final ImageItem item = session.getCurrent();
        final AppSettings snapshot = copyOf(item.getSettings());
        final long myVersion = ++previewVersion;

        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception {
                Dimension d = VectorEngine.displaySize(item.getSource());

                if (imageChanged) {
                    Image orig = new Image(item.getSource().toURI().toString(),
                            d.width, d.height, false, true, false);
                    Platform.runLater(() -> {
                        if (myVersion == previewVersion) {
                            originalPane.setImage(orig);
                            originalPane.zoomToFit();
                        }
                    });
                }

                if (item.getDetectedColor() < 0) {
                    item.setDetectedColor(VectorEngine.detectColors(item.getSource()));
                    Platform.runLater(() -> {
                        if (session != null && session.getCurrent() == item) configureSpinner(item);
                    });
                }
                snapshot.setColorCount(Math.min(snapshot.getColorCount(), colorCap(item)));

                if (myVersion != previewVersion) return null;

                VectorEngine.TraceResult r = engine.trace(item.getSource(), snapshot, d.width, d.height);
                item.setSvg(r.svg());
                return toFxImage(r.preview());
            }
        };

        task.setOnSucceeded(e -> {
            if (myVersion != previewVersion || task.getValue() == null) return;
            vectorPane.setImage(task.getValue());
            if (imageChanged) vectorPane.zoomToFit();
            progressBar.setVisible(false);
        });

        task.setOnFailed(e -> {
            if (myVersion != previewVersion) return;
            progressBar.setVisible(false);
            Throwable ex = task.getException();
            showError("Preview Error", ex == null ? "Unknown error" : ex.getMessage());
        });

        PREVIEW_POOL.submit(task);
    }

    private static Image toFxImage(BufferedImage bi) {
        int w = bi.getWidth(), h = bi.getHeight();
        int[] px = bi.getRGB(0, 0, w, h, null, 0, w);
        WritableImage out = new WritableImage(w, h);
        out.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        return out;
    }

    // ---------- Export (ทำใน M3) ----------
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
            System.out.println("Export to: " + outputDir.getAbsolutePath());

            // ดึงไฟล์รูปทั้งหมดจาก session
            List<File> imageFiles = new ArrayList<>();
            for (ImageItem item : session.getItems()) {
                imageFiles.add(item.getSource());
            }

            // ดึงการตั้งค่า ณ ปัจจุบัน
            String detail = detailLevelCombo.getValue();
            int colors = colorCustomRadio.isSelected() ? customColorSpinner.getValue() : -1;
            boolean removeBg = removeBgCheckbox.isSelected();

            // แสดง Progress Bar เตรียมพร้อม
            progressBar.setVisible(true);
            progressBar.setProgress(0.0);

            // เรียกใช้ FileTaskHandler ให้ทำงานเบื้องหลัง
            FileTaskHandler.startBatchProcess(
                    imageFiles,
                    outputDir,
                    detail,
                    colors,
                    removeBg,
                    this::updateProgress,
                    this::showSuccessDialog
            );
        }
    }

    // เมธอดสำหรับอัปเดตแถบโหลด (ถูกเรียกมาจาก Thread ของ FileTaskHandler)
    private void updateProgress(Double progress) {
        progressBar.setProgress(progress);
    }

    // เมธอดสำหรับแสดง Pop-up ว่าบันทึกสำเร็จ (ถูกเรียกมาจาก Thread ของ FileTaskHandler)
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

    // ---------- Zoom / Navigation ----------
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