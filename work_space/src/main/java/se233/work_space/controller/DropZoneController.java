package se233.work_space.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.Stage;
import javafx.scene.Node;

// เรียกใช้ ZipProcessor ของboing
import se233.work_space.exception.InvalidZipException;
import se233.work_space.service.ZipProcessor;
import se233.work_space.model.ImageSession;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DropZoneController {

    @FXML
    private void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    @FXML
    private void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            List<File> files = new ArrayList<>(db.getFiles());
            Node source = (Node) event.getSource();
            success = true;
            // ทำหลังจบ event ลากวาง กัน scene เปลี่ยนกลางคัน
            Platform.runLater(() -> processDroppedFiles(files, source));
        }
        event.setDropCompleted(success);
        event.consume();
    }



    private void processDroppedFiles(List<File> files, Node sourceNode) {
        List<File> imageFiles = new ArrayList<>();
        try {
            for (File file : files) {
                if (file.isDirectory() || file.getName().toLowerCase().endsWith(".zip")) {
                    // เปิดใช้งานการเรียก ZipProcessor
                    imageFiles.addAll(ZipProcessor.extractFiles(file));

                } else if (file.getName().toLowerCase().endsWith(".jpg") || file.getName().toLowerCase().endsWith(".png")) {
                    imageFiles.add(file);
                }
            }

            if (!imageFiles.isEmpty()) {
                goToMainLayout(imageFiles, sourceNode);
            } else {
                showError("No Valid Files", "Please drop .jpg, .png, or .zip files containing images.");
            }

            // จัดการ Exception ถ้า zip พัง
        } catch (InvalidZipException e) {
            showError("Zip Extract Error", "Failed to extract ZIP: " + e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
            Throwable root = t;
            while (root.getCause() != null) root = root.getCause();
            showError("System Error", root.toString());
        }
    }

    private void goToMainLayout(List<File> imageFiles, Node sourceNode) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/se233/work_space/view/MainLayout.fxml"));
        Parent root = loader.load();

        MainController mainController = loader.getController();
        mainController.setInitialFiles(imageFiles);

        sourceNode.getScene().setRoot(root);
    }

    private void showError(String title, String content) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.show();
        });
    }

    // เพิ่มเมธอดนี้เพื่อให้ Launcher ไม่ Error
    public void setOnImagesReady(Consumer<ImageSession> callback) {
        // ไว้เดี๋ยวให้ boing มาเขียนเชื่อมเพื่อสั่งเปลี่ยนหน้า
    }
}