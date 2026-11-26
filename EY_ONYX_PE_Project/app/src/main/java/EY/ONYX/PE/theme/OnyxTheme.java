package EY.ONYX.PE.theme;

import android.graphics.Color;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Material 3 dynamic palette and multi-stop color cycling engine.
 * Supports static seed color, multiple built-in aurora/neon presets, and an unlimited
 * user-configurable list of custom color stops.
 */
public class OnyxTheme {

    public interface ThemeListener {
        void onThemeChanged(OnyxTheme theme);
    }

    public static final int DEFAULT_SEED_COLOR = 0xFF8E6BFF;

    public static final String[] COLOR_PRESETS = new String[]{
            "Aubergine",
            "Aqua",
            "Banana",
            "Blend",
            "Blossom",
            "Bubblegum",
            "Candy Cane",
            "Cherry",
            "Christmas",
            "Coral",
            "Digital Horizon",
            "Express",
            "Lime Water",
            "Lush",
            "Halogen",
            "Onyx Violet",
            "Native Rainbow",
            "Custom Multi-Color"
    };

    public static int[] getPresetStops(String mode) {
        if ("Aubergine".equalsIgnoreCase(mode)) return new int[]{0xFFB80F7A, 0xFF7B0063};
        if ("Aqua".equalsIgnoreCase(mode)) return new int[]{0xFFAEEEEE, 0xFF66D1D1};
        if ("Banana".equalsIgnoreCase(mode)) return new int[]{0xFFFFF4A8, 0xFFFFFCE6};
        if ("Blend".equalsIgnoreCase(mode)) return new int[]{0xFF3DA9FF, 0xFF4BFFCE};
        if ("Blossom".equalsIgnoreCase(mode)) return new int[]{0xFFCAC7E6, 0xFF678C94};
        if ("Bubblegum".equalsIgnoreCase(mode)) return new int[]{0xFFF69BE2, 0xFFAEAEF9};
        if ("Candy Cane".equalsIgnoreCase(mode)) return new int[]{0xFFE62020, 0xFFFDF5F5};
        if ("Cherry".equalsIgnoreCase(mode)) return new int[]{0xFFBA458F, 0xFFFCC8D8};
        if ("Christmas".equalsIgnoreCase(mode)) return new int[]{0xFFFF463D, 0xFFFFFFFF, 0xFF47F059};
        if ("Coral".equalsIgnoreCase(mode)) return new int[]{0xFFC8A19D, 0xFF6394A0};
        if ("Digital Horizon".equalsIgnoreCase(mode)) return new int[]{0xFF6FC7EE, 0xFFC073C0, 0xFFED6D8E};
        if ("Express".equalsIgnoreCase(mode)) return new int[]{0xFFA84280, 0xFF5D1360};
        if ("Lime Water".equalsIgnoreCase(mode)) return new int[]{0xFF1BF6E0, 0xFF9DF0B6};
        if ("Lush".equalsIgnoreCase(mode)) return new int[]{0xFF93CC54, 0xFF75B53A};
        if ("Halogen".equalsIgnoreCase(mode)) return new int[]{0xFFFF2F5C, 0xFFFF6D2F};
        if ("Native Rainbow".equalsIgnoreCase(mode)) return new int[]{0xFFFF4D6D, 0xFFFFD166, 0xFF06D6A0, 0xFF118AB2, 0xFF9B5DE5};
        return new int[]{0xFF8E6BFF, 0xFF6342D6};
    }

    private int seedColor = DEFAULT_SEED_COLOR;
    private float backgroundTintPercent = 25.0f;
    private float uiScale = 1.0f;
    private boolean frostedGlass = true;

        // Convergence & Dispersion (聚散效果) state and 3-parameter tuning per mode
    public static final String MODE_NORMAL = "Normal";
    public static final String MODE_LIQUID_FLOW = "Liquid Flow";
    public static final String MODE_MACOS_WARP = "macOS Fluid Warp";
    public static final String MODE_ISLAND_FLOW = "Dynamic Island Flow";

    private String transitionMode = MODE_NORMAL;

    // Mode 1: Normal
    private float normalDurationMs = 220f;

