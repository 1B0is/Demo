package se233.work_space.controller;

import se233.work_space.view.ZoomableImagePane;
import java.util.List;
import javafx.scene.image.Image;

public class PreviewController {

    private ZoomableImagePane originalPane;
    private ZoomableImagePane vectorPane;
    private List<Image> imageList; // หรือคลาส ImageItem ที่boingสร้าง
    private int currentIndex = 0;

    public PreviewController(ZoomableImagePane originalPane, ZoomableImagePane vectorPane) {
        this.originalPane = originalPane;
        this.vectorPane = vectorPane;
    }

    public void updateImages(Image original, Image vectorized) {
        originalPane.setImage(original);
        vectorPane.setImage(vectorized);
    }

    public void zoomIn() {
        originalPane.zoomIn();
        vectorPane.zoomIn();
    }

    public void zoomOut() {
        originalPane.zoomOut();
        vectorPane.zoomOut();
    }

    public void zoomFit() {
        originalPane.zoomToFit();
        vectorPane.zoomToFit();
    }

    public void showNextImage() {
        if (imageList != null && currentIndex < imageList.size() - 1) {
            currentIndex++;
            // TODO: เรียกภาพใหม่มาใส่ pane[cite: 3]
        }
    }

    public void showPreviousImage() {
        if (currentIndex > 0) {
            currentIndex--;
            // TODO: เรียกภาพก่อนหน้ามาใส่ pane[cite: 3]
        }
    }
}