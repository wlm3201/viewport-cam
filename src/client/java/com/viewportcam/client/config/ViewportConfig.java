package com.viewportcam.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.JsonAdapter;
import com.viewportcam.client.ViewportCamClient;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class ViewportConfig {

    public enum MouseMode {
        ALT_HOLD,
        ALT_TOGGLE,
        INDEPENDENT,
        HYBRID
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("viewportcam.json");

    private static final double HUD_POSITION_MAX = 1000.0;

    private static final ViewportConfig DEFAULTS = new ViewportConfig();

    private static final Map<String, Object> DEFAULT_CACHE = new HashMap<>();

    public static final ViewportConfig INSTANCE = load();

    public double rotateSensitivity = 1.0;

    public double rotateSmoothing = 1.0;

    public double panSmoothing = 1.0;

    public double panSensitivity = 1.0;

    public double scaleSensitivity = 1.0;

    public double zoomSensitivity = 1.0;

    public double zoomTransitionSpeed = 1.0;

    public double horizontalSpeed = 20.0;

    public double verticalSpeed = 20.0;

    public double sprintHorizontalMultiplier = 2.0;

    public double sprintVerticalMultiplier = 2.0;

    public double flightAccel = 0.5;

    public double flightDecel = 0.25;

    public double clientBlockReach = 128;

    public double clientEntityReach = 128;

    public MouseMode mouseMode = MouseMode.HYBRID;

    public double clickDragThreshold = 4.0;

    public boolean singleplayerServerReach = true;

    public boolean bodyFollowsCamera = true;

    public boolean zoomTrackAnchor = false;

    public boolean showPivotOutline = true;

    @JsonAdapter(HexColor.class)
    public int pivotOutlineColor = HexColor.WHITE;

    public boolean showStatusHud = true;

    public double statusHudX = 10.0;

    public double statusHudY = 10.0;

    public static void save() {
        save(INSTANCE);
    }

    private static void save(ViewportConfig config) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ViewportCamClient.LOGGER.error("Failed to save ViewportCam config", e);
        }
    }

    private static ViewportConfig load() {
        if (Files.exists(FILE)) {
            try {
                String json = Files.readString(FILE, StandardCharsets.UTF_8);
                ViewportConfig parsed = GSON.fromJson(json, ViewportConfig.class);
                if (parsed != null) {
                    JsonObject tree = parseTree(json);
                    int patched = tree == null ? 0 : restoreMissing(tree, parsed);
                    clampToLimits(parsed);
                    if (patched > 0) {
                        save(parsed);
                    }
                    return parsed;
                }
            } catch (Exception e) {
                ViewportCamClient.LOGGER.error(
                        "Failed to load ViewportCam config, using defaults", e);
            }
        }
        ViewportConfig defaults = new ViewportConfig();
        save(defaults);
        return defaults;
    }

    @Nullable
    private static JsonObject parseTree(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            ViewportCamClient.LOGGER.warn("配置文件不是合法的 JSON 对象，跳过字段补齐", e);
            return null;
        }
    }

    private static int restoreMissing(JsonObject tree, ViewportConfig target) {
        int patched = 0;
        for (Field field : ViewportConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || tree.has(field.getName())) {
                continue;
            }
            try {
                field.setAccessible(true);
                field.set(target, field.get(DEFAULTS));
                patched++;
            } catch (ReflectiveOperationException | RuntimeException e) {
                ViewportCamClient.LOGGER.warn("配置项回填失败: " + field.getName(), e);
            }
        }
        return patched;
    }

    private static void clampToLimits(ViewportConfig c) {
        if (c.mouseMode == null) {
            c.mouseMode = DEFAULTS.mouseMode;
        }
        c.rotateSensitivity = limit(c.rotateSensitivity, 0.05, 4.0, DEFAULTS.rotateSensitivity);
        c.rotateSmoothing = limit(c.rotateSmoothing, 0.0, 4.0, DEFAULTS.rotateSmoothing);
        c.panSmoothing = limit(c.panSmoothing, 0.0, 4.0, DEFAULTS.panSmoothing);
        c.panSensitivity = limit(c.panSensitivity, 0.1, 4.0, DEFAULTS.panSensitivity);
        c.scaleSensitivity = limit(c.scaleSensitivity, 0.1, 4.0, DEFAULTS.scaleSensitivity);
        c.zoomSensitivity = limit(c.zoomSensitivity, 0.1, 4.0, DEFAULTS.zoomSensitivity);
        c.zoomTransitionSpeed =
                limit(c.zoomTransitionSpeed, 0.25, 4.0, DEFAULTS.zoomTransitionSpeed);
        c.horizontalSpeed = limit(c.horizontalSpeed, 2.0, 40.0, DEFAULTS.horizontalSpeed);
        c.verticalSpeed = limit(c.verticalSpeed, 2.0, 40.0, DEFAULTS.verticalSpeed);
        c.sprintHorizontalMultiplier =
                limit(c.sprintHorizontalMultiplier, 1.0, 8.0, DEFAULTS.sprintHorizontalMultiplier);
        c.sprintVerticalMultiplier =
                limit(c.sprintVerticalMultiplier, 1.0, 8.0, DEFAULTS.sprintVerticalMultiplier);
        c.flightAccel = limit(c.flightAccel, 0.0, 1.0, DEFAULTS.flightAccel);
        c.flightDecel = limit(c.flightDecel, 0.0, 1.0, DEFAULTS.flightDecel);
        c.clientBlockReach = limit(c.clientBlockReach, -1.0, 256.0, DEFAULTS.clientBlockReach);
        c.clientEntityReach = limit(c.clientEntityReach, -1.0, 256.0, DEFAULTS.clientEntityReach);
        c.clickDragThreshold = limit(c.clickDragThreshold, 0.0, 20.0, DEFAULTS.clickDragThreshold);
        c.statusHudX = limit(c.statusHudX, 0.0, HUD_POSITION_MAX, DEFAULTS.statusHudX);
        c.statusHudY = limit(c.statusHudY, 0.0, HUD_POSITION_MAX, DEFAULTS.statusHudY);
    }

    private static double limit(double value, double min, double max, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            return fallback;
        }
        return value;
    }

    public static Screen createScreen(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("viewportcam.title"))
                .category(
                        ConfigCategory.createBuilder()
                                .name(Component.translatable("viewportcam.title"))
                                .group(
                                        group(
                                                "feel",
                                                slider(
                                                        "rotateSensitivity",
                                                        () -> INSTANCE.rotateSensitivity,
                                                        v -> INSTANCE.rotateSensitivity = v,
                                                        0.05,
                                                        4.0,
                                                        0.05),
                                                slider(
                                                        "rotateSmoothing",
                                                        () -> INSTANCE.rotateSmoothing,
                                                        v -> INSTANCE.rotateSmoothing = v,
                                                        0.0,
                                                        4.0,
                                                        0.05,
                                                        desc("rotateSmoothing")),
                                                slider(
                                                        "panSensitivity",
                                                        () -> INSTANCE.panSensitivity,
                                                        v -> INSTANCE.panSensitivity = v,
                                                        0.05,
                                                        4.0,
                                                        0.05),
                                                slider(
                                                        "panSmoothing",
                                                        () -> INSTANCE.panSmoothing,
                                                        v -> INSTANCE.panSmoothing = v,
                                                        0.0,
                                                        4.0,
                                                        0.05,
                                                        desc("panSmoothing")),
                                                slider(
                                                        "scaleSensitivity",
                                                        () -> INSTANCE.scaleSensitivity,
                                                        v -> INSTANCE.scaleSensitivity = v,
                                                        0.05,
                                                        4.0,
                                                        0.05,
                                                        desc("scaleSensitivity"))))
                                .group(
                                        group(
                                                "move",
                                                slider(
                                                        "horizontalSpeed",
                                                        () -> INSTANCE.horizontalSpeed,
                                                        v -> INSTANCE.horizontalSpeed = v,
                                                        2.0,
                                                        40.0,
                                                        1.0,
                                                        desc("horizontalSpeed")),
                                                slider(
                                                        "verticalSpeed",
                                                        () -> INSTANCE.verticalSpeed,
                                                        v -> INSTANCE.verticalSpeed = v,
                                                        2.0,
                                                        40.0,
                                                        1.0,
                                                        desc("verticalSpeed")),
                                                slider(
                                                        "sprintHorizontalMultiplier",
                                                        () -> INSTANCE.sprintHorizontalMultiplier,
                                                        v ->
                                                                INSTANCE.sprintHorizontalMultiplier =
                                                                        v,
                                                        1.0,
                                                        8.0,
                                                        0.1,
                                                        desc("sprintHorizontalMultiplier")),
                                                slider(
                                                        "sprintVerticalMultiplier",
                                                        () -> INSTANCE.sprintVerticalMultiplier,
                                                        v -> INSTANCE.sprintVerticalMultiplier = v,
                                                        1.0,
                                                        8.0,
                                                        0.1,
                                                        desc("sprintVerticalMultiplier")),
                                                slider(
                                                        "flightAccel",
                                                        () -> INSTANCE.flightAccel,
                                                        v -> INSTANCE.flightAccel = v,
                                                        0.0,
                                                        1.0,
                                                        0.05,
                                                        desc("flightAccel")),
                                                slider(
                                                        "flightDecel",
                                                        () -> INSTANCE.flightDecel,
                                                        v -> INSTANCE.flightDecel = v,
                                                        0.0,
                                                        1.0,
                                                        0.05,
                                                        desc("flightDecel"))))
                                .group(
                                        group(
                                                "zoom",
                                                slider(
                                                        "zoomTransitionSpeed",
                                                        () -> INSTANCE.zoomTransitionSpeed,
                                                        v -> INSTANCE.zoomTransitionSpeed = v,
                                                        0.25,
                                                        4.0,
                                                        0.05,
                                                        desc("zoomTransitionSpeed")),
                                                slider(
                                                        "zoomSensitivity",
                                                        () -> INSTANCE.zoomSensitivity,
                                                        v -> INSTANCE.zoomSensitivity = v,
                                                        0.05,
                                                        4.0,
                                                        0.05,
                                                        desc("zoomSensitivity")),
                                                toggle(
                                                        "zoomTrackAnchor",
                                                        () -> INSTANCE.zoomTrackAnchor,
                                                        v -> INSTANCE.zoomTrackAnchor = v,
                                                        defaultBoolean("zoomTrackAnchor"),
                                                        true,
                                                        desc("zoomTrackAnchor"))))
                                .group(
                                        group(
                                                "mode",
                                                mouseModeOption(),
                                                slider(
                                                        "clickDragThreshold",
                                                        () -> INSTANCE.clickDragThreshold,
                                                        v -> INSTANCE.clickDragThreshold = v,
                                                        0.0,
                                                        20.0,
                                                        1,
                                                        desc("clickDragThreshold"))))
                                .group(
                                        group(
                                                "render",
                                                toggle(
                                                        "showPivotOutline",
                                                        () -> INSTANCE.showPivotOutline,
                                                        v -> INSTANCE.showPivotOutline = v,
                                                        defaultBoolean("showPivotOutline"),
                                                        true,
                                                        desc("showPivotOutline")),
                                                pivotColorOption()))
                                .group(
                                        group(
                                                "reach",
                                                slider(
                                                        "clientBlockReach",
                                                        () -> INSTANCE.clientBlockReach,
                                                        v -> INSTANCE.clientBlockReach = v,
                                                        -1.0,
                                                        256.0,
                                                        1.0,
                                                        desc("clientBlockReach")),
                                                slider(
                                                        "clientEntityReach",
                                                        () -> INSTANCE.clientEntityReach,
                                                        v -> INSTANCE.clientEntityReach = v,
                                                        -1.0,
                                                        256.0,
                                                        1.0,
                                                        desc("clientEntityReach"))))
                                .group(
                                        group(
                                                "singleplayer",
                                                toggle(
                                                        "singleplayerServerReach",
                                                        () -> INSTANCE.singleplayerServerReach,
                                                        v -> INSTANCE.singleplayerServerReach = v,
                                                        defaultBoolean("singleplayerServerReach"),
                                                        singleplayerReachAvailable(),
                                                        desc("singleplayerServerReach")),
                                                toggle(
                                                        "bodyFollowsCamera",
                                                        () -> INSTANCE.bodyFollowsCamera,
                                                        v -> INSTANCE.bodyFollowsCamera = v,
                                                        defaultBoolean("bodyFollowsCamera"),
                                                        bodyFollowAvailable(),
                                                        desc("bodyFollowsCamera"))))
                                .group(
                                        group(
                                                "hud",
                                                toggle(
                                                        "showStatusHud",
                                                        () -> INSTANCE.showStatusHud,
                                                        v -> INSTANCE.showStatusHud = v),
                                                field(
                                                        "statusHudX",
                                                        () -> INSTANCE.statusHudX,
                                                        v -> INSTANCE.statusHudX = v,
                                                        0.0,
                                                        HUD_POSITION_MAX,
                                                        desc("statusHudX")),
                                                field(
                                                        "statusHudY",
                                                        () -> INSTANCE.statusHudY,
                                                        v -> INSTANCE.statusHudY = v,
                                                        0.0,
                                                        HUD_POSITION_MAX,
                                                        desc("statusHudY"))))
                                .build())
                .save(ViewportConfig::save)
                .build()
                .generateScreen(parent);
    }

    private static OptionGroup group(String name, Option<?>... options) {
        OptionGroup.Builder builder =
                OptionGroup.createBuilder()
                        .name(Component.translatable("viewportcam.group." + name));
        for (Option<?> option : options) {
            builder.option(option);
        }
        return builder.build();
    }

    private static Option<MouseMode> mouseModeOption() {
        return Option.<MouseMode>createBuilder()
                .name(Component.translatable("viewportcam.option.mouseMode"))
                .description(
                        OptionDescription.of(
                                Component.translatable(
                                        "viewportcam.option.mouseMode.desc.alt_hold"),
                                Component.translatable(
                                        "viewportcam.option.mouseMode.desc.alt_toggle"),
                                Component.translatable(
                                        "viewportcam.option.mouseMode.desc.independent"),
                                Component.translatable("viewportcam.option.mouseMode.desc.hybrid")))
                .binding(
                        defaultTyped("mouseMode", MouseMode.class, MouseMode.HYBRID),
                        () -> INSTANCE.mouseMode,
                        v -> INSTANCE.mouseMode = v)
                .controller(
                        opt ->
                                EnumControllerBuilder.create(opt)
                                        .enumClass(MouseMode.class)
                                        .formatValue(
                                                mode ->
                                                        Component.translatable(
                                                                "viewportcam.option.mouseMode."
                                                                        + mode.name()
                                                                                .toLowerCase(
                                                                                        Locale
                                                                                                .ROOT))))
                .build();
    }

    private static Option<Color> pivotColorOption() {
        return Option.<Color>createBuilder()
                .name(Component.translatable("viewportcam.option.pivotOutlineColor"))
                .description(OptionDescription.of(desc("pivotOutlineColor")))
                .binding(
                        new Color(HexColor.WHITE, true),
                        () -> new Color(INSTANCE.pivotOutlineColor, true),
                        v -> INSTANCE.pivotOutlineColor = v.getRGB())
                .controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
                .build();
    }

    private static Option<Double> slider(
            String key,
            Supplier<Double> getter,
            Consumer<Double> setter,
            double min,
            double max,
            double step,
            Component... description) {
        Option.Builder<Double> builder =
                Option.<Double>createBuilder()
                        .name(Component.translatable("viewportcam.option." + key))
                        .binding(defaults(key), getter, setter)
                        .controller(
                                opt ->
                                        DoubleSliderControllerBuilder.create(opt)
                                                .range(min, max)
                                                .step(step));
        if (description.length > 0) {
            builder.description(OptionDescription.of(description));
        }
        return builder.build();
    }

    private static Option<Double> field(
            String key,
            Supplier<Double> getter,
            Consumer<Double> setter,
            double min,
            double max,
            Component... description) {
        Option.Builder<Double> builder =
                Option.<Double>createBuilder()
                        .name(Component.translatable("viewportcam.option." + key))
                        .binding(defaults(key), getter, setter)
                        .controller(
                                opt -> DoubleFieldControllerBuilder.create(opt).range(min, max));
        if (description.length > 0) {
            builder.description(OptionDescription.of(description));
        }
        return builder.build();
    }

    private static Component desc(String key) {
        return Component.translatable("viewportcam.option." + key + ".desc");
    }

    private static Option<Boolean> toggle(
            String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return toggle(key, getter, setter, defaultBoolean(key), true);
    }

    private static Option<Boolean> toggle(
            String key,
            Supplier<Boolean> getter,
            Consumer<Boolean> setter,
            boolean defaultValue,
            boolean available,
            Component... description) {
        Option.Builder<Boolean> builder =
                Option.<Boolean>createBuilder()
                        .name(Component.translatable("viewportcam.option." + key))
                        .binding(defaultValue, getter, setter)
                        .controller(BooleanControllerBuilder::create)
                        .available(available);
        if (description.length > 0) {
            builder.description(OptionDescription.of(description));
        }
        return builder.build();
    }

    private static boolean singleplayerReachAvailable() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            return server.getWorldData().isAllowCommands();
        }
        return mc.getConnection() == null;
    }

    private static boolean bodyFollowAvailable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return true;
        }
        return player.isCreative() && mc.getSingleplayerServer() != null;
    }

    private static Object defaultOf(String key) {
        if (DEFAULT_CACHE.containsKey(key)) {
            return DEFAULT_CACHE.get(key);
        }
        Object value = null;
        try {
            Field field = ViewportConfig.class.getDeclaredField(key);
            field.setAccessible(true);
            value = field.get(DEFAULTS);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ViewportCamClient.LOGGER.warn("配置项没有默认值: " + key, e);
        }
        DEFAULT_CACHE.put(key, value);
        return value;
    }

    private static double defaults(String key) {
        return defaultOf(key) instanceof Number number ? number.doubleValue() : 1.0;
    }

    private static boolean defaultBoolean(String key) {
        return Boolean.TRUE.equals(defaultOf(key));
    }

    private static <T> T defaultTyped(String key, Class<T> type, T fallback) {
        Object value = defaultOf(key);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }
}
