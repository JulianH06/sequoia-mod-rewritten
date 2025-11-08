package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.wynntils.services.hades.HadesUser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import star.sequoia2.client.types.ws.message.ws.GGuildWarSubmissionWSMessage;
import star.sequoia2.events.PacketEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.features.impl.ws.WebSocketFeature;
import star.sequoia2.utils.wynn.HadesUtils;

import static star.sequoia2.client.SeqClient.mc;


public class GuildWarTracker extends ToggleFeature {

    private static final double TRACKING_RADIUS_SQ = 120 * 120;
    private static final Pattern DAMAGE_PATTERN = Pattern.compile("Damage:\\s*(.+)");
    private static final Pattern ATTACK_SPEED_PATTERN = Pattern.compile("Attack Speed:\\s*(.+)");
    private static final Pattern HEALTH_PATTERN = Pattern.compile("Health:\\s*(.+)");
    private static final Pattern DEFENSE_PATTERN = Pattern.compile("Defense:\\s*(.+)");
    private WarContext activeContext;
    private String lastSummarySignature;
    private long lastSummaryAt;

    public GuildWarTracker() {
        super("GuildWarTracker", "Tracks guild war results and reports them to Sequoia services", true);
    }

    @Subscribe
    public void onChat(PacketEvent.PacketReceiveEvent event) {
        if (!isActive() || !(event.packet() instanceof GameMessageS2CPacket packet) || packet.overlay()) {
            return;
        }

        Text content = packet.content();
        if (content == null) return;

        String plain = content.getString();
        if (plain == null) return;

        String normalized = plain.trim();
        if (normalized.isEmpty()) return;

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("battle has begun")) {
            handleWarStarted();
            return;
        }

        if (!lower.contains("tower stats - initial")) {
            return;
        }

        if (isDuplicateSummary(normalized)) {
            return;
        }

