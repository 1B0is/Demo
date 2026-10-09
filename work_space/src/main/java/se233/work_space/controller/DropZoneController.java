package se233.work_space.controller;

import com.sun.source.tree.BreakTree;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import se233.work_space.model.ImageItem;
import se233.work_space.model.ImageSession;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

//(รับ Event การลากไฟล์ แล้วส่งต่อข้อมูลไฟล์ไปให้ Model)
public class DropZoneController {
    private  static final String ACTIVE_STYLE = "drag-active";
    private  static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg"); //กรองนามสกุล
    private final ImageSession session = new ImageSession();

    private Consumer<ImageSession> onImagesReady;

    @FXML private VBox dropZone;

    public void setOnImagesReady(Consumer<ImageSession> callback) {
        this.onImagesReady = callback;
    }

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
            success = processDropped((db.getFiles()));
        }
        event.setDropCompleted(success);
        event.consume();
    }

    //คืน true ถ้ามีไฟล์ที่ใช้งานได้อย่างน้อย 1 ไฟล์
    private boolean processDropped(List<File> files) {
        ImageSession session = new ImageSession(); //sessionใหม่ทุกครั้งที่ดรอป
        List<File> zips = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (File f : files) {
            String ext = extensionOf(f);
            if (f.isFile() && IMAGE_EXT.contains(ext)) {
                session.add(new ImageItem(f));
            } else if (f.isFile() && IMAGE_EXT.contains(ext)) {
                zips.add(f); //Step B จะแตกไฟล์ตรงนี้
            } else {
                rejected.add(f.getName());
            }
        }
        System.out.println("Images: " + session.size() + ", ZIP: " + zips.size()
        + ", Rejected: " + rejected.size());

        if (!rejected.isEmpty()) {
            showWarning("Unsupported file(s) were skipped:\n" + String.join("\n", rejected)
                +"\n\nSupported: JPG, PNG, ZIP");
        }
        if (!zips.isEmpty() && session.isEmpty()){
            showWarning("ZIP support is not implemented yet."); // ชั่วคราว จนกว่าจะทำ Step B
        }
        if (!session.isEmpty() && onImagesReady != null) {
            Platform.runLater(() -> onImagesReady.accept(session));
        }
        return !session.isEmpty();
    }
    private String extensionOf(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf(".");
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private void showWarning(String message) {

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING, message);
            alert.setHeaderText("Some files were skipped");
            alert.show();
        });
    }

}
