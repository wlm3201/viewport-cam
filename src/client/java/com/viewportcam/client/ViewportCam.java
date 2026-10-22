package com.viewportcam.client;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.viewportcam.client.compat.Platform;
import com.viewportcam.client.config.ViewportConfig;
import com.viewportcam.client.mixin.KeyMappingAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ViewportCam {
    private static final double MIN_DISTANCE = 3.0;

    private static final double MAX_DISTANCE = 128.0;

    private static final double FALLBACK_DISTANCE = 8.0;

    static final double AIM_RAY_MAX_DISTANCE = 512.0;

    private static final double SCALE_RATE = -Math.log(0.95);

    private static final double PAN_PIXEL_SCALE = 2.0;

    private static final double SMOOTHING_UNIT = 0.05;

    private static final double SMOOTHING_MAX = 4.0;

    private static final double BLEND_TAU = 0.09;

    static final float MAX_PITCH = 89.9F;

    private static final double MAX_PENDING_ROTATE = 360.0;

    private static final double PENDING_EPSILON = 1.0E-3;

    private static final double MAX_PENDING_PAN = 128.0;

    private static final double PENDING_PAN_EPSILON = 1.0E-8;

    private static final double FLIGHT_INERTIA_TAU = 0.5;

    static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);

    public static void syncPlayerRotation(float cameraYaw, float cameraPitch) {
        if (!isActive()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float pitch = Mth.clamp(cameraPitch, -MAX_PITCH, MAX_PITCH);
        player.yRotO = cameraYaw;
        player.xRotO = pitch;
        player.setYRot(cameraYaw);
        player.setXRot(pitch);
        player.yBob = cameraYaw;
        player.xBob = pitch;
        player.yBobO = cameraYaw;
        player.xBobO = pitch;
    }

    public static boolean isRotating() {
        return rotateKey != null
                && isMappingDown(rotateKey)
                && viewControlArmed()
                && isCameraHeld(rotateKey);
    }

    public static boolean isPanning() {
        return panKey != null
                && isMappingDown(panKey)
                && viewControlArmed()
                && isCameraHeld(panKey);
    }

    private static boolean viewControlArmed() {
        return isAlwaysOnMode() || cameraMode;
    }

    private static boolean isConflictingButton(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT
                || button == InputConstants.MOUSE_BUTTON_RIGHT
                || button == InputConstants.MOUSE_BUTTON_MIDDLE;
    }

    public static boolean isHybridMode() {
        return ViewportConfig.INSTANCE.mouseMode == ViewportConfig.MouseMode.HYBRID;
    }

    public static boolean isAlwaysOnMode() {
        ViewportConfig.MouseMode mode = ViewportConfig.INSTANCE.mouseMode;
        return mode == ViewportConfig.MouseMode.INDEPENDENT
                || mode == ViewportConfig.MouseMode.HYBRID;
    }

    public static boolean isInteractionBlocked() {
        return !isAlwaysOnMode() && cameraMode;
    }

    public static boolean isCursorShown() {
        return cameraMode && isModifierMode();
    }

    private static boolean isModifierMode() {
        ViewportConfig.MouseMode mode = ViewportConfig.INSTANCE.mouseMode;
        return mode == ViewportConfig.MouseMode.ALT_HOLD
                || mode == ViewportConfig.MouseMode.ALT_TOGGLE;
    }

    private static boolean isMappingDown(KeyMapping mapping) {
        InputConstants.Key key = currentKey(mapping);
        if (key.getType() == InputConstants.Type.MOUSE) {
            return isMouseButtonDown(key.getValue());
        }
        return mapping.isDown();
    }

    public static boolean shouldCaptureMousePress(int mouseButton) {
        if (!engaged) {
            return false;
        }
        if (!isMouseButtonDown(mouseButton)) {
            return false;
        }
        if (mouseButton == InputConstants.MOUSE_BUTTON_MIDDLE) {
            return cameraMode;
        }
        if (rotateKey != null && matchesMouse(rotateKey, mouseButton)) {
            return isKeyArmed(rotateKey);
        }
        if (panKey != null && matchesMouse(panKey, mouseButton)) {
            return isKeyArmed(panKey);
        }
        return false;
    }

    private static boolean isKeyArmed(KeyMapping mapping) {
        return isMappingDown(mapping) && viewControlArmed();
    }

    private static InputConstants.Key currentKey(KeyMapping mapping) {
        return ((KeyMappingAccessor) mapping).viewportcam$getKey();
    }

    private static boolean matchesMouse(KeyMapping mapping, int mouseButton) {
        InputConstants.Key key = currentKey(mapping);
        return key.getType() == InputConstants.Type.MOUSE && key.getValue() == mouseButton;
    }

    public static void refreshModifier() {
        boolean down = modifierKey != null && modifierKey.isDown();
        if (!engaged) {
            cameraMode = false;
            prevModifierDown = down;
            return;
        }
        if (ViewportConfig.INSTANCE.mouseMode == ViewportConfig.MouseMode.ALT_TOGGLE) {
            if (down && !prevModifierDown) {
                cameraMode = !cameraMode;
            }
        } else {
            cameraMode = down;
        }
        prevModifierDown = down;
    }

    public static String statusText() {
        if (isHybridMode()) {
            return status("viewportcam.status.hybrid");
        }
        if (ViewportConfig.INSTANCE.mouseMode == ViewportConfig.MouseMode.INDEPENDENT) {
            return status("viewportcam.status.independent");
        }
        return status(cameraMode ? "viewportcam.status.camera" : "viewportcam.status.crosshair");
    }

    private static String status(String key) {
        return Component.translatable(key).getString();
    }

    private static KeyMapping toggleKey;
    private static KeyMapping modifierKey;
    private static KeyMapping rotateKey;
    private static KeyMapping panKey;

    static boolean engaged;

    private static boolean prevToggleDown;

    private static boolean cameraMode;

    private static boolean prevModifierDown;

    private static final int MAX_MOUSE_BUTTONS = 9;

    private static final boolean[] hybridHeld = new boolean[MAX_MOUSE_BUTTONS];

    private static final boolean[] cameraHeld = new boolean[MAX_MOUSE_BUTTONS];

    private static final boolean[] hybridDragged = new boolean[MAX_MOUSE_BUTTONS];

    private static final double[] hybridDragX = new double[MAX_MOUSE_BUTTONS];
    private static final double[] hybridDragY = new double[MAX_MOUSE_BUTTONS];

    static Vec3 pivot = Vec3.ZERO;

    static double distance = 4.0;

    static float viewYaw;

    static float viewPitch;

    static float viewportYaw;

    static float viewportPitch;

    private static float blend;

    private static double lastTime = -1.0;

    private static double pendingYaw;

    private static double pendingPitch;
    private static Vec3 pendingPan = Vec3.ZERO;

    private static Vec3 flightVel = Vec3.ZERO;

    static double pointerNdcX;

    static double pointerNdcY;

    private static int cursorMode = -1;

    private static Vec3 vanillaPos = Vec3.ZERO;

    private static float vanillaYaw;
    private static float vanillaPitch;

    static boolean vanillaReady;

    static Vec3 cameraEyePos = Vec3.ZERO;

    private static boolean smartCullBeforeEngage = true;

    private static ClientLevel lastLevel;

    private static LocalPlayer lastPlayer;

    private static volatile boolean serverSyncQueued;

    private ViewportCam() {}

    public static boolean isEngaged() {
        return engaged;
    }

    public static boolean isActive() {
        return engaged || blend > 0.0F;
    }

    public static void bindKeys(
            KeyMapping toggle, KeyMapping modifier, KeyMapping rotate, KeyMapping pan) {
        toggleKey = toggle;
        modifierKey = modifier;
        rotateKey = rotate;
        panKey = pan;
    }

    public static void markCameraHeld(int button) {
        if (button >= 0 && button < MAX_MOUSE_BUTTONS) {
            cameraHeld[button] = true;
        }
    }

    public static void clearCameraHeld(int button) {
        if (button >= 0 && button < MAX_MOUSE_BUTTONS) {
            cameraHeld[button] = false;
        }
    }

    public static boolean isCameraHeld(int button) {
        return button >= 0 && button < MAX_MOUSE_BUTTONS && cameraHeld[button];
    }

    private static boolean isCameraHeld(KeyMapping mapping) {
        InputConstants.Key key = currentKey(mapping);
        return key.getType() != InputConstants.Type.MOUSE || isCameraHeld(key.getValue());
    }

    public static void noteHybridPress(int button) {
        if (!isHybridMode() || !isConflictingButton(button) || button >= MAX_MOUSE_BUTTONS) {
            return;
        }
        hybridHeld[button] = true;
        hybridDragged[button] = false;
        hybridDragX[button] = 0.0;
        hybridDragY[button] = 0.0;
    }

    private static int keyButton(KeyMapping mapping) {
        if (mapping == null) {
            return -1;
        }
        InputConstants.Key key = currentKey(mapping);
        return key.getType() == InputConstants.Type.MOUSE ? key.getValue() : -1;
    }

    private static boolean dragBelowThreshold() {
        if (!isHybridMode() || ViewportConfig.INSTANCE.clickDragThreshold <= 0.0) {
            return false;
        }
        int button = keyButton(rotateKey);
        if (button < 0 || !hybridHeld[button]) {
            button = keyButton(panKey);
        }
        if (button < 0 || button >= MAX_MOUSE_BUTTONS || !hybridHeld[button]) {
            return false;
        }
        return !hybridDragged[button];
    }

    public static void accumulateHybridDrag(double dx, double dy) {
        for (int i = 0; i < MAX_MOUSE_BUTTONS; i++) {
            if (!hybridHeld[i] || hybridDragged[i]) {
                continue;
            }
            hybridDragX[i] += dx;
            hybridDragY[i] += dy;
            double threshold = ViewportConfig.INSTANCE.clickDragThreshold;
            if (threshold <= 0.0
                    || hybridDragX[i] * hybridDragX[i] + hybridDragY[i] * hybridDragY[i]
                            >= threshold * threshold) {
                hybridDragged[i] = true;
            }
        }
    }

    public static boolean consumeHybridClick(int button) {
        if (button < 0 || button >= MAX_MOUSE_BUTTONS) {
            return false;
        }
        boolean held = hybridHeld[button];
        boolean dragged = hybridDragged[button];
        hybridHeld[button] = false;
        hybridDragged[button] = false;
        hybridDragX[button] = 0.0;
        hybridDragY[button] = 0.0;
        return held && !dragged;
    }

    private static void clearHybridPresses() {
        for (int i = 0; i < MAX_MOUSE_BUTTONS; i++) {
            hybridHeld[i] = false;
            hybridDragged[i] = false;
            hybridDragX[i] = 0.0;
            hybridDragY[i] = 0.0;
            cameraHeld[i] = false;
        }
    }

    public static void replayVanillaClick(int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || Platform.hasScreen(mc) || Platform.hasOverlay(mc)) {
            return;
        }
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        KeyMapping.set(key, true);
        KeyMapping.click(key);
        KeyMapping.set(key, false);
    }

    public static Vec3 pivot() {
        return pivot;
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) {}

    public static void toggle() {
        if (engaged) {
            disengage();
            showToast(false);
        } else if (canViewport()) {
            engage();
            showToast(true);
        }
    }

    private static void showToast(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) {
            return;
        }
        Platform.setOverlayMessage(
                mc, Component.translatable(on ? "viewportcam.toast.on" : "viewportcam.toast.off"));
    }

    public static boolean onScroll(double yOffset) {
        if (yOffset == 0.0 || !engaged || !canViewport()) {
            return false;
        }
        if (ViewportZoom.onScroll(yOffset)) {
            return true;
        }
        if (!cameraMode && !isRotating() && !isPanning()) {
            return false;
        }
        Vec3 look = direction(viewYaw, viewPitch);
        double newDistance =
                Mth.clamp(
                        distance
                                * Math.exp(
                                        -yOffset
                                                * SCALE_RATE
                                                * ViewportConfig.INSTANCE.scaleSensitivity),
                        MIN_DISTANCE,
                        MAX_DISTANCE);

        Vec3 pointer = pointerRay();
        Vec3 shift = pointer.subtract(look).scale(distance - newDistance);

        pivot = pivot.add(shift);
        distance = newDistance;
        return true;
    }

    public static void applyLookDelta(double dx, double dy) {
        if (!engaged) {
            return;
        }
        accumulateHybridDrag(dx, dy);
        if (dx == 0.0 && dy == 0.0) {
            return;
        }
        if (dragBelowThreshold()) {
            return;
        }
        refreshModifier();
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (window == null) {
            return;
        }

        boolean rotating = isRotating();
        boolean panning = isPanning();

        if (panning && !rotating) {
            double worldPerPixel =
                    PAN_PIXEL_SCALE
                            * distance
                            * Math.tan(currentFovDeg(mc) * Mth.DEG_TO_RAD / 2.0)
                            / Math.max(window.getScreenHeight(), 1)
                            * ViewportConfig.INSTANCE.panSensitivity;
            Vec3 look = ViewportZoom.effectiveLook();
            Vec3 axisRight = look.cross(WORLD_UP).normalize();
            Vec3 axisUp = axisRight.cross(look);
            pendingPan =
                    pendingPan
                            .subtract(axisRight.scale(dx * worldPerPixel))
                            .add(axisUp.scale(dy * worldPerPixel));
            return;
        }

        double degreesPerPixel =
                360.0
                        / Math.max(window.getScreenHeight(), 1)
                        * ViewportConfig.INSTANCE.rotateSensitivity;
        double xo = dx * degreesPerPixel * ViewportZoom.zoomFovScale();
        double yo = dy * degreesPerPixel * ViewportZoom.zoomFovScale();
        Options options = mc.options;
        if (options.invertMouseX().get()) {
            xo = -xo;
        }
        if (options.invertMouseY().get()) {
            yo = -yo;
        }
        if (rotating) {
            pendingYaw += xo;
            pendingPitch += yo;
        }
    }

    public static double pointerGuiX(int guiWidth) {
        return (pointerNdcX * 0.5 + 0.5) * guiWidth;
    }

    public static double pointerGuiY(int guiHeight) {
        return (0.5 - pointerNdcY * 0.5) * guiHeight;
    }

    private static double currentFovDeg(Minecraft mc) {
        Camera camera = Platform.mainCamera(mc);
        return camera != null && camera.getFov() > 0.0F ? camera.getFov() : mc.options.fov().get();
    }

    public static Vec3 pointerRay() {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = Platform.mainCamera(mc);
        float fov = (float) currentFovDeg(mc);
        Camera.NearPlane plane = camera.getNearPlane(fov);
        return plane.getPointOnPlane((float) pointerNdcX, (float) pointerNdcY).normalize();
    }

    private static void updatePointer(Minecraft mc) {
        Window window = mc.getWindow();
        MouseHandler mouse = mc.mouseHandler;
        if (window == null || mouse == null) {
            pointerNdcX = 0.0;
            pointerNdcY = 0.0;
            return;
        }
        double width = Math.max(window.getScreenWidth(), 1);
        double height = Math.max(window.getScreenHeight(), 1);
        pointerNdcX = (mouse.xpos() / width) * 2.0 - 1.0;
        pointerNdcY = 1.0 - (mouse.ypos() / height) * 2.0;
    }

    public static void recordVanillaPose(Vec3 position, float yaw, float pitch) {
        vanillaPos = position;
        vanillaYaw = yaw;
        vanillaPitch = pitch;
        vanillaReady = true;
    }

    private static void detectWorldChange(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            stopForWorldChange("世界实例变化");
        }
        LocalPlayer player = mc.player;
        if (player != lastPlayer) {
            lastPlayer = player;
            stopForWorldChange("玩家实体重建");
        }
        if (engaged && player != null && player.isDeadOrDying()) {
            stopForWorldChange("玩家死亡");
        }
    }

    private static void updateBlend(double delta, boolean playerTurned) {
        if (engaged) {
            blend = 1.0F;
            return;
        }
        if (blend <= 0.0F) {
            return;
        }
        blend = (float) (blend * Math.exp(-delta / BLEND_TAU));
        if (blend < 0.002F || playerTurned) {
            blend = 0.0F;
        }
    }

    public static void stopForWorldChange(String reason) {
        if (engaged) {
            disengage();
            ViewportCamClient.LOGGER.info("[state] viewportcam 已自动关闭 ({})", reason);
        }
        blend = 0.0F;
        vanillaReady = false;
        lastTime = -1.0;
        serverSyncQueued = false;
        clearHybridPresses();
    }

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        detectWorldChange(mc);

        double now = Blaze3D.getTime();
        if (lastTime < 0.0) {
            lastTime = now;
        }
        double delta = Mth.clamp(now - lastTime, 0.0, 0.25);
        lastTime = now;

        if (toggleKey != null) {
            boolean down = toggleKey.isDown();
            if (down && !prevToggleDown) {
                toggle();
            }
            prevToggleDown = down;
        }

        if (engaged && !isWorldReady()) {
            disengage();
        }

        updateBlend(delta, ViewportZoom.takePlayerTurn());

        refreshModifier();
        updatePointer(mc);
        updateCursor(mc);
        if (!engaged) {
            clearHybridPresses();
        }

        ViewportZoom.updateZoomKey(ViewportCamKeys.ZOOM.isDown() && canViewport());
        ViewportZoom.stepZoom(delta);

        LocalPlayer player = mc.player;
        if (engaged && player != null) {
            if (ViewportZoom.isRestoring()) {
                stopMotion();
            } else {
                if (!isBodyFollowing()) {
                    player.setDeltaMovement(Vec3.ZERO);
                }
                moveByKeys(delta);
                syncBodyToCamera(mc);
                stepMotion(delta);
            }
        } else {
            stopMotion();
        }
    }

    private static void moveByKeys(double delta) {
        Options options = Minecraft.getInstance().options;
        float forward =
                (options.keyUp.isDown() ? 1.0F : 0.0F) - (options.keyDown.isDown() ? 1.0F : 0.0F);
        float strafe =
                (options.keyRight.isDown() ? 1.0F : 0.0F)
                        - (options.keyLeft.isDown() ? 1.0F : 0.0F);
        float vertical =
                (options.keyJump.isDown() ? 1.0F : 0.0F)
                        - (options.keyShift.isDown() ? 1.0F : 0.0F);

        Vec3 look = ViewportZoom.effectiveLook();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-8) {
            flat =
                    new Vec3(
                            -Mth.sin(viewYaw * Mth.DEG_TO_RAD),
                            0.0,
                            Mth.cos(viewYaw * Mth.DEG_TO_RAD));
        }
        flat = flat.normalize();
        Vec3 right = flat.cross(WORLD_UP).normalize();

        ViewportConfig config = ViewportConfig.INSTANCE;
        boolean sprinting = options.keySprint.isDown();
        double horizontalSpeed =
                config.horizontalSpeed * (sprinting ? config.sprintHorizontalMultiplier : 1.0);
        double verticalSpeed =
                config.verticalSpeed * (sprinting ? config.sprintVerticalMultiplier : 1.0);

        Vec3 horizontal = flat.scale(forward).add(right.scale(strafe));
        Vec3 offset = Vec3.ZERO;
        if (horizontal.lengthSqr() > 1.0E-8) {
            offset = horizontal.normalize().scale(horizontalSpeed);
        }
        if (vertical != 0.0F) {
            offset = offset.add(WORLD_UP.scale(vertical * verticalSpeed));
        }

        boolean speedingUp = offset.lengthSqr() > flightVel.lengthSqr() + 1.0E-8;
        double inertia = Mth.clamp(speedingUp ? config.flightAccel : config.flightDecel, 0.0, 1.0);
        if (inertia <= 0.0) {
            flightVel = offset;
        } else {
            double tau = Math.max(inertia * FLIGHT_INERTIA_TAU, 0.01);
            flightVel =
                    flightVel.add(offset.subtract(flightVel).scale(1.0 - Math.exp(-delta / tau)));
        }
        if (flightVel.lengthSqr() > 1.0E-8) {
            pivot = pivot.add(flightVel.scale(delta));
        }
    }

    private static void stepMotion(double delta) {
        ViewportConfig config = ViewportConfig.INSTANCE;

        double rotateK = dampingFactor(delta, smoothingTau(config.rotateSmoothing));
        double dYaw = pendingYaw * rotateK;
        double dPitch = pendingPitch * rotateK;
        pendingYaw = Mth.clamp(pendingYaw - dYaw, -MAX_PENDING_ROTATE, MAX_PENDING_ROTATE);
        pendingPitch = Mth.clamp(pendingPitch - dPitch, -MAX_PENDING_ROTATE, MAX_PENDING_ROTATE);
        if (Math.abs(pendingYaw) < PENDING_EPSILON) {
            pendingYaw = 0.0;
        }
        if (Math.abs(pendingPitch) < PENDING_EPSILON) {
            pendingPitch = 0.0;
        }
        if (dYaw != 0.0 || dPitch != 0.0) {
            applyViewportDelta(dYaw, dPitch);
        }

        double panK = dampingFactor(delta, smoothingTau(config.panSmoothing));
        Vec3 dPan = pendingPan.scale(panK);
        pendingPan = clampLength(pendingPan.subtract(dPan), MAX_PENDING_PAN);
        if (pendingPan.lengthSqr() < PENDING_PAN_EPSILON) {
            pendingPan = Vec3.ZERO;
        }
        if (dPan.lengthSqr() > 1.0E-12) {
            pivot = pivot.add(dPan);
        }
    }

    private static void applyViewportDelta(double dYaw, double dPitch) {
        viewportYaw = Mth.wrapDegrees(viewportYaw + (float) dYaw);
        viewportPitch = Mth.clamp(viewportPitch + (float) dPitch, -MAX_PITCH, MAX_PITCH);
        viewYaw = Mth.wrapDegrees(viewYaw + (float) dYaw);
        viewPitch = Mth.clamp(viewPitch + (float) dPitch, -MAX_PITCH, MAX_PITCH);
    }

    private static double dampingFactor(double delta, double tau) {
        if (!(tau > 0.0)) {
            return 1.0;
        }
        return 1.0 - Math.exp(-delta / tau);
    }

    private static double smoothingTau(double multiplier) {
        if (!(multiplier > 0.0)) {
            return 0.0;
        }
        return Mth.clamp(multiplier, 0.0, SMOOTHING_MAX) * SMOOTHING_UNIT;
    }

    private static Vec3 clampLength(Vec3 v, double max) {
        double len = v.length();
        return len > max && len > 1.0E-12 ? v.scale(max / len) : v;
    }

    private static void stopMotion() {
        pendingYaw = 0.0;
        pendingPitch = 0.0;
        pendingPan = Vec3.ZERO;
        flightVel = Vec3.ZERO;
    }

    private static void updateCursor(Minecraft mc) {
        if (mc.mouseHandler == null || mc.getWindow() == null) {
            return;
        }

        if (Platform.hasScreen(mc)) {
            setCursorVisible(mc, true);
            return;
        }

        if (!engaged) {
            if (Platform.hasOverlay(mc)) {
                cursorMode = -1;
                return;
            }
            if (!mc.mouseHandler.isMouseGrabbed()) {
                mc.mouseHandler.grabMouse();
            }
            cursorMode = -1;
            return;
        }

        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
        setCursorVisible(mc, isCursorShown());
    }

    public static CameraPose computePose() {
        Vec3 dir = direction(viewYaw, viewPitch);
        Vec3 target = pivot.subtract(dir.scale(distance));

        float b = blend;
        Vec3 position = vanillaPos.lerp(target, b);
        Vec3 look = ViewportZoom.effectiveLook();
        float outYaw = vanillaYaw + Mth.wrapDegrees(yawOf(look) - vanillaYaw) * b;
        float outPitch = vanillaPitch + (pitchOf(look) - vanillaPitch) * b;
        cameraEyePos = position;
        return new CameraPose(position, outYaw, outPitch);
    }

    public static Vec3 cameraEye() {
        return engaged && vanillaReady ? cameraEyePos : null;
    }

    public static Vec3 pointerViewRay() {
        return engaged ? pointerRay() : null;
    }

    public static boolean shouldHideInWallOverlay() {
        return engaged && isBodyFollowing();
    }

    public static boolean isBodyFollowing() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return ViewportConfig.INSTANCE.bodyFollowsCamera
                && player != null
                && player.isCreative()
                && mc.getSingleplayerServer() != null;
    }

    private static boolean commandsAllowed() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        return server != null && server.getWorldData().isAllowCommands();
    }

    public static double serverBlockReach(Player player) {
        return serverReach(player, ViewportConfig.INSTANCE.clientBlockReach);
    }

    public static double serverEntityReach(Player player) {
        return serverReach(player, ViewportConfig.INSTANCE.clientEntityReach);
    }

    public static double blockRange(Player player) {
        return interactionRange(player, ViewportConfig.INSTANCE.clientBlockReach);
    }

    public static double entityRange(Player player) {
        return interactionRange(player, ViewportConfig.INSTANCE.clientEntityReach);
    }

    private static double interactionRange(Player player, double configured) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return -1.0;
        }
        if ((Object) player == mc.player) {
            return engaged && configured >= 0.0 ? configured : -1.0;
        }
        return serverReach(player, configured);
    }

    private static double serverReach(Player player, double configured) {
        if (!ViewportConfig.INSTANCE.singleplayerServerReach || !engaged || configured <= 0.0) {
            return -1.0;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            return -1.0;
        }
        if (!commandsAllowed()) {
            return -1.0;
        }
        return player.getUUID().equals(mc.player.getUUID()) ? configured : -1.0;
    }

    public static void syncBodyToCamera(Minecraft mc) {
        if (!engaged || !isBodyFollowing()) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.getSingleplayerServer() == null) {
            return;
        }
        Vec3 eye = computePose().position();
        double feetY = eye.y - player.getEyeHeight();
        if (player.distanceToSqr(eye.x, feetY, eye.z) < 1.0E-8) {
            return;
        }
        if (wouldCollide(mc, eye.x, feetY, eye.z)) {
            return;
        }
        player.setPos(eye.x, feetY, eye.z);
        player.setDeltaMovement(Vec3.ZERO);

        ServerPlayer serverPlayer =
                mc.getSingleplayerServer().getPlayerList().getPlayer(player.getUUID());
        if (serverPlayer != null && !serverSyncQueued) {
            ServerPlayer target = serverPlayer;
            double tx = eye.x;
            double ty = feetY;
            double tz = eye.z;
            serverSyncQueued = true;
            mc.getSingleplayerServer()
                    .execute(
                            () -> {
                                serverSyncQueued = false;
                                if (target.isRemoved() || target.connection == null) {
                                    return;
                                }
                                target.setPos(tx, ty, tz);
                                target.setDeltaMovement(Vec3.ZERO);
                            });
        }
    }

    private static boolean wouldCollide(Minecraft mc, double x, double y, double z) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return true;
        }
        AABB box =
                player.getBoundingBox()
                        .move(x - player.getX(), y - player.getY(), z - player.getZ());
        return !level.noCollision(player, box);
    }

    public static HitResult overrideHitResult(Minecraft mc) {
        if (!engaged || mc.player == null || mc.level == null) {
            return null;
        }
        if (!vanillaReady) {
            LocalPlayer fallback = mc.player;
            recordVanillaPose(
                    fallback.getEyePosition(1.0F),
                    fallback.getViewYRot(1.0F),
                    fallback.getViewXRot(1.0F));
        }
        CameraPose pose = computePose();
        HitResult hit = raycast(mc, pose.position(), pointerRay());

        if (Platform.hasScreen(mc) || Platform.hasOverlay(mc)) {
            Vec3 at = hit.getLocation();
            Direction direction =
                    Direction.getApproximateNearest(
                            at.x - pose.position().x,
                            at.y - pose.position().y,
                            at.z - pose.position().z);
            return BlockHitResult.miss(at, direction, BlockPos.containing(at));
        }
        return hit;
    }

    private static void engage() {
        if (!initFromLook()) {
            return;
        }
        engaged = true;
        blend = 1.0F;
        disableCaveCulling();
    }

    private static void disengage() {
        ViewportZoom.cancelZoom();
        engaged = false;
        cameraMode = false;
        stopMotion();
        restoreCaveCulling();
    }

    private static void disableCaveCulling() {
        Minecraft mc = Minecraft.getInstance();
        smartCullBeforeEngage = mc.smartCull;
        mc.smartCull = false;
    }

    private static void restoreCaveCulling() {
        Minecraft.getInstance().smartCull = smartCullBeforeEngage;
    }

    private static double initialViewportDistance(Minecraft mc, Vec3 eye, Vec3 look) {
        double hit = blockDistanceAlong(mc, eye, look, MAX_DISTANCE);
        return hit > 0.0 ? Mth.clamp(hit, MIN_DISTANCE, MAX_DISTANCE) : FALLBACK_DISTANCE;
    }

    static double blockDistanceAlong(Minecraft mc, Vec3 eye, Vec3 look, double maxDistance) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return -1.0;
        }
        Vec3 to = eye.add(look.scale(maxDistance));
        BlockHitResult hit =
                level.clip(
                        new ClipContext(
                                eye,
                                to,
                                ClipContext.Block.OUTLINE,
                                ClipContext.Fluid.NONE,
                                player));
        return hit.getType() == HitResult.Type.BLOCK
                ? Math.sqrt(hit.getLocation().distanceToSqr(eye))
                : -1.0;
    }

    private static boolean initFromLook() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        Vec3 eye = vanillaReady ? vanillaPos : player.getEyePosition(1.0F);
        Vec3 look = vanillaReady ? direction(vanillaYaw, vanillaPitch) : player.getViewVector(1.0F);
        if (look.lengthSqr() < 1.0E-8) {
            return false;
        }

        double start = initialViewportDistance(mc, eye, look);
        pivot = eye.add(look.scale(start));
        distance = start;
        viewYaw = viewportYaw = yawOf(look);
        viewPitch = viewportPitch = Mth.clamp(pitchOf(look), -MAX_PITCH, MAX_PITCH);
        stopMotion();
        return true;
    }

    private static HitResult raycast(Minecraft mc, Vec3 from, Vec3 look) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        ViewportConfig config = ViewportConfig.INSTANCE;
        double blockRange =
                config.clientBlockReach >= 0.0
                        ? config.clientBlockReach
                        : player.blockInteractionRange();
        double entityRange =
                config.clientEntityReach >= 0.0
                        ? config.clientEntityReach
                        : player.entityInteractionRange();
        double maxDistance = Math.max(blockRange, entityRange);
        double maxDistanceSq = maxDistance * maxDistance;

        Vec3 to = from.add(look.scale(maxDistance));
        HitResult blockHit =
                level.clip(
                        new ClipContext(
                                from,
                                to,
                                ClipContext.Block.OUTLINE,
                                ClipContext.Fluid.NONE,
                                player));
        double blockDistanceSq = blockHit.getLocation().distanceToSqr(from);
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxDistanceSq = blockDistanceSq;
            maxDistance = Math.sqrt(maxDistanceSq);
            to = from.add(look.scale(maxDistance));
        }

        AABB searchBox =
                player.getBoundingBox()
                        .expandTowards(look.scale(maxDistance))
                        .minmax(new AABB(from, to))
                        .inflate(1.0, 1.0, 1.0);
        EntityHitResult entityHit =
                ProjectileUtil.getEntityHitResult(
                        player, from, to, searchBox, EntitySelector.CAN_BE_PICKED, maxDistanceSq);
        return entityHit != null && entityHit.getLocation().distanceToSqr(from) < blockDistanceSq
                ? filterHitResult(entityHit, from, entityRange)
                : filterHitResult(blockHit, from, blockRange);
    }

    private static HitResult filterHitResult(HitResult hitResult, Vec3 from, double maxRange) {
        Vec3 location = hitResult.getLocation();
        if (!location.closerThan(from, maxRange)) {
            Direction direction =
                    Direction.getApproximateNearest(
                            location.x - from.x, location.y - from.y, location.z - from.z);
            return BlockHitResult.miss(location, direction, BlockPos.containing(location));
        }
        return hitResult;
    }

    private static boolean canViewport() {
        Minecraft mc = Minecraft.getInstance();
        return isWorldReady() && !Platform.hasScreen(mc) && !Platform.hasOverlay(mc);
    }

    private static boolean isWorldReady() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && mc.gui != null;
    }

    private static void setCursorVisible(Minecraft mc, boolean visible) {
        int mode = visible ? 1 : 0;
        if (mode == cursorMode) {
            return;
        }
        Platform.setCursorVisible(mc, visible);
        cursorMode = mode;
    }

    private static boolean isMouseButtonDown(int button) {
        return Platform.isMouseButtonDown(Minecraft.getInstance(), button);
    }

    static Vec3 direction(float yaw, float pitch) {
        float yawRad = yaw * Mth.DEG_TO_RAD;
        float pitchRad = pitch * Mth.DEG_TO_RAD;
        float cosPitch = Mth.cos(pitchRad);
        return new Vec3(
                -Mth.sin(yawRad) * cosPitch, -Mth.sin(pitchRad), Mth.cos(yawRad) * cosPitch);
    }

    private static float yawOf(Vec3 vec) {
        return (float) (Mth.atan2(-vec.x, vec.z) * Mth.RAD_TO_DEG);
    }

    static float pitchOf(Vec3 vec) {
        double length = vec.length();
        return (float) (-Math.asin(Mth.clamp(vec.y / length, -1.0, 1.0)) * Mth.RAD_TO_DEG);
    }
}
