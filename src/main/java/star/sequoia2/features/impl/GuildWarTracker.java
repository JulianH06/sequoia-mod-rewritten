package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.wynntils.core.components.Models;
import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.services.hades.HadesUser;
import com.wynntils.utils.type.RangedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.util.math.Vec3d;
import star.sequoia2.client.types.ws.message.ws.GGuildWarSubmissionWSMessage;
import star.sequoia2.events.PlayerTickEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.features.impl.ws.WebSocketFeature;
import star.sequoia2.utils.wynn.HadesUtils;

import static star.sequoia2.client.SeqClient.mc;


public class GuildWarTracker extends ToggleFeature {

    private static final double TRACKING_RADIUS_SQ = 120 * 120;

    private WarContext activeContext;
    private boolean playerWasDead;

    public GuildWarTracker() {
        super("GuildWarTracker", "Tracks guild war results and reports them to Sequoia services", true);
    }

    @Subscribe
    public void onPlayerTick(PlayerTickEvent event) {
        if (mc.player == null) return;
        trackWarState();
        detectPlayerDeath();
    }

    private void trackWarState() {
        WarBattleInfo info = Models.GuildWarTower.getWarBattleInfo().orElse(null);
        if (info != null) {
            String battleId = buildBattleId(info);
            if (activeContext == null || !battleId.equals(activeContext.id)) {
                activeContext = new WarContext(
                        battleId,
                        info,
                        determineStartEpoch(info),
                        collectCurrentWarrers());
            } else {
                activeContext.info = info;
            }

            activeContext.lastKnownState = info.getCurrentState();
            if (!activeContext.submissionSent && isTowerDestroyed(activeContext.lastKnownState)) {
                submitWar(info, activeContext);
            }
        } else if (activeContext != null) {
            if (!activeContext.submissionSent) {
                submitWar(activeContext.info, activeContext);
            }
            activeContext = null;
        }
    }

    private void detectPlayerDeath() {
        boolean isDead = mc.player.isDead() || mc.player.getHealth() <= 0;
        if (!playerWasDead && isDead) {
            handlePlayerDeath();
        }
        playerWasDead = isDead;
    }

    private void handlePlayerDeath() {
        if (activeContext != null && !activeContext.submissionSent) {
            submitWar(activeContext.info, activeContext);
        }
    }

    private void submitWar(WarBattleInfo info, WarContext context) {
        if (info == null || context == null) return;
        WarSummary summary = buildSummary(info);
        if (summary == null) return;

        WebSocketFeature webSocket = features().getIfActive(WebSocketFeature.class)
                .filter(WebSocketFeature::isActive)
                .filter(WebSocketFeature::isAuthenticated)
                .orElse(null);
        if (webSocket == null || mc.player == null) {
            return;
        }

        if (context.warrers.isEmpty()) {
            context.warrers = collectCurrentWarrers();
        }

        List<String> uniqueWarrers = context.warrers.isEmpty()
                ? List.of(mc.player.getGameProfile().getName())
                : new ArrayList<>(new LinkedHashSet<>(context.warrers));

        long submittedAt = System.currentTimeMillis();
        GGuildWarSubmissionWSMessage.Data data = new GGuildWarSubmissionWSMessage.Data(
                summary.territory(),
                mc.player.getUuidAsString(),
                submittedAt,
                context.startEpochMs > 0 ? context.startEpochMs : submittedAt,
                uniqueWarrers,
                new GGuildWarSubmissionWSMessage.Results(
                        toWsStats(summary.initialStats()), toWsStats(summary.finalStats())));

        webSocket.sendMessage(new GGuildWarSubmissionWSMessage(data));
        context.submissionSent = true;
    }

    private WarSummary buildSummary(WarBattleInfo info) {
        if (info == null) return null;
        WarTowerState initialState = info.getInitialState();
        WarTowerState currentState = info.getCurrentState();
        if (initialState == null || currentState == null) {
            return null;
        }

        TowerStats initial = toStats(initialState);
        TowerStats current = toStats(currentState);
        String territory = info.getTerritory() == null || info.getTerritory().isBlank()
                ? "Unknown Territory"
                : info.getTerritory();
        long durationSeconds = Math.max(0, info.getTotalLengthSeconds());
        return new WarSummary(territory, initial, current, durationSeconds);
    }

    private TowerStats toStats(WarTowerState state) {
        if (state == null) {
            return new TowerStats(0, 0, 0, 0, 0);
        }
        RangedValue damage = state.damage();
        long low = damage != null ? damage.low() : 0;
        long high = damage != null ? damage.high() : 0;
        return new TowerStats(low, high, state.attackSpeed(), state.health(), state.defense());
    }

    private long determineStartEpoch(WarBattleInfo info) {
        WarTowerState initial = info.getInitialState();
        return initial != null && initial.timestamp() > 0 ? initial.timestamp() : System.currentTimeMillis();
    }

    private String buildBattleId(WarBattleInfo info) {
        WarTowerState initial = info.getInitialState();
        long timestamp = initial != null ? initial.timestamp() : System.currentTimeMillis();
        String territory = info.getTerritory() == null ? "unknown" : info.getTerritory();
        return territory + ":" + timestamp;
    }

    private boolean isTowerDestroyed(WarTowerState state) {
        return state != null && state.health() <= 0;
    }

    private GGuildWarSubmissionWSMessage.Stats toWsStats(TowerStats stats) {
        if (stats == null) {
            return new GGuildWarSubmissionWSMessage.Stats(
                    new GGuildWarSubmissionWSMessage.Damage(0, 0),
                    0,
                    0,
                    0);
        }
        return new GGuildWarSubmissionWSMessage.Stats(
                new GGuildWarSubmissionWSMessage.Damage(stats.damageLow(), stats.damageHigh()),
                stats.attackSpeed(),
                stats.health(),
                stats.defence());
    }

    private List<String> collectCurrentWarrers() {
        if (mc.player == null) return Collections.emptyList();

        Vec3d playerPos = mc.player.getPos();
        LinkedHashSet<String> uniqueNames = new LinkedHashSet<>();
        uniqueNames.add(mc.player.getGameProfile().getName());

        if (HadesUtils.cachedHadesUsers != null) {
            HadesUtils.cachedHadesUsers.values().stream()
                    .filter(user -> !Float.isNaN(user.getX()) && !Float.isNaN(user.getZ()))
                    .filter(user -> withinRange(playerPos, user))
                    .map(HadesUser::getName)
                    .forEach(uniqueNames::add);
        }

        uniqueNames.removeIf(name -> name == null || name.isBlank());
        return uniqueNames.isEmpty() ? Collections.emptyList() : new ArrayList<>(uniqueNames);
    }

    private boolean withinRange(Vec3d playerPos, HadesUser user) {
        Vec3d other = new Vec3d(user.getX(), user.getY(), user.getZ());
        return playerPos.squaredDistanceTo(other) <= TRACKING_RADIUS_SQ;
    }

    private static final class WarContext {
        private final String id;
        private WarBattleInfo info;
        private final long startEpochMs;
        private List<String> warrers;
        private WarTowerState lastKnownState;
        private boolean submissionSent;

        private WarContext(String id, WarBattleInfo info, long startEpochMs, List<String> warrers) {
            this.id = id;
            this.info = info;
            this.startEpochMs = startEpochMs;
            this.warrers = warrers == null ? new ArrayList<>() : new ArrayList<>(warrers);
        }
    }

    private record WarSummary(String territory, TowerStats initialStats, TowerStats finalStats, long durationSeconds) {}

    private record TowerStats(long damageLow, long damageHigh, double attackSpeed, long health, double defence) {}
}
