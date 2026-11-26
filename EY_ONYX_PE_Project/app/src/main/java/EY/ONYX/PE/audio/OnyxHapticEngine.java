package EY.ONYX.PE.audio;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Tactile haptic feedback engine for module toggles, floating ball, and panel interactions.
 * Safely degrades on devices or emulators without a vibrator hardware service.
 */
public final class OnyxHapticEngine {

    private static volatile boolean enabled = true;
    private static volatile float strength = 0.65f; // 0.10f .. 1.00f

    private OnyxHapticEngine() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean enable) { enabled = enable; }

    public static float getStrength() { return strength; }
    public static void setStrength(float s) {
        strength = Math.max(0.10f, Math.min(1.00f, s));
    }

    public static void vibrateClick(Context ctx) {
        vibrate(ctx, 12, Math.round(90 * strength));
    }

    public static void vibrateToggle(Context ctx, boolean on) {
        if (on) {
            vibrate(ctx, 18, Math.round(160 * strength));
        } else {
            vibrate(ctx, 10, Math.round(75 * strength));
        }
    }

    public static void vibratePanel(Context ctx) {
        vibrate(ctx, 16, Math.round(130 * strength));
    }

    public static void vibrateLongPress(Context ctx) {
        vibrate(ctx, 26, Math.round(190 * strength));
    }

    private static void vibrate(Context ctx, int durationMs, int amplitude) {
        if (!enabled || ctx == null) return;
        try {
            Vibrator vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            int scaledDuration = Math.max(6, Math.round(durationMs * (0.55f + 0.65f * strength)));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int amp = Math.max(1, Math.min(255, amplitude));
                vib.vibrate(VibrationEffect.createOneShot(scaledDuration, amp));
            } else {
                vib.vibrate(scaledDuration);
            }
        } catch (Throwable ignored) {
        }
    }
}
