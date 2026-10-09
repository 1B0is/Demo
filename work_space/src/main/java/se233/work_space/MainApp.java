package se233.work_space;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import se233.work_space.controller.DropZoneController;
import se233.work_space.controller.MainController;
import se233.work_space.model.ImageSession;

import java.io.IOException;

//(extends Application, โหลดหน้า DropZone, ส่ง ImageSession ต่อ)
public class MainApp extends Application {

    private Stage stage;

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        stage.setTitle("Vector Converter");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        showDropZone();
        stage.show();
    }

    private void showDropZone() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/se233/work_space/view/DropZoneView.fxml"));
        Parent root = loader.load();
        DropZoneController controller = loader.getController();
        controller.setOnImagesReady(this::showMain);   // ดรอปเสร็จให้เรียกเมธอดนี้
        setRoot(root);
    }

    private void showMain(ImageSession session) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    MainApp.class.getResource("/se233/work_space/view/MainLayout.fxml"));
            Parent root = loader.load();
            MainController controller = loader.getController();
            controller.setSession(session);
            setRoot(root);
        } catch (IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Cannot open the editor: " + e.getMessage()).show();
        }
    }

    // ครั้งแรกสร้าง Scene ใหม่ หลังจากนั้นเปลี่ยนแค่ root
    public void setRoot(Parent root) {
        if (stage.getScene() == null) {
            Scene scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(
                    MainApp.class.getResource("/se233/work_space/view/Style.css").toExternalForm());
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(root);
        }
    }
    public static void main(String[] args) {
        launch(args);
    }
}
