package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.ibm.icu.impl.Pair;
import com.wynntils.utils.mc.McUtils;
import lombok.Getter;
import lombok.Setter;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.message.ws.GTreasuryEmeraldAlertWSMessage;
import star.sequoia2.client.types.ws.message.ws.guildraid.GGuildRaidWSMessage;
import star.sequoia2.client.types.ws.message.ws.rts.GWarCmdWSMessage;
import star.sequoia2.events.input.KeyEvent;
import star.sequoia2.features.Feature;
import star.sequoia2.features.impl.ws.WebSocketFeature;
import star.sequoia2.gui.screen.BetterGuildMapScreen;
import star.sequoia2.settings.Binding;
import star.sequoia2.settings.types.KeybindSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static star.sequoia2.client.SeqClient.mc;

@Setter
@Getter
public class RTSWar extends Feature {

    boolean commander;
    boolean optedIn;
    Pair<String, String> currentTeam;
    int role;

    List<WarCommand> commandQue = new ArrayList<>();
    Map<String, List<String>> teams = new HashMap<>();

    public final KeybindSetting menuKeybind = settings().binding("GuiKey:", "Opens the Custom gui", Binding.none());

    public RTSWar() {
        super("RTSWar", "Replaces wynntils guild map for an improved RTS one");
    }

    @Subscribe
    public void onKeyDown(KeyEvent event) {
        if (event.isKeyDown() && this.menuKeybind.get().matches(event) && mc.currentScreen == null) {
            event.cancel();
            mc.setScreen(new BetterGuildMapScreen());
        }
    }

    public record WarCommand(int type, int x, int y) {}

    /**
     * @param type
     *        0 : assume controller,
     *		  1 : attack,
     *		  2 : move,
     *		  3 : switch role,
     *		  4 : take command of player,
     *		  5 : drop player,
     *		  6 : drop team,
     *		  7 : OPT IN,
     *		  8 : opt out
     *
     * @param warRole
     *      0 : null,
     *		1 : tank,
     *		2 : dps,
     *		3 : emotional support,
     *      4 : cat
    */
    public void sendGWarCmdMessage(int type, String affectedTeam, String[] affected, int[] coords, int warRole) {
        GWarCmdWSMessage message = new GWarCmdWSMessage(
           new GWarCmdWSMessage.Data(
               type,
               affectedTeam,
               affected,
               coords,
               warRole
           )
        );

        if (features().getIfActive(WebSocketFeature.class).map(WebSocketFeature::isActive).orElse(false) || !features().getIfActive(WebSocketFeature.class).map(WebSocketFeature::isAuthenticated).orElse(false)) {
            features().getIfActive(WebSocketFeature.class).map(webSocketFeature -> webSocketFeature.sendMessage(message));
        }
    }
}
