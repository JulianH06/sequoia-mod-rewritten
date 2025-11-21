package star.sequoia2.settings.types;


import net.minecraft.util.math.MathHelper;
import star.sequoia2.configuration.JsonCompound;

public class DoubleSetting extends NumberSetting<Double> {

    public DoubleSetting(int ordinal, String name, String description, Double defaultValue, Double value, Double min, Double max) {
        super(ordinal, name, description, defaultValue, value, min, max);
    }

    public DoubleSetting(int ordinal, String name, String description, Double defaultValue, Double value, Double min, Double max, int scale) {
        super(ordinal, name, description, defaultValue, value, min, max, scale);
    }

    @Override
    public void load(JsonCompound compound) {
        double value = compound.getDouble("value");
        setInternal(MathHelper.clamp(value, min, max));
    }

    @Override
    protected JsonCompound toJson(JsonCompound json) {
        json.putDouble("value", get());
        return json;
    }
}