    // Mode 2: Liquid Flow (3 tuning parameters: Delay/Duration, Irregularity Wave, Surface Tension/Swirl)
    private float liquidDurationMs = 540f;
    private float liquidIrregularity = 1.15f;
    private float liquidTensionSwirl = 1.10f;

    // Mode 3: macOS Pure Fluid Warp (3 tuning parameters: Delay/Duration, S-Curve Warp, Funnel Pinch)
    private float macosDurationMs = 520f;
    private float macosWarpCurvature = 1.15f;
    private float macosFunnelPinch = 1.10f;

    // Mode 4: Dynamic Island Flow (3 tuning parameters: Delay/Duration, Stream Twist, Island Bounce)
    private float islandDurationMs = 560f;
    private float islandStreamTwist = 1.20f;
    private float islandBounceStrength = 1.30f;

    public String getTransitionMode() { return transitionMode; }
    public void setTransitionMode(String mode) {
        if (mode != null) {
            this.transitionMode = mode;
        }
    }

    public float getNormalDurationMs() { return normalDurationMs; }
    public void setNormalDurationMs(float ms) { this.normalDurationMs = Math.max(100f, Math.min(800f, ms)); }

    public float getLiquidDurationMs() { return liquidDurationMs; }
    public void setLiquidDurationMs(float ms) { this.liquidDurationMs = Math.max(200f, Math.min(1200f, ms)); }
    public float getLiquidIrregularity() { return liquidIrregularity; }
    public void setLiquidIrregularity(float val) { this.liquidIrregularity = Math.max(0.2f, Math.min(2.5f, val)); }
    public float getLiquidTensionSwirl() { return liquidTensionSwirl; }
    public void setLiquidTensionSwirl(float val) { this.liquidTensionSwirl = Math.max(0.2f, Math.min(2.5f, val)); }

    public float getMacosDurationMs() { return macosDurationMs; }
    public void setMacosDurationMs(float ms) { this.macosDurationMs = Math.max(200f, Math.min(1200f, ms)); }
    public float getMacosWarpCurvature() { return macosWarpCurvature; }
    public void setMacosWarpCurvature(float val) { this.macosWarpCurvature = Math.max(0.2f, Math.min(2.5f, val)); }
    public float getMacosFunnelPinch() { return macosFunnelPinch; }
    public void setMacosFunnelPinch(float val) { this.macosFunnelPinch = Math.max(0.2f, Math.min(2.5f, val)); }

    public float getIslandDurationMs() { return islandDurationMs; }
    public void setIslandDurationMs(float ms) { this.islandDurationMs = Math.max(220f, Math.min(1200f, ms)); }
    public float getIslandStreamTwist() { return islandStreamTwist; }
    public void setIslandStreamTwist(float val) { this.islandStreamTwist = Math.max(0.2f, Math.min(2.5f, val)); }
    public float getIslandBounceStrength() { return islandBounceStrength; }
    public void setIslandBounceStrength(float val) { this.islandBounceStrength = Math.max(0.2f, Math.min(2.5f, val)); }

    private String colorMode = "Onyx Violet";
    private float cycleSpeed = 1.0f;
    private final List<Integer> customColorStops = new ArrayList<>(Arrays.asList(
            0xFF9E6BFF,
            0xFFFF6EC7,
            0xFF5CE1E6
    ));

    public int primary;
    public int onPrimary;
    public int primaryContainer;
    public int onPrimaryContainer;
    public int secondaryContainer;
    public int onSecondaryContainer;
    public int error;
    public int errorContainer;
    public int onErrorContainer;

    public int surface;
    public int onSurface;
    public int onSurfaceVariant;
    public int surfaceContainerLowest;
    public int surfaceContainerLow;
    public int surfaceContainer;
    public int surfaceContainerHigh;
    public int surfaceContainerHighest;

    public int outline;
    public int outlineVariant;
    public int dividerColor;

    public int panelBackground;
    public int sidebarBackground;
    public int moduleCardOffBackground;
    public int moduleCardOnBackground;
    public int searchBarBackground;
    public int activeTabPillColor;

    private final List<ThemeListener> listeners = new ArrayList<>();

    public OnyxTheme() {
        rebuildPalette();
    }

