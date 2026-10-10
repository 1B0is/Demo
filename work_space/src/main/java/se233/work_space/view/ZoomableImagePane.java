package se233.work_space.view;

import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

public class ZoomableImagePane extends ScrollPane {
    private ImageView imageView;
    private StackPane zoomPane;
    private double scaleValue = 1.0;
    private final double SCALE_DELTA = 1.1;

    public ZoomableImagePane() {
        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        zoomPane = new StackPane(imageView);
        zoomPane.setAlignment(Pos.CENTER);

        this.setContent(zoomPane);
        this.setFitToWidth(true);
        this.setFitToHeight(true);
        this.setPannable(true);
    }

    public void setImage(Image image) {
        imageView.setImage(image);
        zoomToFit();
    }

    public void zoomIn() {
        scaleValue *= SCALE_DELTA;
        updateScale();
    }

    public void zoomOut() {
        scaleValue /= SCALE_DELTA;
        updateScale();
    }

    public void zoomToFit() {
        // รีเซ็ตการซูมให้กลับมาพอดีจอ[cite: 3]
        scaleValue = 1.0;
        updateScale();
        if (imageView.getImage() != null) {
            imageView.setFitWidth(this.getWidth());
            imageView.setFitHeight(this.getHeight());
        }
    }

    private void updateScale() {
        imageView.setFitWidth(0); // ล้างค่า Fit เพื่อให้ Scale ทำงานอิสระ
        imageView.setFitHeight(0);
        imageView.setScaleX(scaleValue);
        imageView.setScaleY(scaleValue);
    }
}