package autismclient.commands.args;

import autismclient.commands.AutismCommandSource;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class VisibleItemNameArgumentType implements ArgumentType<String> {
    private VisibleItemNameArgumentType() {
    }

    public static VisibleItemNameArgumentType itemName() {
        return new VisibleItemNameArgumentType();
    }

    public static String get(CommandContext<AutismCommandSource> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return reader.readString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (remaining.startsWith("\"")) remaining = remaining.substring(1);

        Set<String> names = new LinkedHashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (menu != null) {
                for (int slot = 0; slot < menu.slots.size(); slot++) {
                    ItemStack stack = menu.slots.get(slot).getItem();
                    if (stack.isEmpty()) continue;
                    String displayName = stack.getHoverName().getString().replaceAll("\\s+", " ").trim();
                    if (!displayName.isEmpty()) names.add(displayName);
                }
            }
        }

        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(StringArgumentType.escapeIfRequired(name));
            }
        }
        return builder.buildFuture();
    }
}