    public void addListener(ThemeListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(ThemeListener l) {
        listeners.remove(l);
    }

    public void notifyListeners() {
        for (ThemeListener l : new ArrayList<>(listeners)) {
            l.onThemeChanged(this);
        }
    }

    public int getSeedColor() { return seedColor; }

    public void setSeedColor(int color) {
        if (this.seedColor != color) {
            this.seedColor = color;
            rebuildPalette();
            notifyListeners();
        }
    }

    public String getColorMode() { return colorMode; }

    public void setColorMode(String mode) {
        if (mode != null && !mode.equals(this.colorMode)) {
            this.colorMode = mode;
            if ("Custom Multi-Color".equalsIgnoreCase(mode) && !customColorStops.isEmpty()) {
                this.seedColor = customColorStops.get(0);
            } else if (!"Native Rainbow".equalsIgnoreCase(mode)) {
                this.seedColor = getPresetStops(mode)[0];
            }
            rebuildPalette();
            notifyListeners();
        }
    }

    public boolean isDynamicCyclingActive() {
        return !"Onyx Violet".equalsIgnoreCase(colorMode);
    }

    public float getCycleSpeed() { return cycleSpeed; }

    public void setCycleSpeed(float speed) {
        this.cycleSpeed = Math.max(0.2f, Math.min(4.0f, speed));
    }

    public List<Integer> getCustomColorStops() {
        return customColorStops;
    }

    public void addCustomColorStop(int color) {
        customColorStops.add(color);
        if ("Custom Multi-Color".equalsIgnoreCase(colorMode)) {
            rebuildPalette();
        }
        notifyListeners();
    }

    public void updateCustomColorStop(int index, int color) {
        if (index >= 0 && index < customColorStops.size()) {
            customColorStops.set(index, color);
            if (index == 0 && "Custom Multi-Color".equalsIgnoreCase(colorMode)) {
                this.seedColor = color;
                rebuildPalette();
            }
            notifyListeners();
        }
    }

    public void removeCustomColorStop(int index) {
        if (customColorStops.size() > 2 && index >= 0 && index < customColorStops.size()) {
            customColorStops.remove(index);
            notifyListeners();
        }
    }

    /**
     * Evaluates the active theme or multi-stop preset color at the given spatial phase offset.
     *
     * @param phaseOffset0to1 phase offset in [0, 1]
     * @return interpolated ARGB color
     */
    public int getDynamicColor(float phaseOffset0to1) {
        if ("Onyx Violet".equalsIgnoreCase(colorMode)) {
            return primary;
        }
        float timeSec = (SystemClock.uptimeMillis() % 600000L) / 1000.0f;
        float phase = (timeSec * 0.48f * cycleSpeed + phaseOffset0to1) % 1.0f;
        if (phase < 0f) phase += 1.0f;

        if ("Native Rainbow".equalsIgnoreCase(colorMode)) {
            float hue = (phase * 360.0f) % 360.0f;
            return Color.HSVToColor(255, new float[]{hue, 0.76f, 1.0f});
        }

        int[] stops;
        if (!"Custom Multi-Color".equalsIgnoreCase(colorMode)) {
            stops = getPresetStops(colorMode);
        } else {
            // Custom Multi-Color stops configured by the user
            int n = customColorStops.size();
            if (n == 0) return primary;
            if (n == 1) return customColorStops.get(0);
            stops = new int[n];
            for (int i = 0; i < n; i++) {
                stops[i] = customColorStops.get(i);
            }
        }

        float scaled = phase * stops.length;
        int idxA = ((int) Math.floor(scaled)) % stops.length;
        int idxB = (idxA + 1) % stops.length;
        float frac = scaled - (float) Math.floor(scaled);
        float smoothFrac = frac * frac * (3f - 2f * frac);
        return blendColors(stops[idxA], stops[idxB], smoothFrac);
    }

    public float getBackgroundTintPercent() { return backgroundTintPercent; }

    public void setBackgroundTintPercent(float pct) {
        float c = Math.max(0.0f, Math.min(100.0f, pct));
        if (Math.abs(this.backgroundTintPercent - c) > 0.01f) {
            this.backgroundTintPercent = c;
            rebuildPalette();
            notifyListeners();
        }
    }

    public float getUiScale() { return uiScale; }

    public void setUiScale(float scale) {
        float c = Math.max(0.5f, Math.min(1.5f, scale));
        if (Math.abs(this.uiScale - c) > 0.005f) {
            this.uiScale = c;
            notifyListeners();
        }
    }

    public boolean isFrostedGlass() { return frostedGlass; }

    public void setFrostedGlass(boolean fg) {
        if (this.frostedGlass != fg) {
            this.frostedGlass = fg;
            rebuildPalette();
            notifyListeners();
        }
    }

    public void rebuildPalette() {
        float[] hsv = new float[3];
        Color.colorToHSV(seedColor, hsv);
        float hue = hsv[0];
        float sat = Math.max(0.38f, hsv[1]);

        primary = toneColor(hue, Math.min(1.0f, sat * 0.72f), 100.0f);
        onPrimary = toneColor(hue, sat * 0.85f, 16.0f);
        primaryContainer = toneColor(hue, sat * 0.65f, 40.0f);
        onPrimaryContainer = 0xFFF3EEFF;

        secondaryContainer = toneColor(hue, sat * 0.45f, 32.0f);
        onSecondaryContainer = 0xFFEAE4F8;

        error = 0xFFFF6B6B;
        errorContainer = 0xFF5C1919;
        onErrorContainer = 0xFFFFDAD6;

        surfaceContainerLowest = toneColor(hue, sat * 0.12f, 7.5f);
        surfaceContainerLow = toneColor(hue, sat * 0.12f, 10.0f);
        surface = toneColor(hue, sat * 0.12f, 11.0f);
        surfaceContainer = toneColor(hue, sat * 0.14f, 14.0f);
        surfaceContainerHigh = toneColor(hue, sat * 0.15f, 18.0f);
        surfaceContainerHighest = toneColor(hue, sat * 0.16f, 23.0f);

        onSurface = 0xFFF4F1FA;
        onSurfaceVariant = 0xFF9E9AA9;

        outline = toneColor(hue, sat * 0.18f, 48.0f);
        outlineVariant = toneColor(hue, sat * 0.16f, 24.0f);
        dividerColor = withAlpha(onSurface, 22);

        float tintRatio = (backgroundTintPercent / 100.0f) * 0.26f;
        int alpha = frostedGlass ? 236 : 255;

        panelBackground = withAlpha(blendColors(0xFF14131B, primaryContainer, tintRatio), alpha);
        sidebarBackground = withAlpha(blendColors(0xFF121118, primaryContainer, tintRatio * 0.7f), alpha);
        moduleCardOffBackground = withAlpha(blendColors(0xFF1E1D27, primaryContainer, tintRatio * 0.35f), 235);
        moduleCardOnBackground = withAlpha(blendColors(0xFF3E3460, primaryContainer, 0.58f), 245);
        searchBarBackground = withAlpha(blendColors(0xFF1D1C25, primaryContainer, tintRatio * 0.4f), 245);
        activeTabPillColor = withAlpha(blendColors(0xFF3E3758, primary, 0.25f), 255);
    }

    private static int toneColor(float hue, float saturation, float value0to100) {
        float s = Math.max(0.0f, Math.min(1.0f, saturation));
        float v = Math.max(0.0f, Math.min(1.0f, value0to100 / 100.0f));
        return Color.HSVToColor(255, new float[]{hue, s, v});
    }

    public static int blendColors(int colorA, int colorB, float ratioB) {
        float rB = Math.max(0.0f, Math.min(1.0f, ratioB));
        float rA = 1.0f - rB;
        int a = Math.round(Color.alpha(colorA) * rA + Color.alpha(colorB) * rB);
        int r = Math.round(Color.red(colorA) * rA + Color.red(colorB) * rB);
        int g = Math.round(Color.green(colorA) * rA + Color.green(colorB) * rB);
        int b = Math.round(Color.blue(colorA) * rA + Color.blue(colorB) * rB);
        return Color.argb(a, r, g, b);
    }

    public static int withAlpha(int color, int alpha0to255) {
        return Color.argb(
                Math.max(0, Math.min(255, alpha0to255)),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }
}
