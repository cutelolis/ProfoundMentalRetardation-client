package autismclient.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AntiVanishModuleTest {
    @Test
    void compactsHudReasons() {
        assertEquals("Staff", AntiVanishModule.compactHudName(new AntiVanishModule.HudEntry("CRITICAL", "Sound + Block", 100)));
        assertEquals("WATCH", AntiVanishModule.compactHudReason(new AntiVanishModule.HudEntry("CRITICAL", "Sound + Block", 100)));
        assertEquals("Rank", AntiVanishModule.compactHudReason(new AntiVanishModule.HudEntry("Alice", "Rank Detection: admin", 10)));
        assertEquals("Sound", AntiVanishModule.compactHudReason(new AntiVanishModule.HudEntry("Unknown", "Suspicious Sound: step", 14)));
    }
}
