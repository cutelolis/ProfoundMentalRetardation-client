package autismclient.commands.args;

import autismclient.commands.AutismCommandSource;
import autismclient.util.multi.MultiProfile;
import autismclient.util.multi.MultiProfileManager;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class MultiProfileArgumentType implements ArgumentType<String> {
    public static MultiProfileArgumentType profileName() {
        return new MultiProfileArgumentType();
    }

    public static String get(CommandContext<AutismCommandSource> ctx, String name) {
        return ctx.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '"') return reader.readQuotedString();
        return reader.readUnquotedString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (MultiProfile profile : MultiProfileManager.get()) {
            if (profile == null || profile.name == null) continue;
            String n = profile.name;
            if (n.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(n.contains(" ") ? "\"" + n + "\"" : n);
            }
        }
        return builder.buildFuture();
    }
}
