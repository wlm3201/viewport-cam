package com.viewportcam.client;

import com.viewportcam.client.config.ViewportConfig;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 在轴心点所在方块位置画一个描边线框，帮助玩家看清相机绕着哪里转 */
public final class PivotOutline {

	/** 线框宽度（像素） */
	private static final float WIDTH = 2.0F;

	private PivotOutline() {
	}

	/**
	 * 提交轴心线框的绘制指令。
	 *
	 * @param state 当前帧的关卡渲染状态，为空或没有相机信息时跳过绘制
	 */
	public static void submit(@Nullable LevelRenderState state) {
		if (!ViewportConfig.INSTANCE.showPivotOutline || !ViewportCam.isEngaged() || state == null
				|| state.cameraRenderState == null) {
			return;
		}
		Vec3 pivot = ViewportCam.pivot();
		int color = ViewportConfig.INSTANCE.pivotOutlineColor;
		// 设为 alwaysOnTop，让线框即使被方块挡住也能看到
		Gizmos.cuboid(BlockPos.containing(pivot), GizmoStyle.stroke(color, WIDTH)).setAlwaysOnTop();
	}
}
