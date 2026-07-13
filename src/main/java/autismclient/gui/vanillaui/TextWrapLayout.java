package autismclient.gui.vanillaui;

import java.util.ArrayList;
import java.util.List;

public final class TextWrapLayout {
    private TextWrapLayout() {
    }

    @FunctionalInterface
    public interface RangeWidth {
        int width(int start, int end);
    }

    public record Line(int start, int end, int renderEnd) {
    }

    public static List<Line> layout(String text, int maxWidth, RangeWidth width) {
        String source = text == null ? "" : text;
        int available = Math.max(1, maxWidth);
        List<Line> lines = new ArrayList<>();
        if (source.isEmpty()) {
            lines.add(new Line(0, 0, 0));
            return lines;
        }

        int paragraphStart = 0;
        while (paragraphStart < source.length()) {
            int newline = source.indexOf('\n', paragraphStart);
            int paragraphEnd = newline >= 0 ? newline : source.length();
            if (paragraphStart == paragraphEnd) {
                lines.add(new Line(paragraphStart, newline + 1, paragraphStart));
            } else {
                int cursor = paragraphStart;
                while (cursor < paragraphEnd) {
                    int end = nextLineEnd(source, cursor, paragraphEnd, available, width);
                    int renderEnd = trimRenderEnd(source, cursor, end);
                    lines.add(new Line(cursor, end, renderEnd));
                    cursor = end;
                }
                if (newline >= 0) {
                    Line last = lines.remove(lines.size() - 1);
                    lines.add(new Line(last.start(), newline + 1, last.renderEnd()));
                }
            }
            paragraphStart = newline >= 0 ? newline + 1 : source.length();
        }
        if (source.endsWith("\n")) {
            lines.add(new Line(source.length(), source.length(), source.length()));
        }
        return lines;
    }

    public static int nextLineEnd(String source, int start, int limit, int maxWidth, RangeWidth width) {
        if (source == null || source.isEmpty()) return 0;
        int safeStart = Math.max(0, Math.min(start, source.length()));
        int safeLimit = Math.max(safeStart, Math.min(limit, source.length()));
        int cursor = safeStart;
        int lastPreferredBreak = -1;
        while (cursor < safeLimit) {
            char ch = source.charAt(cursor);
            if (width.width(safeStart, cursor + 1) > Math.max(1, maxWidth)) {
                if (cursor == safeStart) return cursor + 1;
                return lastPreferredBreak > safeStart ? lastPreferredBreak : cursor;
            }
            if (isPreferredBreak(ch)) lastPreferredBreak = cursor + 1;
            cursor++;
        }
        return safeLimit;
    }

    private static int trimRenderEnd(String source, int start, int end) {
        int renderEnd = end;
        while (renderEnd > start) {
            char ch = source.charAt(renderEnd - 1);
            if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') break;
            renderEnd--;
        }
        return renderEnd;
    }

    private static boolean isPreferredBreak(char ch) {
        return Character.isWhitespace(ch)
            || ch == ',' || ch == ';' || ch == ':' || ch == '/'
            || ch == ')' || ch == ']' || ch == '}' || ch == '>';
    }
}
