package star.sequoia2.settings.types;

import net.minecraft.util.math.MathHelper;
import star.sequoia2.configuration.JsonCompound;

/**
 * Integer setting
 */
public class IntSetting extends NumberSetting<Integer> {

    public IntSetting(int ordinal, String name, String description, Integer defaultValue, Integer value, Integer min, Integer max) {
        super(ordinal, name, description, defaultValue, value, min, max);
    }

    @Override
    public void load(JsonCompound compound) {
        int value = compound.getInt("value");
        setInternal(MathHelper.clamp(value, min, max));
    }

    @Override
    protected JsonCompound toJson(JsonCompound json) {
        json.putInt("value", get());
        return json;
    }
}
