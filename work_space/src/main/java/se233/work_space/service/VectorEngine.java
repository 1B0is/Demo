package se233.work_space.service;

import se233.work_space.exception.CorruptedImageException;
import se233.work_space.lib.ImageTracer;
import se233.work_space.model.AppSettings;
import se233.work_space.model.AppSettings.ColorMode;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * VectorEngine — The core class that converts raster (jpg/png) into vector (SVG).
 * Uses ImageTracer (lib) as the backend, plus three custom pieces:
 *   1. Builds its own palette (non-random → stable results).
 *   2. Removes background (flood fill from image borders).
 *   3. Renders preview from the SAME tracedata as the SVG (no need to rasterize the SVG).
 */
public class VectorEngine {

    public static final int MAX_DISPLAY_SIDE = 1200;           // Max size for displayed preview
    private static final int MAX_TRACE_SIDE = 2048;            // Downscale before tracing (Custom mode)
    private static final int MAX_TRACE_SIDE_UNLIMITED = 1024;  // Unlimited uses lots of memory → downscale more
    private static final int UNLIMITED_COLORS = 64;

    /** Result of one trace: SVG string + preview image */
    public record TraceResult(String svg, BufferedImage preview) {}

    /** Loaded image + original file dimensions */
    private record Loaded(BufferedImage image, int fullW, int fullH) {}

    /** Background info: layer = palette index of bg, mask = true where bg reaches an edge */
    private record BgInfo(int layer, boolean[][] mask) {}

    // Path classification during render
    private static final int NORMAL = 0, SKIP = 1, HOLE = 2;

    /** Color cluster found in the image (used for palette building) */
    private static final class Cluster {
        double sr, sg, sb;   // Sum of R,G,B across pixels in this cluster
        int count;           // Number of pixels
        boolean transparent;
        double r() { return sr / count; }   // Average R
        double g() { return sg / count; }
        double b() { return sb / count; }
    }

    // ============================================================
    // Section 1: Utility — Image sizing
    // ============================================================

    /** Compute the size used for display (capped at MAX_DISPLAY_SIDE) */
    public static Dimension displaySize(File file) throws CorruptedImageException {
        Dimension full = readFullSize(file);
        double f = Math.min(1.0, (double) MAX_DISPLAY_SIDE / Math.max(full.width, full.height));
        return new Dimension(
                Math.max(1, (int) Math.round(full.width * f)),
                Math.max(1, (int) Math.round(full.height * f)));
    }

    // ============================================================
    // Section 2: Color detection
    // ============================================================

    /**
     * Counts the "real" number of colors in the image
     * (after merging similar colors + removing anti-alias edge colors).
     * Used when loading an image to cap the spinner to the actual color count.
     */
    public static int detectColors(File file) throws CorruptedImageException {
        Loaded in = readForTrace(file, 256);           // Downscale aggressively — we just need an estimate
        List<Cluster> cl = clusters(in.image(), 96);   // Merge colors closer than 96

        int total = 0;
        for (Cluster c : cl) total += c.count;

        List<Cluster> accepted = new ArrayList<>();
        for (Cluster c : cl) {   // Already sorted descending
            if (c.transparent || c.count < total * 0.01) continue;  // Drop colors < 1% of pixels
            if (!isBlend(c, accepted)) accepted.add(c);             // Drop blend colors
        }
        return Math.max(1, accepted.size());
    }

    /**
     * Checks whether color c lies on the line segment between two already-accepted colors.
     * Used to filter out anti-alias colors (e.g. purple pixels along a red-blue edge).
     */
    private static boolean isBlend(Cluster c, List<Cluster> big) {
        double[] p = {c.r(), c.g(), c.b()};
        for (int i = 0; i < big.size(); i++) {
            for (int j = i + 1; j < big.size(); j++) {
                double[] a = {big.get(i).r(), big.get(i).g(), big.get(i).b()};
                double[] b = {big.get(j).r(), big.get(j).g(), big.get(j).b()};
                double[] v = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
                double len2 = v[0]*v[0] + v[1]*v[1] + v[2]*v[2];
                if (len2 < 1) continue;
                double t = ((p[0]-a[0])*v[0] + (p[1]-a[1])*v[1] + (p[2]-a[2])*v[2]) / len2;
                if (t < 0.05 || t > 0.95) continue;
                double dx = p[0] - (a[0] + t*v[0]);
                double dy = p[1] - (a[1] + t*v[1]);
                double dz = p[2] - (a[2] + t*v[2]);
                if (Math.sqrt(dx*dx + dy*dy + dz*dz) < 45) return true;
            }
        }
        return false;
    }

