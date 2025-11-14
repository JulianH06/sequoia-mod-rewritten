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
    private static final String LEGACY_TAG_KEY = "lastInstalledTag";

    private final Path path;
    private final Map<UpdateChannel, String> installed;

    private UpdateState(Path path, Map<UpdateChannel, String> installed) {
        this.path = path;
        this.installed = installed;
    }

    public static UpdateState load() {
        Path dir = SeqClient.getModStorageDir("updates").toPath();
        Path file = dir.resolve(FILE_NAME);
        EnumMap<UpdateChannel, String> installed = new EnumMap<>(UpdateChannel.class);
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
            } catch (Exception e) {
                SeqClient.warn("Failed to read update state", e);
            }
        }
        return new UpdateState(file, installed);
    }

    public String lastInstalledTag(UpdateChannel channel) {
        return installed.get(channel);
    }

    public void save(UpdateChannel channel, String tag) {
        if (channel == null || StringUtils.isBlank(tag)) return;
        installed.put(channel, tag);
        try {
            Files.createDirectories(path.getParent());
            JsonObject json = new JsonObject();
            JsonObject channelJson = new JsonObject();
            for (Map.Entry<UpdateChannel, String> entry : installed.entrySet()) {
                if (StringUtils.isNotBlank(entry.getValue())) {
                    channelJson.addProperty(entry.getKey().storageKey(), entry.getValue());
                }
            }
            json.add(INSTALLED_KEY, channelJson);
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
        } catch (IOException e) {
            SeqClient.warn("Failed to persist update state", e);
        }
    }
}
