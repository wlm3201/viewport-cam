package com.viewportcam.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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
        return InputConstants.Type.KEYSYM;
    }

    public static int pressAction() {
        return GLFW.GLFW_PRESS;
    }

    public static int releaseAction() {
        return GLFW.GLFW_RELEASE;
    }

    public static boolean isMouseButtonDown(Minecraft mc, int button) {
        return mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().handle(), button) == GLFW.GLFW_PRESS;
    }

    public static void setCursorVisible(Minecraft mc, boolean visible) {
        if (mc.getWindow() == null) {
            return;
        }
        GLFW.glfwSetInputMode(
                mc.getWindow().handle(),
                GLFW.GLFW_CURSOR,
                visible ? GLFW.GLFW_CURSOR_NORMAL : GLFW.GLFW_CURSOR_HIDDEN);
    }
}
