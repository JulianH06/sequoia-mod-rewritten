package star.sequoia2.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.Services;
import star.sequoia2.client.types.command.Command;
import star.sequoia2.client.types.command.suggestions.PlayerSuggestionProvider;
import star.sequoia2.utils.MinecraftUtils;

import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class PlayerWarsCommand extends Command implements TeXParserAccessor, NotificationsAccessor {
    @Override
    public String getCommandName() {
        return "wars";
    }

    @Override
    public CommandNode<FabricClientCommandSource> register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        return dispatcher.register(
                literal(getCommandName())
                        .then(argument("player", word()).suggests(new PlayerSuggestionProvider())
                                .executes(this::lookupPlayerWars
                                )
                        )
        );
    }
    @Override
    public List<String> getAliases() {
        return List.of("ws");
    }

    private int lookupPlayerWars(CommandContext<FabricClientCommandSource> context) {
        String username = context.getArgument("player", String.class);
        if (StringUtils.isBlank(username) || !MinecraftUtils.isValidUsername(username)) {
            context.getSource()
                    .sendError(prefixed(Text.translatable("sequoia.command.invalidUsername")));
        } else {
            Services.Player.getPlayer(username).whenComplete((playerResponse, throwable) -> {
                if (throwable != null) {
                    SeqClient.error("Error looking up player: " + username, throwable);
                    context.getSource()
                            .sendError(prefixed(Text.translatable(
                                    "sequoia.command.playerWars.errorLookingUpPlayer", username)));
                } else {
                    if (playerResponse == null) {
                        context.getSource()
                                .sendError(prefixed(
                                        Text.translatable("sequoia.command.playerWars.playerNotFound", username)));
                    } else {
                            context.getSource()
                                    .sendFeedback(
                                            prefixed(
                                                    teXParser().parseMutableText(I18n.translate("sequoia.command.playerWars.showingPlayerWars",
                                                            playerResponse.getUsername(), playerResponse.getGlobalData().getWars(), playerResponse.getRanking().get("warsCompletion")
                                                    ))
                                            ));
//                                                    Text.translatable(
//                                                    "sequoia.command.playerWars.showingPlayerWars",
//                                                    playerResponse.getUsername(),
//                                                    playerResponse
//                                                            .getGlobalData()
//                                                            .getWars(),
//                                                    playerResponse.getRanking().get("warsCompletion"))));
                    }
                }
            });
        }
        return 1;
    }

}
