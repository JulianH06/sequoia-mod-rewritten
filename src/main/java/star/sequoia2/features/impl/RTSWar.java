package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.ibm.icu.impl.Pair;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import star.sequoia2.client.types.ws.message.ws.GIC3HWSMessage;
import star.sequoia2.client.types.ws.message.ws.rts.GWarCmdWSMessage;
import star.sequoia2.client.types.ws.type.PlayerSkinCache;
import star.sequoia2.client.types.ws.type.PosCodec;
import star.sequoia2.events.PlayerTickEvent;
import star.sequoia2.events.input.KeyEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.features.impl.ws.WebSocketFeature;
import star.sequoia2.gui.screen.RTSGuildMapScreen;
import star.sequoia2.settings.Binding;
import star.sequoia2.settings.types.KeybindSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static star.sequoia2.client.SeqClient.mc;

@Setter
@Getter
public class RTSWar extends ToggleFeature {

    boolean commander;
    boolean optedIn;
    Pair<String, String> currentTeam;
    int role;

    boolean sendingLocation;

    List<WarCommand> commandQue = new ArrayList<>();
    Map<String, List<String>> teams = new HashMap<>();

    private final Map<String, TrackedPlayer> remotePlayers = new HashMap<>();
    private long lastSendMs = 0L;

    public final KeybindSetting menuKeybind = settings().binding("GuiKey:", "Opens the Custom gui", Binding.none());

    public RTSWar() {
        super("RTSWar", "Replaces wynntils guild map for an improved RTS one");
    }

    @Override
    public void onActivate() {
        sendingLocation = true;
    }

    @Override
    public void onDeactivate() {
        sendingLocation = false;
        remotePlayers.clear();
    }

    @Subscribe
    public void onTick(PlayerTickEvent event) {
        long now = System.currentTimeMillis();
        var wsFeature = features().getIfActive(WebSocketFeature.class);

        if (sendingLocation && mc.player != null) {
            if (wsFeature.map(f -> f.isActive() && f.isAuthenticated()).orElse(false)) {
                if (now - lastSendMs >= 500L) {
                    int[] ints = PosCodec.encode(List.of(new BlockPos(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ())));
                    GIC3HWSMessage message = new GIC3HWSMessage(
                            new GIC3HWSMessage.Data(
                                    0,
                                    0,
                                    "position",
                                    ints,
                                    List.of("*")
                            )
                    );
                    wsFeature.ifPresent(webSocketFeature -> webSocketFeature.sendMessage(message));
                    lastSendMs = now;
                }
            }
        }

        if (!remotePlayers.isEmpty()) {
            List<String> expired = new ArrayList<>();
            for (Map.Entry<String, TrackedPlayer> e : remotePlayers.entrySet()) {
                if (now - e.getValue().lastSeenMs > 5000L) expired.add(e.getKey());
            }
            if (!expired.isEmpty()) {
                for (String k : expired) remotePlayers.remove(k);
            }
        }
    }

    @Subscribe
    public void onKeyDown(KeyEvent event) {
        if (event.isKeyDown() && this.menuKeybind.get().matches(event) && mc.currentScreen == null) {
            event.cancel();
            mc.setScreen(new RTSGuildMapScreen());
        }
    }

    public record WarCommand(int type, int x, int y) {}

    public void sendGWarCmdMessage(int type, String affectedTeam, String[] affected, int[] coords, int warRole) {
        var wsFeature = features().getIfActive(WebSocketFeature.class);
        GWarCmdWSMessage message = new GWarCmdWSMessage(
                new GWarCmdWSMessage.Data(
                        type,
                        affectedTeam,
                        affected,
                        coords,
                        warRole
                )
        );

        if (wsFeature.map(WebSocketFeature::isActive).orElse(false) && wsFeature.map(WebSocketFeature::isAuthenticated).orElse(false)) {
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.sendMessage(message));
        }
    }

    public static final class TrackedPlayer {
        public final String uuid;
        public BlockPos pos;
        public long lastSeenMs;
        public Identifier skin;
        public TrackedPlayer(String uuid, BlockPos pos, long lastSeenMs, Identifier skin) {
            this.uuid = uuid;
            this.pos = pos;
            this.lastSeenMs = lastSeenMs;
            this.skin = skin;
        }
    }

    public void updateRemotePlayer(String uuid, BlockPos pos) {
        long now = System.currentTimeMillis();
        TrackedPlayer existing = remotePlayers.get(uuid);
        if (existing == null) {
            Identifier skin = PlayerSkinCache.get(uuid);
            remotePlayers.put(uuid, new TrackedPlayer(uuid, pos, now, skin));
        } else {
            existing.pos = pos;
            existing.lastSeenMs = now;
            if (existing.skin == null) existing.skin = PlayerSkinCache.get(uuid);
        }
    }

    public List<TrackedPlayer> getActiveRemotePlayers() {
        if (remotePlayers.isEmpty()) return Collections.emptyList();
        long now = System.currentTimeMillis();
        List<TrackedPlayer> list = new ArrayList<>();
        for (TrackedPlayer p : remotePlayers.values()) {
            if (now - p.lastSeenMs <= 5000L) list.add(p);
        }
        return list;
    }
}
