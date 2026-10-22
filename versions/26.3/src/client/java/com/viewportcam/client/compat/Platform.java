package com.viewportcam.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.nio.FloatBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.system.MemoryStack;

public final class Platform {

    private Platform() {}

    public static Class<?> hudClass() {
        return Hud.class;
    }

    public static Screen screen(Minecraft mc) {
        return mc.gui == null ? null : mc.gui.screen();
    }

    public static boolean hasScreen(Minecraft mc) {
        return screen(mc) != null;
    }

    public static boolean hasOverlay(Minecraft mc) {
        return mc.gui != null && mc.gui.overlay() != null;
    }

    public static void setOverlayMessage(Minecraft mc, Component message) {
        if (mc.gui != null) {
            mc.gui.hud.setOverlayMessage(message, false);
        }
    }

    public static Camera mainCamera(Minecraft mc) {
        return mc.gameRenderer.mainCamera();
    }

    public static InputConstants.Type keyboardType() {
        return InputConstants.Type.KEYBOARD;
    }

    public static int pressAction() {
        return InputConstants.PRESS;
    }

    public static int releaseAction() {
        return InputConstants.RELEASE;
    }

    public static boolean isMouseButtonDown(Minecraft mc, int button) {
        if (mc.getWindow() == null || button <= 0) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer x = stack.mallocFloat(1);
            FloatBuffer y = stack.mallocFloat(1);
            int buttons = SDLMouse.SDL_GetMouseState(x, y);
            return (buttons & (1 << (button - 1))) != 0;
        }
    }

    public static void setCursorVisible(Minecraft mc, boolean visible) {
        if (mc.getWindow() == null) {
            return;
        }
        if (visible) {
            SDLMouse.SDL_ShowCursor();
            mc.getWindow().selectCursor(CursorTypes.ARROW);
        } else {
            SDLMouse.SDL_HideCursor();
        }
    }
}
