package com.viewportcam.client;

import com.viewportcam.client.config.ViewportConfig;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PivotOutline {

    private static final float WIDTH = 2.0F;

    private PivotOutline() {}

    public static void submit(@Nullable LevelRenderState state) {
        if (!ViewportConfig.INSTANCE.showPivotOutline
                || !ViewportCam.isEngaged()
                || state == null
                || state.cameraRenderState == null) {
            return;
        }
        Vec3 pivot = ViewportCam.pivot();
        int color = ViewportConfig.INSTANCE.pivotOutlineColor;
        Gizmos.cuboid(BlockPos.containing(pivot), GizmoStyle.stroke(color, WIDTH)).setAlwaysOnTop();
    }
}
