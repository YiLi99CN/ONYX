package EY.ONYX.PE.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;

import EY.ONYX.PE.audio.OnyxHapticEngine;
import EY.ONYX.PE.theme.OnyxTheme;

import static EY.ONYX.PE.ui.OnyxSettingViews.dp;

/**
 * Liquid Glass iOS Continuous Squircle Floating Ball ("O") mounted via
 * Activity WindowManager TYPE_APPLICATION sub-window so it stays cleanly above
 * game SurfaceView / OpenGL layers and can be placed anywhere on screen.
 */
public class OnyxFloatingBall implements OnyxTheme.ThemeListener {

    public interface FloatingActionCallback {
        void onTapToggleMainMenu();
        void onLongPressOpenHudEditor();
        boolean isMainMenuShowing();
    }

    private final Activity activity;
    private final OnyxTheme theme;
    private final FloatingActionCallback callback;
    private final SquircleOIconView iconView;
    private final WindowManager.LayoutParams ballLp;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int ballSizePx;
    private int ballX;
    private int ballY;
    private boolean showing = false;

    private final Runnable idleFadeRunnable = new Runnable() {
        @Override
        public void run() {
            if (iconView != null && (callback == null || !callback.isMainMenuShowing())) {
                iconView.animateAlphaTo(0.68f);
            }
        }
    };

    public OnyxFloatingBall(Activity activity, OnyxTheme theme, FloatingActionCallback callback) {
        this.activity = activity;
        this.theme = theme;
        this.callback = callback;
        this.theme.addListener(this);

        this.ballSizePx = dp(activity, 54);
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        this.ballX = dp(activity, 18);
        this.ballY = Math.max(dp(activity, 40), (dm.heightPixels - ballSizePx) / 2);

        this.iconView = new SquircleOIconView(activity);

        this.ballLp = new WindowManager.LayoutParams(
                ballSizePx,
                ballSizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );
        this.ballLp.gravity = Gravity.TOP | Gravity.START;
        this.ballLp.x = ballX;
        this.ballLp.y = ballY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            this.ballLp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
    }

