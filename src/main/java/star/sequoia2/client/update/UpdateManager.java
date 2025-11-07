package star.sequoia2.client.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.http.HttpClients;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateManager {

    private static final String RELEASES_API = "https://api.github.com/repos/SequoiaWynncraft/sequoia-mod-rewritten/releases/latest";

    private static final AtomicBoolean checking = new AtomicBoolean(false);
    private static final AtomicBoolean installing = new AtomicBoolean(false);
    private static final AtomicBoolean autoCheckQueued = new AtomicBoolean(false);
    private static final AtomicBoolean autoCheckCompleted = new AtomicBoolean(false);

    private static volatile ReleaseInfo cachedRelease;
    private static final UpdateState UPDATE_STATE = UpdateState.load();
    private static final NotificationsAccessor NOTIFIER = new NotificationsAccessor() {};

    private UpdateManager() {}

    public static void scheduleAutomaticCheck() {
        if (SeqClient.getModJar() == null) {
            SeqClient.debug("Skipping update check: mod jar not resolved (likely dev environment).");
            return;
        }
        cleanupBackup();
        if (!autoCheckQueued.compareAndSet(false, true)) return;
        SeqClient.SCHEDULER.schedule(UpdateManager::waitForWorldThenCheck, 5, TimeUnit.SECONDS);
    }

    private static void waitForWorldThenCheck() {
        if (autoCheckCompleted.get()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            SeqClient.SCHEDULER.schedule(UpdateManager::waitForWorldThenCheck, 3, TimeUnit.SECONDS);
            return;
        }
        autoCheckCompleted.set(true);
        notifyUser(Text.literal("Checking for updates...").formatted(Formatting.GRAY));
        checkForUpdates(false);
    }

    public static void checkForUpdates(boolean manualTrigger) {
        if (!checking.compareAndSet(false, true)) {
            if (manualTrigger) {
                notifyUser(Text.literal("Already checking for updates."));
            } else {
                SeqClient.SCHEDULER.schedule(() -> checkForUpdates(false), 2, TimeUnit.SECONDS);
            }
            return;
        }

        CompletableFuture
                .supplyAsync(UpdateManager::fetchLatestRelease)
                .whenComplete((opt, throwable) -> {
                    checking.set(false);
                    if (throwable != null) {
                        if (manualTrigger) {
                            notifyUser(Text.literal("Update check failed: " + throwable.getMessage()).formatted(Formatting.RED));
                        }
                        return;
                    }
                    opt.ifPresent(release -> {
                        cachedRelease = release;
                        if (isNewerThanLocal(release)) {
                            announceUpdate(release);
                        } else {
                            Formatting color = manualTrigger ? Formatting.GREEN : Formatting.DARK_GREEN;
                            String sig = (manualTrigger ? "manual" : "auto") + "-uptodate-" + release.displayVersion();
                            notifyUser(Text.literal("Sequoia is up to date (" + release.displayVersion() + ").")
                                            .formatted(color),
                                    sig);
                        }
                    });
                    if (opt.isEmpty() && manualTrigger) {
                        notifyUser(Text.literal("No release information available.").formatted(Formatting.YELLOW));
                    }
                });
    }

    public static Optional<ReleaseInfo> getCachedRelease() {
        return Optional.ofNullable(cachedRelease);
    }

    private static Optional<ReleaseInfo> fetchLatestRelease() {
        JsonObject json = HttpClients.UPDATE_API.getJson(RELEASES_API, JsonObject.class);
        if (json == null) {
            return Optional.empty();
        }

        String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : "";
        String name = json.has("name") ? json.get("name").getAsString() : tag;
        String changelog = json.has("body") ? json.get("body").getAsString() : "";
        String htmlUrl = json.has("html_url") ? json.get("html_url").getAsString() : "";
        String published = json.has("published_at") ? json.get("published_at").getAsString() : "";

        String downloadUrl = extractDownloadUrl(json.getAsJsonArray("assets"));
        if (downloadUrl == null || downloadUrl.isBlank()) {
            SeqClient.warn("Latest release has no downloadable assets");
            return Optional.empty();
        }

        return Optional.of(new ReleaseInfo(tag, name, downloadUrl, htmlUrl, changelog, published));
    }

    private static String extractDownloadUrl(JsonArray assets) {
        if (assets == null) return null;
        for (JsonElement element : assets) {
            if (!element.isJsonObject()) continue;
            JsonObject asset = element.getAsJsonObject();
            String name = asset.has("name") ? asset.get("name").getAsString() : "";
            if (!name.endsWith(".jar")) continue;
            if (asset.has("browser_download_url")) {
                return asset.get("browser_download_url").getAsString();
            }
        }
        return null;
    }

    private static boolean isNewerThanLocal(ReleaseInfo release) {
        if (release == null || release.tag() == null) return false;
        String tag = release.tag();
        String local = SeqClient.getVersion();
        String stored = UPDATE_STATE.lastInstalledTag();
        if (tag.equalsIgnoreCase(local)) return false;
        return stored == null || !tag.equalsIgnoreCase(stored);
    }

    private static void announceUpdate(ReleaseInfo release) {
        MutableText text = Text.literal("Update available: ")
                .formatted(Formatting.GOLD)
                .append(Text.literal(release.displayVersion()).formatted(Formatting.AQUA))
                .append(Text.literal(" – click to install").formatted(Formatting.GRAY))
                .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sequpdate install"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Text.literal("Download and install the latest Sequoia build"))));

        notifyUser(text);
    }

    public static void installLatest(FabricClientCommandSourceBridge source) {
        if (!installing.compareAndSet(false, true)) {
            source.reply("An installation is already in progress.", Formatting.YELLOW);
            return;
        }

        ReleaseInfo release = cachedRelease;
        if (release == null) {
            installing.set(false);
            source.reply("No release information cached. Run /sequpdate first.", Formatting.RED);
            return;
        }

        if (!isNewerThanLocal(release)) {
            installing.set(false);
            source.reply("You're already on " + release.displayVersion() + ".", Formatting.GREEN);
            return;
        }

        if (SeqClient.getModJar() == null) {
            installing.set(false);
            source.reply("Unable to determine mod jar location. Automatic install is unavailable in this environment.", Formatting.RED);
            return;
        }

        Path modJarPath = SeqClient.getModJar().toPath();
        Path modsDir = modJarPath.getParent();

        source.reply("Downloading " + release.displayVersion() + "...", Formatting.AQUA);

        CompletableFuture
                .runAsync(() -> downloadAndReplace(modJarPath, modsDir, release))
                .whenComplete((unused, throwable) -> {
                    installing.set(false);
                    if (throwable != null) {
                        source.reply("Update failed: " + throwable.getMessage(), Formatting.RED);
                    } else {
                        source.reply("Update installed. Please restart Minecraft to load the new version.", Formatting.GREEN);
                    }
                });
    }

    private static void downloadAndReplace(Path modJarPath, Path modsDir, ReleaseInfo release) {
        Path temp = null;
        Path backup = modJarPath.resolveSibling(modJarPath.getFileName().toString() + ".bak");
        boolean backupCreated = false;
        try {
            temp = Files.createTempFile(modsDir, "seq-update", ".jar");
            downloadFile(release.downloadUrl(), temp);

            if (Files.exists(modJarPath)) {
                try {
                    Files.copy(modJarPath, backup, StandardCopyOption.REPLACE_EXISTING);
                    backupCreated = true;
                } catch (IOException ignored) {
                    SeqClient.warn("Could not create backup for " + modJarPath);
                }
            }

            Files.move(temp, modJarPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (backupCreated) {
                try {
                    Files.deleteIfExists(backup);
                } catch (IOException ignored) {
                    SeqClient.warn("Failed to delete backup " + backup);
                }
            }
            UPDATE_STATE.save(release.tag());
            SeqClient.info("Sequoia updated to " + release.displayVersion());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {}
            }
        }
    }

    private static void downloadFile(String downloadUrl, Path target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setRequestProperty("Accept", "application/octet-stream");
        int status = connection.getResponseCode();
        if (status >= 400) {
            throw new IOException("HTTP " + status + " while downloading update");
        }

        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void notifyUser(Text text) {
        notifyUser(text, text == null ? "" : text.getString());
    }

    private static void notifyUser(Text text, String signature) {
        NOTIFIER.notify(text, signature);
    }

    private static void cleanupBackup() {
        if (SeqClient.getModJar() == null) return;
        Path backup = SeqClient.getModJar().toPath().resolveSibling(SeqClient.getModJar().getName() + ".bak");
        try {
            Files.deleteIfExists(backup);
        } catch (IOException ignored) {}
    }

    public interface FabricClientCommandSourceBridge {
        void reply(String message, Formatting formatting);
    }
}
