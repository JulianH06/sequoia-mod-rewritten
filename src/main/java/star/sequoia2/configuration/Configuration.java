package star.sequoia2.configuration;

import com.google.common.base.StandardSystemProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import lombok.Getter;
import star.sequoia2.client.SeqClient;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import static star.sequoia2.client.SeqClient.mc;

@Getter
public final class Configuration {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final JsonCompound features;

    public Configuration() throws IOException {
        this.features = fromFile(configFile());
    }

    public void save() throws IOException {
        File file = configFile();
        Path path = file.toPath();
        Files.createDirectories(path.getParent());
        Path tmp = Files.createTempFile(path.getParent(), "seq", ".json.tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8);
             FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            GSON.toJson(this.features.getAsJsonObject(), writer);
            writer.flush();
            channel.force(true);
        }
        File bak = backupFile();
        try {
            if (Files.exists(path)) {
                Path bakParent = bak.toPath().getParent();
                if (bakParent != null) Files.createDirectories(bakParent);
                Files.copy(path, bak.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException ex) {
            SeqClient.warn("backup failed, continuing without backup", ex);
        }
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }


    private static JsonCompound fromFile(File file) throws IOException {
        if (file.exists()) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element instanceof JsonObject json) {
                    return JsonCompound.wrap(json);
                }
            } catch (JsonParseException e) {
                SeqClient.warn("Configuration JSON was malformed, attempting to load backup.", e);
                File backupFile = backupFile();
                if (backupFile.exists()) {
                    try (Reader reader = Files.newBufferedReader(backupFile.toPath(), StandardCharsets.UTF_8)) {
                        JsonElement element = JsonParser.parseReader(reader);
                        if (element instanceof JsonObject json) {
                            Files.copy(backupFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                            return JsonCompound.wrap(json);
                        }
                    } catch (JsonParseException | IOException ex) {
                        SeqClient.warn("Backup configuration could not be read.", ex);
                    }
                }
                return new JsonCompound();
            }
        }
        return new JsonCompound();
    }

    private static File backupFile() {
        return new File(mc.runDirectory, "seq.json.bak");
    }

    private File configFile() {
        return new File(mc.runDirectory, "seq.json");
    }

    public File configsFolder() throws IOException {
        File configsFolder = new File(configDirectory(), "configs");
        if (!configsFolder.exists()) {
            if (!configsFolder.mkdirs()) {
                throw new RuntimeException("Failed to create directory \"" + configsFolder.getName() + "\"");
            }
        }
        return configsFolder;
    }

    public File configDirectory() throws IOException {
        String userHome = StandardSystemProperty.USER_HOME.value();
        String osName = StandardSystemProperty.OS_NAME.value().toLowerCase(Locale.ROOT);
        Path configDirPath;
        if (userHome != null) {
            if (osName.contains("win")) {
                configDirPath = Paths.get(userHome, "seq");
            } else if (osName.contains("mac")) {
                configDirPath = Paths.get(userHome, "Library", "Application Support", "seq");
            } else {
                String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
                if (xdgConfigHome != null && !xdgConfigHome.isEmpty()) {
                    configDirPath = Paths.get(xdgConfigHome, "seq");
                } else {
                    configDirPath = Paths.get(userHome, ".config", "seq");
                }
            }
            if (!Files.exists(configDirPath)) {
                Files.createDirectories(configDirPath);
            }
            return configDirPath.toFile();
        } else return new File(mc.runDirectory, "seq");
    }
}
