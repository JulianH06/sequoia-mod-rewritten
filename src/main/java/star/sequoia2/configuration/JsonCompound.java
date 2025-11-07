package star.sequoia2.configuration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class JsonCompound extends JsonElement {
    private final JsonObject delegate;

    public JsonCompound() {
        this(new JsonObject());
    }

    private JsonCompound(JsonObject delegate) {
        this.delegate = delegate;
    }

    public static JsonCompound wrap(JsonElement element) {
        if (element instanceof JsonCompound compound) {
            return compound;
        } else if (element instanceof JsonObject object) {
            return new JsonCompound(object);
        } else {
            throw new ClassCastException("Object cannot be cast to JsonCompound");
        }
    }

    @Override
    public JsonCompound deepCopy() {
        return new JsonCompound(delegate.deepCopy());
    }

    public boolean contains(String key) {
        return delegate.has(key);
    }

    @Nullable
    public JsonElement put(String property, JsonElement value) {
        JsonElement previous = delegate.get(property);
        delegate.add(property, value == null ? JsonNull.INSTANCE : value);
        return previous;
    }

    public void putByte(String key, byte value) {
        put(key, new JsonPrimitive(value));
    }

    public void putShort(String key, short value) {
        put(key, new JsonPrimitive(value));
    }

    public void putInt(String key, int value) {
        put(key, new JsonPrimitive(value));
    }

    public void putLong(String key, long value) {
        put(key, new JsonPrimitive(value));
    }

    public void putFloat(String key, float value) {
        put(key, new JsonPrimitive(value));
    }

    public void putDouble(String key, double value) {
        put(key, new JsonPrimitive(value));
    }

    public void putString(String key, String value) {
        put(key, new JsonPrimitive(value));
    }

    public void putBoolean(String key, boolean value) {
        put(key, new JsonPrimitive(value));
    }

    public void putUuid(String key, UUID value) {
        put(key, new JsonPrimitive(value.toString()));
    }

    public byte getByte(String key) {
        try {
            if (contains(key)) {
                return delegate.get(key).getAsByte();
            }
        } catch (UnsupportedOperationException ignored) {}

        return 0;
    }

    public short getShort(String key) {
        try {
            if (contains(key)) {
                return delegate.get(key).getAsShort();
            }
        } catch (UnsupportedOperationException ignored) {}

        return 0;
    }

    public int getInt(String key) {
        try {
            if (contains(key)) {
                return delegate.get(key).getAsInt();
            }
        } catch (UnsupportedOperationException ignored) {}

        return 0;
    }

    public long getLong(String key) {
        try {
            if (contains(key)) {
                return delegate.get(key).getAsLong();
            }
        } catch (UnsupportedOperationException ignored) {}

        return 0L;
    }

    public float getFloat(String key) {
        try {
            if (contains(key)) {
                return delegate.get(key).getAsFloat();
            }
        } catch (UnsupportedOperationException ignored) {}

        return 0.0F;
    }

    public double getDouble(String key) {
        try {
            if (contains(key)) {
                return delegate.get(key).getAsDouble();
            }
        } catch (UnsupportedOperationException ignored) {}

        return 0.0;
    }

    public String getString(String key) {
        try {
            if (contains(key)) {
                return delegate.get(key).getAsString();
            }
        } catch (UnsupportedOperationException ignored) {}

        return "";
    }

    public boolean getBoolean(String key) {
        try {
            if (contains(key)) {
                return delegate.get(key).getAsBoolean();
            }
        } catch (UnsupportedOperationException ignored) {}

        return false;
    }

    public UUID getUuid(String key) {
        try {
            if (contains(key)) {
                return UUID.fromString(delegate.get(key).getAsString());
            }
        } catch (UnsupportedOperationException | IllegalArgumentException ignored) {}

        return new UUID(0L, 0L);
    }

    public JsonCompound getCompound(String key) {
        if (!contains(key)) {
            return new JsonCompound();
        }
        JsonElement element = delegate.get(key);
        if (element instanceof JsonCompound compound) {
            return compound;
        } else if (element instanceof JsonObject object) {
            return JsonCompound.wrap(object);
        }
        return new JsonCompound();
    }

    public JsonArray getList(String key) {
        if (contains(key)) {
            return delegate.get(key).getAsJsonArray();
        }
        return new JsonArray();
    }

    public int size() {
        return delegate.size();
    }

    public boolean isEmpty() {
        return delegate.size() == 0;
    }

    public Set<Map.Entry<String, JsonElement>> entrySet() {
        return delegate.entrySet();
    }

    public Set<String> keySet() {
        return delegate.keySet();
    }

    public Map<String, JsonElement> asMap() {
        return delegate.asMap();
    }

    @Override
    public JsonObject getAsJsonObject() {
        return delegate.deepCopy();
    }

    @Override
    public boolean isJsonObject() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return (o == this) || (o instanceof JsonCompound compound && compound.delegate.equals(delegate));
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
