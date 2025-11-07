package star.sequoia2.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.command.Command;

public class SeqDebugCommand extends Command {
    @Override
    public String getCommandName() {
        return "seqdebug";
    }

    @Override
    public CommandNode<FabricClientCommandSource> register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        return dispatcher.register(
                ClientCommandManager.literal(getCommandName())
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(statusMessage());
                            return 1;
                        })
                        .then(ClientCommandManager.literal("on").executes(ctx -> setDebug(ctx.getSource(), true)))
                        .then(ClientCommandManager.literal("off").executes(ctx -> setDebug(ctx.getSource(), false)))
                        .then(ClientCommandManager.literal("toggle").executes(ctx -> setDebug(ctx.getSource(), !SeqClient.isDebugMode())))
        );
    }

    private int setDebug(FabricClientCommandSource source, boolean enabled) {
        SeqClient.setDebugMode(enabled);
        source.sendFeedback(statusMessage());
        return 1;
    }

    private Text statusMessage() {
        String state = SeqClient.isDebugMode() ? "enabled" : "disabled";
        return SeqClient.prefix(Text.literal("Debug logging is " + state + "."));
    }
}
