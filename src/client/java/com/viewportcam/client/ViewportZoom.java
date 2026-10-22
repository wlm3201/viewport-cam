package com.viewportcam.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.viewportcam.client.compat.Platform;
import com.viewportcam.client.config.ViewportConfig;
import com.viewportcam.client.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ViewportZoom {

    private static final double ZOOM_TAU = 0.10;

    private static final double ANCHOR_TOLERANCE = 5.0;

    private static final double ANCHOR_MAX_SPEED = 600.0;

    private static final double ZOOM_TRACK_TAU = 0.12;

    private static final double ANCHOR_POLE_SAFE = 3.0;

    private static final double ANCHOR_MAX_PITCH = 90.0 - ANCHOR_POLE_SAFE;

    private static final double ZOOM_RATE = 0.0513;

    private static final double ZOOM_FACTOR_MIN = 0.05;

    private static final double ZOOM_FACTOR_MAX = 0.95;

    private static final double ZOOM_REFERENCE_DISTANCE = 8.0;

    private static boolean zoomHeld;

    private static boolean zoomRestoring;

    private static boolean playerTurned;

    public static void notePlayerTurn() {
        playerTurned = true;
    }

    public static boolean takePlayerTurn() {
        boolean was = playerTurned;
        playerTurned = false;
        return was;
    }

    private static double zoomBlend;

    private static double zoomFactor = 0.25;

    private static final Quaternionf zoomAnchorRot = new Quaternionf();

    private static boolean zoomAnchorRotActive;

    private static boolean zoomHasSnapshot;

    private static Vec3 zoomAnchorPoint = Vec3.ZERO;

    private static Vec3 zoomAnchorDir = new Vec3(0.0, 0.0, 1.0);

    private static Vec3 zoomTrackDir = new Vec3(0.0, 0.0, 1.0);

    private static double zoomAnchorNdcX;

    private static double zoomAnchorNdcY;

    private static float zoomBaseYaw;

    private static float zoomBasePitch;

    private static Vec3 zoomSnapPivot = Vec3.ZERO;

    private static double zoomSnapDistance;
    private static float zoomSnapViewYaw;
    private static float zoomSnapViewPitch;
    private static float zoomSnapViewportYaw;
    private static float zoomSnapViewportPitch;

    public static double zoomFovScale() {
        return zoomBlend <= 0.0 ? 1.0 : Math.pow(zoomFactor, zoomBlend);
    }

    private static double autoZoomFactor(double distance) {
        double baseHalf = Math.toRadians(Math.max(vanillaFovDeg, 1.0F)) / 2.0;
        double zoomedHalf =
                Math.atan(
                        Math.tan(baseHalf)
                                * (ZOOM_REFERENCE_DISTANCE / Math.max(distance, 1.0E-3)));
        return Mth.clamp(zoomedHalf / baseHalf, ZOOM_FACTOR_MIN, ZOOM_FACTOR_MAX);
    }

    static Vec3 effectiveLook() {
        Vec3 forward = ViewportCam.direction(ViewportCam.viewYaw, ViewportCam.viewPitch);
        if (!zoomAnchorRotActive) {
            return forward;
        }
        Quaternionf src = new Quaternionf(zoomAnchorRot);
        if (src.w < 0.0F) {
            src.set(-src.x, -src.y, -src.z, -src.w);
        }
        double poleMargin = 90.0 - Math.abs(ViewportCam.pitchOf(rotate(src, forward)));
        double fade = Mth.clamp(poleMargin / ANCHOR_POLE_SAFE, 0.0, 1.0);
        if (fade <= 0.0) {
            return forward;
        }
        Quaternionf probe = new Quaternionf().identity().slerp(src, (float) fade);
        return rotate(probe, forward);
    }

    private static Vec3 rotate(Quaternionf rotation, Vec3 vec) {
        Vector3f in = SCRATCH_VEC_IN.set((float) vec.x, (float) vec.y, (float) vec.z);
        Vector3f out = rotation.transform(in, SCRATCH_VEC_OUT);
        return new Vec3(out.x, out.y, out.z);
    }

    private static final Vector3f SCRATCH_VEC_IN = new Vector3f();

    private static final Vector3f SCRATCH_VEC_OUT = new Vector3f();

    static boolean isAnchorActive() {
        return zoomAnchorRotActive;
    }

    private static Quaternionf orientationQuat(float yaw, float pitch) {
        return new Quaternionf()
                .rotationYXZ((float) Math.PI - yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD, 0.0F);
    }

    private static float vanillaFovDeg = 70.0F;

    public static void noteVanillaFov(float fov) {
        if (fov > 0.0F) {
            vanillaFovDeg = fov;
        }
    }

    private static double requiredAnchorAngle(double blend) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (window == null) {
            return 0.0;
        }
        double aspect =
                (double) Math.max(window.getScreenWidth(), 1)
                        / Math.max(window.getScreenHeight(), 1);
        double fov = vanillaFovDeg * (blend <= 0.0 ? 1.0 : Math.pow(zoomFactor, blend));
        Vec3 dir = ViewportConfig.INSTANCE.zoomTrackAnchor ? zoomTrackDir : zoomAnchorDir;
        double[] solved = solveAnchor(dir.normalize(), zoomAnchorNdcX, zoomAnchorNdcY, fov, aspect);
        if (solved == null) {
            return 0.0;
        }
        Quaternionf rot = anchorRotation(solved[0], solved[1]);
        return 2.0 * Math.toDegrees(Math.acos(Mth.clamp(Math.abs(rot.w), -1.0, 1.0)));
    }

    private static Quaternionf anchorRotation(double yaw, double pitch) {
        float limited = (float) Mth.clamp(pitch, -ANCHOR_MAX_PITCH, ANCHOR_MAX_PITCH);
        return new Quaternionf(orientationQuat((float) yaw, limited))
                .mul(orientationQuat(zoomBaseYaw, zoomBasePitch).invert());
    }

    private static double renderedFovDeg() {
        return vanillaFovDeg * zoomFovScale();
    }

    public static boolean consumeZoomKey(int action, KeyEvent event) {
        if (!ViewportCam.engaged || ViewportCamKeys.ZOOM == null) {
            return false;
        }
        InputConstants.Key pressed = InputConstants.getKey(event);
        if (!pressed.equals(((KeyMappingAccessor) ViewportCamKeys.ZOOM).viewportcam$getKey())) {
            return false;
        }
        KeyMapping.set(pressed, action != 0);
        return true;
    }

    public static boolean isRestoring() {
        return zoomRestoring;
    }

    public static boolean onScroll(double yOffset) {
        if (zoomHeld) {
            zoomFactor =
                    Mth.clamp(
                            zoomFactor
                                    * Math.exp(
                                            -yOffset
                                                    * ZOOM_RATE
                                                    * ViewportConfig.INSTANCE.zoomSensitivity),
                            ZOOM_FACTOR_MIN,
                            ZOOM_FACTOR_MAX);
            updateZoomAnchor(1.0 / 60.0);
            return true;
        }
        return zoomRestoring;
    }

    public static void updateZoomKey(boolean down) {
        if (down == zoomHeld) {
            return;
        }
        if (down) {
            startZoom();
        } else {
            zoomHeld = false;
            zoomRestoring = true;
        }
    }

    private static void startZoom() {
        Minecraft mc = Minecraft.getInstance();
        if (!ViewportCam.engaged
                || zoomRestoring
                || zoomBlend > zoomBlendEpsilon()
                || Platform.hasScreen(mc)
                || Platform.hasOverlay(mc)) {
            return;
        }
        zoomSnapPivot = ViewportCam.pivot;
        zoomSnapDistance = ViewportCam.distance;
        zoomSnapViewYaw = ViewportCam.viewYaw;
        zoomSnapViewPitch = ViewportCam.viewPitch;
        zoomSnapViewportYaw = ViewportCam.viewportYaw;
        zoomSnapViewportPitch = ViewportCam.viewportPitch;
        zoomBaseYaw = ViewportCam.viewYaw;
        zoomBasePitch = ViewportCam.viewPitch;
        Vec3 dir = ViewportCam.pointerRay();
        Vec3 eye =
                ViewportCam.vanillaReady
                        ? ViewportCam.cameraEyePos
                        : ViewportCam.computePose().position();
        zoomAnchorDir =
                dir != null && dir.lengthSqr() > 1.0E-8
                        ? dir.normalize()
                        : ViewportCam.direction(ViewportCam.viewYaw, ViewportCam.viewPitch);
        boolean aimed = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS;
        double blockDistance =
                aimed
                        ? -1.0
                        : ViewportCam.blockDistanceAlong(
                                mc, eye, zoomAnchorDir, ViewportCam.AIM_RAY_MAX_DISTANCE);
        double anchorDistance =
                aimed
                        ? mc.hitResult.getLocation().distanceTo(eye)
                        : (blockDistance > 0.0 ? blockDistance : ViewportCam.distance);
        zoomAnchorPoint = eye.add(zoomAnchorDir.scale(anchorDistance));
        zoomTrackDir = zoomAnchorDir;
        zoomFactor = autoZoomFactor(anchorDistance);
        zoomAnchorNdcX = ViewportCam.pointerNdcX;
        zoomAnchorNdcY = ViewportCam.pointerNdcY;
        zoomHasSnapshot = true;
        zoomHeld = true;
        if (zoomBlend <= zoomBlendEpsilon()) {
            zoomAnchorRot.identity();
            zoomAnchorRotActive = false;
        }
    }

    static void cancelZoom() {
        if (!zoomHasSnapshot) {
            return;
        }
        ViewportCam.pivot = zoomSnapPivot;
        ViewportCam.distance = zoomSnapDistance;
        ViewportCam.viewYaw = zoomSnapViewYaw;
        ViewportCam.viewPitch = zoomSnapViewPitch;
        ViewportCam.viewportYaw = zoomSnapViewportYaw;
        ViewportCam.viewportPitch = zoomSnapViewportPitch;
        zoomHeld = false;
        zoomRestoring = false;
        zoomBlend = 0.0;
        zoomAnchorRot.identity();
        zoomAnchorRotActive = false;
        zoomHasSnapshot = false;
    }

    private static double zoomBlendEpsilon() {
        return 3.0E-4
                / Math.max(
                        Math.abs(Math.log(Mth.clamp(zoomFactor, ZOOM_FACTOR_MIN, ZOOM_FACTOR_MAX))),
                        1.0);
    }

    static void stepZoom(double delta) {
        double tau = ZOOM_TAU / Mth.clamp(ViewportConfig.INSTANCE.zoomTransitionSpeed, 0.1, 4.0);
        double k = 1.0 - Math.exp(-delta / tau);
        double want = zoomHeld ? 1.0 : 0.0;
        double proposed = zoomBlend + (want - zoomBlend) * k;

        double needNow = requiredAnchorAngle(zoomBlend);
        double needNext = requiredAnchorAngle(proposed);
        double need = Math.abs(needNext - needNow);
        double budget = ANCHOR_MAX_SPEED * Math.max(delta, 1.0 / 240.0);
        if (need > budget && need > 1.0E-9) {
            proposed = zoomBlend + (proposed - zoomBlend) * (budget / need);
        }
        zoomBlend = proposed;

        if (zoomHeld || zoomBlend > 0.0) {
            updateZoomAnchor(delta);
        } else {
            zoomAnchorRot.identity();
            zoomAnchorRotActive = false;
        }

        if (!zoomRestoring) {
            if (zoomBlend < zoomBlendEpsilon() && !zoomHeld) {
                zoomBlend = 0.0;
            }
            return;
        }

        ViewportCam.pivot =
                ViewportCam.pivot.add(zoomSnapPivot.subtract(ViewportCam.pivot).scale(k));
        ViewportCam.distance += (zoomSnapDistance - ViewportCam.distance) * k;
        ViewportCam.viewYaw += Mth.wrapDegrees(zoomSnapViewYaw - ViewportCam.viewYaw) * (float) k;
        ViewportCam.viewPitch += (zoomSnapViewPitch - ViewportCam.viewPitch) * (float) k;
        ViewportCam.viewportYaw +=
                Mth.wrapDegrees(zoomSnapViewportYaw - ViewportCam.viewportYaw) * (float) k;
        ViewportCam.viewportPitch +=
                (zoomSnapViewportPitch - ViewportCam.viewportPitch) * (float) k;

        boolean arrived =
                ViewportCam.pivot.distanceToSqr(zoomSnapPivot) < 1.0E-6
                        && Math.abs(ViewportCam.distance - zoomSnapDistance) < 1.0E-3
                        && Math.abs(Mth.wrapDegrees(zoomSnapViewYaw - ViewportCam.viewYaw)) < 0.01
                        && Math.abs(zoomSnapViewPitch - ViewportCam.viewPitch) < 0.01;
        if (arrived) {
            finishZoomRestore();
        }
    }

    private static void finishZoomRestore() {
        if (!zoomHasSnapshot) {
            return;
        }
        ViewportCam.pivot = zoomSnapPivot;
        ViewportCam.distance = zoomSnapDistance;
        ViewportCam.viewYaw = zoomSnapViewYaw;
        ViewportCam.viewPitch = zoomSnapViewPitch;
        ViewportCam.viewportYaw = zoomSnapViewportYaw;
        ViewportCam.viewportPitch = zoomSnapViewportPitch;
        zoomRestoring = false;
        zoomHasSnapshot = false;
    }

    static void updateZoomAnchor(double delta) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        Quaternionf targetRot = new Quaternionf();
        if (window != null) {
            Vec3 eye = ViewportCam.computePose().position();
            Vec3 dir;
            if (ViewportConfig.INSTANCE.zoomTrackAnchor) {
                Vec3 toPoint = zoomAnchorPoint.subtract(eye);
                Vec3 wanted = toPoint.lengthSqr() > 1.0E-8 ? toPoint.normalize() : zoomAnchorDir;
                double smooth = 1.0 - Math.exp(-delta / ZOOM_TRACK_TAU);
                zoomTrackDir = zoomTrackDir.lerp(wanted, smooth).normalize();
                dir = zoomTrackDir;
            } else {
                dir = zoomAnchorDir;
            }
            dir = dir.normalize();
            double aspect =
                    (double) Math.max(window.getScreenWidth(), 1)
                            / Math.max(window.getScreenHeight(), 1);
            double[] solved =
                    solveAnchor(dir, zoomAnchorNdcX, zoomAnchorNdcY, renderedFovDeg(), aspect);
            if (solved != null) {
                targetRot = anchorRotation(solved[0], solved[1]);
            }
        }

        double maxStepRad = Mth.DEG_TO_RAD * ANCHOR_MAX_SPEED * Math.max(delta, 1.0 / 240.0);
        Quaternionf oriented = new Quaternionf(targetRot);
        double rawDot = zoomAnchorRot.dot(oriented);
        if (rawDot < 0.0) {
            oriented.set(-oriented.x, -oriented.y, -oriented.z, -oriented.w);
            rawDot = -rawDot;
        }
        double dot = Mth.clamp(rawDot, -1.0, 1.0);
        double angle = 2.0 * Math.acos(dot);
        if (angle > 1.0E-6) {
            float t = (float) Mth.clamp(maxStepRad / angle, 0.0, 1.0);
            zoomAnchorRot.slerp(oriented, t);
        } else {
            zoomAnchorRot.set(oriented);
        }
        zoomAnchorRotActive = zoomHeld || zoomBlend > 0.0;
    }

    private static double[] solveAnchor(Vec3 dir, double px, double py, double fov, double aspect) {
        double h = Math.tan(Math.toRadians(fov) / 2.0);
        double w = h * aspect;
        double a = 1.0 / Math.sqrt(1.0 + px * px * w * w + py * py * h * h);
        double b = -a * px * w;
        double c = a * py * h;
        double ex = -b;
        double ey = c;
        double ez = -a;

        double r = Math.sqrt(ey * ey + ez * ez);
        double psi = Math.atan2(ez, ey);
        double acos = Math.acos(Mth.clamp(dir.y / r, -1.0, 1.0));

        double bestYaw = 0.0;
        double bestPitch = 0.0;
        double bestErr = Double.MAX_VALUE;
        for (int sign = 0; sign < 2; sign++) {
            double bb = -psi + (sign == 0 ? acos : -acos);
            double u = ex;
            double ww = ey * Math.sin(bb) + ez * Math.cos(bb);
            double denom = u * u + ww * ww;
            if (denom < 1.0E-12) {
                continue;
            }
            double cosA = (u * dir.x + ww * dir.z) / denom;
            double sinA = (ww * dir.x - u * dir.z) / denom;
            double yaw = Math.toDegrees(Math.PI - Math.atan2(sinA, cosA));
            double pitch =
                    Mth.clamp(-Math.toDegrees(bb), -ViewportCam.MAX_PITCH, ViewportCam.MAX_PITCH);
            Vec3 got = rayFromNdc(yaw, pitch, px, py, fov, aspect);
            double err = Math.toDegrees(Math.acos(Mth.clamp(got.dot(dir), -1.0, 1.0)));
            if (err < bestErr) {
                bestErr = err;
                bestYaw = yaw;
                bestPitch = pitch;
            }
        }
        return bestErr > ANCHOR_TOLERANCE ? null : new double[] {bestYaw, bestPitch};
    }

    private static Vec3 rayFromNdc(
            double yaw, double pitch, double px, double py, double fov, double aspect) {
        Vec3 forward = ViewportCam.direction((float) yaw, (float) pitch);
        Vec3 left = ViewportCam.WORLD_UP.cross(forward).normalize();
        Vec3 up = forward.cross(left);
        double h = Math.tan(Math.toRadians(fov) / 2.0);
        double w = h * aspect;
        return forward.subtract(left.scale(px * w)).add(up.scale(py * h)).normalize();
    }
}
