package se233.work_space.service;

import se233.work_space.exception.CorruptedImageException;
import se233.work_space.lib.ImageTracer;
import se233.work_space.model.AppSettings;
import se233.work_space.model.AppSettings.ColorMode;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

// แปลงภาพเป็น SVG (ใช้ ImageTracer) + วาดพรีวิวจากข้อมูลเส้นชุดเดียวกัน
public class VectorEngine {

    public static final int MAX_DISPLAY_SIDE = 1200;         // ขนาดภาพที่ใช้แสดงผล
    private static final int MAX_TRACE_SIDE = 1000;          // ย่อภาพก่อน trace (Custom)
    private static final int MAX_TRACE_SIDE_UNLIMITED = 700; // Unlimited สีเยอะ ใช้ memory มาก
    private static final int UNLIMITED_COLORS = 64;

    public record TraceResult(String svg, BufferedImage preview) {}
    private record Loaded(BufferedImage image, int fullW, int fullH) {}
    // layer = ชั้นสีพื้นหลัง, mask[y][x] = true ถ้าพิกเซลนั้นเป็นพื้นหลังที่ต่อถึงขอบภาพ (พิกัดเดียวกับ ii.array)
    private record BgInfo(int layer, boolean[][] mask) {}

    private static final int NORMAL = 0, SKIP = 1, HOLE = 2;

    // กลุ่มสีที่ตรวจเจอในภาพ
    private static final class Cluster {
        double sr, sg, sb;
        int count;
        boolean transparent;
        double r() { return sr / count; }
        double g() { return sg / count; }
        double b() { return sb / count; }
    }

    // ---------- ขนาดแสดงผล ----------
    public static Dimension displaySize(File file) throws CorruptedImageException {
        Dimension full = readFullSize(file);
        double f = Math.min(1.0, (double) MAX_DISPLAY_SIDE / Math.max(full.width, full.height));
        return new Dimension(Math.max(1, (int) Math.round(full.width * f)),
                Math.max(1, (int) Math.round(full.height * f)));
    }

    // ---------- ตรวจจับจำนวนสีของภาพ ----------
    // 1) รวมสีที่ใกล้กัน (เฉดของสีเดียวกันจาก noise)
    // 2) ไม่นับสีที่เป็นแค่ "รอยต่อ" ระหว่างสีใหญ่สองสี (เช่นขอบ anti-alias สีม่วงระหว่างแดงกับน้ำเงิน)
    public static int detectColors(File file) throws CorruptedImageException {
        Loaded in = readForTrace(file, 256);
        List<Cluster> cl = clusters(in.image(), 96);
        int total = 0;
        for (Cluster c : cl) total += c.count;

        List<Cluster> accepted = new ArrayList<>();
        for (Cluster c : cl) {   // เรียงจากมากไปน้อยแล้ว
            if (c.transparent || c.count < total * 0.01) continue;
            if (!isBlend(c, accepted)) accepted.add(c);
        }
        return Math.max(1, accepted.size());
    }

    // true ถ้าสี c อยู่บนเส้นตรงระหว่างสีที่รับไว้แล้วสองสี (ใกล้เส้นไม่เกิน ~45 หน่วย RGB)
    private static boolean isBlend(Cluster c, List<Cluster> big) {
        double[] p = {c.r(), c.g(), c.b()};
        for (int i = 0; i < big.size(); i++) {
            for (int j = i + 1; j < big.size(); j++) {
                double[] a = {big.get(i).r(), big.get(i).g(), big.get(i).b()};
                double[] b = {big.get(j).r(), big.get(j).g(), big.get(j).b()};
                double[] v = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
                double len2 = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
                if (len2 < 1) continue;
                double t = ((p[0] - a[0]) * v[0] + (p[1] - a[1]) * v[1] + (p[2] - a[2]) * v[2]) / len2;
                if (t < 0.05 || t > 0.95) continue;
                double dx = p[0] - (a[0] + t * v[0]), dy = p[1] - (a[1] + t * v[1]), dz = p[2] - (a[2] + t * v[2]);
                if (Math.sqrt(dx * dx + dy * dy + dz * dz) < 45) return true;
            }
        }
        return false;
    }

