package se233.work_space.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.image.Image;

import se233.work_space.view.ZoomableImagePane;
import se233.work_space.exception.CorruptedImageException;
//import se233.work_space.exception.TracerNotFoundException;
// เรียกใช้ Service ของboing
//import se233.work_space.service.ImageTracerEngine;
import se233.work_space.service.FileTaskHandler;
import se233.work_space.model.ImageSession;

import java.io.File;
import java.util.List;

public class MainController {

    // UI Elements
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

    // Data Variables
    private List<File> imageFiles;
    private int currentIndex = 0;

    @FXML
    public void initialize() {
        // 1. ตั้งค่า Controls
        detailLevelCombo.getItems().addAll("Low", "Medium", "High");
        detailLevelCombo.setValue("Medium");

        colorToggleGroup = new ToggleGroup();
        colorUnlimitedRadio.setToggleGroup(colorToggleGroup);
        colorCustomRadio.setToggleGroup(colorToggleGroup);
        colorUnlimitedRadio.setSelected(true);
        customColorSpinner.disableProperty().bind(colorCustomRadio.selectedProperty().not());

        // 2. สร้างที่ใส่รูป (ZoomableImagePane)
        originalPane = new ZoomableImagePane();
        vectorPane = new ZoomableImagePane();
        originalImageContainer.getChildren().add(originalPane);
        vectorImageContainer.getChildren().add(vectorPane);

        // 3. ผูก Event เปลี่ยนค่าแล้วอัปเดตทันที
        setupInstantPreviewListeners();
    }

    public void setInitialFiles(List<File> files) {
        this.imageFiles = files;
        this.currentIndex = 0;
        loadImage(currentIndex);
    }

    private void loadImage(int index) {
        if (imageFiles == null || imageFiles.isEmpty()) return;

        try {
            File currentFile = imageFiles.get(index);
            Image original = new Image(currentFile.toURI().toString());
            originalPane.setImage(original);
            originalPane.zoomToFit();
            vectorPane.zoomToFit();

            imageStatusLabel.setText("Image " + (index + 1) + " of " + imageFiles.size());
            triggerPreviewUpdate();

        } catch (Exception e) {
            showError("Image Error", "Failed to load image: " + e.getMessage());
        }
    }

    private void setupInstantPreviewListeners() {
        detailLevelCombo.valueProperty().addListener((obs, oldVal, newVal) -> triggerPreviewUpdate());
        colorToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> triggerPreviewUpdate());
        customColorSpinner.valueProperty().addListener((obs, oldVal, newVal) -> triggerPreviewUpdate());
        removeBgCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> triggerPreviewUpdate());
    }

    private void triggerPreviewUpdate() {
        if (imageFiles == null || imageFiles.isEmpty()) return;

        File currentFile = imageFiles.get(currentIndex);
        String detail = detailLevelCombo.getValue();
        boolean isCustomColor = colorCustomRadio.isSelected();
        int colors = isCustomColor ? customColorSpinner.getValue() : -1; // -1 สมมติว่าเป็น Unlimited
        boolean removeBg = removeBgCheckbox.isSelected();

        try {
            // TODO (จุดเชื่อมต่อ): เปลี่ยนชื่อ method "generatePreview" ตามที่เพื่อนตั้งใน ImageTracerEngine
            // ให้เพื่อนคืนค่ากลับมาเป็น JavaFX Image เพื่อโชว์ผลลัพธ์
//            Image vectorizedImage = ImageTracerEngine.generatePreview(currentFile, detail, colors, removeBg);
//            vectorPane.setImage(vectorizedImage);
//
//        } catch (TracerNotFoundException e) {
//            showError("System Error", "Vector Tracer engine (e.g., Potrace) not found!");
//        } catch (CorruptedImageException e) {
//            showError("File Error", "The current image file is corrupted.");
        } catch (Exception e) {
            showError("Render Error", "Failed to render vector preview: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportAll() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Output Folder");
        Stage stage = (Stage) detailLevelCombo.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            progressBar.setVisible(true);

            String detail = detailLevelCombo.getValue();
            boolean isCustomColor = colorCustomRadio.isSelected();
            int colors = isCustomColor ? customColorSpinner.getValue() : -1;
            boolean removeBg = removeBgCheckbox.isSelected();

            try {
                // TODO (จุดเชื่อมต่อ): เปลี่ยนชื่อ method "startBatchProcess" ตามที่เพื่อนตั้งใน FileTaskHandler
                // ส่ง callback ไปอัปเดต ProgressBar ให้เพื่อนด้วย จะได้ไม่ค้าง
//                FileTaskHandler.startBatchProcess(
//                        imageFiles, selectedDirectory, detail, colors, removeBg,
//                        progressValue -> progressBar.setProgress(progressValue),
//                        () -> {
//                            progressBar.setVisible(false);
//                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
//                            alert.setTitle("Success");
//                            alert.setHeaderText(null);
//                            alert.setContentText("All images exported successfully!");
//                            alert.showAndWait();
//                        }
//                );
            } catch (Exception e) {
                progressBar.setVisible(false);
                showError("Export Error", "Failed to export files: " + e.getMessage());
            }
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // --- ควบคุมปุ่ม Navigation และ Zoom ---
    @FXML private void handleZoomIn() { originalPane.zoomIn(); vectorPane.zoomIn(); }
    @FXML private void handleZoomOut() { originalPane.zoomOut(); vectorPane.zoomOut(); }
    @FXML private void handleZoomFit() { originalPane.zoomToFit(); vectorPane.zoomToFit(); }

    @FXML
    private void handleBack() {
        if (currentIndex > 0) {
            currentIndex--;
            loadImage(currentIndex);
        }
    }

    @FXML
    private void handleNext() {
        if (currentIndex < imageFiles.size() - 1) {
            currentIndex++;
            loadImage(currentIndex);
        }
    }

    public void setSession(ImageSession session) {
        // ไว้ให้ boing มาจัดการดึงข้อมูลทีหลัง
    }
}