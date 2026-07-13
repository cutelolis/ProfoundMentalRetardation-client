package autismclient.commands.args;

import autismclient.commands.AutismCommandSource;
import autismclient.util.AutismInventoryHelper;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class MenuSlotArgumentType implements ArgumentType<Integer> {
    private static final SimpleCommandExceptionType INVALID_SLOT = new SimpleCommandExceptionType(
        Component.literal("Unknown slot. Use gui1, hotbar1, inventory1, helmet, chestplate, leggings, boots, offhand, or a visible numeric slot id."));

    private MenuSlotArgumentType() {
    }

    public static MenuSlotArgumentType slot() {
        return new MenuSlotArgumentType();
    }

    public static int get(CommandContext<AutismCommandSource> context, String name) {
        return context.getArgument(name, Integer.class);
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        String token = reader.readUnquotedString().trim().toLowerCase(Locale.ROOT);
        if (token.isEmpty()) throw INVALID_SLOT.create();

        Integer named = parseNamed(token);
        if (named != null) return named;
        try {
            int numeric = Integer.parseInt(token);
            if (numeric >= 0) return numeric;
        } catch (NumberFormatException ignored) {  }
        throw INVALID_SLOT.create();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        Set<String> values = new LinkedHashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu != null) {
            AbstractContainerMenu menu = mc.player.containerMenu;
            for (int handlerSlot = 0; handlerSlot < menu.slots.size(); handlerSlot++) {
                int visible = AutismInventoryHelper.toUserVisibleSlot(mc, handlerSlot);
                values.add(displayToken(visible));
                values.add(String.valueOf(visible));
            }
        } else {
            for (int slot = 1; slot <= 9; slot++) values.add("hotbar" + slot);
        }

        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(value);
        }
        return builder.buildFuture();
    }

    public static String displayToken(int visibleSlot) {
        if (visibleSlot >= AutismInventoryHelper.FIRST_GUI_SLOT) {
            return "gui" + (visibleSlot - AutismInventoryHelper.FIRST_GUI_SLOT + 1);
        }
        if (visibleSlot >= 0 && visibleSlot <= 8) return "hotbar" + (visibleSlot + 1);
        if (visibleSlot >= 9 && visibleSlot <= 35) return "inventory" + (visibleSlot - 8);
        return switch (visibleSlot) {
            case 36 -> "boots";
            case 37 -> "leggings";
            case 38 -> "chestplate";
            case 39 -> "helmet";
            case 40 -> "offhand";
            default -> String.valueOf(visibleSlot);
        };
    }

    private static Integer parseNamed(String token) {
        return switch (token) {
            case "boots" -> 36;
            case "leggings" -> 37;
            case "chestplate" -> 38;
            case "helmet" -> 39;
            case "offhand" -> 40;
            default -> {
                Integer gui = parseIndexed(token, "gui", AutismInventoryHelper.FIRST_GUI_SLOT, Integer.MAX_VALUE);
                if (gui != null) yield gui;
                Integer hotbar = parseIndexed(token, "hotbar", 0, 9);
                if (hotbar != null) yield hotbar;
                yield parseIndexed(token, "inventory", 9, 27);
            }
        };
    }

    private static Integer parseIndexed(String token, String prefix, int offset, int maxIndex) {
        if (!token.startsWith(prefix)) return null;
        String suffix = token.substring(prefix.length());
        if (suffix.startsWith(":")) suffix = suffix.substring(1);
        try {
            int oneBased = Integer.parseInt(suffix);
            if (oneBased < 1 || oneBased > maxIndex) return null;
            return offset + oneBased - 1;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
