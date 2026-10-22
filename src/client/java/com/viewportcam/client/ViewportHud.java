package com.viewportcam.client;

import com.viewportcam.client.compat.Platform;
import com.viewportcam.client.config.ViewportConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ViewportHud {

    private static boolean crosshairShifted;

    private ViewportHud() {}

    public static boolean beginCrosshair(GuiGraphicsExtractor graphics) {
        crosshairShifted = false;
        if (!shouldMoveCrosshair()) {
            return false;
        }
        if (ViewportCam.isCursorShown()) {
            return true;
        }
        graphics.pose().pushMatrix();
        graphics.pose()
                .translate(
                        (float)
                                (ViewportCam.pointerGuiX(graphics.guiWidth())
                                        - graphics.guiWidth() / 2.0),
                        (float)
                                (ViewportCam.pointerGuiY(graphics.guiHeight())
                                        - graphics.guiHeight() / 2.0));
        crosshairShifted = true;
        return false;
    }

    public static void endCrosshair(GuiGraphicsExtractor graphics) {
        if (crosshairShifted) {
            crosshairShifted = false;
            graphics.pose().popMatrix();
        }
    }

    public static CameraType crosshairPerspective(Options options) {
        return ViewportCam.isEngaged() ? CameraType.FIRST_PERSON : options.getCameraType();
    }

    public static void submitStatusText(GuiGraphicsExtractor graphics) {
        ViewportConfig config = ViewportConfig.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (!ViewportCam.isEngaged() || Platform.hasScreen(mc)) {
            return;
        }
        if (config.showStatusHud) {
            graphics.nextStratum();
            graphics.text(
                    mc.font,
                    ViewportCam.statusText(),
                    (int) config.statusHudX,
                    (int) config.statusHudY,
                    0xFFFFFFFF);
        }
    }

    private static boolean shouldMoveCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        return ViewportCam.isEngaged() && !Platform.hasScreen(mc);
    }
}