    // ============================================================
    // Section 3: Trace (main entry)
    // ============================================================

    /** Trace without preview (used by export) */
    public TraceResult trace(File file, AppSettings s) throws CorruptedImageException {
        return trace(file, s, 0, 0);
    }

    /**
     * Traces once → returns both SVG string and preview image.
     * @param previewW/H if <= 0 → skip preview rendering
     */
    public TraceResult trace(File file, AppSettings s, int previewW, int previewH)
            throws CorruptedImageException {
        boolean unlimited = s.getColorMode() == ColorMode.UNLIMITED;
        int side = unlimited ? MAX_TRACE_SIDE_UNLIMITED : MAX_TRACE_SIDE;

        Loaded in = readForTrace(file, side);
        // SVG width should match the ORIGINAL file, not the downscaled image
        float svgScale = (float) in.fullW() / in.image().getWidth();

        try {
            int n = Math.max(1, Math.min(AppSettings.MAX_CUSTOM_COLORS, s.getColorCount()));
            byte[][] palette = buildPalette(in.image(), n, unlimited);   // Build palette ourselves (non-random)

            HashMap<String, Float> opts = ImageTracer.checkoptions(buildOptions(s, svgScale, palette.length));
            ImageTracer.ImageData data = ImageTracer.loadImageData(in.image());
            ImageTracer.IndexedImage ii = ImageTracer.imagedataToTracedata(data, opts, palette);

            // Find background (only if user asked for removeBackground)
            BgInfo bg = s.isRemoveBackground() ? findBackground(ii) : null;

            // Pass flip flags into buildSvg/render
            boolean fh = s.isFlipHorizontal(), fv = s.isFlipVertical();
            String svg = buildSvg(ii, opts, bg, fh, fv);
            BufferedImage preview = (previewW > 0) ? render(ii, previewW, previewH, bg, fh, fv) : null;

            return new TraceResult(svg, preview);
        } catch (Exception e) {
            throw new CorruptedImageException("Failed to trace " + file.getName(), e);
        }
    }

    /**
     * Builds ImageTracer options.
     * ltres/qtres/pathomit = path fidelity (lower = finer).
     * blurradius/blurdelta = pre-blur edges to reduce jaggedness.
     */
    private HashMap<String, Float> buildOptions(AppSettings s, float svgScale, int colors) {
        HashMap<String, Float> o = new HashMap<>();

        // Pre-blur to smooth pixel edges before vectorizing
        o.put("blurradius", 1f);
        o.put("blurdelta", 20f);

        switch (s.getDetailLevel()) {
            case LOW -> { o.put("ltres", 8f);   o.put("qtres", 8f);   o.put("pathomit", 80f); }
            case MEDIUM -> { o.put("ltres", 1f);   o.put("qtres", 1f);   o.put("pathomit", 8f); }
            case HIGH -> { o.put("ltres", 0.1f); o.put("qtres", 0.1f); o.put("pathomit", 2f); }
        }
        o.put("numberofcolors", (float) colors);
        o.put("mincolorratio", 0f);      // Disable ImageTracer's color re-sampling → stable output
        o.put("scale", svgScale);
        return o;
    }

    // ============================================================
    // Section 4: Background removal
    // ============================================================