    // ---------- trace ----------
    // ใช้ตอน export (ไม่ต้องวางพรีวิว)
    public TraceResult trace(File file, AppSettings s) throws CorruptedImageException {
        return trace(file, s, 0, 0);
    }

    // previewW/H <= 0 หมายถึงไม่ต้องวาดพรีวิว
    public TraceResult trace(File file, AppSettings s, int previewW, int previewH)
            throws CorruptedImageException {
        boolean unlimited = s.getColorMode() == ColorMode.UNLIMITED;
        int side = unlimited ? MAX_TRACE_SIDE_UNLIMITED : MAX_TRACE_SIDE;
        Loaded in = readForTrace(file, side);
        float svgScale = (float) in.fullW() / in.image().getWidth();   // ให้ SVG กว้างเท่าภาพจริง

        try {
            int n = Math.max(1, Math.min(AppSettings.MAX_CUSTOM_COLORS, s.getColorCount()));
            byte[][] palette = buildPalette(in.image(), n, unlimited);   // คำนวณเอง ไม่สุ่ม ผลจึงนิ่ง

            HashMap<String, Float> opts = ImageTracer.checkoptions(buildOptions(s, svgScale, palette.length));
            ImageTracer.ImageData data = ImageTracer.loadImageData(in.image());
            ImageTracer.IndexedImage ii = ImageTracer.imagedataToTracedata(data, opts, palette);

            BgInfo bg = s.isRemoveBackground() ? findBackground(ii) : null;

            String svg = buildSvg(ii, opts, bg);
            BufferedImage preview = (previewW > 0) ? render(ii, previewW, previewH, bg) : null;
            return new TraceResult(svg, preview);
        } catch (Exception e) {
            throw new CorruptedImageException("Failed to trace " + file.getName(), e);
        }
    }

    private HashMap<String, Float> buildOptions(AppSettings s, float svgScale, int colors) {
        HashMap<String, Float> o = new HashMap<>();
        switch (s.getDetailLevel()) {
            case LOW -> { o.put("ltres", 8f);   o.put("qtres", 8f);   o.put("pathomit", 80f); }
            case MEDIUM -> { o.put("ltres", 1f);   o.put("qtres", 1f);   o.put("pathomit", 8f); }
            case HIGH -> { o.put("ltres", 0.5f); o.put("qtres", 0.5f); o.put("pathomit", 1f); }
        }
        o.put("numberofcolors", (float) colors);
        o.put("mincolorratio", 0f);      // ปิดการสุ่มสีใหม่ของ ImageTracer เพื่อให้ผลนิ่ง
        o.put("scale", svgScale);
        return o;
    }