    public void show(final View decorView) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || decorView == null) return;
        if (showing && iconView.getParent() != null) {
            return;
        }
        if (decorView.getWindowToken() == null || decorView.getWidth() <= 0) {
            handler.postDelayed(() -> show(decorView), 220);
            return;
        }
        try {
            WindowManager wm = activity.getWindowManager();
            if (wm != null && iconView.getParent() == null) {
                ballLp.x = ballX;
                ballLp.y = ballY;
                wm.addView(iconView, ballLp);
                showing = true;
                scheduleIdleFade();
            }
        } catch (Throwable t) {
            showing = false;
            handler.postDelayed(() -> show(decorView), 300);
        }
    }

    public void dismiss() {
        handler.removeCallbacks(idleFadeRunnable);
        if (showing && iconView.getParent() != null && activity != null) {
            try {
                WindowManager wm = activity.getWindowManager();
                if (wm != null) wm.removeViewImmediate(iconView);
            } catch (Throwable ignored) {
            }
        }
        showing = false;
    }

    public void setVisible(boolean visible) {
        if (iconView != null) {
            iconView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void notifyMenuStateChanged() {
        if (iconView != null) {
            iconView.invalidate();
            scheduleIdleFade();
        }
    }

    private void scheduleIdleFade() {
        handler.removeCallbacks(idleFadeRunnable);
        if (iconView != null) {
            iconView.animateAlphaTo(1.0f);
        }
        handler.postDelayed(idleFadeRunnable, 2800);
    }

    private void updateWindowPosition() {
        if (!showing || iconView.getParent() == null || activity == null || activity.isFinishing()) return;
        try {
            ballLp.x = ballX;
            ballLp.y = ballY;
            WindowManager wm = activity.getWindowManager();
            if (wm != null) wm.updateViewLayout(iconView, ballLp);
        } catch (Throwable ignored) {
        }
    }

    private void clampBallToScreen() {
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        int margin = dp(activity, 6);

        ballX = Math.max(margin, Math.min(screenW - ballSizePx - margin, ballX));
        ballY = Math.max(margin, Math.min(screenH - ballSizePx - margin, ballY));
        updateWindowPosition();
    }

    @Override
    public void onThemeChanged(OnyxTheme updatedTheme) {
        if (iconView != null) {
            iconView.invalidate();
        }
    }

    private class SquircleOIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path squirclePath = new Path();
        private final RectF specRect = new RectF();

        private float pressScale = 1.0f;
        private float downRawX, downRawY;
        private int downBallX, downBallY;
        private boolean movedDuringTouch = false;
        private boolean longPressTriggered = false;
        private final int touchSlop;

        private final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (!movedDuringTouch && callback != null) {
                    longPressTriggered = true;
                    OnyxHapticEngine.vibrateLongPress(activity);
                    callback.onLongPressOpenHudEditor();
                }
            }
        };

        public SquircleOIconView(Context context) {
            super(context);
            this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        public void animateAlphaTo(float targetAlpha) {
            animate().alpha(targetAlpha).setDuration(260).start();
        }

        private void animatePressScale(float target) {
            ValueAnimator va = ValueAnimator.ofFloat(pressScale, target);
            va.setDuration(150);
            va.setInterpolator(new OvershootInterpolator(1.5f));
            va.addUpdateListener(a -> {
                pressScale = (Float) a.getAnimatedValue();
                invalidate();
            });
            va.start();
        }

        private void buildIosSquirclePath(float left, float top, float right, float bottom, Path outPath) {
            outPath.reset();
            float w = right - left;
            float h = bottom - top;
            float r = Math.min(w, h) * 0.34f;
            float k = r * 0.62f;

            outPath.moveTo(left + r, top);
            outPath.lineTo(right - r, top);
            outPath.cubicTo(right - r + k, top, right, top + r - k, right, top + r);
            outPath.lineTo(right, bottom - r);
            outPath.cubicTo(right, bottom - r + k, right - r + k, bottom, right - r, bottom);
            outPath.lineTo(left + r, bottom);
            outPath.cubicTo(left + r - k, bottom, left, bottom - r + k, left, bottom - r);
            outPath.lineTo(left, top + r);
            outPath.cubicTo(left, top + r - k, left + r - k, top, left + r, top);
            outPath.close();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float inset = dp(getContext(), 4.2f);
            float t = (SystemClock.uptimeMillis() % 8000L) / 8000.0f;
            int dyn1 = theme.getDynamicColor(0f);
            int dyn2 = theme.getDynamicColor(0.45f);
            boolean menuActive = callback != null && callback.isMainMenuShowing();

            canvas.save();
            canvas.scale(pressScale, pressScale, cx, cy);

            buildIosSquirclePath(inset, inset, w - inset, h - inset, squirclePath);

            // 1. Liquid Glass translucent tinted body
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            LinearGradient glassBody = new LinearGradient(
                    inset, inset, w - inset, h - inset,
                    new int[]{0xC8262436, 0xB8141320, 0xCE1D1A2E},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP
            );
            paint.setShader(glassBody);
            canvas.drawPath(squirclePath, paint);
            paint.setShader(null);

            // 2. Subtle frosted center glow
            RadialGradient innerGlow = new RadialGradient(
                    cx, cy, (w - inset * 2) * 0.50f,
                    new int[]{OnyxTheme.withAlpha(dyn1, menuActive ? 75 : 40), 0x00000000},
                    null, Shader.TileMode.CLAMP
            );
            paint.setShader(innerGlow);
            canvas.drawPath(squirclePath, paint);
            paint.setShader(null);

            // 3. Clean frosted glass inset rim
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.25f));
            paint.setColor(OnyxTheme.withAlpha(0xFFFFFFFF, 56));
            canvas.drawPath(squirclePath, paint);

            // 5. Luminous central geometric "O" ring
            float oRadius = (w - inset * 2) * 0.25f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 4.6f));
            paint.setColor(OnyxTheme.withAlpha(dyn1, menuActive ? 105 : 72));
            canvas.drawCircle(cx, cy, oRadius, paint);

            paint.setStrokeWidth(dp(getContext(), 2.6f));
            LinearGradient oRingGrad = new LinearGradient(
                    cx - oRadius, cy - oRadius, cx + oRadius, cy + oRadius,
                    new int[]{0xFFFFFFFF, dyn1, 0xFFE5DEFF},
                    null, Shader.TileMode.CLAMP
            );
            paint.setShader(oRingGrad);
            canvas.drawCircle(cx, cy, oRadius, paint);
            paint.setShader(null);

            canvas.restore();
            if (showing && getVisibility() == View.VISIBLE) {
                postInvalidateOnAnimation();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scheduleIdleFade();
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downBallX = ballX;
                    downBallY = ballY;
                    movedDuringTouch = false;
                    longPressTriggered = false;
                    animatePressScale(0.91f);
                    handler.removeCallbacks(longPressRunnable);
                    handler.postDelayed(longPressRunnable, 550);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (Math.hypot(dx, dy) > touchSlop) {
                        movedDuringTouch = true;
                        handler.removeCallbacks(longPressRunnable);
                    }
                    if (movedDuringTouch) {
                        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                        int maxX = Math.max(0, dm.widthPixels - ballSizePx);
                        int maxY = Math.max(0, dm.heightPixels - ballSizePx);
                        ballX = Math.max(0, Math.min(maxX, Math.round(downBallX + dx)));
                        ballY = Math.max(0, Math.min(maxY, Math.round(downBallY + dy)));
                        updateWindowPosition();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPressRunnable);
                    animatePressScale(1.0f);
                    if (movedDuringTouch) {
                        clampBallToScreen();
                    } else if (!longPressTriggered && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        if (callback != null) {
                            OnyxHapticEngine.vibrateClick(activity);
                            callback.onTapToggleMainMenu();
                            invalidate();
                        }
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