    /**
     * Finds the image's background.
     * Approach:
     *   1. Find the color occupying >= 40% of edge pixels → likely bg.
     *   2. Flood fill from borders to find where bg touches the edges.
     * Returns null if no reliable bg is found.
     */
    private static BgInfo findBackground(ImageTracer.IndexedImage ii) {
        int w = ii.width, h = ii.height;
        int[] count = new int[ii.palette.length];
        int total = 0;

        // Count colors along image edges
        for (int x = 1; x <= w; x++) {
            count[ii.array[1][x]]++;   // top
            count[ii.array[h][x]]++;   // bottom
            total += 2;
        }
        for (int y = 2; y <= h - 1; y++) {
            count[ii.array[y][1]]++;   // left
            count[ii.array[y][w]]++;   // right
            total += 2;
        }

        int best = 0;
        for (int k = 1; k < count.length; k++) if (count[k] > count[best]) best = k;
        if (count[best] < total * 0.4) return null;   // No color dominates edges

        // Flood fill from borders
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

    /** Starts flood fill from pixel (y,x) if it matches the bg color */
    private static void seed(ImageTracer.IndexedImage ii, int layer, boolean[][] mask,
                             ArrayDeque<int[]> q, int y, int x) {
        if (ii.array[y][x] == layer && !mask[y][x]) {
            mask[y][x] = true;
            q.add(new int[]{y, x});
        }
    }

    /**
     * Classifies a path as NORMAL / SKIP / HOLE.
     *   SKIP = bg path touching the image border → don't draw.
     *   HOLE = bg path enclosed inside an element → punch through (transparent hole).
     */
    private static int classify(ImageTracer.IndexedImage ii, int layer, ArrayList<Double[]> segs, BgInfo bg) {
        if (bg == null || layer != bg.layer()) return NORMAL;
        Double[] f = segs.get(0);
        for (int py : candidates(f[2])) {
            for (int px : candidates(f[1])) {
                int ay = py + 1, ax = px + 1;
                if (ay < 1 || ay > ii.height || ax < 1 || ax > ii.width) continue;
                if (ii.array[ay][ax] == layer && bg.mask()[ay][ax]) return SKIP;
            }
        }
        return HOLE;
    }

    /** Path point sits at pixel edge midpoint → find adjacent pixels */
    private static int[] candidates(double c) {
        double r = Math.rint(c);
        if (Math.abs(c - r) < 1e-6) return new int[]{(int) r - 1, (int) r};
        return new int[]{(int) Math.floor(c)};
    }

    /** Builds a Z-order like ImageTracer's getsvgstring (draw top-to-bottom) */
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

    // ============================================================
    // Section 5: Build SVG string
    // ============================================================

    /** Builds a color attribute from a palette entry (b[0..3] = R,G,B,A offset by -128) */
    private static String colorStr(byte[] c) {
        int r = c[0] + 128, g = c[1] + 128, b = c[2] + 128;
        String rgb = "rgb(" + r + "," + g + "," + b + ")";
        return "fill=\"" + rgb + "\" stroke=\"" + rgb + "\" stroke-width=\"1\" opacity=\""
                + ((c[3] + 128) / 255.0) + "\" ";
    }

    /**
     * Builds the SVG string from tracedata.
     * - Iterate paths in Z-order.
     * - SKIP = skip, HOLE = collect for masking later, NORMAL = draw.
     * - If HOLEs remain → create a mask to punch transparent holes.
     * - If flip → wrap body in <g transform="...">.
     */
    private static String buildSvg(ImageTracer.IndexedImage ii, HashMap<String, Float> opts, BgInfo bg,
                                   boolean flipH, boolean flipV) {
        float scale = opts.get("scale");
        int w = (int) (ii.width * scale), h = (int) (ii.height * scale);

        StringBuilder defs = new StringBuilder();     // <defs> for masks
        StringBuilder body = new StringBuilder();     // SVG body
        StringBuilder holes = new StringBuilder();    // pending holes to punch
        int maskId = 0;

        for (int[] e : zorder(ii).values()) {
            ArrayList<Double[]> segs = ii.layers.get(e[0]).get(e[1]);
            int kind = classify(ii, e[0], segs, bg);
            if (kind == SKIP) continue;

            if (kind == HOLE) {
                // Collect hole path for later punching
                ImageTracer.svgpathstring(holes, "", segs, "fill=\"black\" ", opts);
                continue;
            }

            // If there are pending holes → build a mask covering everything drawn so far
            if (holes.length() > 0) {
                if (body.length() > 0) {
                    String id = "bgm" + (maskId++);
                    defs.append("<mask id=\"").append(id).append("\" maskUnits=\"userSpaceOnUse\" x=\"0\" y=\"0\" width=\"")
                            .append(w).append("\" height=\"").append(h)
                            .append("\"><rect width=\"").append(w).append("\" height=\"").append(h)
                            .append("\" fill=\"white\"/>").append(holes).append("</mask>");
                    body.insert(0, "<g mask=\"url(#" + id + ")\">").append("</g>");
                }
                holes.setLength(0);
            }
            ImageTracer.svgpathstring(body, "", segs, colorStr(ii.palette[e[0]]), opts);
        }

        // Punch any remaining holes
        if (holes.length() > 0 && body.length() > 0) {
            String id = "bgm" + (maskId++);
            defs.append("<mask id=\"").append(id).append("\" maskUnits=\"userSpaceOnUse\" x=\"0\" y=\"0\" width=\"")
                    .append(w).append("\" height=\"").append(h)
                    .append("\"><rect width=\"").append(w).append("\" height=\"").append(h)
                    .append("\" fill=\"white\"/>").append(holes).append("</mask>");
            body.insert(0, "<g mask=\"url(#" + id + ")\">").append("</g>");
        }

        // Assemble SVG
        StringBuilder svg = new StringBuilder();
        svg.append("<svg width=\"100%\" height=\"100%\"")
                .append(" viewBox=\"0 0 ").append(w).append(" ").append(h)
                .append("\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\">");
        if (defs.length() > 0) svg.append("<defs>").append(defs).append("</defs>");

        // Wrap with flip transform if needed
        String flip = flipTransform(w, h, flipH, flipV);
        if (flip.isEmpty()) {
            svg.append(body);
        } else {
            svg.append("<g transform=\"").append(flip).append("\">").append(body).append("</g>");
        }
        svg.append("</svg>");
        return svg.toString();
    }

    /** Builds the transform string for H/V/both flips */
    private static String flipTransform(int w, int h, boolean flipH, boolean flipV) {
        if (flipH && flipV) return "translate(" + w + "," + h + ") scale(-1,-1)";
        if (flipH) return "translate(" + w + ",0) scale(-1,1)";
        if (flipV) return "translate(0," + h + ") scale(1,-1)";
        return "";
    }

    // ============================================================
    // Section 6: Palette building
    // ============================================================

    /**
     * Groups image colors — returns Cluster list sorted descending.
     * Uses a 12-bit histogram (R4/G4/B4) for speed.
     */
    private static List<Cluster> clusters(BufferedImage img, int mergeDist) {
        int w = img.getWidth(), h = img.getHeight();
        int step = Math.max(1, (int) Math.sqrt((double) w * h / 40000.0));  // Sample step

        int[] hist = new int[4096];
        long[] sr = new long[4096], sg = new long[4096], sb = new long[4096];
        int transparent = 0;

        // Build histogram
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

        // Sort keys descending by frequency
        List<Integer> keys = new ArrayList<>();
        for (int k = 0; k < 4096; k++) if (hist[k] > 0) keys.add(k);
        keys.sort((a, b) -> hist[b] != hist[a] ? Integer.compare(hist[b], hist[a]) : Integer.compare(a, b));

        // Merge keys closer than mergeDist
        List<Cluster> merged = new ArrayList<>();
        for (int k : keys) {
            double mr = (double) sr[k]/hist[k], mg = (double) sg[k]/hist[k], mb = (double) sb[k]/hist[k];
            Cluster best = null;
            double bestD = mergeDist;
            for (Cluster c : merged) {
                double d = Math.abs(c.r()-mr) + Math.abs(c.g()-mg) + Math.abs(c.b()-mb);
                if (d < bestD) { bestD = d; best = c; }
            }
            if (best == null) { best = new Cluster(); merged.add(best); }
            best.sr += sr[k]; best.sg += sg[k]; best.sb += sb[k];
            best.count += hist[k];
        }

        // Add transparent cluster if any transparent pixels were seen
        if (transparent > 0) {
            Cluster t = new Cluster();
            t.transparent = true;
            t.count = transparent;
            merged.add(t);
        }
        merged.sort((a, b) -> Integer.compare(b.count, a.count));
        return merged;
    }

    /**
     * Picks seed colors for the palette.
     * Starts from the most frequent → adds colors that are both frequent and distinct.
     */
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
                    double d = Math.abs(c.r()-s.r()) + Math.abs(c.g()-s.g()) + Math.abs(c.b()-s.b());
                    if (d < minDist) minDist = d;
                }
                double score = c.count * minDist;   // High frequency + high distinctness = high score
                if (score > bestScore) { bestScore = score; best = c; }
            }
            if (best == null) break;
            seeds.add(best);
        }
        return seeds;
    }

    /**
     * Builds the byte[][] palette used by ImageTracer.
     * Each entry = 4 bytes [R-128, G-128, B-128, A-128].
     */
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
            pal[i][0] = (byte) (r - 128);  // ImageTracer stores colors offset by -128
            pal[i][1] = (byte) (g - 128);
            pal[i][2] = (byte) (b - 128);
            pal[i][3] = (byte) 127;        // Opaque
        }
        if (hasTransparent) {
            pal[colorSlots] = new byte[]{-128, -128, -128, -128};   // Transparent
        }
        return pal;
    }

    // ============================================================
    // Section 7: Preview rendering
    // ============================================================

    /**
     * Renders preview directly from tracedata (no need to rasterize SVG).
     * Uses the same paths as buildSvg → preview matches real SVG.
     */
    private static BufferedImage render(ImageTracer.IndexedImage ii, int w, int h, BgInfo bg,
                                        boolean flipH, boolean flipV) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double sx = (double) w / ii.width, sy = (double) h / ii.height;
            g.scale(sx, sy);

            // Apply flips
            if (flipH) { g.translate(ii.width, 0); g.scale(-1, 1); }
            if (flipV) { g.translate(0, ii.height); g.scale(1, -1); }

            g.setStroke(new BasicStroke((float) (1.0 / sx)));  // ~1px stroke to hide color seams

            for (int[] e : zorder(ii).values()) {
                ArrayList<Double[]> segs = ii.layers.get(e[0]).get(e[1]);
                int kind = classify(ii, e[0], segs, bg);
                if (kind == SKIP) continue;

                // Build Path2D from segments
                Path2D.Double path = new Path2D.Double();
                path.moveTo(segs.get(0)[1], segs.get(0)[2]);
                for (Double[] sg : segs) {
                    if (sg[0] == 1.0) path.lineTo(sg[3], sg[4]);
                    else path.quadTo(sg[3], sg[4], sg[5], sg[6]);
                }
                path.closePath();

                if (kind == HOLE) {
                    // Punch hole: clear alpha at that position
                    g.setComposite(AlphaComposite.Clear);
                    g.fill(path);
                    g.draw(path);
                    g.setComposite(AlphaComposite.SrcOver);
                } else {
                    byte[] c = ii.palette[e[0]];
                    g.setColor(new Color(c[0]+128, c[1]+128, c[2]+128, c[3]+128));
                    g.fill(path);
                    g.draw(path);
                }
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    // ============================================================
    // Section 8: Reading images
    // ============================================================

    /** Finds the ImageReader matching the file */
    private static ImageReader firstReader(ImageInputStream in, File file) throws CorruptedImageException {
        if (in == null) throw new CorruptedImageException("Cannot open image: " + file.getName());
        Iterator<ImageReader> it = ImageIO.getImageReaders(in);
        if (!it.hasNext()) throw new CorruptedImageException("Not a valid JPG/PNG image: " + file.getName());
        return it.next();
    }

    /** Reads only the dimensions (doesn't load pixels) — very fast */
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

    /**
     * Reads the image with subsampling (skip every N pixels) to avoid loading
     * the full-size image into memory. Great for multi-MB images.
     */
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

    /** Downscales an image so its largest side <= maxSide (bilinear interpolation) */
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