        parseWarSummary(normalized).ifPresent(summary -> {
            WarContext context = activeContext != null ? activeContext : new WarContext();
            long now = System.currentTimeMillis();
            if (summary.durationSeconds() > 0) {
                context.startEpochMs = now - summary.durationSeconds() * 1000L;
            } else if (context.startEpochMs <= 0) {
                context.startEpochMs = now;
            }
            if (context.warrers.isEmpty()) {
                context.warrers = collectCurrentWarrers();
            }
            sendSummary(summary, context);
            activeContext = null;
        });
    }

    private void handleWarStarted() {
        WarContext context = new WarContext();
        context.startEpochMs = System.currentTimeMillis();
        context.warrers = collectCurrentWarrers();
        activeContext = context;
    }

    private boolean isDuplicateSummary(String message) {
        String signature = message.replaceAll("\\s+", "");
        long now = System.currentTimeMillis();
        // what the FUCK am i doing bro :pray:
        if (signature.equals(lastSummarySignature) && (now - lastSummaryAt < 2000)) {
            return true;
        }
        lastSummarySignature = signature;
        lastSummaryAt = now;
        return false;
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

    private Optional<WarSummary> parseWarSummary(String message) {
        String[] rawLines = message.replace("\r", "").split("\\n");
        List<String> lines = new ArrayList<>();
        for (String rawLine : rawLines) {
            String line = rawLine.strip();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }

        List<String> initialBlock = new ArrayList<>();
        List<String> finalBlock = new ArrayList<>();
        long durationSeconds = -1L;
        String territory = null;

        boolean inInitial = false;
        boolean inFinal = false;
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("tower stats - initial")) {
                inInitial = true;
                inFinal = false;
                continue;
            }
            if (lower.contains("tower stats - end") || lower.contains("tower stats - final")) {
                inFinal = true;
                inInitial = false;
                continue;
            }
            if (line.startsWith("=")) continue;

            if (inInitial) {
                if (territory == null && looksLikeTerritory(line)) {
                    territory = extractTerritory(line);
                }
                initialBlock.add(line);
            } else if (inFinal) {
                if (durationSeconds < 0 && lower.contains("time in war")) {
                    durationSeconds = parseLongValue(line);
                }
                finalBlock.add(line);
            }
        }

        if ((territory == null || territory.isEmpty())) {
            territory = initialBlock.stream()
                    .filter(line -> !line.contains(":"))
                    .map(String::trim)
                    .filter(str -> !str.isEmpty())
                    .findFirst()
                    .orElse(null);
        }

        TowerStats initialStats = parseStats(initialBlock);
        TowerStats finalStats = parseStats(finalBlock);

        if (initialStats == null || finalStats == null || territory == null || territory.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new WarSummary(territory, initialStats, finalStats, durationSeconds));
    }

    private boolean looksLikeTerritory(String line) {
        return line.contains("[") && line.contains("]");
    }

    private String extractTerritory(String line) {
        int idx = line.indexOf('[');
        return idx > 0 ? line.substring(0, idx).trim() : line.trim();
    }

    private TowerStats parseStats(List<String> lines) {
        long damageLow = 0;
        long damageHigh = 0;
        double attack = 0;
        long health = -1;
        double defence = 0;

        for (String line : lines) {
            Matcher dmgMatcher = DAMAGE_PATTERN.matcher(line);
            if (dmgMatcher.find()) {
                String value = dmgMatcher.group(1);
                String[] parts = value.split("-");
                damageLow = parts.length > 0 ? parseLongValue(parts[0]) : 0;
                damageHigh = parts.length > 1 ? parseLongValue(parts[1]) : damageLow;
                continue;
            }

            Matcher atkMatcher = ATTACK_SPEED_PATTERN.matcher(line);
            if (atkMatcher.find()) {
                attack = parseDoubleValue(atkMatcher.group(1));
                continue;
            }

            Matcher healthMatcher = HEALTH_PATTERN.matcher(line);
            if (healthMatcher.find()) {
                health = parseLongValue(healthMatcher.group(1));
                continue;
            }

            Matcher defMatcher = DEFENSE_PATTERN.matcher(line);
            if (defMatcher.find()) {
                defence = parseDoubleValue(defMatcher.group(1));
            }
        }

        if (health < 0) return null;

        return new TowerStats(damageLow, damageHigh, attack, health, defence);
    }

    private long parseLongValue(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        return Long.parseLong(digits);
    }

    private double parseDoubleValue(String text) {
        String sanitized = text.replace(',', '.').replaceAll("[^0-9.]", "");
        if (sanitized.isEmpty() || ".".equals(sanitized)) return 0;
        int firstDot = sanitized.indexOf('.');
        if (firstDot != -1) {
            int nextDot = sanitized.indexOf('.', firstDot + 1);
            while (nextDot != -1) {
                sanitized = sanitized.substring(0, nextDot) + sanitized.substring(nextDot + 1);
                nextDot = sanitized.indexOf('.', firstDot + 1);
            }
        }
        return sanitized.isEmpty() ? 0 : Double.parseDouble(sanitized);
    }

    private void sendSummary(WarSummary summary, WarContext context) {
        WebSocketFeature webSocket = features().getIfActive(WebSocketFeature.class)
                .filter(WebSocketFeature::isActive)
                .filter(WebSocketFeature::isAuthenticated)
                .orElse(null);
        if (webSocket == null || mc.player == null) {
            return;
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
                        toStats(summary.initialStats()), toStats(summary.finalStats())));

        webSocket.sendMessage(new GGuildWarSubmissionWSMessage(data));
    }

    private GGuildWarSubmissionWSMessage.Stats toStats(TowerStats stats) {
        return new GGuildWarSubmissionWSMessage.Stats(
                new GGuildWarSubmissionWSMessage.Damage(stats.damageLow(), stats.damageHigh()),
                stats.attackSpeed(),
                stats.health(),
                stats.defence());
    }

    private static final class WarContext {
        private long startEpochMs;
        private List<String> warrers = new ArrayList<>();
    }

    private record WarSummary(String territory, TowerStats initialStats, TowerStats finalStats, long durationSeconds) {}

    private record TowerStats(long damageLow, long damageHigh, double attackSpeed, long health, double defence) {}
}
