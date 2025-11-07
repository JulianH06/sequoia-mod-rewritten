package star.sequoia2.utils;

import org.apache.commons.lang3.StringUtils;
import star.sequoia2.client.SeqClient;

import javax.crypto.SecretKey;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

import static star.sequoia2.client.SeqClient.mc;

public final class AccessTokenManager {
    public static final String ACCESS_TOKEN_FILE_NAME = "access_token.properties";
    public static final String ACCESS_TOKEN_KEY = "SequoiaModAccessToken";
    public static final String ENV_FILE_NAME = ".env";
    public static final String ENCRYPTION_KEY_PROPERTY = "SEQUOIA_MOD_ENCRYPTION_KEY";

    public static String getEncryptionKey() {
        File envFile = userDirectory().resolve(ENV_FILE_NAME).toFile();
        if (!envFile.exists()) {
            generateAndStoreEncryptionKey(envFile);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while (StringUtils.isNotBlank(line = reader.readLine())) {
                if (line.startsWith(ENCRYPTION_KEY_PROPERTY)) {
                    return line.split("=")[1].trim();
                }
            }
        } catch (IOException exception) {
            SeqClient.error("Failed to read encryption key from .env file", exception);
        }
        return StringUtils.EMPTY;
    }

    public static void generateAndStoreEncryptionKey(File envFile) {
        File directory = envFile.getParentFile();
        if (directory != null && !directory.exists() && !directory.mkdirs())
            SeqClient.warn("Failed to create directory for encryption key: " + directory.getAbsolutePath());

        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        String encryptionKey = Base64.getUrlEncoder().encodeToString(key);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(envFile, StandardCharsets.UTF_8))) {
            writer.write(ENCRYPTION_KEY_PROPERTY + "=" + encryptionKey + "\n");
            SeqClient.debug(
                    "Generated encryption key: " + encryptionKey + " and stored in " + envFile.getAbsoluteFile());
        } catch (IOException exception) {
            SeqClient.error("Failed to write encryption key to .env file", exception);
        }
    }

    public static void storeAccessToken(String token) {
        Properties properties = new Properties();
        File tokenFile = userDirectory().resolve(ACCESS_TOKEN_FILE_NAME).toFile();

        try {
            File parent = tokenFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                SeqClient.warn("Failed to create directory for token file: " + parent.getAbsolutePath());
            String encryptionKey = getEncryptionKey();
            if (StringUtils.isBlank(encryptionKey)) {
                SeqClient.error("Encryption key not found in .env file");
                return;
            }

            SecretKey secretKey = EncryptionUtils.getKeyFromString(encryptionKey);
            String encryptedToken = EncryptionUtils.encrypt(token, secretKey);

            String encodedToken =
                    Base64.getUrlEncoder().encodeToString(encryptedToken.getBytes(StandardCharsets.UTF_8));

            if (!tokenFile.exists()) {
                tokenFile.createNewFile();
            }

            try (FileInputStream fileInputStream = new FileInputStream(tokenFile)) {
                properties.load(fileInputStream);
            }

            properties.setProperty(ACCESS_TOKEN_KEY, encodedToken);

            try (FileOutputStream fileOutputStream = new FileOutputStream(tokenFile)) {
                properties.store(fileOutputStream, "Make sure to NEVER share this token with anyone!");
            }
        } catch (Exception exception) {
            SeqClient.error("Failed to store access token", exception);
        }
    }

    public static String retrieveAccessToken() {
        Properties properties = new Properties();
        File tokenFile = userDirectory().resolve(ACCESS_TOKEN_FILE_NAME).toFile();

        try {
            if (tokenFile.exists()) {
                try (FileInputStream fileInputStream = new FileInputStream(tokenFile)) {
                    properties.load(fileInputStream);
                }

                String encodedToken = properties.getProperty(ACCESS_TOKEN_KEY);
                if (StringUtils.isNotBlank(encodedToken)) {
                    String decodedToken =
                            new String(Base64.getUrlDecoder().decode(encodedToken), StandardCharsets.UTF_8);
                    String encryptionKey = getEncryptionKey();

                    if (StringUtils.isNotBlank(encryptionKey)) {
                        SecretKey secretKey = EncryptionUtils.getKeyFromString(encryptionKey);
                        return EncryptionUtils.decrypt(decodedToken, secretKey);
                    }
                }
            }
        } catch (Exception exception) {
            SeqClient.error("Failed to retrieve access token", exception);
        }
        return StringUtils.EMPTY;
    }

    public static void invalidateAccessToken() {
        File tokenFile = userDirectory().resolve(ACCESS_TOKEN_FILE_NAME).toFile();
        if (tokenFile.exists() && !tokenFile.delete()) {
            SeqClient.warn("Failed to delete access token file: " + tokenFile.getAbsolutePath());
        } // i don't even care if this is insecure, blame dot jj or whoever originally wrote this
        SeqClient.debug("Access token was invalidated.");
    }

    private static Path userDirectory() {
        Path base = resolveBaseDirectory();
        String uuid = activeUuid();
        Path userDir = base.resolve(uuid);
        try {
            Files.createDirectories(userDir);
        } catch (IOException ignored) {}
        return userDir;
    }

    private static Path resolveBaseDirectory() {
        try {
            if (SeqClient.getConfiguration() != null) {
                File configDir = SeqClient.getConfiguration().configDirectory();
                Path auth = configDir.toPath().resolve("auth");
                Files.createDirectories(auth);
                return auth;
            }
        } catch (IOException ignored) {}
        Path fallback = mc.runDirectory.toPath().resolve("sequoia").resolve("auth");
        try {
            Files.createDirectories(fallback);
        } catch (IOException ignored) {}
        return fallback;
    }

    private static String activeUuid() {
        if (mc.getSession() != null && mc.getSession().getUuidOrNull() != null) {
            return mc.getSession().getUuidOrNull().toString();
        }
        String user = System.getProperty("user.name", "user");
        return "offline-" + user;
    }
}
