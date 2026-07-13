package autismclient.commands.args;

import autismclient.commands.AutismCommandSource;
import autismclient.util.AutismPacketRegistry;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.network.protocol.Packet;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class PacketClassArgumentType implements ArgumentType<String> {
    public static PacketClassArgumentType packetClass() { return new PacketClassArgumentType(); }

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

        for (Class<? extends Packet<?>> cls : AutismPacketRegistry.getC2SPackets()) {
            String name = AutismPacketRegistry.getName(cls);
            if (name == null) continue;
            if (name.toLowerCase(Locale.ROOT).contains(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    }
}
