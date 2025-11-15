package star.sequoia2.features;

import com.collarmc.pounce.Subscribe;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.events.SettingChanged;
import star.sequoia2.events.input.KeyEvent;
import star.sequoia2.events.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import star.sequoia2.settings.Binding;
import star.sequoia2.settings.Setting;
import star.sequoia2.settings.types.KeybindSetting;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static star.sequoia2.client.SeqClient.mc;


public class Features implements EventBusAccessor {
    private final ConcurrentMap<Class<?>, Feature> features = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ToggleFeature> toggleFeatures = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<Integer, CopyOnWriteArrayList<ToggleFeature>> keyBindings = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, CopyOnWriteArrayList<ToggleFeature>> mouseBindings = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, ToggleFeature[]> keyBindingArrays = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, ToggleFeature[]> mouseBindingArrays = new ConcurrentHashMap<>();
    private volatile boolean bindingsDirty = true;

    public Features() {
        subscribe(this);
    }

    @SuppressWarnings("unchecked")
    public <M extends Feature> Optional<M> get(Class<M> moduleType) {
        Feature feature = features.get(moduleType);
        return Optional.ofNullable((M) feature);
    }

    @SuppressWarnings("unchecked")
    public <M extends Feature> Optional<M> getIfActive(Class<M> moduleType) {
        Feature feature = features.get(moduleType);
        return Optional.ofNullable((M) feature)
                .filter(m -> m instanceof ToggleFeature tm && tm.isActive());
    }

    /**
     * Add a feature
     * @param feature to register
     */
    public void add(Feature feature) {
        features.computeIfAbsent(feature.getClass(), aClass -> {
            if (feature instanceof ToggleFeature toggleFeature) {
                toggleFeatures.add(toggleFeature);
                bindingsDirty = true;
            } else {
                // Non toggleable features always receive events
                subscribe(feature);
            }
            return feature;
        });
    }

    public Stream<Feature> all() {
        return features.values().stream();
    }

    public Optional<Feature> featureByClass(String clazz) {
        return features.values()
                .stream()
                .filter(feature -> feature.getClass().getName().equals(clazz))
                .findFirst();
    }

    public Optional<Feature> featureByName(String name) {
        return features.values()
                .stream()
                .filter(feature -> feature.getName().equals(name))
                .findFirst();
    }

    @Subscribe
    private void onKeyDown(KeyEvent event) {
        if (event.key() <= 0) return;
        if (mc.currentScreen != null || mc.inGameHud.getChatHud().isChatFocused()) {
            return;
        }
        if (bindingsDirty) rebuildBindings();

        ToggleFeature[] bound = keyBindingArrays.get(event.key());
        if (bound == null) return;
        for (ToggleFeature toggleFeature : bound) {
            if (event.isKeyDown() && toggleFeature.keybind.get().matches(event) && event.action() != GLFW.GLFW_RELEASE) {
                toggleFeature.toggle();
            }
            if (!event.isKeyDown() && toggleFeature.keybind.get().matches(event) && event.action() == GLFW.GLFW_RELEASE && !toggleFeature.keybind.getToggle()) {
                toggleFeature.toggle();
            }
        }
    }

    @Subscribe
    private void onMouseKey(MouseButtonEvent event) {
        if (mc.currentScreen != null
                || mc.inGameHud.getChatHud().isChatFocused()) {
            return;
        }
        if (bindingsDirty) rebuildBindings();

        ToggleFeature[] bound = mouseBindingArrays.get(event.button());
        if (bound == null) return;
        for (ToggleFeature toggleFeature : bound) {
            if (event.action() == GLFW.GLFW_PRESS) {
                toggleFeature.toggle();
            } else if (event.action() == GLFW.GLFW_RELEASE && !toggleFeature.keybind.getToggle()) {
                toggleFeature.toggle();
            }
        }
    }

    @Subscribe
    private void onSettingChanged(SettingChanged event) {
        Setting<?> setting = event.setting();
        if (setting instanceof KeybindSetting) {
            bindingsDirty = true;
        }
    }

    private void rebuildBindings() {
        keyBindings.clear();
        mouseBindings.clear();
        keyBindingArrays.clear();
        mouseBindingArrays.clear();
        toggleFeatures.forEach(this::registerBinding);
        keyBindings.forEach((k, list) -> keyBindingArrays.put(k, list.toArray(ToggleFeature[]::new)));
        mouseBindings.forEach((k, list) -> mouseBindingArrays.put(k, list.toArray(ToggleFeature[]::new)));
        bindingsDirty = false;
    }

    private void registerBinding(ToggleFeature toggleFeature) {
        Binding binding = toggleFeature.keybind.get();
        if (binding.key() > -1) {
            keyBindings.computeIfAbsent(binding.key(), k -> new CopyOnWriteArrayList<>()).add(toggleFeature);
        }
        if (binding.button() > -1) {
            mouseBindings.computeIfAbsent(binding.button(), k -> new CopyOnWriteArrayList<>()).add(toggleFeature);
        }
    }
}
