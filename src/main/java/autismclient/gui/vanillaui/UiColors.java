package autismclient.gui.vanillaui;

import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;

public final class UiColors {
    public int screenScrim;
    public int window;
    public int windowStrong;
    public int header;
    public int headerHover;
    public int row;
    public int rowAlt;
    public int rowHover;
    public int field;
    public int fieldFocused;
    public int border;
    public int borderSoft;
    public int accent;
    public int accentDark;
    public int accentSoft;
    public int success;
    public int successSoft;
    public int text;
    public int muted;
    public int disabled;
    public int bad;

    public UiColors() {
        recompute();
    }

    public void recompute() {
        screenScrim = 0x66000000;
        window = 0xD80B0C10;
        windowStrong = 0xEE0A0A0D;
        header = 0xF0181A20;
        headerHover = 0xFF242832;
        row = 0xB0101116;
        rowAlt = 0x9413151B;
        rowHover = 0xC91E2028;
        field = 0xD9111217;
        fieldFocused = 0xE8171921;
        border = AutismTheme.recolor(0xFF8F3131, Channel.OUTLINE);
        borderSoft = AutismTheme.recolor(0x99662C2C, Channel.OUTLINE);
        accent = AutismTheme.recolor(0xFFFF3B3B, Channel.ACCENT);
        accentDark = AutismTheme.recolor(0xFF8F1F24, Channel.ACCENT);
        accentSoft = AutismTheme.recolor(0x44FF3B3B, Channel.ACCENT);
        success = AutismTheme.recolor(0xFF35D873, Channel.SUCCESS);
        successSoft = AutismTheme.recolor(0x4435D873, Channel.SUCCESS);
        text = AutismTheme.recolor(0xFFF3ECE7, Channel.TEXT);
        muted = AutismTheme.recolor(0xFFB79E9E, Channel.TEXT);
        disabled = AutismTheme.recolor(0xFF766A6A, Channel.TEXT);
        bad = AutismTheme.recolor(0xFFFF5555, Channel.DANGER);
    }
}
