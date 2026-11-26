package EY.ONYX.PE.hud;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import EY.ONYX.PE.module.OnyxModuleManager;
import EY.ONYX.PE.module.OnyxModuleManager.OnyxModule;
import EY.ONYX.PE.setting.OnyxSettings.*;
import EY.ONYX.PE.theme.OnyxTheme;
import EY.ONYX.PE.ui.OnyxSettingViews;
import EY.ONYX.PE.ui.OnyxVectorIcons;
import EY.ONYX.PE.ui.OnyxVectorIcons.IconType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static EY.ONYX.PE.ui.OnyxSettingViews.dp;
import static EY.ONYX.PE.ui.OnyxSettingViews.roundedRect;

/**
 * Renders all HUD widgets (Dynamic Island Watermark, FPS & Ping, Keybinds, PotionHUD,
 * TargetHUD, Array List, Notifications), floating matte-Acrylic shortcut capsules,
 * and the interactive HUD layout and scale editor.
 */
public class OnyxHudOverlayView extends FrameLayout implements OnyxTheme.ThemeListener, OnyxModuleManager.ModuleEventListener {

    public interface OnExitEditorListener {
        void onExitHudEditor();
    }

    /**
     * Configuration parameters for a sub-element inside the TargetHUD card.
     */
    public static class SubElementConfig {
        public final String name;
        public boolean enabled = true;
        public String textTemplate = "";
        public String fontWeight = "Medium";
        public float thickness = 3.20f;
        public float size = 1.00f;
        public float offsetX = 0.00f;
        public float offsetY = 0.00f;
        public int color;
        public float glow = 0.00f;
        public boolean absorption = true;
        public boolean damageTrail = true;

        public SubElementConfig(String name, String defaultText, int defaultColor) {
            this.name = name;
            this.textTemplate = defaultText;
            this.color = defaultColor;
        }

        public void reset(String defaultText, int defaultColor) {
            this.enabled = true;
            this.textTemplate = defaultText;
            this.fontWeight = "Medium";
            this.thickness = 3.20f;
            this.size = 1.00f;
            this.offsetX = 0.00f;
            this.offsetY = 0.00f;
            this.color = defaultColor;
            this.glow = 0.00f;
            this.absorption = true;
            this.damageTrail = true;
        }
    }

    /**
     * Independent position and scale container for a single HUD widget.
     */
    private static class HudWidgetBox {
        final String moduleName;
        float x, y, baseW, baseH;
        float scale = 1.0f;
        final float defaultX, defaultY;

        HudWidgetBox(String moduleName, float x, float y, float baseW, float baseH) {
            this.moduleName = moduleName;
            this.x = x;
            this.y = y;
            this.baseW = baseW;
            this.baseH = baseH;
            this.defaultX = x;
            this.defaultY = y;
        }

        float getScaledW() { return baseW * scale; }
        float getScaledH() { return baseH * scale; }

        void resetPositionAndScale() {
            this.x = defaultX;
            this.y = defaultY;
            this.scale = 1.0f;
        }

        boolean contains(float px, float py) {
            return px >= x && px <= x + getScaledW() && py >= y && py <= y + getScaledH();
        }
    }

    /**
     * State model for a bottom-right module toggle notification card.
     */
    private static class ToastPopup {
        final String title;
        final String subtitle;
        final boolean enabledState;
        final long createdAt;
        final long durationMs = 2200L;
        float animatedY = -1f;

        ToastPopup(String title, String subtitle, boolean enabledState) {
            this.title = title;
            this.subtitle = subtitle;
            this.enabledState = enabledState;
            this.createdAt = SystemClock.uptimeMillis();
        }
    }

    /**
     * Per-module animation state for smooth entry and exit in the top-right Array List.
     */
    private static class ArrayListAnimState {
        float progress = 0f;
        float currentY = -1f;
        long lastFrameTime = 0L;
    }

    /**
     * State for a floating matte-Acrylic shortcut capsule button.
     */
    private static class ShortcutCapsuleState {
        final OnyxModule module;
        final RectF bounds = new RectF();
        float activeProgress;
        ValueAnimator fillAnimator;

        ShortcutCapsuleState(OnyxModule module) {
            this.module = module;
            this.activeProgress = module.isEnabled() ? 1f : 0f;
        }
    }

    private final OnyxModuleManager moduleManager;
    private final OnyxTheme theme;
    private final OnExitEditorListener exitEditorListener;

    private final HudCanvasLayer canvasLayer;
    private LinearLayout rightInspectorWrapper;
    private LinearLayout inspectorContent;
    private String selectedSubElement = "Avatar";
    private final Map<String, SubElementConfig> targetHudElements = new LinkedHashMap<>();

    private boolean editorMode = false;
    private OnyxModule singleEditingModule = null;
    private String selectedWidgetName = "TargetHUD";
    private boolean whiteCanvasBackground = true;

    // 6 Strictly Independent HUD Widget Boxes
    private HudWidgetBox fpsPingBox;
    private HudWidgetBox dynamicIslandBox;
    private HudWidgetBox keybindsBox;
    private HudWidgetBox potionHudBox;
    private HudWidgetBox targetHudBox;
    private HudWidgetBox notificationsBox;
    private HudWidgetBox draggingBox = null;
    private float dragDx, dragDy;

    // Apple-Curve Dynamic Island reactive state
    private String islandAlertModule = null;
    private boolean islandAlertState = false;
    private float islandExpandProgress = 0f;
    private float islandContentProgress = 0f;
    private float islandHoldProgress = 0f;
    private float islandQBounceScaleX = 1f;
    private float islandQBounceScaleY = 1f;
    private float islandAbsorbOffsetY = 0f;
    private float islandAbsorbGlow = 0f;
    private long islandForceVisibleUntil = 0L;
    private ValueAnimator islandAnimator;
    private ValueAnimator islandAbsorbAnimator;

    // Bottom-Right Notifications list
    private final List<ToastPopup> activeToasts = new ArrayList<>();

    // Top-Right Array List smooth entry/exit state map
    private final Map<String, ArrayListAnimState> arrayListAnimMap = new LinkedHashMap<>();

    // Interactive keybind hit areas on the Keybinds HUD widget during normal mode
    private final Map<OnyxModule, RectF> keybindRowHitRects = new LinkedHashMap<>();
    private OnyxModule activeHoldModule = null;

