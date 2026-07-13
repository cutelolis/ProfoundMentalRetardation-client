package autismclient.util.macro;

import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismFakeGamemode;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;

public class FakeGamemodeAction implements MacroAction {
    public enum Mode {
        SURVIVAL,
        CREATIVE,
        ADVENTURE,
        SPECTATOR,
        RESET
    }

    public Mode mode = Mode.RESET;
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
        AutismFakeGamemode.Result result = mode == Mode.RESET
            ? AutismFakeGamemode.reset()
            : AutismFakeGamemode.apply(toGameType(mode));
        AutismClientMessaging.sendPrefixed((result.success() ? "§a" : "§c") + result.message());
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("mode", mode.name());
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("mode")) {
            try {
                mode = Mode.valueOf(tag.getStringOr("mode", "RESET"));
            } catch (IllegalArgumentException ignored) {
                mode = Mode.RESET;
            }
        }
        enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.FAKE_GAMEMODE;
    }

    @Override
    public String getDisplayName() {
        return "Fake GM: " + displayMode(mode);
    }

    @Override
    public String getIcon() {
        return "GM";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private static GameType toGameType(Mode mode) {
        return switch (mode) {
            case CREATIVE -> GameType.CREATIVE;
            case ADVENTURE -> GameType.ADVENTURE;
            case SPECTATOR -> GameType.SPECTATOR;
            case SURVIVAL, RESET -> GameType.SURVIVAL;
        };
    }

    private static String displayMode(Mode mode) {
        if (mode == null) return "Reset";
        String lower = mode.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