    // ---------- หาพื้นหลัง ----------
    // พื้นหลัง = สีที่ครองขอบภาพ (>= 40% ของพิกเซลขอบ) แล้วไล่ flood fill จากขอบเพื่อหาส่วนที่ต่อถึงขอบ
    private static BgInfo findBackground(ImageTracer.IndexedImage ii) {
        int w = ii.width, h = ii.height;
        int[] count = new int[ii.palette.length];
        int total = 0;
        for (int x = 1; x <= w; x++) {
            count[ii.array[1][x]]++;
            count[ii.array[h][x]]++;
            total += 2;
        }
        for (int y = 2; y <= h - 1; y++) {
            count[ii.array[y][1]]++;
            count[ii.array[y][w]]++;
            total += 2;
        }
        int best = 0;
        for (int k = 1; k < count.length; k++) if (count[k] > count[best]) best = k;
        if (count[best] < total * 0.4) return null;   // ขอบภาพไม่มีสีใดครอง: ไม่ลบอะไร

        boolean[][] mask = new boolean[h + 2][w + 2];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int x = 1; x <= w; x++) { seed(ii, best, mask, q, 1, x); seed(ii, best, mask, q, h, x); }
        for (int y = 1; y <= h; y++) { seed(ii, best, mask, q, y, 1); seed(ii, best, mask, q, y, w); }
        int[] dy = {1, -1, 0, 0}, dx = {0, 0, 1, -1};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int d = 0; d < 4; d++) {
                int ny = p[0] + dy[d], nx = p[1] + dx[d];
                if (ny < 1 || ny > h || nx < 1 || nx > w) continue;
                if (mask[ny][nx] || ii.array[ny][nx] != best) continue;
                mask[ny][nx] = true;
                q.add(new int[]{ny, nx});
            }
        }
        return new BgInfo(best, mask);
    }

    private static void seed(ImageTracer.IndexedImage ii, int layer, boolean[][] mask,
                             ArrayDeque<int[]> q, int y, int x) {
        if (ii.array[y][x] == layer && !mask[y][x]) {
            mask[y][x] = true;
            q.add(new int[]{y, x});
        }
    }

    // แยกประเภท path: NORMAL = วาดปกติ, SKIP = พื้นหลังที่ต่อถึงขอบ (ไม่วาด),
    // HOLE = พื้นหลังที่ถูกล้อมอยู่ข้างใน element (ต้องเจาะให้โปร่งใส)
    private static int classify(ImageTracer.IndexedImage ii, int layer, ArrayList<Double[]> segs, BgInfo bg) {
        if (bg == null || layer != bg.layer()) return NORMAL;
        Double[] f = segs.get(0);
        for (int py : candidates(f[2])) {
            for (int px : candidates(f[1])) {
                int ay = py + 1, ax = px + 1;   // พิกัดใน ii.array (มีขอบเพิ่ม 1)
                if (ay < 1 || ay > ii.height || ax < 1 || ax > ii.width) continue;
                if (ii.array[ay][ax] == layer && bg.mask()[ay][ax]) return SKIP;
            }
        }
        return HOLE;
    }

    // จุดของ path อยู่กึ่งกลางขอบพิกเซล: หาพิกเซลที่อยู่ติดกับจุดนั้น
    private static int[] candidates(double c) {
        double r = Math.rint(c);
        if (Math.abs(c - r) < 1e-6) return new int[]{(int) r - 1, (int) r};
        return new int[]{(int) Math.floor(c)};
    }

    // ลำดับการวาด (Z-index) เหมือน getsvgstring ของ ImageTracer
    private static TreeMap<Double, int[]> zorder(ImageTracer.IndexedImage ii) {
        TreeMap<Double, int[]> z = new TreeMap<>();
        for (int k = 0; k < ii.layers.size(); k++) {
            for (int p = 0; p < ii.layers.get(k).size(); p++) {
                Double[] first = ii.layers.get(k).get(p).get(0);
                z.put(first[2] * ii.width + first[1], new int[]{k, p});
            }
        }
        return z;
    }

    // ---------- สร้างสตริง SVG ----------
    private static String colorStr(byte[] c) {
        int r = c[0] + 128, g = c[1] + 128, b = c[2] + 128;
        String rgb = "rgb(" + r + "," + g + "," + b + ")";
        return "fill=\"" + rgb + "\" stroke=\"" + rgb + "\" stroke-width=\"1\" opacity=\""
                + ((c[3] + 128) / 255.0) + "\" ";
    }

    private static String buildSvg(ImageTracer.IndexedImage ii, HashMap<String, Float> opts, BgInfo bg) {
        float scale = opts.get("scale");
        int w = (int) (ii.width * scale), h = (int) (ii.height * scale);

        StringBuilder defs = new StringBuilder();
        StringBuilder body = new StringBuilder();
        StringBuilder holes = new StringBuilder();   // รูโปร่งใสที่รอเจาะ
        int maskId = 0;

        for (int[] e : zorder(ii).values()) {
            ArrayList<Double[]> segs = ii.layers.get(e[0]).get(e[1]);
            int kind = classify(ii, e[0], segs, bg);
            if (kind == SKIP) continue;
            if (kind == HOLE) {
                ImageTracer.svgpathstring(holes, "", segs, "fill=\"black\" ", opts);
                continue;
            }
            if (holes.length() > 0) {   // มีรูรอเจาะ: เจาะทุกอย่างที่วาดมาก่อนหน้านี้
                if (body.length() > 0) {
                    String id = "bgm" + (maskId++);
                    defs.append("<mask id=\"").append(id).append("\" maskUnits=\"userSpaceOnUse\" x=\"0\" y=\"0\" width=\"")
                            .append(w).append("\" height=\"").append(h).append("\"><rect width=\"").append(w)
                            .append("\" height=\"").append(h).append("\" fill=\"white\"/>").append(holes).append("</mask>");
                    body.insert(0, "<g mask=\"url(#" + id + ")\">").append("</g>");
                }
                holes.setLength(0);
            }
            ImageTracer.svgpathstring(body, "", segs, colorStr(ii.palette[e[0]]), opts);
        }
        if (holes.length() > 0 && body.length() > 0) {
            String id = "bgm" + (maskId++);
            defs.append("<mask id=\"").append(id).append("\" maskUnits=\"userSpaceOnUse\" x=\"0\" y=\"0\" width=\"")
                    .append(w).append("\" height=\"").append(h).append("\"><rect width=\"").append(w)
                    .append("\" height=\"").append(h).append("\" fill=\"white\"/>").append(holes).append("</mask>");
            body.insert(0, "<g mask=\"url(#" + id + ")\">").append("</g>");
        }

        StringBuilder svg = new StringBuilder();
        svg.append("<svg width=\"").append(w).append("\" height=\"").append(h)
                .append("\" viewBox=\"0 0 ").append(w).append(" ").append(h)
                .append("\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\">");
        if (defs.length() > 0) svg.append("<defs>").append(defs).append("</defs>");
        svg.append(body).append("</svg>");
        return svg.toString();
    }

    // ---------- สร้าง palette แบบไม่สุ่ม ----------
    private static List<Cluster> clusters(BufferedImage img, int mergeDist) {
        int w = img.getWidth(), h = img.getHeight();
        int step = Math.max(1, (int) Math.sqrt((double) w * h / 40000.0));
        int[] hist = new int[4096];
        long[] sr = new long[4096], sg = new long[4096], sb = new long[4096];
        int transparent = 0;

        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int p = img.getRGB(x, y);
                if ((p >>> 24) < 128) { transparent++; continue; }
                int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                int key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                hist[key]++;
                sr[key] += r; sg[key] += g; sb[key] += b;
            }
        }

        List<Integer> keys = new ArrayList<>();
        for (int k = 0; k < 4096; k++) if (hist[k] > 0) keys.add(k);
        keys.sort((a, b) -> hist[b] != hist[a] ? Integer.compare(hist[b], hist[a]) : Integer.compare(a, b));

        // รวมกลุ่มสีที่ใกล้กัน (เริ่มจากสีที่พบมากที่สุด)
        List<Cluster> merged = new ArrayList<>();
        for (int k : keys) {
            double mr = (double) sr[k] / hist[k], mg = (double) sg[k] / hist[k], mb = (double) sb[k] / hist[k];
            Cluster best = null;
            double bestD = mergeDist;
            for (Cluster c : merged) {
                double d = Math.abs(c.r() - mr) + Math.abs(c.g() - mg) + Math.abs(c.b() - mb);
                if (d < bestD) { bestD = d; best = c; }
            }
            if (best == null) { best = new Cluster(); merged.add(best); }
            best.sr += sr[k]; best.sg += sg[k]; best.sb += sb[k];
            best.count += hist[k];
        }
        if (transparent > 0) {
            Cluster t = new Cluster();
            t.transparent = true;
            t.count = transparent;
            merged.add(t);
        }
        merged.sort((a, b) -> Integer.compare(b.count, a.count));
        return merged;
    }

    // เลือกสีตั้งต้น: เริ่มจากสีที่พบมากสุด แล้วเพิ่มสีที่ "พบมากและต่างจากที่เลือกไปแล้วมาก"
    private static List<Cluster> pickSeeds(List<Cluster> opaque, int want) {
        List<Cluster> seeds = new ArrayList<>();
        if (opaque.isEmpty()) return seeds;
        seeds.add(opaque.get(0));
        int limit = Math.min(want, opaque.size());
        while (seeds.size() < limit) {
            Cluster best = null;
            double bestScore = -1;
            for (Cluster c : opaque) {
                if (seeds.contains(c)) continue;
                double minDist = Double.MAX_VALUE;
                for (Cluster s : seeds) {
                    double d = Math.abs(c.r() - s.r()) + Math.abs(c.g() - s.g()) + Math.abs(c.b() - s.b());
                    if (d < minDist) minDist = d;
                }
                double score = c.count * minDist;
                if (score > bestScore) { bestScore = score; best = c; }
            }
            if (best == null) break;
            seeds.add(best);
        }
        return seeds;
    }

    private static byte[][] buildPalette(BufferedImage img, int customCount, boolean unlimited) {
        List<Cluster> all = clusters(img, unlimited ? 24 : 48);
        List<Cluster> opaque = new ArrayList<>();
        int total = 0, transCount = 0;
        for (Cluster c : all) {
            total += c.count;
            if (c.transparent) transCount = c.count; else opaque.add(c);
        }
        boolean hasTransparent = total > 0 && transCount >= total * 0.01;

        int want = unlimited ? Math.min(UNLIMITED_COLORS, Math.max(1, opaque.size())) : customCount;
        List<Cluster> seeds = pickSeeds(opaque, want);
        int colorSlots = Math.max(1, seeds.size());
        byte[][] pal = new byte[colorSlots + (hasTransparent ? 1 : 0)][4];

        for (int i = 0; i < colorSlots; i++) {
            int r = 0, g = 0, b = 0;
            if (i < seeds.size()) {
                Cluster c = seeds.get(i);
                r = (int) Math.round(c.r());
                g = (int) Math.round(c.g());
                b = (int) Math.round(c.b());
            }
            pal[i][0] = (byte) (r - 128);   // ImageTracer เก็บค่าสีแบบเลื่อน -128
            pal[i][1] = (byte) (g - 128);
            pal[i][2] = (byte) (b - 128);
            pal[i][3] = (byte) 127;         // ทึบ
        }
        if (hasTransparent) {
            pal[colorSlots] = new byte[]{-128, -128, -128, -128};   // สีโปร่งใส
        }
        return pal;
    }

    // ---------- วาดพรีวิวจากข้อมูล trace ----------
    private static BufferedImage render(ImageTracer.IndexedImage ii, int w, int h, BgInfo bg) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double sx = (double) w / ii.width, sy = (double) h / ii.height;
            g.scale(sx, sy);
            g.setStroke(new BasicStroke((float) (1.0 / sx)));   // เส้นขอบ ~1 พิกเซล กันรอยต่อระหว่างสี

            for (int[] e : zorder(ii).values()) {
                ArrayList<Double[]> segs = ii.layers.get(e[0]).get(e[1]);
                int kind = classify(ii, e[0], segs, bg);
                if (kind == SKIP) continue;

                Path2D.Double path = new Path2D.Double();
                path.moveTo(segs.get(0)[1], segs.get(0)[2]);
                for (Double[] sg : segs) {
                    if (sg[0] == 1.0) path.lineTo(sg[3], sg[4]);
                    else path.quadTo(sg[3], sg[4], sg[5], sg[6]);
                }
                path.closePath();

                if (kind == HOLE) {
                    g.setComposite(AlphaComposite.Clear);    // เจาะรูให้โปร่งใส
                    g.fill(path);
                    g.draw(path);
                    g.setComposite(AlphaComposite.SrcOver);
                } else {
                    byte[] c = ii.palette[e[0]];
                    g.setColor(new Color(c[0] + 128, c[1] + 128, c[2] + 128, c[3] + 128));
                    g.fill(path);
                    g.draw(path);
                }
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