    // Mobile Floating Matte-Acrylic Shortcut Capsule Buttons
    private static final long SHORTCUT_LONG_PRESS_MS = 480L;
    private final Map<String, ShortcutCapsuleState> shortcutStates = new LinkedHashMap<>();
    private ShortcutCapsuleState pressedShortcut = null;
    private boolean draggingShortcut = false;
    private float shortcutDownX, shortcutDownY;
    private float shortcutDragOffsetX, shortcutDragOffsetY;
    private final Runnable shortcutLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (pressedShortcut != null && !draggingShortcut) {
                draggingShortcut = true;
                if (canvasLayer != null) canvasLayer.invalidate();
            }
        }
    };

    public OnyxHudOverlayView(Context context, OnyxModuleManager moduleManager, OnyxTheme theme,
                              OnExitEditorListener exitEditorListener) {
        super(context);
        this.moduleManager = moduleManager;
        this.theme = theme;
        this.exitEditorListener = exitEditorListener;

        this.theme.addListener(this);
        this.moduleManager.addListener(this);

        initTargetHudSubElements();

        canvasLayer = new HudCanvasLayer(context);
        addView(canvasLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildRightElementsInspector(context);
    }

    private void initTargetHudSubElements() {
        targetHudElements.put("Avatar", new SubElementConfig("Avatar", "", 0xFF1F1E2A));
        targetHudElements.put("Name", new SubElementConfig("Name", "{name}", 0xFFF2EFF7));
        targetHudElements.put("Health", new SubElementConfig("Health", "{health}", 0xFF9C86FF));
        targetHudElements.put("Distance", new SubElementConfig("Distance", "{distance}m", 0xFFD6D2E4));
        targetHudElements.put("Status", new SubElementConfig("Status", "{status}", 0xFFD6D2E4));
        targetHudElements.put("Health bar", new SubElementConfig("Health bar", "", 0xFF9C86FF));
    }

    public void setWhiteCanvasBackground(boolean whiteCanvasBackground) {
        this.whiteCanvasBackground = whiteCanvasBackground;
        if (canvasLayer != null) canvasLayer.invalidate();
    }

        public float getDynamicIslandCenterX() {
        if (dynamicIslandBox != null) {
            return dynamicIslandBox.x + dynamicIslandBox.getScaledW() / 2f;
        }
        return getWidth() > 0 ? getWidth() / 2f : dp(getContext(), 360);
    }

    public float getDynamicIslandCenterY() {
        if (dynamicIslandBox != null) {
            return dynamicIslandBox.y + dynamicIslandBox.getScaledH() / 2f;
        }
        return dp(getContext(), 25);
    }

    /**
     * Triggers an elastic Q-bounce and luminous pulse on the Dynamic Island when the main panel
     * streams into it during Mode 4 (Dynamic Island Flow / 灵动聚散).
     *
     * @param strength user-configured bounce multiplier (0.2x .. 2.5x)
     */
    public void triggerDynamicIslandAbsorbBounce(final float strength) {
        islandForceVisibleUntil = SystemClock.uptimeMillis() + 950L;
        if (islandAbsorbAnimator != null) {
            islandAbsorbAnimator.cancel();
        }
        final float amp = Math.max(0.25f, Math.min(2.5f, strength));
        islandAbsorbAnimator = ValueAnimator.ofFloat(0f, 1f);
        islandAbsorbAnimator.setDuration(680L);
        islandAbsorbAnimator.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            // Damped elastic spring oscillation upon absorbing the panel stream
            float env = (float) Math.exp(-4.6f * t);
            float wave = (float) Math.sin(t * Math.PI * 3.6f);
            islandQBounceScaleX = 1.0f + 0.30f * amp * env * wave;
            islandQBounceScaleY = 1.0f - 0.22f * amp * env * wave;
            islandAbsorbOffsetY = -dp(getContext(), 7.5f) * amp * env * (float) Math.sin(t * Math.PI * 2.4f);
            islandAbsorbGlow = (float) Math.sin(t * Math.PI) * (1.0f - t * 0.4f);
            if (canvasLayer != null) canvasLayer.invalidate();
        });
        islandAbsorbAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                islandQBounceScaleX = 1f;
                islandQBounceScaleY = 1f;
                islandAbsorbOffsetY = 0f;
                islandAbsorbGlow = 0f;
                if (canvasLayer != null) canvasLayer.invalidate();
            }
        });
        islandAbsorbAnimator.start();
    }

    public boolean isEditorMode() {
        return editorMode;
    }

    /**
     * Enters the HUD layout editor.
     *
     * @param module specific HUD module to edit exclusively, or null to open the general HUDEditor
     */
    public void enterEditorMode(OnyxModule module) {
        this.editorMode = true;
        this.singleEditingModule = module;
        if (module != null) {
            this.selectedWidgetName = module.getName();
        } else {
            this.selectedWidgetName = "TargetHUD";
        }
        rightInspectorWrapper.setVisibility(View.VISIBLE);
        rebuildInspectorControls();
        canvasLayer.invalidate();
    }

    public void exitEditorMode() {
        this.editorMode = false;
        this.singleEditingModule = null;
        rightInspectorWrapper.setVisibility(View.GONE);
        canvasLayer.invalidate();
        if (exitEditorListener != null) {
            exitEditorListener.onExitHudEditor();
        }
    }

    /**
     * Maps a HUD module name to its strictly independent HudWidgetBox.
     * Watermark (Dynamic Island) and FPS & Ping are completely separate!
     */
    private HudWidgetBox getWidgetBoxByName(String name) {
        if ("Watermark".equalsIgnoreCase(name)) return dynamicIslandBox;
        if ("Stats".equalsIgnoreCase(name)) return fpsPingBox;
        if ("Keybinds".equalsIgnoreCase(name)) return keybindsBox;
        if ("PotionHUD".equalsIgnoreCase(name)) return potionHudBox;
        if ("Notifications".equalsIgnoreCase(name)) return notificationsBox;
        return targetHudBox;
    }

    private void resetCurrentEditedWidgets() {
        if (singleEditingModule != null) {
            String mName = singleEditingModule.getName();
            HudWidgetBox b = getWidgetBoxByName(mName);
            if (b != null) b.resetPositionAndScale();
            if ("TargetHUD".equalsIgnoreCase(mName)) {
                resetTargetHudSubElements();
            }
        } else {
            if (fpsPingBox != null) fpsPingBox.resetPositionAndScale();
            if (dynamicIslandBox != null) dynamicIslandBox.resetPositionAndScale();
            if (keybindsBox != null) keybindsBox.resetPositionAndScale();
            if (potionHudBox != null) potionHudBox.resetPositionAndScale();
            if (targetHudBox != null) targetHudBox.resetPositionAndScale();
            if (notificationsBox != null) notificationsBox.resetPositionAndScale();
            resetTargetHudSubElements();
        }
        rebuildInspectorControls();
        canvasLayer.invalidate();
    }

    private void resetTargetHudSubElements() {
        targetHudElements.get("Avatar").reset("", 0xFF1F1E2A);
        targetHudElements.get("Name").reset("{name}", 0xFFF2EFF7);
        targetHudElements.get("Health").reset("{health}", 0xFF9C86FF);
        targetHudElements.get("Distance").reset("{distance}m", 0xFFD6D2E4);
        targetHudElements.get("Status").reset("{status}", 0xFFD6D2E4);
        targetHudElements.get("Health bar").reset("", 0xFF9C86FF);
    }

    private void buildRightElementsInspector(Context ctx) {
        rightInspectorWrapper = new LinearLayout(ctx);
        rightInspectorWrapper.setOrientation(LinearLayout.VERTICAL);
        rightInspectorWrapper.setVisibility(View.GONE);

        ScrollView inspectorScroll = new ScrollView(ctx);
        inspectorScroll.setVerticalScrollBarEnabled(false);
        inspectorScroll.setHorizontalScrollBarEnabled(false);
        inspectorScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        inspectorScroll.setBackground(roundedRect(0xF217161F, 16, dp(ctx, 1), 0xFF2E2C3B, ctx));

        inspectorContent = new LinearLayout(ctx);
        inspectorContent.setOrientation(LinearLayout.VERTICAL);
        inspectorContent.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14));
        inspectorScroll.addView(inspectorContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rightInspectorWrapper.addView(inspectorScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout bottomBtns = new LinearLayout(ctx);
        bottomBtns.setOrientation(LinearLayout.HORIZONTAL);
        bottomBtns.setGravity(Gravity.CENTER_VERTICAL);
        bottomBtns.setPadding(0, dp(ctx, 8), 0, 0);

        TextView resetBtn = new TextView(ctx);
        resetBtn.setText("Reset");
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        resetBtn.setTypeface(Typeface.DEFAULT_BOLD);
        resetBtn.setTextColor(0xFFE6E2F0);
        resetBtn.setGravity(Gravity.CENTER);
        resetBtn.setPadding(0, dp(ctx, 9), 0, dp(ctx, 9));
        resetBtn.setBackground(roundedRect(0xCC1B1A24, 18, dp(ctx, 1.2f), 0xFF565264, ctx));
        resetBtn.setOnClickListener(v -> resetCurrentEditedWidgets());

        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        rLp.rightMargin = dp(ctx, 8);
        bottomBtns.addView(resetBtn, rLp);

        TextView doneBtn = new TextView(ctx);
        doneBtn.setText("Done");
        doneBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        doneBtn.setTypeface(Typeface.DEFAULT_BOLD);
        doneBtn.setTextColor(0xFF181426);
        doneBtn.setGravity(Gravity.CENTER);
        doneBtn.setPadding(0, dp(ctx, 9), 0, dp(ctx, 9));
        doneBtn.setBackground(roundedRect(theme.primary, 18, 0, 0, ctx));
        doneBtn.setOnClickListener(v -> exitEditorMode());

        bottomBtns.addView(doneBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        rightInspectorWrapper.addView(bottomBtns);

        FrameLayout.LayoutParams wrapperLp = new FrameLayout.LayoutParams(
                dp(ctx, 236), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        wrapperLp.topMargin = dp(ctx, 12);
        wrapperLp.bottomMargin = dp(ctx, 12);
        wrapperLp.rightMargin = dp(ctx, 12);
        addView(rightInspectorWrapper, wrapperLp);
    }

    /**
     * Populates the right-side inspector controls.
     * Each widget (Watermark, FPS & Ping, TargetHUD, Keybinds, PotionHUD, Notifications)
     * has its own independent Scale slider.
     */
    private void rebuildInspectorControls() {
        Context ctx = getContext();
        inspectorContent.removeAllViews();

        boolean isGeneralEditor = (singleEditingModule == null);
        String activeModuleName = isGeneralEditor ? selectedWidgetName : singleEditingModule.getName();

        if (isGeneralEditor) {
            TextView wHeader = new TextView(ctx);
            wHeader.setText("HUD Widgets");
            wHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            wHeader.setTypeface(Typeface.DEFAULT_BOLD);
            wHeader.setTextColor(theme.onSurface);
            inspectorContent.addView(wHeader);

            String[][] widgetGroups = new String[][]{
                    {"Watermark", "Stats"},
                    {"TargetHUD", "Keybinds"},
                    {"PotionHUD", "Notifications"}
            };
            for (String[] grp : widgetGroups) {
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(ctx, 6);
                for (final String wName : grp) {
                    boolean active = wName.equalsIgnoreCase(selectedWidgetName);
                    TextView chip = new TextView(ctx);
                    chip.setText(wName);
                    chip.setSingleLine(true);
                    chip.setGravity(Gravity.CENTER);
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                    chip.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                    chip.setTextColor(active ? theme.onSurface : theme.onSurfaceVariant);
                    chip.setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6));
                    chip.setBackground(roundedRect(active ? theme.activeTabPillColor : 0xFF201E29, 12, 0, 0, ctx));
                    chip.setOnClickListener(v -> {
                        selectedWidgetName = wName;
                        rebuildInspectorControls();
                        canvasLayer.invalidate();
                    });
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    clp.rightMargin = dp(ctx, 5);
                    row.addView(chip, clp);
                }
                inspectorContent.addView(row, rlp);
            }
            inspectorContent.addView(createInspectorDivider(ctx));
        }

        // Independent Scale slider for the currently selected widget ONLY!
        final HudWidgetBox activeBox = getWidgetBoxByName(activeModuleName);
        if (activeBox != null) {
            NumberSliderSetting scaleSetting = new NumberSliderSetting(
                    activeModuleName + " Scale",
                    "Resize this widget independently",
                    activeBox.scale, 0.50f, 2.00f, 0.05f, "x"
            );
            scaleSetting.setChangeListener(() -> {
                activeBox.scale = scaleSetting.getValue();
                canvasLayer.invalidate();
            });
            inspectorContent.addView(OnyxSettingViews.createSettingRowView(ctx, scaleSetting, theme, null));
            inspectorContent.addView(createInspectorDivider(ctx));
        }

        // TargetHUD Elements Inspector
        if ("TargetHUD".equalsIgnoreCase(activeModuleName)) {
            TextView title = new TextView(ctx);
            title.setText("Elements");
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(theme.onSurface);
            inspectorContent.addView(title);

            String[][] rows = new String[][]{
                    {"Avatar", "Name", "Health"},
                    {"Distance", "Status", "Health bar"}
            };
            for (String[] rowItems : rows) {
                LinearLayout r = new LinearLayout(ctx);
                r.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(ctx, 7);
                for (final String elName : rowItems) {
                    boolean active = elName.equals(selectedSubElement);
                    TextView pill = new TextView(ctx);
                    pill.setText(elName);
                    pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                    pill.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                    pill.setTextColor(active ? theme.onSurface : theme.onSurfaceVariant);
                    pill.setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5));
                    pill.setBackground(roundedRect(active ? theme.activeTabPillColor : 0xFF201E29, 12, 0, 0, ctx));
                    pill.setOnClickListener(v -> {
                        selectedSubElement = elName;
                        rebuildInspectorControls();
                        canvasLayer.invalidate();
                    });
                    LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    plp.rightMargin = dp(ctx, 5);
                    r.addView(pill, plp);
                }
                inspectorContent.addView(r, rlp);
            }

            final SubElementConfig cfg = targetHudElements.get(selectedSubElement);
            if (cfg == null) return;

            inspectorContent.addView(createInspectorDivider(ctx));

            BooleanSetting enabledProxy = new BooleanSetting("Enabled", "", cfg.enabled);
            enabledProxy.setChangeListener(() -> {
                cfg.enabled = enabledProxy.getValue();
                canvasLayer.invalidate();
            });
            inspectorContent.addView(OnyxSettingViews.createSettingRowView(ctx, enabledProxy, theme, null));
            inspectorContent.addView(createInspectorDivider(ctx));

            if ("Health bar".equals(selectedSubElement)) {
                NumberSliderSetting thickProxy = new NumberSliderSetting("Thickness", "", cfg.thickness, 1.50f, 8.00f, 0.25f, "");
                thickProxy.setChangeListener(() -> {
                    cfg.thickness = thickProxy.getValue();
                    canvasLayer.invalidate();
                });
                inspectorContent.addView(OnyxSettingViews.createSettingRowView(ctx, thickProxy, theme, null));
                inspectorContent.addView(createInspectorDivider(ctx));
            } else {
                NumberSliderSetting sizeProxy = new NumberSliderSetting("Size", "", cfg.size, 0.60f, 1.60f, 0.05f, "x");
                sizeProxy.setChangeListener(() -> {
                    cfg.size = sizeProxy.getValue();
                    canvasLayer.invalidate();
                });
                inspectorContent.addView(OnyxSettingViews.createSettingRowView(ctx, sizeProxy, theme, null));
                inspectorContent.addView(createInspectorDivider(ctx));
            }

            NumberSliderSetting oxProxy = new NumberSliderSetting("Offset X", "", cfg.offsetX, -24.00f, 24.00f, 0.50f, "");
            oxProxy.setChangeListener(() -> {
                cfg.offsetX = oxProxy.getValue();
                canvasLayer.invalidate();
            });
            inspectorContent.addView(OnyxSettingViews.createSettingRowView(ctx, oxProxy, theme, null));
            inspectorContent.addView(createInspectorDivider(ctx));

            NumberSliderSetting oyProxy = new NumberSliderSetting("Offset Y", "", cfg.offsetY, -24.00f, 24.00f, 0.50f, "");
            oyProxy.setChangeListener(() -> {
                cfg.offsetY = oyProxy.getValue();
                canvasLayer.invalidate();
            });
            inspectorContent.addView(OnyxSettingViews.createSettingRowView(ctx, oyProxy, theme, null));
            inspectorContent.addView(createInspectorDivider(ctx));

            ColorPickerSetting colProxy = new ColorPickerSetting("Colour", "", cfg.color);
            colProxy.setChangeListener(() -> {
                cfg.color = colProxy.getArgbColor();
                canvasLayer.invalidate();
            });
            inspectorContent.addView(OnyxSettingViews.createSettingRowView(ctx, colProxy, theme, null));
        }
    }

    private View createInspectorDivider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(theme.dividerColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1));
        lp.topMargin = dp(ctx, 2);
        lp.bottomMargin = dp(ctx, 2);
        v.setLayoutParams(lp);
        return v;
    }

    /**
     * Checks whether a given HUD module widget should be rendered in the current frame.
     */
    private boolean shouldRenderHudWidget(String moduleName) {
        if (editorMode) {
            if (singleEditingModule != null) {
                return singleEditingModule.getName().equalsIgnoreCase(moduleName);
            }
            return true;
        }
        if ("Watermark".equalsIgnoreCase(moduleName) && SystemClock.uptimeMillis() < islandForceVisibleUntil) {
            return true;
        }
        OnyxModule m = moduleManager.getModule(moduleName);
        return m != null && m.isEnabled();
    }

    private class HudCanvasLayer extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF innerRect = new RectF();
        private final Path path = new Path();
        private int liveFps = 60;
        private int fpsFrameCount = 0;
        private long lastFpsSampleTime = 0L;

        public HudCanvasLayer(Context context) {
            super(context);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            subTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (fpsPingBox == null) {
                fpsPingBox = new HudWidgetBox("Stats", dp(getContext(), 12), dp(getContext(), 10), dp(getContext(), 158), dp(getContext(), 28));
                dynamicIslandBox = new HudWidgetBox("Watermark", w / 2f - dp(getContext(), 48), dp(getContext(), 10), dp(getContext(), 96), dp(getContext(), 30));
                keybindsBox = new HudWidgetBox("Keybinds", dp(getContext(), 14), Math.min(dp(getContext(), 96), h * 0.25f), dp(getContext(), 118), dp(getContext(), 84));
                potionHudBox = new HudWidgetBox("PotionHUD", dp(getContext(), 14), Math.max(dp(getContext(), 185), h * 0.52f), dp(getContext(), 138), dp(getContext(), 64));
                targetHudBox = new HudWidgetBox("TargetHUD", w / 2f - dp(getContext(), 84), h * 0.16f, dp(getContext(), 168), dp(getContext(), 50));
                notificationsBox = new HudWidgetBox("Notifications", w - dp(getContext(), 188), h - dp(getContext(), 62), dp(getContext(), 174), dp(getContext(), 44));
            } else if (w > 0 && h > 0) {
                clampBoxToScreen(fpsPingBox, w, h);
                clampBoxToScreen(dynamicIslandBox, w, h);
                clampBoxToScreen(keybindsBox, w, h);
                clampBoxToScreen(potionHudBox, w, h);
                clampBoxToScreen(targetHudBox, w, h);
                clampBoxToScreen(notificationsBox, w, h);
            }
        }

        private void clampBoxToScreen(HudWidgetBox b, int w, int h) {
            if (b == null) return;
            b.x = Math.max(dp(getContext(), 4), Math.min(w - b.getScaledW() - dp(getContext(), 4), b.x));
            b.y = Math.max(dp(getContext(), 4), Math.min(h - b.getScaledH() - dp(getContext(), 4), b.y));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();

            fpsFrameCount++;
            long nowMs = SystemClock.uptimeMillis();
            if (nowMs - lastFpsSampleTime >= 1000L) {
                liveFps = Math.max(12, Math.min(240, fpsFrameCount));
                fpsFrameCount = 0;
                lastFpsSampleTime = nowMs;
            }

            if (whiteCanvasBackground) {
                canvas.drawColor(Color.WHITE);
            }
            if (!editorMode) {
                drawActiveRenderVisuals(canvas, w, h, nowMs);
            }
            if (editorMode) {
                canvas.drawColor(0x2E000000);
            }

            // 1. FPS & Ping Pill (Strictly independent from Watermark / Dynamic Island!)
            if (shouldRenderHudWidget("Stats")) {
                drawFpsPingPill(canvas, fpsPingBox);
            }

            // 2. Watermark: Top-Center Dynamic Island (Strictly independent from FPS & Ping!)
            if (shouldRenderHudWidget("Watermark")) {
                drawDynamicIsland(canvas, dynamicIslandBox);
            }

            // 3. Keybinds Card
            if (shouldRenderHudWidget("Keybinds")) {
                drawKeybindsCard(canvas, keybindsBox);
            } else {
                keybindRowHitRects.clear();
            }

            // 4. PotionHUD Card
            if (shouldRenderHudWidget("PotionHUD")) {
                drawPotionHud(canvas, potionHudBox);
            }

            // 5. TargetHUD Card (Clean, non-overlapping layout at its actual screen position)
            if (shouldRenderHudWidget("TargetHUD")) {
                drawTargetHudCard(canvas, targetHudBox);
            }

            // 6. Right-Edge Array List with Smooth Slide/Fade/Scale Entry & Exit Animations
            if (!editorMode) {
                OnyxModule alMod = moduleManager.getModule("Array List");
                if (alMod != null && alMod.isEnabled()) {
                    drawRightEdgeArrayList(canvas, w);
                }
            }

            // 7. Mobile Floating Matte-Acrylic Shortcut Capsule Buttons (Double border + inner flash)
            if (!editorMode) {
                drawMobileShortcutCapsules(canvas, w, h);
            }

            // 8. Bottom-Right Notifications (or Notifications Drag Preview in Editor Mode)
            if (editorMode && shouldRenderHudWidget("Notifications")) {
                drawNotificationsEditPreview(canvas, notificationsBox);
            } else if (!editorMode) {
                OnyxModule notifMod = moduleManager.getModule("Notifications");
                if (notifMod == null || notifMod.isEnabled()) {
                    drawBottomRightNotifications(canvas, w, h);
                }
            }

            if (theme.isDynamicCyclingActive()) {
                postInvalidateOnAnimation();
            }
        }

        /**
         * Renders the top-left FPS & Ping pill (1:1 matching image_77408a.png: "[speedometer] 12 fps · [wifi] 0 ms").
         * Dynamically measures every text segment prior to drawing the capsule background so "ms" never overflows
         * regardless of how long the FPS, username, or ping string becomes.
         */
        private void drawFpsPingPill(Canvas canvas, HudWidgetBox box) {
            if (box == null) return;
            OnyxModule fpsMod = moduleManager.getModule("Stats");
            boolean showUsername = fpsMod != null && fpsMod.getBool("Show username", false);
            boolean showPing = fpsMod == null || fpsMod.getBool("Show ping", true);

            float fontSize = dp(getContext(), 12.0f);
            textPaint.setTextSize(fontSize);
            subTextPaint.setTextSize(fontSize);

            String userStr = moduleManager.getCustomDisplayName();
            String fpsNumStr = String.valueOf(liveFps);
            String fpsUnitStr = " fps";
            String pingNumStr = "0";
            String pingUnitStr = " ms";

            float padL = dp(getContext(), 12);
            float padR = dp(getContext(), 13);
            float iconSz = dp(getContext(), 15);
            float iconTextGap = dp(getContext(), 5.5f);
            float dotSectionW = dp(getContext(), 15);

            float totalW = padL;
            float userW = 0f;
            if (showUsername && userStr != null && !userStr.isEmpty()) {
                userW = textPaint.measureText(userStr);
                totalW += iconSz + iconTextGap + userW + dotSectionW;
            }

            float wFpsNum = textPaint.measureText(fpsNumStr);
            float wFpsUnit = subTextPaint.measureText(fpsUnitStr);
            totalW += iconSz + iconTextGap + wFpsNum + wFpsUnit;

            float wPingNum = 0f;
            float wPingUnit = 0f;
            if (showPing) {
                wPingNum = textPaint.measureText(pingNumStr);
                wPingUnit = subTextPaint.measureText(pingUnitStr);
                totalW += dotSectionW + iconSz + iconTextGap + wPingNum + wPingUnit;
            }
            totalW += padR;

            box.baseW = totalW;
            box.baseH = dp(getContext(), 28);

            // Keep pill inside screen width even if parameters grow long
            int screenW = getWidth();
            if (screenW > 0 && box.x + box.getScaledW() > screenW - dp(getContext(), 4)) {
                box.x = Math.max(dp(getContext(), 4), screenW - box.getScaledW() - dp(getContext(), 4));
            }

            canvas.save();
            canvas.translate(box.x, box.y);
            canvas.scale(box.scale, box.scale);

            float r = box.baseH / 2f;
            rect.set(0, 0, box.baseW, box.baseH);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xEE221F2D);
            canvas.drawRoundRect(rect, r, r, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.0f));
            paint.setColor(0x28FFFFFF);
            canvas.drawRoundRect(rect, r, r, paint);

            if (editorMode) {
                drawEditorWhiteOutline(canvas, 0, 0, box.baseW, box.baseH, r,
                        "Stats".equalsIgnoreCase(selectedWidgetName));
            }

            float cy = box.baseH / 2f;
            float baseline = cy + dp(getContext(), 4.2f);
            float curX = padL;

            if (showUsername && userW > 0f) {
                OnyxVectorIcons.drawIcon(canvas, IconType.PLAYER,
                        curX, cy - iconSz / 2f, iconSz, iconSz,
                        0xFFB8B4C6, paint, path, rect);
                curX += iconSz + iconTextGap;

                textPaint.setColor(0xFFF4F1FA);
                canvas.drawText(userStr, curX, baseline, textPaint);
                curX += userW;

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF4E4A5E);
                canvas.drawCircle(curX + dotSectionW / 2f, cy, dp(getContext(), 1.5f), paint);
                curX += dotSectionW;
            }

            // Speedometer icon + bold FPS number + regular " fps"
            OnyxVectorIcons.drawIcon(canvas, IconType.SPEEDOMETER,
                    curX, cy - iconSz / 2f, iconSz, iconSz,
                    0xFFB8B4C6, paint, path, rect);
            curX += iconSz + iconTextGap;

            textPaint.setColor(0xFFF4F1FA);
            canvas.drawText(fpsNumStr, curX, baseline, textPaint);
            curX += wFpsNum;

            subTextPaint.setColor(0xFFB4B0C4);
            canvas.drawText(fpsUnitStr, curX, baseline, subTextPaint);
            curX += wFpsUnit;

            if (showPing) {
                // Subtle middle separator dot
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF4E4A5E);
                canvas.drawCircle(curX + dotSectionW / 2f, cy, dp(getContext(), 1.5f), paint);
                curX += dotSectionW;

                // Wi-Fi ping icon + bold ping number + regular " ms"
                OnyxVectorIcons.drawIcon(canvas, IconType.WIFI_PING,
                        curX, cy - iconSz / 2f, iconSz, iconSz,
                        0xFFB8B4C6, paint, path, rect);
                curX += iconSz + iconTextGap;

                textPaint.setColor(0xFFF4F1FA);
                canvas.drawText(pingNumStr, curX, baseline, textPaint);
                curX += wPingNum;

                subTextPaint.setColor(0xFFB4B0C4);
                canvas.drawText(pingUnitStr, curX, baseline, subTextPaint);
            }

            canvas.restore();
        }

        /**
         * Renders the top-center Dynamic Island (Watermark) with all authentic states:
         * 1. HUD Edit mode: "[planet] Drag to move" with crisp white outline.
         * 2. Module Toggle Alert: "[planet] ModuleName [ON/OFF]" with Apple spring expansion.
         * 3. Scaffold Active: "[planet] Blocks   61" + bottom progress bar.
         * 4. BedBreaker Active: "[planet] Bed   Idle" or "[planet] Wooden Planks  32%" + progress bar.
         * 5. Default Idle: "[planet] onyx".
         */
        private void drawDynamicIsland(Canvas canvas, HudWidgetBox box) {
            if (box == null) return;
            float exp = Math.max(0f, islandExpandProgress);
            OnyxModule scafMod = moduleManager.getModule("Scaffold");
            OnyxModule bbMod = moduleManager.getModule("BedBreaker");

            OnyxModule wmMod = moduleManager.getModule("Watermark");
            boolean allowScaffold = wmMod == null || wmMod.getBool("Scaffold block counter", true);
            boolean allowBedBreaker = wmMod == null || wmMod.getBool("BedBreaker status", true);

            boolean inEdit = editorMode;
            boolean showScaffold = !inEdit && allowScaffold && (exp < 0.05f) && scafMod != null && scafMod.isEnabled();
            boolean showBedBreaker = !inEdit && allowBedBreaker && !showScaffold && (exp < 0.05f) && bbMod != null && bbMod.isEnabled();

            float baseW = dp(getContext(), 84);
            float baseH = dp(getContext(), 30);
            if (inEdit) {
                baseW = dp(getContext(), 124);
            } else if (showScaffold) {
                baseW = dp(getContext(), 122);
                baseH = dp(getContext(), 32);
            } else if (showBedBreaker) {
                baseW = dp(getContext(), 112);
                baseH = dp(getContext(), 30);
            }

            textPaint.setTextSize(dp(getContext(), 12f));
            String modName = (islandAlertModule == null || islandAlertModule.isEmpty()) ? "onyx" : islandAlertModule;
            float modTextW = textPaint.measureText(modName);
            float badgeW = dp(getContext(), 34);
            float expandedTargetW = Math.max(dp(getContext(), 148), dp(getContext(), 42) + modTextW + badgeW + dp(getContext(), 16));
            float expandedTargetH = dp(getContext(), 32);

            float pillW = (baseW + (expandedTargetW - baseW) * exp) * islandQBounceScaleX;
            float pillH = (baseH + (expandedTargetH - baseH) * exp) * islandQBounceScaleY;

            // Anchor horizontal center so the island expands symmetrically around its center X
            float cx = box.x + box.baseW * box.scale / 2f;
            box.baseW = pillW;
            box.baseH = pillH;
            box.x = cx - (pillW * box.scale) / 2f;

            canvas.save();
            canvas.translate(box.x, box.y + islandAbsorbOffsetY);
            canvas.scale(box.scale, box.scale);

            if (islandAbsorbGlow > 0.01f) {
                float glowSpread = dp(getContext(), 10f) * islandAbsorbGlow;
                rect.set(-glowSpread, -glowSpread * 0.6f, pillW + glowSpread, pillH + glowSpread * 0.6f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(OnyxTheme.withAlpha(theme.getDynamicColor(0f), Math.round(135 * islandAbsorbGlow)));
                canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint);
            }

            float cy = pillH / 2f;
            int dynAccent = theme.getDynamicColor(0.0f);

            rect.set(0, 0, pillW, pillH);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xEE221F2D);
            canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.0f));
            paint.setColor(0x28FFFFFF);
            canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, paint);

            if (inEdit) {
                drawEditorWhiteOutline(canvas, 0, 0, pillW, pillH, pillH / 2f,
                        "Watermark".equalsIgnoreCase(selectedWidgetName));
            }

            canvas.save();
            path.reset();
            path.addRoundRect(rect, pillH / 2f, pillH / 2f, Path.Direction.CW);
            canvas.clipPath(path);

            OnyxVectorIcons.drawIcon(canvas, IconType.PLANET,
                    dp(getContext(), 10), cy - dp(getContext(), 9),
                    dp(getContext(), 18), dp(getContext(), 18),
                    dynAccent, paint, path, rect);

            if (inEdit) {
                textPaint.setTextSize(dp(getContext(), 11.5f));
                textPaint.setColor(0xFFF2EFF7);
                canvas.drawText("Drag to move", dp(getContext(), 34), cy + dp(getContext(), 4f), textPaint);
            } else {
                // Idle / Scaffold / BedBreaker content when not expanded by a toggle alert
                if (exp < 0.80f) {
                    int idleAlpha = Math.round(255 * Math.max(0f, 1f - exp * 1.55f));
                    if (showScaffold) {
                        textPaint.setTextSize(dp(getContext(), 11f));
                        textPaint.setColor(OnyxTheme.withAlpha(0xFFD0CCDE, idleAlpha));
                        canvas.drawText("Blocks", dp(getContext(), 34), dp(getContext(), 15), textPaint);
                        textPaint.setColor(OnyxTheme.withAlpha(0xFFFFFFFF, idleAlpha));
                        canvas.drawText("61", pillW - dp(getContext(), 24), dp(getContext(), 15), textPaint);

                        // Track + Progress bar under "Blocks  61"
                        float barL = dp(getContext(), 34);
                        float barR = pillW - dp(getContext(), 10);
                        rect.set(barL, dp(getContext(), 21), barR, dp(getContext(), 24.5f));
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(OnyxTheme.withAlpha(0xFF353244, idleAlpha));
                        canvas.drawRoundRect(rect, dp(getContext(), 2), dp(getContext(), 2), paint);

                        rect.set(barL, dp(getContext(), 21), barL + (barR - barL) * (61f / 64f), dp(getContext(), 24.5f));
                        paint.setColor(OnyxTheme.withAlpha(0xFFD8C8FF, idleAlpha));
                        canvas.drawRoundRect(rect, dp(getContext(), 2), dp(getContext(), 2), paint);
                    } else if (showBedBreaker) {
                        textPaint.setTextSize(dp(getContext(), 11.5f));
                        textPaint.setColor(OnyxTheme.withAlpha(0xFFF2EFF7, idleAlpha));
                        canvas.drawText("Bed", dp(getContext(), 35), cy + dp(getContext(), 4f), textPaint);
                        subTextPaint.setTextSize(dp(getContext(), 11.5f));
                        subTextPaint.setColor(OnyxTheme.withAlpha(0xFFA6A2B6, idleAlpha));
                        canvas.drawText("Idle", pillW - dp(getContext(), 30), cy + dp(getContext(), 4f), subTextPaint);
                    } else {
                        textPaint.setTextSize(dp(getContext(), 11.8f));
                        textPaint.setColor(OnyxTheme.withAlpha(0xFFE6E2F0, idleAlpha));
                        canvas.drawText("onyx", dp(getContext(), 35), cy + dp(getContext(), 4.2f), textPaint);
                    }
                }

                // Expanded Module Toggle Status ("[planet] ModuleName  [ON/OFF]")
                if (islandContentProgress > 0.02f) {
                    float cp = Math.max(0f, Math.min(1f, islandContentProgress));
                    int activeAlpha = Math.round(255 * cp);
                    float slideY = (1f - cp) * dp(getContext(), 6f);

                    textPaint.setTextSize(dp(getContext(), 11.8f));
                    textPaint.setColor(OnyxTheme.withAlpha(0xFFF5F2FC, activeAlpha));
                    canvas.drawText(modName, dp(getContext(), 34), cy + dp(getContext(), 4.2f) + slideY, textPaint);

                    String st = islandAlertState ? "ON" : "OFF";
                    float bh = dp(getContext(), 18);
                    float bx = pillW - badgeW - dp(getContext(), 7);
                    float by = cy - bh / 2f + slideY * 0.6f;
                    rect.set(bx, by, bx + badgeW, by + bh);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(OnyxTheme.withAlpha(islandAlertState ? dynAccent : 0xFF322F42, activeAlpha));
                    canvas.drawRoundRect(rect, bh / 2f, bh / 2f, paint);

                    textPaint.setTextSize(dp(getContext(), 9.8f));
                    textPaint.setColor(OnyxTheme.withAlpha(islandAlertState ? 0xFF181426 : 0xFFC8C4D8, activeAlpha));
                    float tw = textPaint.measureText(st);
                    canvas.drawText(st, bx + (badgeW - tw) / 2f, by + bh / 2f + dp(getContext(), 3.4f), textPaint);
                }
            }

            canvas.restore();
            canvas.restore();
        }

        /**
         * Renders the floating shortcut capsule buttons with:
         * - Matte greyish-translucent Acrylic plate background (flat, no wave undulation)
         * - Double border (outer border + inset inner border)
         * - Interior and inner border flashing/pulsing in sync with the main theme color when enabled
         */
        private void drawMobileShortcutCapsules(Canvas canvas, int screenW, int screenH) {
            boolean anyEnabled = false;
            int index = 0;
            float pulseWave = 0.5f + 0.5f * (float) Math.sin((SystemClock.uptimeMillis() % 60000L) * 0.0055);

            for (OnyxModule mod : moduleManager.getModules()) {
                KeybindSetting kb = mod.getKeybind();
                if (!kb.isShortcutButtonEnabled()) continue;

                ShortcutCapsuleState state = shortcutStates.get(mod.getName());
                if (state == null) {
                    state = new ShortcutCapsuleState(mod);
                    shortcutStates.put(mod.getName(), state);
                }
                if (state.activeProgress > 0.01f) {
                    anyEnabled = true;
                }

                textPaint.setTextSize(dp(getContext(), 13.0f));
                String label = mod.getName();
                float textW = textPaint.measureText(label);
                float capW = Math.max(dp(getContext(), 94), textW + dp(getContext(), 34));
                float capH = dp(getContext(), 36);

                if (kb.getShortcutX() < 0f || kb.getShortcutY() < 0f) {
                    int col = index / 5;
                    int row = index % 5;
                    float defX = dp(getContext(), 18) + col * dp(getContext(), 110);
                    float defY = dp(getContext(), 60) + row * dp(getContext(), 44);
                    kb.setShortcutPosition(defX, defY);
                }

                float left = Math.max(dp(getContext(), 6), Math.min(screenW - capW - dp(getContext(), 6), kb.getShortcutX()));
                float top = Math.max(dp(getContext(), 6), Math.min(screenH - capH - dp(getContext(), 6), kb.getShortcutY()));
                kb.setShortcutPosition(left, top);
                state.bounds.set(left, top, left + capW, top + capH);

                float cx = state.bounds.centerX();
                float cy = state.bounds.centerY();
                float r = capH / 2f;
                float p = Math.max(0f, Math.min(1f, state.activeProgress));

                int mainColor = theme.getDynamicColor(index * 0.12f);

                // 1. Matte Greyish-Translucent Acrylic Plate Base (semi-opaque smoky grey, no wave undulation)
                int matteAcrylicBase = 0xCC282732;
                int activeFlashTint = OnyxTheme.withAlpha(
                        OnyxTheme.blendColors(matteAcrylicBase, mainColor, 0.36f + 0.24f * pulseWave),
                        216
                );
                paint.reset();
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(OnyxTheme.blendColors(matteAcrylicBase, activeFlashTint, p));
                canvas.drawRoundRect(state.bounds, r, r, paint);

                // 2. Outer Border (Layer 1)
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(getContext(), 1.35f));
                int outerBorderCol = OnyxTheme.blendColors(
                        0x45FFFFFF,
                        OnyxTheme.withAlpha(mainColor, 235),
                        p
                );
                paint.setColor(outerBorderCol);
                canvas.drawRoundRect(state.bounds, r, r, paint);

                // 3. Second Inset Inner Border (Layer 2) flashing with main color
                float inset = dp(getContext(), 2.8f);
                innerRect.set(left + inset, top + inset, left + capW - inset, top + capH - inset);
                float innerR = innerRect.height() / 2f;
                paint.setStrokeWidth(dp(getContext(), 1.0f));
                int innerPulseColor = OnyxTheme.blendColors(mainColor, 0xFFFFFFFF, 0.25f + 0.35f * pulseWave);
                int innerBorderCol = OnyxTheme.blendColors(
                        0x1EFFFFFF,
                        OnyxTheme.withAlpha(innerPulseColor, Math.round(140 + 85 * pulseWave)),
                        p
                );
                paint.setColor(innerBorderCol);
                canvas.drawRoundRect(innerRect, innerR, innerR, paint);

                // 4. Centered Module Name Text
                textPaint.setTextSize(dp(getContext(), 13.0f));
                textPaint.setColor(OnyxTheme.blendColors(0xFFDAD6E8, 0xFFFFFFFF, p));
                canvas.drawText(label, cx - textW / 2f, cy + dp(getContext(), 4.5f), textPaint);

                index++;
            }

            if (anyEnabled) {
                postInvalidateOnAnimation();
            }
        }

        /**
         * Renders the Keybinds card (1:1 matching image_774085.png):
         * - Rounded dark violet-charcoal card (#EE221F2D) with subtle outline (#2CFFFFFF)
         * - Header with 10-dot+spacebar purple keyboard icon and "Keybinds" label
         * - Thin horizontal divider line separating the header from the keybind rows
         * - 28dp x 28dp rounded-square deep purple key badges (#3B3358) with centered key label
         */
        private void drawKeybindsCard(Canvas canvas, HudWidgetBox box) {
            if (box == null) return;
            keybindRowHitRects.clear();

            OnyxModule kbMod = moduleManager.getModule("Keybinds");
            boolean showOnlyActive = kbMod == null || kbMod.getBool("Show only active", true);

            List<OnyxModule> boundMods = new ArrayList<>();
            for (OnyxModule m : moduleManager.getModules()) {
                if ("ClickGUI".equalsIgnoreCase(m.getName())) continue;
                String kn = m.getKeybind().getKeyName();
                if (kn != null && !"None".equalsIgnoreCase(kn)) {
                    if (!showOnlyActive || m.isEnabled()) {
                        boundMods.add(m);
                    }
                }
            }

            if (boundMods.isEmpty() && !editorMode) {
                return;
            }

            textPaint.setTextSize(dp(getContext(), 12.2f));
            float maxModNameW = textPaint.measureText("Keybinds");
            for (OnyxModule m : boundMods) {
                float mw = textPaint.measureText(m.getName());
                if (mw > maxModNameW) maxModNameW = mw;
            }

            float badgeSize = dp(getContext(), 27);
            float rowGap = dp(getContext(), 7);
            float rowStep = badgeSize + rowGap;
            float headerH = dp(getContext(), 38);

            box.baseW = Math.max(dp(getContext(), 116), dp(getContext(), 12) + badgeSize + dp(getContext(), 12) + maxModNameW + dp(getContext(), 14));
            box.baseH = headerH + dp(getContext(), 8) + Math.max(1, boundMods.size()) * rowStep + dp(getContext(), 5);

            canvas.save();
            canvas.translate(box.x, box.y);
            canvas.scale(box.scale, box.scale);

            float cardR = dp(getContext(), 15);
            rect.set(0, 0, box.baseW, box.baseH);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xEE221F2D);
            canvas.drawRoundRect(rect, cardR, cardR, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.0f));
            paint.setColor(0x2CFFFFFF);
            canvas.drawRoundRect(rect, cardR, cardR, paint);

            if (editorMode) {
                drawEditorWhiteOutline(canvas, 0, 0, box.baseW, box.baseH, cardR,
                        "Keybinds".equalsIgnoreCase(selectedWidgetName));
            }

            int accentColor = theme.getDynamicColor(0f);
            OnyxVectorIcons.drawIcon(canvas, IconType.KEYBOARD,
                    dp(getContext(), 11), dp(getContext(), 10),
                    dp(getContext(), 19), dp(getContext(), 19),
                    accentColor, paint, path, rect);

            textPaint.setTextSize(dp(getContext(), 12.5f));
            textPaint.setColor(0xFFEAE6F4);
            canvas.drawText("Keybinds", dp(getContext(), 36), dp(getContext(), 24), textPaint);

            // Thin horizontal divider line underneath the header (1:1 image_774085.png)
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.0f));
            paint.setColor(0xFF2E2B3D);
            canvas.drawLine(dp(getContext(), 11), headerH, box.baseW - dp(getContext(), 11), headerH, paint);

            float startY = headerH + dp(getContext(), 8);
            for (int i = 0; i < boundMods.size(); i++) {
                OnyxModule m = boundMods.get(i);
                float ry = startY + i * rowStep;

                // Rounded-square deep purple key badge [ F ] / [ R ]
                float bx = dp(getContext(), 11);
                rect.set(bx, ry, bx + badgeSize, ry + badgeSize);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(m.isEnabled()
                        ? OnyxTheme.blendColors(0xFF3B3358, accentColor, 0.35f)
                        : 0xFF3B3358);
                canvas.drawRoundRect(rect, dp(getContext(), 6.5f), dp(getContext(), 6.5f), paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(getContext(), 0.9f));
                paint.setColor(0x35FFFFFF);
                canvas.drawRoundRect(rect, dp(getContext(), 6.5f), dp(getContext(), 6.5f), paint);

                textPaint.setTextSize(dp(getContext(), 11.5f));
                textPaint.setColor(0xFFEAE6F6);
                String kStr = m.getKeybind().getKeyName();
                if (kStr.length() > 3) kStr = kStr.substring(0, 3);
                float kw = textPaint.measureText(kStr);
                canvas.drawText(kStr, bx + (badgeSize - kw) / 2f, ry + badgeSize / 2f + dp(getContext(), 4.0f), textPaint);

                textPaint.setTextSize(dp(getContext(), 12.0f));
                textPaint.setColor(0xFFEAE6F4);
                canvas.drawText(m.getName(), bx + badgeSize + dp(getContext(), 12), ry + badgeSize / 2f + dp(getContext(), 4.2f), textPaint);

                keybindRowHitRects.put(m, new RectF(
                        box.x, box.y + ry * box.scale,
                        box.x + box.getScaledW(), box.y + (ry + badgeSize) * box.scale
                ));
            }
            canvas.restore();
        }

        private void drawPotionHud(Canvas canvas, HudWidgetBox box) {
            if (box == null) return;
            box.baseW = dp(getContext(), 138);
            box.baseH = dp(getContext(), 64);

            canvas.save();
            canvas.translate(box.x, box.y);
            canvas.scale(box.scale, box.scale);

            rect.set(0, 0, box.baseW, box.baseH);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xE8171620);
            canvas.drawRoundRect(rect, dp(getContext(), 12), dp(getContext(), 12), paint);

            if (editorMode) {
                drawEditorWhiteOutline(canvas, 0, 0, box.baseW, box.baseH, dp(getContext(), 12),
                        "PotionHUD".equalsIgnoreCase(selectedWidgetName));
            }

            OnyxVectorIcons.drawIcon(canvas, IconType.POTION_FLASK,
                    dp(getContext(), 10), dp(getContext(), 9),
                    dp(getContext(), 15), dp(getContext(), 15),
                    theme.getDynamicColor(0f), paint, path, rect);

            textPaint.setTextSize(dp(getContext(), 11.5f));
            textPaint.setColor(0xFFF2EFF7);
            canvas.drawText("Potions", dp(getContext(), 30), dp(getContext(), 21), textPaint);

            OnyxVectorIcons.drawIcon(canvas, IconType.HEART,
                    dp(getContext(), 10), dp(getContext(), 34),
                    dp(getContext(), 14), dp(getContext(), 14),
                    0xFFFACC15, paint, path, rect);

            textPaint.setTextSize(dp(getContext(), 11f));
            textPaint.setColor(0xFF60A5FA);
            canvas.drawText("Absorption", dp(getContext(), 30), dp(getContext(), 45), textPaint);

            OnyxModule potMod = moduleManager.getModule("PotionHUD");
            if (potMod == null || potMod.getBool("Time left", true)) {
                textPaint.setColor(0xFFE6E2F0);
                canvas.drawText("1:51", box.baseW - dp(getContext(), 32), dp(getContext(), 45), textPaint);
            }
            canvas.restore();
        }

        /**
         * Renders live on-screen visual effects for enabled Render modules
         * (Ambience, Fullbright, Hurtcam, Crosshair, Arrows, Particles, TargetESP)
         * and applies their configured sliders, colors, and modes in real time.
         */
        private void drawActiveRenderVisuals(Canvas canvas, int w, int h, long nowMs) {
            float cx = w / 2f;
            float cy = h / 2f;

            // 1. Ambience tint overlay
            OnyxModule ambMod = moduleManager.getModule("Ambience");
            if (ambMod != null && ambMod.isEnabled()) {
                int tintCol = ambMod.getColor("Tint", 0xFF8E6BFF);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(OnyxTheme.withAlpha(tintCol, 28));
                canvas.drawRect(0, 0, w, h, paint);
            }

            // 2. Hurtcam soft red edge vignette preview when enabled
            OnyxModule hurtMod = moduleManager.getModule("Hurtcam");
            if (hurtMod != null && hurtMod.isEnabled()) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(nowMs * 0.004);
                int flashCol = hurtMod.getColor("Colour", 0xFFFF3B5C);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(getContext(), 14));
                paint.setColor(OnyxTheme.withAlpha(flashCol, Math.round(45 * pulse)));
                rect.set(0, 0, w, h);
                canvas.drawRect(rect, paint);
            }

            // 3. Particles (drifting glowing fireflies when enabled)
            OnyxModule partMod = moduleManager.getModule("Particles");
            if (partMod != null && partMod.isEnabled()) {
                int pCol = theme.getDynamicColor(0.2f);
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 8; i++) {
                    double angle = (nowMs * 0.0012) + i * 0.785;
                    float px = cx + (float) Math.cos(angle * 1.3 + i) * dp(getContext(), 90 + i * 18);
                    float py = cy + (float) Math.sin(angle * 0.9 - i) * dp(getContext(), 55 + i * 12);
                    float pr = dp(getContext(), 3.2f + (i % 3) * 1.2f);
                    paint.setColor(OnyxTheme.withAlpha(pCol, 110));
                    canvas.drawCircle(px, py, pr * 1.8f, paint);
                    paint.setColor(0xCCFFFFFF);
                    canvas.drawCircle(px, py, pr * 0.7f, paint);
                }
                postInvalidateOnAnimation();
            }

            // 4. Arrows (Off-screen radar pointers around crosshair)
            OnyxModule arrMod = moduleManager.getModule("Arrows");
            if (arrMod != null && arrMod.isEnabled()) {
                float radius = dp(getContext(), arrMod.getNumber("Radius", 48f));
                float arrSz = dp(getContext(), arrMod.getNumber("Size", 8f));
                int arrCol = arrMod.getColor("Colour", theme.getDynamicColor(0f));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(OnyxTheme.withAlpha(arrCol, 210));
                float[] angles = new float[]{-42f, 38f, 145f};
                for (float deg : angles) {
                    canvas.save();
                    canvas.translate(cx, cy);
                    canvas.rotate(deg);
                    path.reset();
                    path.moveTo(0, -radius - arrSz);
                    path.lineTo(-arrSz * 0.65f, -radius);
                    path.lineTo(0, -radius - arrSz * 0.28f);
                    path.lineTo(arrSz * 0.65f, -radius);
                    path.close();
                    canvas.drawPath(path, paint);
                    canvas.restore();
                }
            }

            // 5. Crosshair (Dynamic custom center crosshair)
            OnyxModule chMod = moduleManager.getModule("Crosshair");
            if (chMod != null && chMod.isEnabled()) {
                float gap = dp(getContext(), chMod.getNumber("Gap", 3.5f));
                float len = dp(getContext(), chMod.getNumber("Length", 5.5f));
                float thick = dp(getContext(), chMod.getNumber("Thickness", 1.5f));
                int chCol = theme.getDynamicColor(0f);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(thick);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(chCol);

                canvas.drawLine(cx - gap - len, cy, cx - gap, cy, paint);
                canvas.drawLine(cx + gap, cy, cx + gap + len, cy, paint);
                canvas.drawLine(cx, cy - gap - len, cx, cy - gap, paint);
                canvas.drawLine(cx, cy + gap, cx, cy + gap + len, paint);
            }
        }

        /**
         * Renders the TargetHUD card with dynamic width calculation so the avatar, player name,
         * health number, distance/status subtitle, and health bar never collide or overlap.
         */
        private void drawTargetHudCard(Canvas canvas, HudWidgetBox box) {
            if (box == null) return;
            SubElementConfig avatarCfg = targetHudElements.get("Avatar");
            SubElementConfig nameCfg = targetHudElements.get("Name");
            SubElementConfig healthCfg = targetHudElements.get("Health");
            SubElementConfig distCfg = targetHudElements.get("Distance");
            SubElementConfig statusCfg = targetHudElements.get("Status");
            SubElementConfig barCfg = targetHudElements.get("Health bar");

            String displayPlayerName = moduleManager.getCustomDisplayName();
            String hpStr = "20.0";
            String subLine = editorMode ? "Drag to move" : "3m · Winning";

            float nameFontSz = dp(getContext(), 12.0f) * (nameCfg != null ? nameCfg.size : 1f);
            textPaint.setTextSize(nameFontSz);
            float nameW = (nameCfg == null || nameCfg.enabled) ? textPaint.measureText(displayPlayerName) : 0f;

            float hpFontSz = dp(getContext(), 11.2f) * (healthCfg != null ? healthCfg.size : 1f);
            textPaint.setTextSize(hpFontSz);
            float hpW = (healthCfg == null || healthCfg.enabled) ? textPaint.measureText(hpStr) : 0f;

            subTextPaint.setTextSize(dp(getContext(), 10.5f));
            float subW = subTextPaint.measureText(subLine);

            float textLeftPad = (avatarCfg == null || avatarCfg.enabled) ? dp(getContext(), 46) : dp(getContext(), 12);
            float topRowContentW = nameW + (nameW > 0 && hpW > 0 ? dp(getContext(), 8) : 0f) + hpW;
            float neededW = textLeftPad + Math.max(topRowContentW, subW) + dp(getContext(), 14);
            box.baseW = Math.max(dp(getContext(), 150), neededW);
            box.baseH = dp(getContext(), 48);

            canvas.save();
            canvas.translate(box.x, box.y);
            canvas.scale(box.scale, box.scale);

            float corner = dp(getContext(), 12);
            rect.set(0, 0, box.baseW, box.baseH);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(avatarCfg != null ? OnyxTheme.withAlpha(avatarCfg.color, 236) : 0xEC1A1924);
            canvas.drawRoundRect(rect, corner, corner, paint);

            if (editorMode) {
                drawEditorWhiteOutline(canvas, 0, 0, box.baseW, box.baseH, corner,
                        "TargetHUD".equalsIgnoreCase(selectedWidgetName));
            }

            // 1. Skin Face Avatar
            if (avatarCfg == null || avatarCfg.enabled) {
                float avSize = dp(getContext(), 28) * (avatarCfg != null ? avatarCfg.size : 1f);
                float avX = dp(getContext(), 9) + (avatarCfg != null ? dp(getContext(), avatarCfg.offsetX) : 0);
                float avY = dp(getContext(), 8) + (avatarCfg != null ? dp(getContext(), avatarCfg.offsetY) : 0);
                drawPixelAvatar(canvas, avX, avY, avSize);
            }

            // 2. Top Row: Player Name (left) + Health 20.0 (right-aligned or cleanly spaced)
            if (nameCfg == null || nameCfg.enabled) {
                float nx = textLeftPad + (nameCfg != null ? dp(getContext(), nameCfg.offsetX) : 0);
                float ny = dp(getContext(), 19.5f) + (nameCfg != null ? dp(getContext(), nameCfg.offsetY) : 0);
                textPaint.setTextSize(nameFontSz);
                textPaint.setColor(0xFFF5F2FC);
                canvas.drawText(displayPlayerName, nx, ny, textPaint);
            }

            if (healthCfg == null || healthCfg.enabled) {
                float hx = box.baseW - hpW - dp(getContext(), 11) + (healthCfg != null ? dp(getContext(), healthCfg.offsetX) : 0);
                float hy = dp(getContext(), 19.5f) + (healthCfg != null ? dp(getContext(), healthCfg.offsetY) : 0);
                textPaint.setTextSize(hpFontSz);
                textPaint.setColor(0xFFC4B8E8);
                canvas.drawText(hpStr, hx, hy, textPaint);
            }

            // 3. Second Row: Distance & Status ("3m · Winning" or "Drag to move")
            if (distCfg == null || distCfg.enabled || statusCfg == null || statusCfg.enabled) {
                float sx = textLeftPad + (distCfg != null ? dp(getContext(), distCfg.offsetX) : 0);
                float sy = dp(getContext(), 32.5f) + (distCfg != null ? dp(getContext(), distCfg.offsetY) : 0);
                subTextPaint.setTextSize(dp(getContext(), 10.2f));
                subTextPaint.setColor(0xFFB8B4C8);
                canvas.drawText(subLine, sx, sy, subTextPaint);
            }

            // 4. Bottom Row: Health Bar
            if (barCfg == null || barCfg.enabled) {
                float bh = dp(getContext(), barCfg != null ? barCfg.thickness : 3.0f);
                float bx = dp(getContext(), 9) + (barCfg != null ? dp(getContext(), barCfg.offsetX) : 0);
                float by = box.baseH - dp(getContext(), 6.5f) - bh / 2f + (barCfg != null ? dp(getContext(), barCfg.offsetY) : 0);
                float bw = box.baseW - dp(getContext(), 18);

                rect.set(bx, by, bx + bw, by + bh);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF2E2C3C);
                canvas.drawRoundRect(rect, bh / 2f, bh / 2f, paint);

                rect.set(bx, by, bx + bw * 0.86f, by + bh);
                paint.setColor(barCfg != null ? barCfg.color : theme.getDynamicColor(0f));
                canvas.drawRoundRect(rect, bh / 2f, bh / 2f, paint);
            }

            canvas.restore();
        }

        private void drawPixelAvatar(Canvas canvas, float x, float y, float size) {
            paint.setStyle(Paint.Style.FILL);
            rect.set(x, y, x + size, y + size);
            paint.setColor(0xFFF3D0B8);
            canvas.drawRoundRect(rect, dp(getContext(), 4), dp(getContext(), 4), paint);

            paint.setColor(0xFF4A3228);
            canvas.drawRect(x, y, x + size, y + size * 0.34f, paint);
            canvas.drawRect(x, y + size * 0.34f, x + size * 0.15f, y + size * 0.58f, paint);
            canvas.drawRect(x + size * 0.85f, y + size * 0.34f, x + size, y + size * 0.58f, paint);

            paint.setColor(Color.WHITE);
            canvas.drawRect(x + size * 0.14f, y + size * 0.50f, x + size * 0.38f, y + size * 0.63f, paint);
            canvas.drawRect(x + size * 0.62f, y + size * 0.50f, x + size * 0.86f, y + size * 0.63f, paint);

            paint.setColor(0xFF3B2820);
            canvas.drawRect(x + size * 0.25f, y + size * 0.50f, x + size * 0.38f, y + size * 0.63f, paint);
            canvas.drawRect(x + size * 0.62f, y + size * 0.50f, x + size * 0.75f, y + size * 0.63f, paint);

            paint.setColor(0xFFD98B82);
            canvas.drawRect(x + size * 0.38f, y + size * 0.75f, x + size * 0.62f, y + size * 0.86f, paint);
        }

        private void drawEditorWhiteOutline(Canvas canvas, float l, float t, float r, float b, float corner, boolean isSelected) {
            float inset = -dp(getContext(), 2.5f);
            rect.set(l + inset, t + inset, r - inset, b - inset);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), isSelected ? 1.8f : 1.2f));
            paint.setColor(isSelected ? 0xFFFFFFFF : 0xAAFFFFFF);
            canvas.drawRoundRect(rect, corner + dp(getContext(), 2), corner + dp(getContext(), 2), paint);
        }

        /**
         * Renders the Notifications preview card during HUD Layout Edit mode (matching 2024.mp4 at 02:23).
         */
        private void drawNotificationsEditPreview(Canvas canvas, HudWidgetBox box) {
            if (box == null) return;
            box.baseW = dp(getContext(), 168);
            box.baseH = dp(getContext(), 44);

            canvas.save();
            canvas.translate(box.x, box.y);
            canvas.scale(box.scale, box.scale);

            rect.set(0, 0, box.baseW, box.baseH);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xEE1A1924);
            canvas.drawRoundRect(rect, dp(getContext(), 12), dp(getContext(), 12), paint);

            drawEditorWhiteOutline(canvas, 0, 0, box.baseW, box.baseH, dp(getContext(), 12),
                    "Notifications".equalsIgnoreCase(selectedWidgetName));

            OnyxVectorIcons.drawIcon(canvas, IconType.INFO_CIRCLE,
                    dp(getContext(), 10), dp(getContext(), 12),
                    dp(getContext(), 18), dp(getContext(), 18),
                    0xFFD0CCDE, paint, path, rect);

            textPaint.setTextSize(dp(getContext(), 11.8f));
            textPaint.setColor(0xFFF2EFF7);
            canvas.drawText("Notifications", dp(getContext(), 34), dp(getContext(), 19), textPaint);

            subTextPaint.setTextSize(dp(getContext(), 10f));
            subTextPaint.setColor(0xFFA6A2B5);
            canvas.drawText("Drag to move", dp(getContext(), 34), dp(getContext(), 32), subTextPaint);

            rect.set(dp(getContext(), 12), box.baseH - dp(getContext(), 4.5f),
                    box.baseW - dp(getContext(), 42), box.baseH - dp(getContext(), 2.2f));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFD0CCDE);
            canvas.drawRoundRect(rect, dp(getContext(), 1.5f), dp(getContext(), 1.5f), paint);
            canvas.restore();
        }

        private void drawRightEdgeArrayList(Canvas canvas, int screenW) {
            long now = SystemClock.uptimeMillis();
            boolean anyAnimating = false;

            List<OnyxModule> visibleMods = new ArrayList<>();
            for (OnyxModule m : moduleManager.getModules()) {
                if ("ClickGUI".equalsIgnoreCase(m.getName()) || "HUDEditor".equalsIgnoreCase(m.getName())) continue;
                ArrayListAnimState st = arrayListAnimMap.get(m.getName());
                if (m.isEnabled() && st == null) {
                    st = new ArrayListAnimState();
                    st.progress = 0.01f;
                    st.lastFrameTime = now;
                    arrayListAnimMap.put(m.getName(), st);
                }
                if (st != null) {
                    float dt = Math.min(0.05f, Math.max(0.004f, (now - st.lastFrameTime) / 1000.0f));
                    st.lastFrameTime = now;
                    float speed = 4.0f;
                    if (m.isEnabled()) {
                        if (st.progress < 1.0f) {
                            st.progress = Math.min(1.0f, st.progress + dt * speed);
                            anyAnimating = true;
                        }
                    } else {
                        if (st.progress > 0.0f) {
                            st.progress = Math.max(0.0f, st.progress - dt * (speed * 1.15f));
                            anyAnimating = true;
                        }
                    }
                    if (st.progress > 0.005f) {
                        visibleMods.add(m);
                    }
                }
            }

            if (visibleMods.isEmpty()) return;

            textPaint.setTextSize(dp(getContext(), 11.5f));
            final boolean longestFirst = !"Shortest first".equalsIgnoreCase(moduleManager.getArrayListSort());
            Collections.sort(visibleMods, (a, b) -> {
                String sa = a.getName() + (a.getArrayListSuffix().isEmpty() ? "" : (" " + a.getArrayListSuffix()));
                String sb = b.getName() + (b.getArrayListSuffix().isEmpty() ? "" : (" " + b.getArrayListSuffix()));
                float wa = textPaint.measureText(sa);
                float wb = textPaint.measureText(sb);
                return longestFirst ? Float.compare(wb, wa) : Float.compare(wa, wb);
            });

            String style = moduleManager.getArrayListStyle();
            String animType = moduleManager.getArrayListAnimation();
            float rowH = dp(getContext(), 19.5f);
            float barW = "Accent Bar".equalsIgnoreCase(style) ? dp(getContext(), 3.0f) : 0f;
            float targetY = dp(getContext(), 4);

            for (int idx = 0; idx < visibleMods.size(); idx++) {
                OnyxModule m = visibleMods.get(idx);
                ArrayListAnimState st = arrayListAnimMap.get(m.getName());
                float rawP = st != null ? st.progress : 1f;
                float inv = 1f - rawP;
                float easedP = 1f - inv * inv * inv;

                if (st.currentY < 0f) {
                    st.currentY = targetY;
                } else if (Math.abs(st.currentY - targetY) > 0.4f) {
                    st.currentY += (targetY - st.currentY) * 0.30f;
                    anyAnimating = true;
                } else {
                    st.currentY = targetY;
                }

                String name = m.getName();
                String suffix = m.getArrayListSuffix();
                float nameW = textPaint.measureText(name);
                float suffixW = suffix.isEmpty() ? 0f : textPaint.measureText(" " + suffix);
                float totalTextW = nameW + suffixW;
                float padH = dp(getContext(), 7);
                float boxW = totalTextW + padH * 2 + barW;

                float slideOffsetX = 0f;
                float alphaFactor = 1f;
                float scaleFactor = 1f;

                if ("Fade".equalsIgnoreCase(animType)) {
                    alphaFactor = easedP;
                } else if ("Scale".equalsIgnoreCase(animType)) {
                    alphaFactor = easedP;
                    scaleFactor = 0.65f + 0.35f * easedP;
                } else if ("Slide".equalsIgnoreCase(animType)) {
                    slideOffsetX = (1f - easedP) * (boxW + dp(getContext(), 8));
                } else {
                    slideOffsetX = (1f - easedP) * (boxW + dp(getContext(), 6));
                    alphaFactor = easedP;
                }

                float drawY = st.currentY;
                float left = screenW - boxW + slideOffsetX;
                int rowColor = theme.getDynamicColor(idx * 0.06f);
                int alphaInt = Math.round(255 * alphaFactor);

                canvas.save();
                if (scaleFactor != 1f) {
                    canvas.scale(scaleFactor, scaleFactor, screenW, drawY + rowH / 2f);
                }

                if (!"Text".equalsIgnoreCase(style)) {
                    rect.set(left, drawY, screenW - barW + slideOffsetX, drawY + rowH);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(OnyxTheme.withAlpha(0xFF14131C, Math.round(155 * alphaFactor)));
                    if ("Pills".equalsIgnoreCase(style)) {
                        canvas.drawRoundRect(rect, dp(getContext(), 6), dp(getContext(), 6), paint);
                    } else {
                        canvas.drawRect(rect, paint);
                    }
                }

                if ("Accent Bar".equalsIgnoreCase(style)) {
                    rect.set(screenW - barW + slideOffsetX, drawY, screenW + slideOffsetX, drawY + rowH);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(OnyxTheme.withAlpha(rowColor, alphaInt));
                    canvas.drawRect(rect, paint);
                }

                float textBaseline = drawY + dp(getContext(), 14f);
                int nameColor = theme.isDynamicCyclingActive()
                        ? OnyxTheme.blendColors(rowColor, 0xFFFFFFFF, 0.22f)
                        : OnyxTheme.blendColors(theme.primary, 0xFFF2EFF7, 0.45f);
                textPaint.setColor(OnyxTheme.withAlpha(nameColor, alphaInt));
                canvas.drawText(name, left + padH, textBaseline, textPaint);

                if (!suffix.isEmpty()) {
                    textPaint.setColor(OnyxTheme.withAlpha(0xFFA6A2B5, alphaInt));
                    canvas.drawText(" " + suffix, left + padH + nameW, textBaseline, textPaint);
                }

                canvas.restore();
                targetY += rowH * easedP;
            }

            if (anyAnimating) {
                postInvalidateOnAnimation();
            }
        }

        /**
         * Renders bottom-right notifications matching 2024.mp4:
         * - Enabled toast: circular dark badge with checkmark ✓ on left + shrinking bottom progress bar.
         * - Disabled toast: filled circular info icon (i) on left + shrinking bottom progress bar.
         * - Right-to-Left spring slide-in and Left-to-Right smooth slide-out.
         */
        private void drawBottomRightNotifications(Canvas canvas, int screenW, int screenH) {
            if (activeToasts.isEmpty()) return;
            long now = SystemClock.uptimeMillis();
            float scale = notificationsBox != null ? notificationsBox.scale : 1.0f;
            float cardW = dp(getContext(), 168) * scale;
            float cardH = dp(getContext(), 44) * scale;
            float baseRight = (notificationsBox != null)
                    ? Math.min(screenW - dp(getContext(), 10), notificationsBox.x + cardW)
                    : (screenW - dp(getContext(), 14));
            float baseBottomY = (notificationsBox != null)
                    ? Math.min(screenH - cardH - dp(getContext(), 10), notificationsBox.y)
                    : (screenH - dp(getContext(), 16) - cardH);

            boolean anyAlive = false;
            int visibleIdx = 0;
            for (int i = 0; i < activeToasts.size(); i++) {
                ToastPopup t = activeToasts.get(i);
                long elapsed = now - t.createdAt;
                if (elapsed >= t.durationMs) {
                    activeToasts.remove(i);
                    i--;
                    continue;
                }
                anyAlive = true;

                float slideOffsetX = 0f;
                float alpha = 1f;
                long exitStart = t.durationMs - 340L;

                if (elapsed < 340L) {
                    float u = elapsed / 340.0f;
                    float spring = 1f - (float) (Math.exp(-5.5f * u) * Math.cos(8.0f * u));
                    slideOffsetX = (1f - spring) * (cardW + dp(getContext(), 28));
                    alpha = Math.min(1f, u * 2.2f);
                } else if (elapsed > exitStart) {
                    float u = (elapsed - exitStart) / 340.0f;
                    float cubicOut = u * u * (2.5f * u - 1.5f);
                    slideOffsetX = cubicOut * (cardW + dp(getContext(), 32));
                    alpha = Math.max(0f, 1f - u * 1.15f);
                }

                float targetY = baseBottomY - visibleIdx * (cardH + dp(getContext(), 8));
                if (t.animatedY < 0f) {
                    t.animatedY = targetY;
                } else {
                    t.animatedY += (targetY - t.animatedY) * 0.28f;
                }

                float left = baseRight - cardW + slideOffsetX;
                float y = t.animatedY;
                int alphaInt = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));

                rect.set(left, y, left + cardW, y + cardH);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(OnyxTheme.withAlpha(0xFF1A1923, Math.round(238 * alpha)));
                canvas.drawRoundRect(rect, dp(getContext(), 12) * scale, dp(getContext(), 12) * scale, paint);

                // Left icon badge: Checkmark inside circle when Enabled, Info circle (i) when Disabled
                float iconCx = left + dp(getContext(), 19) * scale;
                float iconCy = y + cardH * 0.46f;
                if (t.enabledState) {
                    paint.setColor(OnyxTheme.withAlpha(0xFF2B2838, alphaInt));
                    canvas.drawCircle(iconCx, iconCy, dp(getContext(), 9.5f) * scale, paint);
                    OnyxVectorIcons.drawIcon(canvas, IconType.CHECK_MARK,
                            iconCx - dp(getContext(), 7) * scale, iconCy - dp(getContext(), 7) * scale,
                            dp(getContext(), 14) * scale, dp(getContext(), 14) * scale,
                            OnyxTheme.withAlpha(0xFFE6E2F0, alphaInt), paint, path, rect);
                } else {
                    OnyxVectorIcons.drawIcon(canvas, IconType.INFO_CIRCLE,
                            iconCx - dp(getContext(), 9.5f) * scale, iconCy - dp(getContext(), 9.5f) * scale,
                            dp(getContext(), 19) * scale, dp(getContext(), 19) * scale,
                            OnyxTheme.withAlpha(0xFFD6D2E4, alphaInt), paint, path, rect);
                }

                textPaint.setTextSize(dp(getContext(), 11.8f) * scale);
                textPaint.setColor(OnyxTheme.withAlpha(0xFFF2EFF7, alphaInt));
                canvas.drawText(t.title, left + dp(getContext(), 34) * scale, y + dp(getContext(), 18.5f) * scale, textPaint);

                subTextPaint.setTextSize(dp(getContext(), 10f) * scale);
                subTextPaint.setColor(OnyxTheme.withAlpha(0xFFA6A2B5, alphaInt));
                canvas.drawText(t.subtitle, left + dp(getContext(), 34) * scale, y + dp(getContext(), 31.5f) * scale, subTextPaint);

                OnyxModule notifMod = moduleManager.getModule("Notifications");
                if (notifMod == null || notifMod.getBool("Show progress bar", true)) {
                    float lifeProgress = 1.0f - ((float) elapsed / t.durationMs);
                    float barMaxW = cardW - dp(getContext(), 24) * scale;
                    rect.set(left + dp(getContext(), 12) * scale, y + cardH - dp(getContext(), 4.5f) * scale,
                            left + dp(getContext(), 12) * scale + Math.max(0f, barMaxW * lifeProgress), y + cardH - dp(getContext(), 2.2f) * scale);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(OnyxTheme.withAlpha(t.enabledState ? theme.getDynamicColor(0f) : 0xFFD0CCDE, alphaInt));
                    canvas.drawRoundRect(rect, dp(getContext(), 1.5f) * scale, dp(getContext(), 1.5f) * scale, paint);
                }

                visibleIdx++;
            }

            if (anyAlive) {
                postInvalidateOnAnimation();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            int action = event.getActionMasked();

            if (!editorMode) {
                if (action == MotionEvent.ACTION_DOWN) {
                    List<OnyxModule> mods = moduleManager.getModules();
                    for (int i = mods.size() - 1; i >= 0; i--) {
                        OnyxModule m = mods.get(i);
                        if (!m.getKeybind().isShortcutButtonEnabled()) continue;
                        ShortcutCapsuleState st = shortcutStates.get(m.getName());
                        if (st != null && st.bounds.contains(x, y)) {
                            pressedShortcut = st;
                            draggingShortcut = false;
                            shortcutDownX = x;
                            shortcutDownY = y;
                            shortcutDragOffsetX = x - st.bounds.left;
                            shortcutDragOffsetY = y - st.bounds.top;

                            removeCallbacks(shortcutLongPressRunnable);
                            postDelayed(shortcutLongPressRunnable, SHORTCUT_LONG_PRESS_MS);

                            if (m.getKeybind().isHoldMode()) {
                                moduleManager.setModuleEnabled(m, true);
                            }
                            return true;
                        }
                    }

                    for (Map.Entry<OnyxModule, RectF> entry : keybindRowHitRects.entrySet()) {
                        if (entry.getValue().contains(x, y)) {
                            OnyxModule m = entry.getKey();
                            if (m.getKeybind().isHoldMode()) {
                                activeHoldModule = m;
                                moduleManager.setModuleEnabled(m, true);
                            } else {
                                moduleManager.toggleModule(m);
                            }
                            invalidate();
                            return true;
                        }
                    }
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (pressedShortcut != null) {
                        if (draggingShortcut) {
                            KeybindSetting kb = pressedShortcut.module.getKeybind();
                            float newX = Math.max(dp(getContext(), 6), Math.min(getWidth() - pressedShortcut.bounds.width() - dp(getContext(), 6), x - shortcutDragOffsetX));
                            float newY = Math.max(dp(getContext(), 6), Math.min(getHeight() - pressedShortcut.bounds.height() - dp(getContext(), 6), y - shortcutDragOffsetY));
                            kb.setShortcutPosition(newX, newY);
                            invalidate();
                        } else if (Math.hypot(x - shortcutDownX, y - shortcutDownY) > dp(getContext(), 18)) {
                            removeCallbacks(shortcutLongPressRunnable);
                        }
                        return true;
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (pressedShortcut != null) {
                        removeCallbacks(shortcutLongPressRunnable);
                        OnyxModule mod = pressedShortcut.module;
                        boolean wasDragging = draggingShortcut;
                        float moveDist = (float) Math.hypot(x - shortcutDownX, y - shortcutDownY);
                        pressedShortcut = null;
                        draggingShortcut = false;

                        if (!wasDragging) {
                            if (mod.getKeybind().isHoldMode()) {
                                moduleManager.setModuleEnabled(mod, false);
                            } else if (action == MotionEvent.ACTION_UP && moveDist < dp(getContext(), 24)) {
                                moduleManager.toggleModule(mod);
                            }
                        }
                        invalidate();
                        return true;
                    }
                    if (activeHoldModule != null) {
                        moduleManager.setModuleEnabled(activeHoldModule, false);
                        activeHoldModule = null;
                        invalidate();
                        return true;
                    }
                }
                return false;
            }

            // EDITOR MODE: Directly drag the active HUD widgets at their real positions
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    draggingBox = null;
                    if (shouldRenderHudWidget("TargetHUD") && targetHudBox != null && targetHudBox.contains(x, y)) {
                        draggingBox = targetHudBox;
                        selectedWidgetName = "TargetHUD";
                    } else if (shouldRenderHudWidget("Watermark") && dynamicIslandBox != null && dynamicIslandBox.contains(x, y)) {
                        draggingBox = dynamicIslandBox;
                        selectedWidgetName = "Watermark";
                    } else if (shouldRenderHudWidget("Stats") && fpsPingBox != null && fpsPingBox.contains(x, y)) {
                        draggingBox = fpsPingBox;
                        selectedWidgetName = "Stats";
                    } else if (shouldRenderHudWidget("Keybinds") && keybindsBox != null && keybindsBox.contains(x, y)) {
                        draggingBox = keybindsBox;
                        selectedWidgetName = "Keybinds";
                    } else if (shouldRenderHudWidget("PotionHUD") && potionHudBox != null && potionHudBox.contains(x, y)) {
                        draggingBox = potionHudBox;
                        selectedWidgetName = "PotionHUD";
                    } else if (shouldRenderHudWidget("Notifications") && notificationsBox != null && notificationsBox.contains(x, y)) {
                        draggingBox = notificationsBox;
                        selectedWidgetName = "Notifications";
                    }

                    if (draggingBox != null) {
                        dragDx = x - draggingBox.x;
                        dragDy = y - draggingBox.y;
                        if (singleEditingModule == null) {
                            rebuildInspectorControls();
                        }
                        invalidate();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (draggingBox != null) {
                        draggingBox.x = Math.max(0, Math.min(getWidth() - draggingBox.getScaledW(), x - dragDx));
                        draggingBox.y = Math.max(0, Math.min(getHeight() - draggingBox.getScaledH(), y - dragDy));
                        invalidate();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    draggingBox = null;
                    break;
            }
            return true;
        }
    }

    private void triggerDynamicIslandAppleAnimation(OnyxModule module) {
        islandAlertModule = module.getName();
        islandAlertState = module.isEnabled();

        if (islandAnimator != null) {
            islandAnimator.cancel();
        }
        final float startExp = islandExpandProgress;
        islandAnimator = ValueAnimator.ofFloat(0f, 2050f);
        islandAnimator.setDuration(2050L);
        islandAnimator.addUpdateListener(a -> {
            float elapsed = (Float) a.getAnimatedValue();
            if (elapsed <= 450f) {
                float t = elapsed / 450f;
                float spring = 1f - (float) (Math.exp(-5.6f * t) * Math.cos(10.0f * t));
                islandExpandProgress = startExp + (1f - startExp) * spring;

                float ct = Math.max(0f, (elapsed - 45f) / 360f);
                islandContentProgress = 1f - (float) (Math.exp(-6.0f * ct) * Math.cos(8.5f * ct));
                islandHoldProgress = 0f;

                float bounceWave = (float) (Math.exp(-5.0f * t) * Math.sin(10.8f * t));
                islandQBounceScaleX = 1f + 0.075f * bounceWave;
                islandQBounceScaleY = 1f - 0.048f * bounceWave;
            } else if (elapsed <= 1550f) {
                islandExpandProgress = 1f;
                islandContentProgress = 1f;
                islandHoldProgress = (elapsed - 450f) / 1100f;
                islandQBounceScaleX = 1f;
                islandQBounceScaleY = 1f;
            } else {
                float t = (elapsed - 1550f) / 500f;
                float springBack = (float) (Math.exp(-5.8f * t) * Math.cos(9.5f * t));
                islandExpandProgress = Math.max(0f, springBack);
                islandContentProgress = Math.max(0f, 1f - t * 2.1f);
                islandHoldProgress = 1f;

                float reboundWave = (float) (Math.exp(-5.2f * t) * Math.sin(10.2f * t));
                islandQBounceScaleX = 1f - 0.068f * reboundWave;
                islandQBounceScaleY = 1f + 0.052f * reboundWave;
            }
            if (canvasLayer != null) canvasLayer.invalidate();
        });
        islandAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                islandExpandProgress = 0f;
                islandContentProgress = 0f;
                islandHoldProgress = 0f;
                islandQBounceScaleX = 1f;
                islandQBounceScaleY = 1f;
                if (canvasLayer != null) canvasLayer.invalidate();
            }
        });
        islandAnimator.start();
    }

    private void animateShortcutCapsuleFill(final ShortcutCapsuleState state, boolean enabled) {
        float target = enabled ? 1f : 0f;
        if (Math.abs(state.activeProgress - target) < 0.005f &&
                (state.fillAnimator == null || !state.fillAnimator.isRunning())) {
            return;
        }
        if (state.fillAnimator != null) state.fillAnimator.cancel();
        float start = state.activeProgress;
        state.fillAnimator = ValueAnimator.ofFloat(start, target);
        state.fillAnimator.setDuration(280L);
        state.fillAnimator.setInterpolator(new DecelerateInterpolator(1.4f));
        state.fillAnimator.addUpdateListener(a -> {
            state.activeProgress = (Float) a.getAnimatedValue();
            if (canvasLayer != null) canvasLayer.invalidate();
        });
        state.fillAnimator.start();
    }

    @Override
    public void onThemeChanged(OnyxTheme updatedTheme) {
        canvasLayer.invalidate();
    }

    @Override
    public void onModuleToggled(OnyxModule module) {
        if ("HUDEditor".equalsIgnoreCase(module.getName())) {
            return;
        }
        OnyxModule wmMod = moduleManager.getModule("Watermark");
        if (wmMod == null || wmMod.getBool("Dynamic island alerts", true)) {
            triggerDynamicIslandAppleAnimation(module);
        }

        ShortcutCapsuleState st = shortcutStates.get(module.getName());
        if (st != null) {
            animateShortcutCapsuleFill(st, module.isEnabled());
        }

        activeToasts.add(0, new ToastPopup(
                module.getName(),
                module.isEnabled() ? "Enabled" : "Disabled",
                module.isEnabled()
        ));
        if (activeToasts.size() > 5) {
            activeToasts.remove(activeToasts.size() - 1);
        }
        canvasLayer.invalidate();
    }

    @Override
    public void onConfigApplied(String configName) {
        for (OnyxModule m : moduleManager.getModules()) {
            ShortcutCapsuleState st = shortcutStates.get(m.getName());
            if (st != null) {
                st.activeProgress = m.isEnabled() ? 1f : 0f;
            }
        }
        activeToasts.add(0, new ToastPopup(configName, "Loaded", true));
        canvasLayer.invalidate();
    }

    @Override
    public void onSettingsChanged() {
        OnyxModule thMod = moduleManager.getModule("TargetHUD");
        if (thMod != null) {
            SubElementConfig avCfg = targetHudElements.get("Avatar");
            if (avCfg != null) {
                avCfg.enabled = thMod.getBool("Avatar", avCfg.enabled);
                avCfg.size = thMod.getNumber("Size", avCfg.size);
                avCfg.offsetX = thMod.getNumber("Offset X", avCfg.offsetX);
                avCfg.offsetY = thMod.getNumber("Offset Y", avCfg.offsetY);
            }
        }
        canvasLayer.invalidate();
    }
}
