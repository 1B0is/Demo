package se233.work_space.controller;

import javafx.fxml.FXML;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.List;

//(รับ Event การลากไฟล์ แล้วส่งต่อข้อมูลไฟล์ไปให้ Model)
public class DropZoneController {
    private  static final String ACTIVE_STYLE = "drag-active";
    @FXML private VBox dropZone;
    @FXML
    private void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!dropZone.getStyleClass().contains(ACTIVE_STYLE)) {
                dropZone.getStyleClass().add(ACTIVE_STYLE);
            }
        }
        event.consume();
    }
    @FXML
    private void handleDragExited(DragEvent event) {
        dropZone.getStyleClass().remove(ACTIVE_STYLE);
        event.consume();
    }
    @FXML
    private void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        // check ว่ารับไฟล์ได้ ไม่กรองชนิด
        if (db.hasFiles()) {
            List<File> files = db.getFiles();
            files.forEach(f -> System.out.println("Dropped: "+ f.getAbsolutePath()));
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }
}
