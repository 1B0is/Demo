package se233.work_space.service;

import se233.work_space.exception.CorruptedImageException;
import se233.work_space.lib.ImageTracer;
import se233.work_space.model.AppSettings;
import se233.work_space.model.AppSettings.ColorMode;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

// แปลงภาพเป็น SVG (ใช้ ImageTracer) + วาดพรีวิวจากข้อมูลเส้นชุดเดียวกัน
public class VectorEngine {

    public static final int MAX_DISPLAY_SIDE = 1200;       // ขนาดภาพที่ใช้แสดงผล
    private static final int MAX_TRACE_SIDE = 1000;        // ย่อภาพก่อน trace (Custom)
    private static final int MAX_TRACE_SIDE_UNLIMITED = 700; // Unlimited สีเยอะ ใช้ memory มาก
    private static final int UNLIMITED_COLORS = 64;

    public record TraceResult(String svg, BufferedImage preview) {}
    private record Loaded(BufferedImage image, int fullW, int fullH) {}

    // ขนาดแสดงผล: ทั้งภาพต้นฉบับและผลลัพธ์ใช้ขนาดนี้ ซูมจึงตรงกัน
    public static Dimension displaySize(File file) throws CorruptedImageException {
        Dimension full = readFullSize(file);
        double f = Math.min(1.0, (double) MAX_DISPLAY_SIDE / Math.max(full.width, full.height));
        return new Dimension(Math.max(1, (int) Math.round(full.width * f)),
                Math.max(1, (int) Math.round(full.height * f)));
    }

    // ใช้ตอน export (ไม่ต้องวางพรีวิว)
    public TraceResult trace(File file, AppSettings s) throws CorruptedImageException {
        return trace(file, s, 0, 0);
    }

    // previewW/H <= 0 หมายถึงไม่ต้องวาดพรีวิว
    public TraceResult trace(File file, AppSettings s, int previewW, int previewH)
            throws CorruptedImageException {
        int side = (s.getColorMode() == ColorMode.CUSTOM) ? MAX_TRACE_SIDE : MAX_TRACE_SIDE_UNLIMITED;
        Loaded in = readForTrace(file, side);
        // ให้ SVG มีขนาดเท่าภาพจริง แม้จะ trace จากภาพที่ย่อแล้ว
        float svgScale = (float) in.fullW() / in.image().getWidth();

        try {
            HashMap<String, Float> opts = ImageTracer.checkoptions(buildOptions(s, svgScale));
            ImageTracer.ImageData data = ImageTracer.loadImageData(in.image());
            ImageTracer.IndexedImage ii = ImageTracer.imagedataToTracedata(data, opts, null);
            String svg = ImageTracer.getsvgstring(ii, opts);
            if (svg == null || svg.isBlank()) {
                throw new CorruptedImageException("Tracing produced no output: " + file.getName());
            }
            BufferedImage preview = (previewW > 0) ? render(ii, previewW, previewH) : null;
            return new TraceResult(svg, preview);
        } catch (CorruptedImageException e) {
            throw e;
        } catch (Exception e) {
            throw new CorruptedImageException("Failed to trace " + file.getName(), e);
        }
    }

    private HashMap<String, Float> buildOptions(AppSettings s, float svgScale) {
        HashMap<String, Float> o = new HashMap<>();
        switch (s.getDetailLevel()) {
            case LOW -> { o.put("ltres", 5f);   o.put("qtres", 5f);   o.put("pathomit", 32f); }
            case MEDIUM -> { o.put("ltres", 1f);   o.put("qtres", 1f);   o.put("pathomit", 8f); }
            case HIGH -> { o.put("ltres", 0.5f); o.put("qtres", 0.5f); o.put("pathomit", 1f); }
        }
        int colors = (s.getColorMode() == ColorMode.CUSTOM) ? s.getColorCount() : UNLIMITED_COLORS;
        o.put("numberofcolors", (float) colors);
        o.put("scale", svgScale);
        return o;
    }

    // ---------- วาดพรีวิวจากข้อมูล trace (ลำดับชั้นเหมือน getsvgstring) ----------
    private static BufferedImage render(ImageTracer.IndexedImage ii, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double sx = (double) w / ii.width, sy = (double) h / ii.height;
            g.scale(sx, sy);
            g.setStroke(new BasicStroke((float) (1.0 / sx)));   // เส้นขอบ ~1 พิกเซล กันรอยต่อระหว่างสี

            TreeMap<Double, int[]> zindex = new TreeMap<>();
            for (int k = 0; k < ii.layers.size(); k++) {
                for (int p = 0; p < ii.layers.get(k).size(); p++) {
                    Double[] first = ii.layers.get(k).get(p).get(0);
                    zindex.put(first[2] * ii.width + first[1], new int[]{k, p});
                }
            }

            for (int[] e : zindex.values()) {
                ArrayList<Double[]> segs = ii.layers.get(e[0]).get(e[1]);
                byte[] c = ii.palette[e[0]];
                Color color = new Color(c[0] + 128, c[1] + 128, c[2] + 128, c[3] + 128);

                Path2D.Double path = new Path2D.Double();
                path.moveTo(segs.get(0)[1], segs.get(0)[2]);
                for (Double[] sg : segs) {
                    if (sg[0] == 1.0) path.lineTo(sg[3], sg[4]);
                    else path.quadTo(sg[3], sg[4], sg[5], sg[6]);
                }
                path.closePath();

                g.setColor(color);
                g.fill(path);
                g.draw(path);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    // ---------- อ่านภาพ ----------
    private static ImageReader firstReader(ImageInputStream in, File file) throws CorruptedImageException {
        if (in == null) throw new CorruptedImageException("Cannot open image: " + file.getName());
        Iterator<ImageReader> it = ImageIO.getImageReaders(in);
        if (!it.hasNext()) throw new CorruptedImageException("Not a valid JPG/PNG image: " + file.getName());
        return it.next();
    }

    private static Dimension readFullSize(File file) throws CorruptedImageException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            ImageReader reader = firstReader(in, file);
            try {
                reader.setInput(in, true, true);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw new CorruptedImageException("Cannot read image: " + file.getName(), e);
        }
    }

    // อ่านแบบ subsample เพื่อไม่ต้องโหลดภาพเต็มขนาดเข้า memory
    private static Loaded readForTrace(File file, int maxSide) throws CorruptedImageException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            ImageReader reader = firstReader(in, file);
            try {
                reader.setInput(in, true, true);
                int fullW = reader.getWidth(0), fullH = reader.getHeight(0);
                int step = Math.max(1, Math.max(fullW, fullH) / maxSide);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage img = reader.read(0, param);
                return new Loaded(shrink(img, maxSide), fullW, fullH);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw new CorruptedImageException("Cannot read image: " + file.getName(), e);
        }
    }

    private static BufferedImage shrink(BufferedImage src, int maxSide) {
        int big = Math.max(src.getWidth(), src.getHeight());
        if (big <= maxSide) return src;
        double f = (double) maxSide / big;
        int nw = Math.max(1, (int) Math.round(src.getWidth() * f));
        int nh = Math.max(1, (int) Math.round(src.getHeight() * f));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }
}