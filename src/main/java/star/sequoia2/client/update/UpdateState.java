package star.sequoia2.client.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import star.sequoia2.client.SeqClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
public final class UpdateState {
    private static final String FILE_NAME = "update_state.json";
    private static final String INSTALLED_KEY = "installed";
    private static final String SIGNATURES_KEY = "signatures";
    private static final String LEGACY_TAG_KEY = "lastInstalledTag";

    private final Path path;
    private final Map<UpdateChannel, String> installedTags;
    private final Map<UpdateChannel, String> installedSignatures;

    private UpdateState(Path path, Map<UpdateChannel, String> installed, Map<UpdateChannel, String> signatures) {
        this.path = path;
        this.installedTags = installed;
        this.installedSignatures = signatures;
    }

    public static UpdateState load() {
        Path dir = SeqClient.getModStorageDir("updates").toPath();
        Path file = dir.resolve(FILE_NAME);
        EnumMap<UpdateChannel, String> installed = new EnumMap<>(UpdateChannel.class);
        EnumMap<UpdateChannel, String> signatures = new EnumMap<>(UpdateChannel.class);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has(INSTALLED_KEY) && json.get(INSTALLED_KEY).isJsonObject()) {
                    JsonObject channels = json.getAsJsonObject(INSTALLED_KEY);
                    for (UpdateChannel channel : UpdateChannel.values()) {
                        if (channels.has(channel.storageKey())) {
                            installed.put(channel, channels.get(channel.storageKey()).getAsString());
                        }
                    }
                } else if (json.has(LEGACY_TAG_KEY)) {
                    installed.put(UpdateChannel.STABLE, json.get(LEGACY_TAG_KEY).getAsString());
                }
                if (json.has(SIGNATURES_KEY) && json.get(SIGNATURES_KEY).isJsonObject()) {
                    JsonObject sigs = json.getAsJsonObject(SIGNATURES_KEY);
                    for (UpdateChannel channel : UpdateChannel.values()) {
                        if (sigs.has(channel.storageKey())) {
                            signatures.put(channel, sigs.get(channel.storageKey()).getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                SeqClient.warn("Failed to read update state", e);
            }
        }
        return new UpdateState(file, installed, signatures);
    }

    public String lastInstalledTag(UpdateChannel channel) {
        return installedTags.get(channel);
    }

    public String lastInstalledSignature(UpdateChannel channel) {
        // fall back to tag for legacy installs
        String sig = installedSignatures.get(channel);
        if (sig == null) {
            return lastInstalledTag(channel);
        }
        return sig;
    }

    public void save(UpdateChannel channel, String tag) {
        save(channel, tag, null);
    }

    public void save(UpdateChannel channel, String tag, String signature) {
        if (channel == null) return;
        if (StringUtils.isNotBlank(tag)) {
            installedTags.put(channel, tag);
        }
        if (StringUtils.isNotBlank(signature)) {
            installedSignatures.put(channel, signature);
        }
        try {
            Files.createDirectories(path.getParent());
            JsonObject json = new JsonObject();
            JsonObject channelJson = new JsonObject();
            for (Map.Entry<UpdateChannel, String> entry : installedTags.entrySet()) {
                if (StringUtils.isNotBlank(entry.getValue())) {
                    channelJson.addProperty(entry.getKey().storageKey(), entry.getValue());
                }
            }
            json.add(INSTALLED_KEY, channelJson);
            JsonObject sigJson = new JsonObject();
            for (Map.Entry<UpdateChannel, String> entry : installedSignatures.entrySet()) {
                if (StringUtils.isNotBlank(entry.getValue())) {
                    sigJson.addProperty(entry.getKey().storageKey(), entry.getValue());
                }
            }
            json.add(SIGNATURES_KEY, sigJson);
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
        } catch (IOException e) {
            SeqClient.warn("Failed to persist update state", e);
        }
    }
}
