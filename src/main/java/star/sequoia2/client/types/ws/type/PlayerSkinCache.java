package star.sequoia2.client.types.ws.type;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static star.sequoia2.client.SeqClient.mc;

//dont touch this class or i will do bad things to you, it took to long to get it to wokr

public final class PlayerSkinCache {
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private PlayerSkinCache() {}

    public static Identifier get(String uuidStr) {
        Identifier cached = CACHE.get(uuidStr);
        if (cached != null) return cached;
        if (IN_FLIGHT.add(uuidStr)) new Thread(() -> fetch(uuidStr), "SkinFetch-" + uuidStr).start();
        return DefaultSkinHelper.getTexture();
    }

    private static void fetch(String uuidStr) {
        try {
            String profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr.replace("-", "") + "?unsigned=false";
            HttpURLConnection conn = (HttpURLConnection) new URL(profileUrl).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray props = json.getAsJsonArray("properties");
                String value = null;
                for (int i = 0; i < props.size(); i++) {
                    JsonObject p = props.get(i).getAsJsonObject();
                    if ("textures".equals(p.get("name").getAsString())) {
                        value = p.get("value").getAsString();
                        break;
                    }
                }
                if (value == null) return;
                byte[] decoded = Base64.getDecoder().decode(value);
                JsonObject texturesObj = JsonParser.parseString(new String(decoded)).getAsJsonObject();
                JsonObject textures = texturesObj.getAsJsonObject("textures");
                if (textures == null || !textures.has("SKIN")) return;
                String skinUrl = textures.getAsJsonObject("SKIN").get("url").getAsString();
                HttpURLConnection imgConn = (HttpURLConnection) new URL(skinUrl).openConnection();
                imgConn.setConnectTimeout(5000);
                imgConn.setReadTimeout(5000);
                imgConn.setRequestMethod("GET");
                try (InputStream in = imgConn.getInputStream()) {
                    NativeImage image = NativeImage.read(in);
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
                    Identifier id = Identifier.of("sequoia2", "remote_skins/" + uuidStr.toLowerCase());
                    mc.getTextureManager().registerTexture(id, tex);
                    CACHE.put(uuidStr, id);
                }
            }
        } catch (Exception ignored) {
        } finally {
            IN_FLIGHT.remove(uuidStr);
        }
    }
}
