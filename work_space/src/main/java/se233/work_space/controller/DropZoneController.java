package se233.work_space.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.Node;
import se233.work_space.exception.InvalidZipException;
import se233.work_space.service.ZipProcessor;
import se233.work_space.model.ImageSession;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * DropZoneController — First screen (drag files here).
 * Handles drag-drop events, filters jpg/png/zip, then switches to MainLayout.
 */
public class DropZoneController {

    /** Drag over handler — accept COPY mode */
    @FXML
    private void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    /** Drop handler — dispatch to process (via Platform.runLater so the drag event finishes first) */
    @FXML
    private void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            List<File> files = new ArrayList<>(db.getFiles());
            Node source = (Node) event.getSource();
            success = true;
            // Use runLater so we don't switch the scene mid-event
            Platform.runLater(() -> processDroppedFiles(files, source));
        }
        event.setDropCompleted(success);
        event.consume();
    }

    /**
     * Filters dropped files → extracts zips → collects image file list,
     * then navigates to MainLayout.
     */
    private void processDroppedFiles(List<File> files, Node sourceNode) {
        List<File> imageFiles = new ArrayList<>();
        try {
            for (File file : files) {
                if (file.isDirectory() || file.getName().toLowerCase().endsWith(".zip")) {
                    imageFiles.addAll(ZipProcessor.extractFiles(file));
                } else if (file.getName().toLowerCase().endsWith(".jpg") ||
                        file.getName().toLowerCase().endsWith(".png")) {
                    imageFiles.add(file);
                }
            }

            if (!imageFiles.isEmpty()) {
                goToMainLayout(imageFiles, sourceNode);
            } else {
                showError("No Valid Files", "Please drop .jpg, .png, or .zip files containing images.");
            }
        } catch (InvalidZipException e) {
            showError("Zip Extract Error", "Failed to extract ZIP: " + e.getMessage());
        } catch (Throwable t) {
            // Show root cause — easier to debug
            t.printStackTrace();
            Throwable root = t;
            while (root.getCause() != null) root = root.getCause();
            showError("System Error", root.toString());
        }
    }

    /** Loads MainLayout.fxml, passes files to MainController, then swaps scene root */
    private void goToMainLayout(List<File> imageFiles, Node sourceNode) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/se233/work_space/view/MainLayout.fxml"));
        Parent root = loader.load();

        MainController mainController = loader.getController();
        mainController.setInitialFiles(imageFiles);

        sourceNode.getScene().setRoot(root);
    }

    /** Shows an error alert (on JavaFX thread) */
    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.show();
        });
    }

    /** Hook for Launcher (currently unused) */
    public void setOnImagesReady(Consumer<ImageSession> callback) {
        // Not used yet — reserved for future
    }
}