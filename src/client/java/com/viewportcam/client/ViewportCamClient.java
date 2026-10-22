package com.viewportcam.client;

import com.viewportcam.client.compat.Platform;
import com.viewportcam.client.config.ViewportConfig;
import java.lang.reflect.Method;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewportCamClient implements ClientModInitializer {

    public static final String MOD_ID = "viewportcam";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Hook[] HOOKS = {
        new Hook("相机接管", Camera.class, "viewportcam$applyViewport"),
        new Hook("屏蔽液体雾", Camera.class, "viewportcam$hideFluidFog"),
        new Hook("准星射线", LocalPlayer.class, "viewportcam$pickFromCamera"),
        new Hook("长按连续破坏", Minecraft.class, "viewportcam$allowHoldMining"),
        new Hook("鼠标按键", MouseHandler.class, "viewportcam$onButton"),
        new Hook("视角输入", MouseHandler.class, "viewportcam$turnPlayer"),
        new Hook("滚轮缩放", MouseHandler.class, "viewportcam$onScroll"),
        new Hook("冻结身体", LocalPlayer.class, "viewportcam$freezeBody"),
        new Hook("准星跟随指针", Platform.hudClass(), "viewportcam$shiftCrosshair"),
        new Hook("准心不受视角限制", Platform.hudClass(), "viewportcam$crosshairPerspective"),
        new Hook("按键注册", Options.class, "viewportcam$registerBeforeLoad"),
        new Hook("第三方选点起点", Entity.class, "viewportcam$eyeAtCamera"),
        new Hook("第三方选点视线", Entity.class, "viewportcam$lookAtPointer"),
        new Hook("服务端交互距离", Player.class, "viewportcam$blockRange"),
        new Hook("服务端实体交互距离", Player.class, "viewportcam$entityRange"),
        new Hook("服务端实体攻击距离", Player.class, "viewportcam$serverAttackRange"),
        new Hook("隐藏穿墙遮挡", ScreenEffectRenderer.class, "viewportcam$hideInWallOverlay"),
        new Hook("轴心方块线框", LevelRenderer.class, "viewportcam$submitPivotOutline"),
        new Hook("放大镜 FOV", Camera.class, "viewportcam$applyZoomFov"),
        new Hook("放大镜按键", KeyboardHandler.class, "viewportcam$captureZoomKey"),
        new Hook("模式状态文字", Platform.hudClass(), "viewportcam$statusHud"),
        new Hook("屏蔽方块描边", GameRenderer.class, "viewportcam$hideOutline"),
        new Hook("取消走路晃动", GameRenderer.class, "viewportcam$noViewBob"),
        new Hook("取消受伤晃动", GameRenderer.class, "viewportcam$noHurtBob"),
        new Hook("保持鼠标自由", MouseHandler.class, "viewportcam$keepCursorFree"),
        new Hook("自由指针拖拽", MouseHandler.class, "viewportcam$captureFreeCursor"),
        new Hook("阻止连续放置", Minecraft.class, "viewportcam$stopHoldPlacing"),
        new Hook("切换世界时关闭", Minecraft.class, "viewportcam$stopOnLevelChange"),
        new Hook("退出存档时关闭", Minecraft.class, "viewportcam$stopOnDisconnect"),
    };

    @Override
    public void onInitializeClient() {
        ViewportCam.bindKeys(
                ViewportCamKeys.TOGGLE,
                ViewportCamKeys.MODIFIER,
                ViewportCamKeys.ROTATE,
                ViewportCamKeys.PAN);

        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            ViewportCamKeys.register(mc.options);
        }

        checkHooks();

        LOGGER.info(
                "ViewportCam client initialized (config: {})",
                ViewportConfig.class.getSimpleName());
    }

    private static void checkHooks() {
        int missing = 0;
        for (Hook hook : HOOKS) {
            if (!hasDeclaredMethod(hook.owner, hook.method)) {
                LOGGER.error("[state] hook MISSING: {} ({})", hook.method, hook.label);
                missing++;
            }
        }
        if (missing == 0) {
            LOGGER.info("[state] viewportcam hooks installed ({}/{})", HOOKS.length, HOOKS.length);
        } else {
            LOGGER.error(
                    "[state] viewportcam hooks {}/{} MISSING - 功能可能部分失效", missing, HOOKS.length);
        }
    }

    private static boolean hasDeclaredMethod(Class<?> owner, String name) {
        boolean relaxed = false;
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
            if (method.getName().endsWith(name)) {
                relaxed = true;
            }
        }
        return relaxed;
    }

    private record Hook(String label, Class<?> owner, String method) {}
}
