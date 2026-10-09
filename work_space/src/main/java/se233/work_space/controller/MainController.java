package se233.work_space.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import se233.work_space.model.ImageItem;
import se233.work_space.model.ImageSession;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.awt.*;

// (ผูก Event ของปุ่มตั้งค่าต่างๆ, สั่งเปลี่ยนภาพพรีวิว, และจัดการปุ่ม Export All)
public class MainController {

    @FXML private StackPane originalPane;
    @FXML private ImageView originalView;
    @FXML private Label originalCaption;
    @FXML private Label pageLabel;
    @FXML private Button backButton;
    @FXML private Button nextButton;

    private ImageSession session;

    @FXML
    private void initialize() {
        // ให้ภาพพอดีกับกรอบ และย่อ/ขยายตามหน้าต่าง
        originalView.fitWidthProperty().bind(originalPane.widthProperty().subtract(4));
        originalView.fitHeightProperty().bind(originalPane.heightProperty().subtract(4));
    }
    // MainApp เรียกหลังโหลด FXML เสร็จ
    public void setSession(ImageSession session) {
        this.session = session;
        showCurrent();
    }

    private void showCurrent() {
        ImageItem item = session.getCurrent();
        originalView.setImage(new Image(item.getSource().toURI().toString(), true));
        originalCaption.setText(item.getName());
        pageLabel.setText((session.getCurrentIndex()+1) + "/" + session.size());
        backButton.setDisable(!session.hasPrevious());
        nextButton.setDisable(!session.hasNext());
    }

    @FXML private void handleBack() {session.previous(); showCurrent(); }
    @FXML private void handleNext() {session.next(); showCurrent(); }

    // stub: จะเติมโค้ดใน step ถัดไป
    @FXML private void handleZoomIn()   {System.out.println("Zoom In");} //Step F
    @FXML private void handleZoomOut()   {System.out.println("Zoom out");} //Step F
    @FXML private void handleZoomFit()   {System.out.println("Zoom to Fit");} //Step F
    @FXML private void handleExportAll()   {System.out.println("Export All");} //Step F
}
