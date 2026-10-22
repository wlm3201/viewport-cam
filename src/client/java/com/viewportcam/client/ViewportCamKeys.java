package com.viewportcam.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.viewportcam.client.compat.Platform;
import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;

public final class ViewportCamKeys {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath("viewportcam", "viewportcam"));

    public static final KeyMapping TOGGLE =
            new KeyMapping(
                    "key.viewportcam.toggle",
                    Platform.keyboardType(),
                    InputConstants.KEY_F6,
                    CATEGORY);

    public static final KeyMapping MODIFIER =
            new KeyMapping(
                    "key.viewportcam.modifier",
                    Platform.keyboardType(),
                    InputConstants.KEY_LALT,
                    CATEGORY);

    public static final KeyMapping ROTATE =
            new KeyMapping(
                    "key.viewportcam.rotate",
                    InputConstants.Type.MOUSE,
                    InputConstants.MOUSE_BUTTON_LEFT,
                    CATEGORY);

    public static final KeyMapping PAN =
            new KeyMapping(
                    "key.viewportcam.pan",
                    InputConstants.Type.MOUSE,
                    InputConstants.MOUSE_BUTTON_RIGHT,
                    CATEGORY);

    public static final KeyMapping ZOOM =
            new KeyMapping(
                    "key.viewportcam.zoom",
                    Platform.keyboardType(),
                    InputConstants.KEY_C,
                    CATEGORY);

    private static boolean registered;

    private static Field keyMappingsField;

    private static boolean keyMappingsLookupFailed;

    private ViewportCamKeys() {}

    public static void register(Options options) {
        if (options == null || registered) {
            return;
        }
        boolean ok = append(options, TOGGLE);
        ok &= append(options, MODIFIER);
        ok &= append(options, ROTATE);
        ok &= append(options, PAN);
        ok &= append(options, ZOOM);
        registered = ok;
    }

    private static boolean append(Options options, KeyMapping binding) {
        Field field = keyMappingsField();
        if (field == null) {
            return false;
        }
        try {
            KeyMapping[] all = (KeyMapping[]) field.get(options);
            for (KeyMapping mapping : all) {
                if (mapping == binding) {
                    return true;
                }
            }
            KeyMapping[] updated = Arrays.copyOf(all, all.length + 1);
            updated[all.length] = binding;
            field.set(options, updated);
            return true;
        } catch (Throwable t) {
            ViewportCamClient.LOGGER.error("Could not register ViewportCam keybind in options", t);
            return false;
        }
    }

    private static Field keyMappingsField() {
        if (keyMappingsField != null) {
            return keyMappingsField;
        }
        if (keyMappingsLookupFailed) {
            return null;
        }
        try {
            Field field = Options.class.getDeclaredField("keyMappings");
            field.setAccessible(true);
            keyMappingsField = field;
            return field;
        } catch (Throwable t) {
            keyMappingsLookupFailed = true;
            ViewportCamClient.LOGGER.error("找不到 Options.keyMappings 字段，ViewportCam 的按键将无法生效", t);
            return null;
        }
    }
}
