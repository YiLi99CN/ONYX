package EY.ONYX.PE.xposed;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import EY.ONYX.PE.ui.OnyxOverlayController;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed / Xposed entry point aligned with InGameHost / RlinEntry architecture:
 * 1. Filters strictly to main game process (skips PushService / secondary processes).
 * 2. Hooks Activity.onResume, Activity.onDestroy, and Activity.dispatchKeyEvent.
 * 3. Polls DecorView.isAttachedToWindow() && width > 0 (250ms x 40) + 600ms settle delay,
 *    then mounts TYPE_APPLICATION WindowManager sub-windows above the game surface.
 */
public class OnyxXposedEntry implements IXposedHookLoadPackage {

    private static volatile boolean hooksInstalled = false;
    private static final Set<Activity> POLLING_ACTIVITIES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<Activity, Boolean>()));

    private boolean needHook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) return false;
        String pkg = lpparam.packageName;
        if ("EY.ONYX.PE".equals(pkg)) return false;

        // Skip secondary processes such as com.netease.x19:PushService
        if (lpparam.processName != null && !pkg.equals(lpparam.processName)) {
            return false;
        }

        if ("com.netease.x19".equals(pkg) || "com.mojang.minecraftpe".equals(pkg)) {
            return true;
        }
        try {
            if (lpparam.appInfo != null && lpparam.appInfo.nativeLibraryDir != null) {
                File mcSo = new File(lpparam.appInfo.nativeLibraryDir, "libminecraftpe.so");
                if (mcSo.exists()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isGameActivity(Activity a) {
        if (a == null) return false;
        String cls = a.getClass().getName();
        String lower = cls.toLowerCase();
        if (lower.contains("minecraftpe") || cls.endsWith("MainActivity")) {
            return true;
        }
        Class<?> sup = a.getClass().getSuperclass();
        while (sup != null) {
            if (sup.getName().contains("GameActivity") || sup.getName().contains("NativeActivity")) {
                return true;
            }
            sup = sup.getSuperclass();
        }
        // Fallback: allow NetEase primary non-splash game activity
        String pkg = a.getPackageName();
        return ("com.netease.x19".equals(pkg) || "com.mojang.minecraftpe".equals(pkg))
                && !lower.contains("splash") && !lower.contains("permission");
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!needHook(lpparam)) return;

        XposedBridge.log("[ONYX] Main process hooked: " + lpparam.packageName);
        if (hooksInstalled) return;
        hooksInstalled = true;

        try {
            XposedHelpers.findAndHookMethod(
                    Activity.class,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            final Activity activity = (Activity) param.thisObject;
                            if (activity == null || activity.isFinishing() || !isGameActivity(activity)) return;
                            if (OnyxOverlayController.getIfAttached(activity) != null) {
                                OnyxOverlayController.getIfAttached(activity).ensureFloatingBallMounted();
                                return;
                            }
                            if (POLLING_ACTIVITIES.add(activity)) {
                                startWhenReady(activity, 0);
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    Activity.class,
                    "onDestroy",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Activity activity = (Activity) param.thisObject;
                            if (activity != null) {
                                POLLING_ACTIVITIES.remove(activity);
                                OnyxOverlayController.detachFromActivity(activity);
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    Activity.class,
                    "dispatchKeyEvent",
                    KeyEvent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Activity activity = (Activity) param.thisObject;
                            if (activity == null || param.args == null || param.args.length == 0) return;
                            KeyEvent event = (KeyEvent) param.args[0];
                            OnyxOverlayController controller = OnyxOverlayController.getIfAttached(activity);
                            if (controller != null && controller.dispatchKeyEvent(event)) {
                                param.setResult(true);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log("[ONYX] Hook registration error: " + t.getMessage());
        }
    }

    private void startWhenReady(final Activity activity, final int attempt) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getWindow() == null) {
            POLLING_ACTIVITIES.remove(activity);
            return;
        }
        View decor = activity.getWindow().getDecorView();
        boolean ready = decor != null && decor.isAttachedToWindow() && decor.getWidth() > 0 && decor.getWindowToken() != null;
        Handler mainHandler = new Handler(Looper.getMainLooper());
        if (!ready) {
            if (attempt < 40) {
                mainHandler.postDelayed(() -> startWhenReady(activity, attempt + 1), 250L);
            } else {
                POLLING_ACTIVITIES.remove(activity);
            }
            return;
        }
        mainHandler.postDelayed(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            try {
                OnyxOverlayController controller = OnyxOverlayController.attachToActivity(activity, false);
                controller.ensureFloatingBallMounted();
                XposedBridge.log("[ONYX] Overlay mounted above " + activity.getClass().getName());
            } catch (Throwable t) {
                XposedBridge.log("[ONYX] Overlay mount error: " + t.getMessage());
            }
        }, 600L);
    }
}
