package se233.work_space;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

//(extends Application, โหลดหน้า DropZone, ส่ง ImageSession ต่อ)
public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/se233/work_space/view/DropZoneView.fxml"));
        Scene scene = new Scene(loader.load(),900,600);
        scene.getStylesheets().add(
                MainApp.class.getResource("/se233/work_space/view/Style.css").toExternalForm());
        stage.setTitle("Vector Converter");
        stage.setScene(scene);
        System.out.println("Controller = " + loader.getController());
        stage.show();
    }
    public static void main(String[] args) {
        launch(args);
    }
}
