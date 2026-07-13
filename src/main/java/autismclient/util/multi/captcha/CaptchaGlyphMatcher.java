package autismclient.util.multi.captcha;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

public final class CaptchaGlyphMatcher {

    static final int GRID_W = 32;
    static final int GRID_H = 48;

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private record Template(char ch, boolean[] mask, int inkCount, double aspect, boolean asc, boolean desc) {
    }

    private record Rendered(boolean[] mask, double aspect) {
    }

    private final List<Template> templates = new ArrayList<>();
    private final boolean heightAware;

    public CaptchaGlyphMatcher(Font font, String charset, double[] shears, boolean heightAware) {
        this.heightAware = heightAware;
        for (int i = 0; i < charset.length(); i++) {
            char ch = charset.charAt(i);
            boolean asc = isAscender(ch);
            boolean desc = isDescender(ch);
            for (double shear : shears) {
                Rendered r = renderNormalized(font, ch, shear);
                if (r != null && r.mask() != null) {
                    int ink = 0;
                    for (boolean b : r.mask()) if (b) ink++;
                    if (ink > 0) templates.add(new Template(ch, r.mask(), ink, r.aspect(), asc, desc));
                }
            }
        }
    }

    private static boolean isAscender(char ch) {
        return "bdfhkt".indexOf(ch) >= 0;
    }

    private static boolean isDescender(char ch) {
        return "jpy".indexOf(ch) >= 0;
    }

    private static Rendered renderNormalized(Font font, char ch, double shear) {
        GlyphVector gv = font.createGlyphVector(FRC, String.valueOf(ch));
        java.awt.Shape outline = gv.getOutline();
        if (Math.abs(shear) > 1e-6) {
            AffineTransform sh = AffineTransform.getShearInstance(shear, shear);
            outline = sh.createTransformedShape(outline);
        }
        Rectangle2D b = outline.getBounds2D();
        if (b.getWidth() < 1 || b.getHeight() < 1) return null;
        double aspect = b.getWidth() / b.getHeight();

        int rw = (int) Math.ceil(b.getWidth()) + 2;
        int rh = (int) Math.ceil(b.getHeight()) + 2;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(rw, rh, java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, rw, rh);
        g.translate(1 - b.getX(), 1 - b.getY());
        g.setColor(java.awt.Color.WHITE);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(outline);
        g.dispose();
        boolean[] src = new boolean[rw * rh];
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                src[y * rw + x] = (img.getRGB(x, y) & 0xFF) > 96;
            }
        }
        return new Rendered(normalizeToGrid(src, rw, rh), aspect);
    }

    static boolean[] normalizeToGrid(boolean[] src, int w, int h) {
        boolean[] out = new boolean[GRID_W * GRID_H];
        if (w <= 0 || h <= 0) return out;
        for (int gy = 0; gy < GRID_H; gy++) {
            int sy = (int) ((gy + 0.5) * h / GRID_H);
            if (sy >= h) sy = h - 1;
            for (int gx = 0; gx < GRID_W; gx++) {
                int sx = (int) ((gx + 0.5) * w / GRID_W);
                if (sx >= w) sx = w - 1;
                out[gy * GRID_W + gx] = src[sy * w + sx];
            }
        }
        return out;
    }

    public Result match(boolean[] cellMask, int w, int h) {
        List<Result> ranked = matchTopK(cellMask, w, h, 0.0, 1.0, 1);
        return ranked.isEmpty() ? new Result('\0', 0.0) : ranked.get(0);
    }

    public List<Result> matchTopK(boolean[] cellMask, int w, int h, double topFrac, double botFrac, int k) {
        double cellAspect = h > 0 ? (double) w / h : 1.0;
        boolean[] cell = normalizeToGrid(cellMask, w, h);
        int cellInk = 0;
        for (boolean b : cell) if (b) cellInk++;
        if (cellInk == 0) return List.of();

        boolean cellReachesTop = topFrac <= 0.16;
        boolean cellReachesBottom = botFrac >= 0.84;
        java.util.Map<Character, Double> bestPerChar = new java.util.HashMap<>();
        for (Template t : templates) {
            int inter = 0;
            for (int i = 0; i < cell.length; i++) if (cell[i] && t.mask[i]) inter++;
            int union = cellInk + t.inkCount - inter;
            double iou = union == 0 ? 0 : (double) inter / union;

            double aspectSim = aspectSimilarity(cellAspect, t.aspect);
            double score = iou * (0.65 + 0.35 * aspectSim);
            if (heightAware) score += heightClassBonus(t, cellReachesTop, cellReachesBottom);
            Double prev = bestPerChar.get(t.ch);
            if (prev == null || score > prev) bestPerChar.put(t.ch, score);
        }
        List<Result> out = new ArrayList<>();
        for (java.util.Map.Entry<Character, Double> e : bestPerChar.entrySet()) out.add(new Result(e.getKey(), e.getValue()));
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out.size() > k ? out.subList(0, k) : out;
    }

    private static double heightClassBonus(Template t, boolean cellReachesTop, boolean cellReachesBottom) {
        double bonus = 0;
        bonus += (t.asc == cellReachesTop) ? 0.06 : -0.06;
        bonus += (t.desc == cellReachesBottom) ? 0.04 : -0.04;
        return bonus;
    }

    private static double aspectSimilarity(double a, double b) {
        if (a <= 0 || b <= 0) return 1.0;
        return Math.min(a, b) / Math.max(a, b);
    }

    public record Result(char ch, double score) {
    }
}
