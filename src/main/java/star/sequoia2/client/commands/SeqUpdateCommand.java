package star.sequoia2.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Formatting;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.commands.SeqUpdateCommand.SourceBridge;
import star.sequoia2.client.types.command.Command;
import star.sequoia2.client.update.UpdateManager;

import java.util.Optional;

public class SeqUpdateCommand extends Command {

    @Override
    public String getCommandName() {
        return "sequpdate";
    }

    @Override
    public CommandNode<FabricClientCommandSource> register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        return dispatcher.register(
                ClientCommandManager.literal(getCommandName())
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(SeqClient.prefix(Text.literal("Checking for updates...").formatted(Formatting.GRAY)));
                            UpdateManager.checkForUpdates(true);
                            return 1;
                        })
                        .then(ClientCommandManager.literal("install")
                                .executes(ctx -> {
                                    UpdateManager.installLatest(new SourceBridge(ctx.getSource()));
                                    return 1;
                                }))
        );
    }

    public static class SourceBridge implements UpdateManager.FabricClientCommandSourceBridge {
        private final FabricClientCommandSource source;

        public SourceBridge(FabricClientCommandSource source) {
            this.source = source;
        }

        @Override
        public void reply(String message, Formatting formatting) {
            source.sendFeedback(SeqClient.prefix(Text.literal(message).formatted(formatting)));
        }
    }
}
