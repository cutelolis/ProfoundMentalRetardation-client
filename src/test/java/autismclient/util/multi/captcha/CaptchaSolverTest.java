package autismclient.util.multi.captcha;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaSolverTest {

    @Test
    void advancedCaptchaMathPicksResultButton() {
        CaptchaChatSolver solver = new CaptchaChatSolver();

        assertNull(solver.onChat(Component.literal("Solve: 7 + 3 = ? and click the correct answer!")));

        MutableComponent buttons = Component.literal("")
            .append(button("[5]", "/captcha_click uid_tok_0", null))
            .append(button("[10]", "/captcha_click uid_tok_1", null))
            .append(button("[12]", "/captcha_click uid_tok_2", null));
        CaptchaChatSolver.Answer a = solver.onChat(buttons);
        assertNotNull(a);
        assertEquals(CaptchaChatSolver.Kind.COMMAND, a.kind());
        assertEquals("captcha_click uid_tok_1", a.text());
    }

    @Test
    void advancedCaptchaMathSubtraction() {
        CaptchaChatSolver solver = new CaptchaChatSolver();
        solver.onChat(Component.literal("Solve: 9 - 4 = ?"));
        MutableComponent buttons = Component.literal("")
            .append(button("[5]", "/captcha_click uid_tok_0", null))
            .append(button("[3]", "/captcha_click uid_tok_1", null));
        CaptchaChatSolver.Answer a = solver.onChat(buttons);
        assertNotNull(a);
        assertEquals("captcha_click uid_tok_0", a.text());
    }

    @Test
    void advancedCaptchaButtonPicksByLabelInInstruction() {
        CaptchaChatSolver solver = new CaptchaChatSolver();
        solver.onChat(Component.literal("Click the [Click] button of pink color!"));
        MutableComponent buttons = Component.literal("")
            .append(button("[Tap]", "/captcha_click uid_tok_0", "light_purple"))
            .append(button("[Click]", "/captcha_click uid_tok_1", "light_purple"))
            .append(button("[Press]", "/captcha_click uid_tok_2", "blue"));
        CaptchaChatSolver.Answer a = solver.onChat(buttons);
        assertNotNull(a);
        assertEquals("captcha_click uid_tok_1", a.text());
    }

    @Test
    void captchaVerifyZEchoesTheCode() {
        CaptchaChatSolver solver = new CaptchaChatSolver();
        CaptchaChatSolver.Answer a = solver.onChat(Component.literal("➤ A7K9Q"));
        assertNotNull(a);
        assertEquals(CaptchaChatSolver.Kind.CHAT, a.kind());
        assertEquals("A7K9Q", a.text());
    }

    @Test
    void captchaVerifyZRetryCode() {
        CaptchaChatSolver solver = new CaptchaChatSolver();
        CaptchaChatSolver.Answer a = solver.onChat(Component.literal("Incorrect! Try again: XYZ12"));
        assertNotNull(a);
        assertEquals("XYZ12", a.text());
    }

    @Test
    void plainChatIsNotTreatedAsCaptcha() {
        CaptchaChatSolver solver = new CaptchaChatSolver();
        assertNull(solver.onChat(Component.literal("<Steve> hey whats up")));
        assertNull(solver.onChat(Component.literal("Player joined the game")));
    }

    @Test
    void voidCaptchaDigitsAreReadExactly() {
        for (String code : new String[]{"1234", "5678", "9012", "0426", "3751"}) {
            CaptchaMapImage img = renderDigits(code);
            CaptchaMapOcr.OcrResult r = CaptchaMapOcr.detectAndSolve(img);
            assertEquals(code, r.text(), "OCR mismatch for " + code);
            assertTrue(r.confidence() > 0.6, "low confidence for " + code + ": " + r.confidence());
        }
    }

    @Test
    void detectAndSolvePicksDigitModeForDarkMap() {
        CaptchaMapImage img = renderDigits("8321");
        CaptchaMapOcr.OcrResult r = CaptchaMapOcr.detectAndSolve(img);
        assertEquals("8321", r.text());
    }

    @Test
    void sonarLettersAreReadOnCleanRenders() {
        Font kingthings = loadKingthings();

        String[] codes = {"abc", "kmn", "prs", "xyz", "hjk", "abcd", "most", "then"};
        int correct = 0, chars = 0, hits = 0;
        for (String code : codes) {
            CaptchaMapImage img = renderSonarLike(kingthings, code);
            CaptchaMapOcr.OcrResult r = CaptchaMapOcr.detectAndSolve(img);
            if (code.equals(r.text())) correct++;
            for (int i = 0; i < Math.min(code.length(), r.text().length()); i++) {
                chars++;
                if (code.charAt(i) == r.text().charAt(i)) hits++;
            }
        }

        double charAcc = chars == 0 ? 0 : (double) hits / chars;
        assertTrue(charAcc >= 0.8, "clean-render Sonar char accuracy too low: " + charAcc + " (" + correct + "/" + codes.length + " exact)");
    }

    @Test
    void sonarRankedPutsAnswerNearTop() {
        Font kingthings = loadKingthings();
        String[] codes = {"abc", "kmn", "prs", "xyz", "hjk", "abcd", "most", "then"};
        int inTop3 = 0;
        for (String code : codes) {
            CaptchaMapImage img = renderSonarLike(kingthings, code);
            List<CaptchaMapOcr.OcrResult> ranked = CaptchaMapOcr.detectAndSolveRanked(img, 3);
            for (CaptchaMapOcr.OcrResult r : ranked) {
                if (code.equals(r.text())) { inTop3++; break; }
            }
        }

        assertTrue(inTop3 >= 6, "answer missing from top-3 on clean renders: " + inTop3 + "/" + codes.length);
    }

    @Test
    void realSonarCaptchasSolveWithinThreeTries() {
        String[][] truth = {
            {"captcha-7b0955819761c1e1", "hch"},
            {"captcha-61bad0d82906ea46", "bcba"},
            {"captcha-916b42e56e75edab", "xtm"},
            {"captcha-a80af9dead08a144", "ksk"},
            {"captcha-b576fbd90c32ec59", "npxb"},
            {"captcha-e2ea50a7f12cf95a", "fpf"},
            {"captcha-e795293664959207", "bft"},
        };
        int top1 = 0, top3 = 0, total = 0;
        for (String[] entry : truth) {
            CaptchaMapImage img = loadCaptchaPng("/captcha/" + entry[0] + ".png");
            if (img == null) { System.out.println("[real] " + entry[0] + " -> (missing)"); continue; }
            total++;
            List<CaptchaMapOcr.OcrResult> ranked = CaptchaMapOcr.detectAndSolveRanked(img, 3);
            boolean hit1 = !ranked.isEmpty() && entry[1].equals(ranked.get(0).text());
            boolean hit3 = false;
            for (CaptchaMapOcr.OcrResult r : ranked) if (entry[1].equals(r.text())) { hit3 = true; break; }
            if (hit1) top1++;
            if (hit3) top3++;
            StringBuilder sb = new StringBuilder();
            for (CaptchaMapOcr.OcrResult r : ranked) sb.append(r.text()).append('(').append(String.format("%.2f", r.confidence())).append(") ");
            System.out.println("[real] want " + entry[1] + " -> " + sb + (hit1 ? "[top1]" : hit3 ? "[top3]" : "[MISS]"));
        }
        System.out.println("[real] top1=" + top1 + "/" + total + " top3=" + top3 + "/" + total);
        assertTrue(top3 >= 5, "real Sonar captcha solve rate regressed: only " + top3 + "/" + total + " within 3 tries");
        assertTrue(top1 >= 4, "real Sonar top-1 accuracy regressed: only " + top1 + "/" + total);
    }

    private static CaptchaMapImage loadCaptchaPng(String resource) {
        try (java.io.InputStream is = CaptchaSolverTest.class.getResourceAsStream(resource)) {
            if (is == null) return null;
            BufferedImage bi = javax.imageio.ImageIO.read(is);
            CaptchaMapImage img = new CaptchaMapImage();
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) img.rgb[y * 128 + x] = bi.getRGB(x, y) & 0xFFFFFF;
            }
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    private static Font loadKingthings() {
        try (var is = CaptchaSolverTest.class.getResourceAsStream("/assets/autismclient/captcha/fonts/Kingthings_Trypewriter_2.ttf")) {
            assertNotNull(is, "bundled Kingthings font missing");
            return Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(Font.PLAIN, 25f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static CaptchaMapImage renderSonarLike(Font baseFont, String code) {
        BufferedImage bi = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(70, 72, 78));
        g.fillRect(0, 0, 128, 128);
        double scale = 5 - Math.min(code.length(), 5) * 0.65;
        double beginX = Math.max(Math.min(64 - code.length() * 7 * scale, 30), 6);
        double beginY = 70 + scale;
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            GlyphVector gv = baseFont.createGlyphVector(g.getFontRenderContext(), String.valueOf(ch));
            double shear = Math.sin(beginX + beginY) / 6.0;
            AffineTransform t = new AffineTransform();
            t.translate(beginX, beginY);
            t.shear(shear, shear);
            t.scale(scale, scale);
            java.awt.Shape outline = t.createTransformedShape(gv.getOutline());
            g.setColor(Color.getHSBColor((i * 0.23f) % 1f, 0.78f, 0.98f));
            g.fill(outline);
            beginX += gv.getVisualBounds().getWidth() * scale + 2;
            beginY += (i % 2 == 0 ? 2 : -2);

        }
        g.dispose();
        CaptchaMapImage img = new CaptchaMapImage();
        for (int i = 0; i < 128 * 128; i++) img.rgb[i] = bi.getRGB(i % 128, i / 128) & 0xFFFFFF;
        return img;
    }

    private static MutableComponent button(String label, String command, String colorName) {
        Style style = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command));
        if (colorName != null) {
            TextColor color = TextColor.parseColor(colorName).result().orElse(null);
            if (color != null) style = style.withColor(color);
        }
        return Component.literal(label).setStyle(style);
    }

    private static CaptchaMapImage renderDigits(String code) {
        BufferedImage bi = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 128, 128);
        g.setColor(new Color(50, 50, 50));
        g.drawRect(4, 4, 119, 119);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, code.length() > 4 ? 36 : 48));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(code);
        int x = (128 - w) / 2;
        int y = (128 - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(code, x, y);
        g.dispose();
        CaptchaMapImage img = new CaptchaMapImage();
        for (int i = 0; i < 128 * 128; i++) {
            img.rgb[i] = bi.getRGB(i % 128, i / 128) & 0xFFFFFF;
        }
        return img;
    }
}
