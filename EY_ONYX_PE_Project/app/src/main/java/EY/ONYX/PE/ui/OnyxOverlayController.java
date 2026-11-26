package EY.ONYX.PE.ui;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import EY.ONYX.PE.auth.OnyxCloudAuthClient;
import EY.ONYX.PE.auth.OnyxLoginGateView;
import EY.ONYX.PE.hud.OnyxHudOverlayView;
import EY.ONYX.PE.module.OnyxModuleManager;
import EY.ONYX.PE.theme.OnyxTheme;

import java.util.WeakHashMap;

/**
 * Coordinates strict Login-First gating:
 * - Floating ball, HUD layer, and ClickGUI are NEVER mounted until server login succeeds.
 * - After authentication succeeds, mounts TYPE_APPLICATION WindowManager sub-windows above the game.
 */
public class OnyxOverlayController {

    private static final WeakHashMap<Activity, OnyxOverlayController> ACTIVE_CONTROLLERS = new WeakHashMap<>();

    public static synchronized OnyxOverlayController getIfAttached(final Activity activity) {
        return ACTIVE_CONTROLLERS.get(activity);
    }

    public static synchronized OnyxOverlayController attachToActivity(final Activity activity, final boolean whiteCanvasBackground) {
        OnyxOverlayController existing = ACTIVE_CONTROLLERS.get(activity);
        if (existing != null) {
            existing.ensureFloatingBallMounted();
            return existing;
        }
        OnyxOverlayController controller = new OnyxOverlayController(activity, whiteCanvasBackground);
        ACTIVE_CONTROLLERS.put(activity, controller);
        return controller;
    }

    public static synchronized void detachFromActivity(final Activity activity) {
        OnyxOverlayController existing = ACTIVE_CONTROLLERS.remove(activity);
        if (existing != null) {
            existing.destroy();
        }
    }

    private final Activity activity;
    private final OnyxTheme theme;
    private final OnyxModuleManager moduleManager;
    private final boolean whiteCanvasBackground;

    private OnyxHudOverlayView hudOverlayView;
    private WindowManager.LayoutParams hudWindowParams;
    private boolean hudMountedInWindowManager = false;
    private OpenOnyxPopupWindow mainMenuPopup;
    private OnyxFloatingBall floatingBall;
    private PopupWindow authPopup;
    private boolean uiInitialized = false;

    private OnyxOverlayController(final Activity activity, boolean whiteCanvasBackground) {
        this.activity = activity;
        this.whiteCanvasBackground = whiteCanvasBackground;
        this.theme = new OnyxTheme();
        this.moduleManager = new OnyxModuleManager(theme);
        this.moduleManager.setAppContext(activity);

        if (OnyxCloudAuthClient.isAuthenticated()) {
            initializeAndMountUi();
        } else {
            showLoginGateWhenReady();
        }
    }

    private void showLoginGateWhenReady() {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getWindow() == null) return;
        final View decor = activity.getWindow().getDecorView();
        if (decor == null) return;

