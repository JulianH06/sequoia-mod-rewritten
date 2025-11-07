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

public final class UpdateState {
    private static final String FILE_NAME = "update_state.json";
    private static final String TAG_KEY = "lastInstalledTag";

    private final Path path;
    private volatile String lastInstalledTag;

    private UpdateState(Path path, String tag) {
        this.path = path;
        this.lastInstalledTag = tag;
    }

    public static UpdateState load() {
        Path dir = SeqClient.getModStorageDir("updates").toPath();
        Path file = dir.resolve(FILE_NAME);
        String tag = null;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has(TAG_KEY)) {
                    tag = json.get(TAG_KEY).getAsString();
                }
            } catch (Exception e) {
                SeqClient.warn("Failed to read update state", e);
            }
        }
        return new UpdateState(file, tag);
    }

    public String lastInstalledTag() {
        return lastInstalledTag;
    }

    public void save(String tag) {
        if (StringUtils.isBlank(tag)) return;
        lastInstalledTag = tag;
        try {
            Files.createDirectories(path.getParent());
            JsonObject json = new JsonObject();
            json.addProperty(TAG_KEY, tag);
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
        } catch (IOException e) {
            SeqClient.warn("Failed to persist update state", e);
        }
    }
}
