package autismclient.commands.args;

import autismclient.commands.AutismCommandSource;
import autismclient.modules.ModuleRegistry;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class ModuleArgumentType implements ArgumentType<String> {
    public static ModuleArgumentType moduleName() { return new ModuleArgumentType(); }

    public static String get(CommandContext<AutismCommandSource> ctx, String name) {
        return ctx.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readUnquotedString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String name : ModuleRegistry.names()) {
            if (name == null) continue;
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }

        if ("all".startsWith(remaining)) builder.suggest("all");
        if ("hud".startsWith(remaining)) builder.suggest("hud");
        return builder.buildFuture();
    }
}