        decor.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (OnyxCloudAuthClient.isAuthenticated()) {
                initializeAndMountUi();
                return;
            }
            if (authPopup != null && authPopup.isShowing()) return;
            if (decor.getWindowToken() == null) {
                decor.postDelayed(this::showLoginGateWhenReady, 220);
                return;
            }
            OnyxLoginGateView gate = new OnyxLoginGateView(activity, theme, () -> {
                if (authPopup != null && authPopup.isShowing()) {
                    authPopup.dismiss();
                }
                initializeAndMountUi();
            });
            authPopup = new PopupWindow(gate, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
            authPopup.setOutsideTouchable(false);
            authPopup.setFocusable(true);
            authPopup.setClippingEnabled(false);
            authPopup.showAtLocation(decor, Gravity.CENTER, 0, 0);
            applyWindowBlur(authPopup, 36);
        });
    }

    private void initializeAndMountUi() {
        if (!uiInitialized) {
            uiInitialized = true;
            this.hudOverlayView = new OnyxHudOverlayView(activity, moduleManager, theme, () -> {
                setHudWindowTouchable(false);
                if (floatingBall != null) floatingBall.setVisible(true);
                if (mainMenuPopup != null && !mainMenuPopup.isShowing()) {
                    mainMenuPopup.showAtCenter(activity.getWindow().getDecorView());
                }
                if (floatingBall != null) floatingBall.notifyMenuStateChanged();
            });
            this.hudOverlayView.setWhiteCanvasBackground(whiteCanvasBackground);

            this.mainMenuPopup = new OpenOnyxPopupWindow(activity, moduleManager, theme);
            this.mainMenuPopup.setHudOverlayView(this.hudOverlayView);
            this.mainMenuPopup.setOnDismissCallback(() -> {
                if (floatingBall != null) floatingBall.notifyMenuStateChanged();
            });

            this.moduleManager.setHudEditorCallback(targetHudModule -> {
                if (mainMenuPopup != null && mainMenuPopup.isShowing()) mainMenuPopup.dismiss();
                if (floatingBall != null) floatingBall.setVisible(false);
                setHudWindowTouchable(true);
                hudOverlayView.enterEditorMode(targetHudModule);
            });

            this.floatingBall = new OnyxFloatingBall(activity, theme, new OnyxFloatingBall.FloatingActionCallback() {
                @Override
                public void onTapToggleMainMenu() {
                    toggleMainMenu();
                }

                @Override
                public void onLongPressOpenHudEditor() {
                    if (mainMenuPopup != null && mainMenuPopup.isShowing()) mainMenuPopup.dismiss();
                    if (floatingBall != null) floatingBall.setVisible(false);
                    setHudWindowTouchable(true);
                    hudOverlayView.enterEditorMode(null);
                }

                @Override
                public boolean isMainMenuShowing() {
                    return mainMenuPopup != null && mainMenuPopup.isShowing();
                }
            });
        }
        mountHudLayerAndFloatingBall();
    }

    private static void applyWindowBlur(PopupWindow popup, int radiusPx) {
        if (popup == null || Build.VERSION.SDK_INT < 31) return;
        try {
            View root = popup.getContentView().getRootView();
            ViewGroup.LayoutParams rawLp = root.getLayoutParams();
            if (rawLp instanceof WindowManager.LayoutParams) {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) rawLp;
                lp.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                lp.setBlurBehindRadius(radiusPx);
                WindowManager wm = (WindowManager) root.getContext().getSystemService(android.content.Context.WINDOW_SERVICE);
                if (wm != null) wm.updateViewLayout(root, lp);
            }
        } catch (Throwable ignored) {
        }
    }

    private WindowManager.LayoutParams createHudWindowParams(boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (!touchable) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                flags,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        return lp;
    }

    private void setHudWindowTouchable(boolean touchable) {
        if (!hudMountedInWindowManager || hudWindowParams == null || hudOverlayView == null
                || activity == null || activity.isFinishing()) {
            return;
        }
        try {
            if (touchable) {
                hudWindowParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            } else {
                hudWindowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            }
            WindowManager wm = activity.getWindowManager();
            if (wm != null && hudOverlayView.getParent() != null) {
                wm.updateViewLayout(hudOverlayView, hudWindowParams);
            }
        } catch (Throwable ignored) {
        }
    }

    public void ensureFloatingBallMounted() {
        if (!OnyxCloudAuthClient.isAuthenticated()) {
            showLoginGateWhenReady();
            return;
        }
        initializeAndMountUi();
    }

    private void mountHudLayerAndFloatingBall() {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getWindow() == null) return;
        if (!uiInitialized || hudOverlayView == null) return;
        final View decor = activity.getWindow().getDecorView();
        if (decor == null) return;

        decor.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;

            if (whiteCanvasBackground) {
                if (decor instanceof ViewGroup && hudOverlayView.getParent() == null) {
                    ViewGroup contentParent = decor.findViewById(android.R.id.content);
                    ViewGroup targetGroup = contentParent != null ? contentParent : (ViewGroup) decor;
                    targetGroup.addView(hudOverlayView, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.FILL
                    ));
                }
            } else if (!hudMountedInWindowManager && hudOverlayView.getParent() == null) {
                try {
                    WindowManager wm = activity.getWindowManager();
                    if (wm != null && decor.getWindowToken() != null) {
                        hudWindowParams = createHudWindowParams(hudOverlayView.isEditorMode());
                        wm.addView(hudOverlayView, hudWindowParams);
                        hudMountedInWindowManager = true;
                    }
                } catch (Throwable ignored) {
                }
            }

            if (floatingBall != null) {
                floatingBall.show(decor);
            }
        });
    }

    public void destroy() {
        try {
            if (authPopup != null && authPopup.isShowing()) authPopup.dismiss();
        } catch (Throwable ignored) {}
        try {
            if (mainMenuPopup != null && mainMenuPopup.isShowing()) mainMenuPopup.dismiss();
        } catch (Throwable ignored) {}
        try {
            if (floatingBall != null) floatingBall.dismiss();
        } catch (Throwable ignored) {}
        try {
            if (hudMountedInWindowManager && hudOverlayView != null && activity != null && hudOverlayView.getParent() != null) {
                WindowManager wm = activity.getWindowManager();
                if (wm != null) wm.removeViewImmediate(hudOverlayView);
            }
            hudMountedInWindowManager = false;
        } catch (Throwable ignored) {}
    }

    public void toggleMainMenu() {
        if (!OnyxCloudAuthClient.isAuthenticated()) {
            showLoginGateWhenReady();
            return;
        }
        if (!uiInitialized) {
            initializeAndMountUi();
        }
        if (hudOverlayView != null && hudOverlayView.isEditorMode()) {
            hudOverlayView.exitEditorMode();
            return;
        }
        if (mainMenuPopup != null && mainMenuPopup.isShowing()) {
            mainMenuPopup.dismissWithDissolveAnimation();
        } else if (mainMenuPopup != null) {
            mainMenuPopup.showAtCenter(activity.getWindow().getDecorView());
        }
        if (floatingBall != null) {
            floatingBall.notifyMenuStateChanged();
        }
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event == null || !OnyxCloudAuthClient.isAuthenticated()) return false;
        int code = event.getKeyCode();
        int action = event.getAction();
        if ((code == KeyEvent.KEYCODE_SHIFT_RIGHT
                || code == KeyEvent.KEYCODE_SHIFT_LEFT
                || code == KeyEvent.KEYCODE_U)
                && action == KeyEvent.ACTION_DOWN) {
            toggleMainMenu();
            return true;
        }
        if (code == KeyEvent.KEYCODE_ESCAPE && action == KeyEvent.ACTION_DOWN) {
            if (hudOverlayView != null && hudOverlayView.isEditorMode()) {
                hudOverlayView.exitEditorMode();
                return true;
            }
            if (mainMenuPopup != null && mainMenuPopup.isShowing()) {
                mainMenuPopup.dismissWithDissolveAnimation();
                return true;
            }
        }
        return moduleManager.handleKeyEvent(code, action);
    }
}
