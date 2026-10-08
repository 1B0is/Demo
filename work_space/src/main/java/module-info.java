module se233.work_space {
    requires javafx.controls;
    requires javafx.fxml;
    requires jdk.compiler;
    requires java.desktop;


    opens se233.work_space to javafx.fxml;
    opens se233.work_space.controller to javafx.fxml;
    exports se233.work_space;
}