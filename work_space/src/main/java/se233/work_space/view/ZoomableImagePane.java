package se233.work_space.view;

import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;

public class ZoomableImagePane extends ScrollPane {
    private static final double SCALE_DELTA = 1.25;
    private static final double MIN_SCALE = 0.05;
    private static final double MAX_SCALE = 16.0;

    private final ImageView imageView = new ImageView();
    private final StackPane zoomPane = new StackPane(imageView);
    private double scaleValue = 1.0;
    private boolean fitMode = true;      // true = พอดีกรอบ และปรับตามขนาดหน้าต่างอัตโนมัติ

    public ZoomableImagePane() {
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        zoomPane.setAlignment(Pos.CENTER);

        // เนื้อหาต้องใหญ่อย่างน้อยเท่ากรอบ เพื่อให้ภาพอยู่กลางตอนเล็กกว่ากรอบ
        // และขยายเกินกรอบได้ตอนซูม (จึงไม่ใช้ setFitToWidth/Height)
        zoomPane.minWidthProperty().bind(Bindings.createDoubleBinding(
                () -> getViewportBounds().getWidth(), viewportBoundsProperty()));
        zoomPane.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> getViewportBounds().getHeight(), viewportBoundsProperty()));

        setContent(zoomPane);
        setPannable(true);
        setMinSize(0, 0);
        setPrefSize(100, 100);           // กันภาพใหญ่ดันหน้าต่างให้โตเอง

        // ย่อ/ขยายหน้าต่าง: ถ้าอยู่โหมด fit ให้คำนวณใหม่
        viewportBoundsProperty().addListener((obs, o, n) -> {
            if (fitMode) applyFit();
        });

        // Ctrl + ล้อเมาส์ = ซูม (ไม่บังคับ)
        addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown() && e.getDeltaY() != 0) {
                if (e.getDeltaY() > 0) zoomIn(); else zoomOut();
                e.consume();
            }
        });
    }

    // เปลี่ยนภาพโดยคงโหมดซูมปัจจุบันไว้ (ผู้ใช้ปรับค่า Detail แล้วไม่เสียระดับซูม)
    public void setImage(Image image) {
        imageView.setImage(image);
        if (image != null && image.getProgress() < 1.0) {
            image.progressProperty().addListener((obs, o, n) -> {
                if (n.doubleValue() >= 1.0) refresh();
            });
        }
        refresh();
    }

    public void zoomIn() {
        fitMode = false;
        scaleValue = Math.min(MAX_SCALE, scaleValue * SCALE_DELTA);
        applyScale();
    }

    public void zoomOut() {
        fitMode = false;
        scaleValue = Math.max(MIN_SCALE, scaleValue / SCALE_DELTA);
        applyScale();
    }

    public void zoomToFit() {
        fitMode = true;
        applyFit();
    }

    private void refresh() {
        if (fitMode) applyFit(); else applyScale();
    }

    private void applyFit() {
        Image img = imageView.getImage();
        if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) return;
        Bounds vp = getViewportBounds();
        double w = vp.getWidth() - 4, h = vp.getHeight() - 4;
        if (w <= 0 || h <= 0) return;            // ยังไม่ได้วางหน้าจอ: listener จะเรียกซ้ำเมื่อพร้อม
        scaleValue = Math.max(MIN_SCALE, Math.min(MAX_SCALE,
                Math.min(w / img.getWidth(), h / img.getHeight())));
        applyScale();
    }

    private void applyScale() {
        Image img = imageView.getImage();
        if (img == null) return;
        imageView.setFitWidth(img.getWidth() * scaleValue);
        imageView.setFitHeight(img.getHeight() * scaleValue);
    }
}