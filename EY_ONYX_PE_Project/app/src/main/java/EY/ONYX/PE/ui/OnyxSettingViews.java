package EY.ONYX.PE.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import EY.ONYX.PE.setting.OnyxSettings.*;
import EY.ONYX.PE.theme.OnyxTheme;
import EY.ONYX.PE.ui.OnyxVectorIcons.IconType;
import EY.ONYX.PE.ui.OnyxVectorIcons.VectorIconView;

/**
 * Factory and custom views for rendering module configuration controls:
 * 1. AnimatedPillSwitchView (Module cards & TargetHUD Avatar toggle)
 * 2. AnimatedCheckmarkBoxView (Purple rounded square with checkmark ✓)
 * 3. AnimatedRadioDotView (Vertical radio buttons in Array List & TargetHUD Weight)
 * 4. 2D HSV Saturation-Value Box + Vertical Rainbow Hue Strip + "Use accent colour" (BedESP Own color at 00:43!)
 * 5. Interactive Keybind Picker ("..." live key/mouse listener + Toggle 单击 / Hold 长按 mode)
 * 6. Full-width "Edit layout" Action Button (TargetHUD at 01:28!)
 */
public class OnyxSettingViews {

    /**
     * Automatic Phone Screen Aspect-Ratio & Resolution Scaling Factor:
     * Adapts all UI dimensions proportionally to any phone screen (16:9, 18:9, 19.5:9, 20:9, 21:9)
     * so panels, cards, and HUD elements never overflow or look oversized on narrow phone screens.
     */
    public static float getScreenAutoScale(Context context) {
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = dm.density <= 0 ? 1f : dm.density;
        float longSideDp = Math.max(dm.widthPixels, dm.heightPixels) / density;
        float shortSideDp = Math.min(dm.widthPixels, dm.heightPixels) / density;
        float scaleW = longSideDp / 780.0f;
        float scaleH = shortSideDp / 425.0f;
        float autoScale = Math.min(scaleW, scaleH);
        return Math.max(0.74f, Math.min(1.06f, autoScale));
    }

    public static int dp(Context context, float dpVal) {
        float scaledDp = dpVal * getScreenAutoScale(context);
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, scaledDp, context.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable roundedRect(int fillColor, float radiusDp, int strokeWidthPx, int strokeColor, Context ctx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadius(dp(ctx, radiusDp));
        if (strokeWidthPx > 0) {
            gd.setStroke(strokeWidthPx, strokeColor);
        }
        return gd;
    }

    public static GradientDrawable circleDrawable(int fillColor, int strokeWidthPx, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(fillColor);
        if (strokeWidthPx > 0) {
            gd.setStroke(strokeWidthPx, strokeColor);
        }
        return gd;
    }

    /** 1. Animated Pill Switch with Silky Spring Overshoot & Liquid Capsule Thumb Stretch */
    public static class AnimatedPillSwitchView extends View {
        private boolean checked;
        private float progress;
        private float stretchFactor = 0f;
        private final OnyxTheme theme;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF thumbRect = new RectF();
        private ValueAnimator animator;

        public AnimatedPillSwitchView(Context context, boolean initialChecked, OnyxTheme theme) {
            super(context);
            this.checked = initialChecked;
            this.progress = initialChecked ? 1.0f : 0.0f;
            this.theme = theme;
        }

        public void setCheckedAnimated(boolean newChecked) {
            setChecked(newChecked, true);
        }

        public void setChecked(boolean newChecked, boolean animate) {
            if (this.checked == newChecked && animate && (animator == null || !animator.isRunning())) return;
            this.checked = newChecked;
            float target = newChecked ? 1.0f : 0.0f;
            if (animator != null) animator.cancel();
            if (!animate) {
                progress = target;
                stretchFactor = 0f;
                invalidate();
                return;
            }
            final float start = progress;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(360);
            animator.setInterpolator(new android.view.animation.OvershootInterpolator(1.35f));
            animator.addUpdateListener(a -> {
                float frac = (Float) a.getAnimatedValue();
                progress = start + (target - start) * frac;
                float clamped = Math.max(0f, Math.min(1f, frac));
                stretchFactor = (float) Math.sin(clamped * Math.PI);
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = dp(getContext(), 1.5f);
            float w = getWidth();
            float h = getHeight();
            float r = (h - pad * 2) / 2.0f;
            rect.set(pad, pad, w - pad, h - pad);

            float pClamped = Math.max(0f, Math.min(1f, progress));
            int trackColor = OnyxTheme.blendColors(0xFF24222E, theme.primary, pClamped);
            int strokeColor = OnyxTheme.blendColors(0xFF757182, theme.primary, pClamped);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(trackColor);
            canvas.drawRoundRect(rect, r, r, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.4f));
            paint.setColor(strokeColor);
            canvas.drawRoundRect(rect, r, r, paint);

            float minCx = pad + r;
            float maxCx = w - pad - r;
            float cx = minCx + (maxCx - minCx) * progress;
            float cy = h / 2.0f;
            float thumbR = dp(getContext(), 5.4f + 2.0f * pClamped);
            float extraHalfW = dp(getContext(), 3.6f) * stretchFactor;
            float squashHalfH = dp(getContext(), 0.6f) * stretchFactor;

            thumbRect.set(
                    cx - thumbR - extraHalfW,
                    cy - thumbR + squashHalfH,
                    cx + thumbR + extraHalfW,
                    cy + thumbR - squashHalfH
            );

            int thumbColor = OnyxTheme.blendColors(0xFF8E8A9B, 0xFF1E1936, pClamped);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(thumbColor);
            canvas.drawRoundRect(thumbRect, thumbRect.height() / 2f, thumbRect.height() / 2f, paint);
        }
    }

    /** 2. Animated Rounded-Square Checkmark Box ✓  */
    public static class AnimatedCheckmarkBoxView extends View {
        private boolean checked;
        private float progress;
        private final OnyxTheme theme;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path checkPath = new Path();
        private final RectF rect = new RectF();
        private ValueAnimator animator;

        public AnimatedCheckmarkBoxView(Context context, boolean initialChecked, OnyxTheme theme) {
            super(context);
            this.checked = initialChecked;
            this.progress = initialChecked ? 1.0f : 0.0f;
            this.theme = theme;
        }

        public void setCheckedAnimated(boolean newChecked) {
            if (this.checked == newChecked) return;
            this.checked = newChecked;
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(progress, newChecked ? 1.0f : 0.0f);
            animator.setDuration(160);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                progress = (Float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = dp(getContext(), 1.5f);
            float w = getWidth();
            float h = getHeight();
            float corner = dp(getContext(), 5.0f);
            rect.set(pad, pad, w - pad, h - pad);

            if (progress < 0.99f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(getContext(), 1.6f));
                paint.setColor(0xFFC8C4D4);
                canvas.drawRoundRect(rect, corner, corner, paint);
            }

            if (progress > 0.01f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(OnyxTheme.withAlpha(theme.primary, Math.round(255 * progress)));
                canvas.save();
                float sc = 0.75f + 0.25f * progress;
                canvas.scale(sc, sc, w / 2f, h / 2f);
                canvas.drawRoundRect(rect, corner, corner, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(getContext(), 2.0f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(OnyxTheme.withAlpha(0xFF181426, Math.round(255 * progress)));

                checkPath.reset();
                checkPath.moveTo(w * 0.27f, h * 0.52f);
                checkPath.lineTo(w * 0.44f, h * 0.69f);
                checkPath.lineTo(w * 0.74f, h * 0.33f);
                canvas.drawPath(checkPath, paint);
                canvas.restore();
            }
        }
    }

    /** 3. Animated Radio Button Circle  */
    public static class AnimatedRadioDotView extends View {
        private boolean selected;
        private float progress;
        private final OnyxTheme theme;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator animator;

        public AnimatedRadioDotView(Context context, boolean initialSelected, OnyxTheme theme) {
            super(context);
            this.selected = initialSelected;
            this.progress = initialSelected ? 1.0f : 0.0f;
            this.theme = theme;
        }

        public void setSelectedAnimated(boolean newSelected) {
            if (this.selected == newSelected) return;
            this.selected = newSelected;
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(progress, newSelected ? 1.0f : 0.0f);
            animator.setDuration(150);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                progress = (Float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float outerR = Math.min(cx, cy) - dp(getContext(), 1.6f);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.6f));
            paint.setColor(OnyxTheme.blendColors(0xFFC2BED0, theme.primary, progress));
            canvas.drawCircle(cx, cy, outerR, paint);

            if (progress > 0.01f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(theme.primary);
                canvas.drawCircle(cx, cy, outerR * 0.52f * progress, paint);
            }
        }
    }

    public static View createSettingRowView(Context context, Setting setting, OnyxTheme theme, Runnable onAnyChange) {
        if (setting instanceof KeybindSetting) {
            return buildBindRow(context, (KeybindSetting) setting, theme, onAnyChange);
        } else if (setting instanceof ActionButtonSetting) {
            return buildActionButtonRow(context, (ActionButtonSetting) setting, theme);
        } else if (setting instanceof BooleanSetting) {
            return buildBooleanRow(context, (BooleanSetting) setting, theme, onAnyChange);
        } else if (setting instanceof NumberSliderSetting) {
            return buildVerticalBarSliderRow(context, (NumberSliderSetting) setting, theme, onAnyChange);
        } else if (setting instanceof RangeSliderSetting) {
            return buildRangeSliderRow(context, (RangeSliderSetting) setting, theme, onAnyChange);
        } else if (setting instanceof ColorPickerSetting) {
            return build2DColorPickerRow(context, (ColorPickerSetting) setting, theme, onAnyChange);
        } else if (setting instanceof RadioListSetting) {
            return buildRadioListRow(context, (RadioListSetting) setting, theme, onAnyChange);
        } else if (setting instanceof TextInputSetting) {
            return buildTextInputRow(context, (TextInputSetting) setting, theme, onAnyChange);
        } else if (setting instanceof BlockFilterSetting) {
            return buildBlockFilterRow(context, (BlockFilterSetting) setting, theme, onAnyChange);
        }
        return new View(context);
    }

    // ==================== 1. Interactive Bind Row (Real Key/Mouse Listener + Toggle/Hold Mode) ====================
    private static View buildBindRow(Context ctx, final KeybindSetting setting, final OnyxTheme theme, final Runnable onAnyChange) {
        LinearLayout wrapper = createCleanRowContainer(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = createTitleSubtitleColumn(ctx, setting.getName(), setting.getDescription(), theme);
        topRow.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        final TextView bindPill = new TextView(ctx);
        bindPill.setText(setting.getKeyName());
        bindPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        bindPill.setTypeface(Typeface.DEFAULT_BOLD);
        bindPill.setTextColor(theme.onSurface);
        bindPill.setGravity(Gravity.CENTER);
        bindPill.setMinWidth(dp(ctx, 76));
        bindPill.setPadding(dp(ctx, 18), dp(ctx, 6), dp(ctx, 18), dp(ctx, 6));
        bindPill.setBackground(roundedRect(0xFF1A1922, 16, dp(ctx, 1.2f), 0xFF646073, ctx));
        bindPill.setFocusable(true);
        bindPill.setFocusableInTouchMode(true);
        topRow.addView(bindPill);
        wrapper.addView(topRow);

        // Expandable inline Keybind & Mode (Toggle 单击 / Hold 长按) Drawer when Bind is clicked
        final LinearLayout bindDrawer = new LinearLayout(ctx);
        bindDrawer.setOrientation(LinearLayout.VERTICAL);
        bindDrawer.setVisibility(View.GONE);
        bindDrawer.setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 8));
        bindDrawer.setBackground(roundedRect(0xFF1D1C26, 12, dp(ctx, 1), 0xFF343144, ctx));
        LinearLayout.LayoutParams drawerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        drawerLp.topMargin = dp(ctx, 8);
        wrapper.addView(bindDrawer, drawerLp);

        TextView listenHint = new TextView(ctx);
        listenHint.setText("Press any keyboard/mouse key now, or tap a key below:");
        listenHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        listenHint.setTextColor(theme.onSurfaceVariant);
        bindDrawer.addView(listenHint);

        // Quick key chips for mobile + PC
        final String[] keyNames = new String[]{"None", "R", "K", "G", "V", "C", "Z", "X", "F", "RSHIFT", "LSHIFT", "MOUSE4", "MOUSE5"};
        final int[] keyCodes = new int[]{
                0, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_V,
                KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_F,
                KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_BUTTON_4, KeyEvent.KEYCODE_BUTTON_5
        };

        LinearLayout chipRows = new LinearLayout(ctx);
        chipRows.setOrientation(LinearLayout.VERTICAL);
        chipRows.setPadding(0, dp(ctx, 6), 0, dp(ctx, 8));

        LinearLayout curRow = new LinearLayout(ctx);
        curRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRows.addView(curRow);
        int col = 0;
        for (int i = 0; i < keyNames.length; i++) {
            if (col >= 5) {
                curRow = new LinearLayout(ctx);
                curRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(ctx, 5);
                chipRows.addView(curRow, rlp);
                col = 0;
            }
            final String kn = keyNames[i];
            final int kc = keyCodes[i];
            TextView kChip = new TextView(ctx);
            kChip.setText(kn);
            kChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            kChip.setTypeface(Typeface.DEFAULT_BOLD);
            kChip.setTextColor(theme.onSurface);
            kChip.setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5));
            kChip.setBackground(roundedRect(0xFF282636, 10, dp(ctx, 1), 0xFF4A465C, ctx));
            kChip.setOnClickListener(v -> {
                setting.setKeyBinding(kn, kc);
                bindPill.setText(setting.getKeyName());
                bindPill.setBackground(roundedRect(0xFF1A1922, 16, dp(ctx, 1.2f), 0xFF646073, ctx));
                bindDrawer.setVisibility(View.GONE);
                if (onAnyChange != null) onAnyChange.run();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = dp(ctx, 6);
            curRow.addView(kChip, clp);
            col++;
        }
        bindDrawer.addView(chipRows);

        // Toggle (单击切换) vs Hold (长按按住生效) Mode Selector
        TextView modeHeader = new TextView(ctx);
        modeHeader.setText("Trigger Mode");
        modeHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        modeHeader.setTypeface(Typeface.DEFAULT_BOLD);
        modeHeader.setTextColor(theme.onSurface);
        bindDrawer.addView(modeHeader);

        final LinearLayout modeRadioBox = new LinearLayout(ctx);
        modeRadioBox.setOrientation(LinearLayout.HORIZONTAL);
        modeRadioBox.setPadding(0, dp(ctx, 6), 0, 0);

        final Runnable refreshHoldToggleUI = new Runnable() {
            @Override
            public void run() {
                modeRadioBox.removeAllViews();
                String[] modes = new String[]{"Toggle", "Hold"};
                boolean[] isHoldVal = new boolean[]{false, true};
                for (int i = 0; i < 2; i++) {
                    final boolean targetHold = isHoldVal[i];
                    boolean active = (setting.isHoldMode() == targetHold);
                    LinearLayout opt = new LinearLayout(ctx);
                    opt.setOrientation(LinearLayout.HORIZONTAL);
                    opt.setGravity(Gravity.CENTER_VERTICAL);
                    opt.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
                    if (active) {
                        opt.setBackground(roundedRect(0xFF2B283A, 10, dp(ctx, 1), theme.primary, ctx));
                    } else {
                        opt.setBackground(roundedRect(0xFF22202E, 10, 0, 0, ctx));
                    }
                    AnimatedRadioDotView dot = new AnimatedRadioDotView(ctx, active, theme);
                    opt.addView(dot, new LinearLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16)));

                    TextView tv = new TextView(ctx);
                    tv.setText(modes[i]);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                    tv.setTextColor(active ? theme.onSurface : theme.onSurfaceVariant);
                    tv.setPadding(dp(ctx, 8), 0, 0, 0);
                    opt.addView(tv);

                    opt.setOnClickListener(v -> {
                        setting.setHoldMode(targetHold);
                        this.run();
                        if (onAnyChange != null) onAnyChange.run();
                    });
                    LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    if (i == 0) olp.rightMargin = dp(ctx, 8);
                    modeRadioBox.addView(opt, olp);
                }
            }
        };
        refreshHoldToggleUI.run();
        bindDrawer.addView(modeRadioBox);

        // Clicking Bind pill enters "..." Listening Mode & toggles drawer
        View.OnClickListener toggleListen = v -> {
            boolean opening = bindDrawer.getVisibility() != View.VISIBLE;
            if (opening) {
                bindPill.setText("...");
                bindPill.setBackground(roundedRect(0xFF282240, 16, dp(ctx, 1.5f), theme.primary, ctx));
                bindDrawer.setVisibility(View.VISIBLE);
                bindPill.requestFocus();
            } else {
                bindPill.setText(setting.getKeyName());
                bindPill.setBackground(roundedRect(0xFF1A1922, 16, dp(ctx, 1.2f), 0xFF646073, ctx));
                bindDrawer.setVisibility(View.GONE);
            }
        };
        bindPill.setOnClickListener(toggleListen);
        topRow.setOnClickListener(toggleListen);

        // Listen for physical keyboard / mouse button press while in "..." mode
        bindPill.setOnKeyListener((v, keyCode, event) -> {
            if (bindDrawer.getVisibility() == View.VISIBLE && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_BACK) {
                    setting.setKeyBinding("None", 0);
                } else {
                    String kName = KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "");
                    setting.setKeyBinding(kName, keyCode);
                }
                bindPill.setText(setting.getKeyName());
                bindPill.setBackground(roundedRect(0xFF1A1922, 16, dp(ctx, 1.2f), 0xFF646073, ctx));
                bindDrawer.setVisibility(View.GONE);
                if (onAnyChange != null) onAnyChange.run();
                return true;
            }
            return false;
        });

        // Subtle divider between Keyboard/Mouse Bind and Mobile Shortcut Capsule Button
        View midDiv = new View(ctx);
        midDiv.setBackgroundColor(theme.dividerColor);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1));
        divLp.topMargin = dp(ctx, 10);
        divLp.bottomMargin = dp(ctx, 8);
        wrapper.addView(midDiv, divLp);

        // Mobile Floating Capsule Shortcut Button Row ("Shortcut Button 快捷按钮")
        LinearLayout shortcutRow = new LinearLayout(ctx);
        shortcutRow.setOrientation(LinearLayout.HORIZONTAL);
        shortcutRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout scTextCol = createTitleSubtitleColumn(
                ctx,
                "Shortcut Button",
                "Show floating capsule button on screen",
                theme
        );
        shortcutRow.addView(scTextCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        final AnimatedPillSwitchView scSwitch = new AnimatedPillSwitchView(ctx, setting.isShortcutButtonEnabled(), theme);
        LinearLayout.LayoutParams scSwLp = new LinearLayout.LayoutParams(dp(ctx, 46), dp(ctx, 24));
        scSwLp.leftMargin = dp(ctx, 12);
        shortcutRow.addView(scSwitch, scSwLp);

        View.OnClickListener toggleShortcut = v -> {
            boolean next = !setting.isShortcutButtonEnabled();
            setting.setShortcutButtonEnabled(next);
            scSwitch.setCheckedAnimated(next);
            if (onAnyChange != null) onAnyChange.run();
        };
        shortcutRow.setOnClickListener(toggleShortcut);
        scSwitch.setOnClickListener(toggleShortcut);

        wrapper.addView(shortcutRow);

        return wrapper;
    }

    // ==================== 2. Full-Width Action Button Row ("Edit layout" in TargetHUD at 01:28!) ====================
    private static View buildActionButtonRow(Context ctx, final ActionButtonSetting setting, OnyxTheme theme) {
        LinearLayout wrapper = createCleanRowContainer(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        TextView btn = new TextView(ctx);
        btn.setText(setting.getName());
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(theme.onSurface);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, dp(ctx, 11), 0, dp(ctx, 11));
        btn.setBackground(roundedRect(theme.activeTabPillColor, 18, 0, 0, ctx));
        btn.setOnClickListener(v -> setting.performAction());

        wrapper.addView(btn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    // ==================== 3. Boolean Row (Checkmark ✓ or Pill Switch) ====================
    private static View buildBooleanRow(Context ctx, final BooleanSetting setting, OnyxTheme theme, final Runnable onAnyChange) {
        LinearLayout row = createCleanRowContainer(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = createTitleSubtitleColumn(ctx, setting.getName(), setting.getDescription(), theme);
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        if (setting.isUsePillSwitchStyle()) {
            final AnimatedPillSwitchView pillSwitch = new AnimatedPillSwitchView(ctx, setting.getValue(), theme);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 23));
            swLp.leftMargin = dp(ctx, 12);
            row.addView(pillSwitch, swLp);
            row.setOnClickListener(v -> {
                setting.toggle();
                pillSwitch.setCheckedAnimated(setting.getValue());
                if (onAnyChange != null) onAnyChange.run();
            });
        } else {
            final AnimatedCheckmarkBoxView checkBox = new AnimatedCheckmarkBoxView(ctx, setting.getValue(), theme);
            LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(dp(ctx, 21), dp(ctx, 21));
            cbLp.leftMargin = dp(ctx, 12);
            row.addView(checkBox, cbLp);
            row.setOnClickListener(v -> {
                setting.toggle();
                checkBox.setCheckedAnimated(setting.getValue());
                if (onAnyChange != null) onAnyChange.run();
            });
        }
        return row;
    }

    // ==================== 4. Vertical-Thumb Single Slider Row ====================
    private static View buildVerticalBarSliderRow(Context ctx, final NumberSliderSetting setting, OnyxTheme theme, final Runnable onAnyChange) {
        LinearLayout box = createCleanRowContainer(ctx);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.TOP);

        LinearLayout titleCol = createTitleSubtitleColumn(ctx, setting.getName(), setting.getDescription(), theme);
        topRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        LinearLayout valGroup = new LinearLayout(ctx);
        valGroup.setOrientation(LinearLayout.HORIZONTAL);
        valGroup.setGravity(Gravity.CENTER_VERTICAL);

        final TextView valueLabel = new TextView(ctx);
        valueLabel.setText(setting.getFormattedValue());
        valueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        valueLabel.setTextColor(0xFFD6D2E2);
        valueLabel.setPadding(dp(ctx, 6), 0, dp(ctx, 6), 0);

        final VerticalThumbSliderView sliderCanvas = new VerticalThumbSliderView(ctx, setting, theme, () -> {
            valueLabel.setText(setting.getFormattedValue());
            if (onAnyChange != null) onAnyChange.run();
        });

        TextView minusSmall = createSubtleStepBtn(ctx, "−", theme, () -> {
            float step = setting.getStep() > 0 ? setting.getStep() : (setting.getMax() - setting.getMin()) * 0.05f;
            setting.setValue(setting.getValue() - step);
            valueLabel.setText(setting.getFormattedValue());
            sliderCanvas.invalidate();
            if (onAnyChange != null) onAnyChange.run();
        });

        TextView plusSmall = createSubtleStepBtn(ctx, "+", theme, () -> {
            float step = setting.getStep() > 0 ? setting.getStep() : (setting.getMax() - setting.getMin()) * 0.05f;
            setting.setValue(setting.getValue() + step);
            valueLabel.setText(setting.getFormattedValue());
            sliderCanvas.invalidate();
            if (onAnyChange != null) onAnyChange.run();
        });

        valGroup.addView(minusSmall, new LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)));
        valGroup.addView(valueLabel);
        valGroup.addView(plusSmall, new LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)));
        topRow.addView(valGroup);
        box.addView(topRow);

        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 24));
        sLp.topMargin = dp(ctx, 4);
        box.addView(sliderCanvas, sLp);

        return box;
    }

    private static TextView createSubtleStepBtn(Context ctx, String text, OnyxTheme theme, Runnable onClick) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setTextColor(theme.onSurfaceVariant);
        tv.setBackground(roundedRect(0x18FFFFFF, 5, 0, 0, ctx));
        tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    private static class VerticalThumbSliderView extends View {
        private final NumberSliderSetting setting;
        private final OnyxTheme theme;
        private final Runnable onUpdate;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        public VerticalThumbSliderView(Context context, NumberSliderSetting setting, OnyxTheme theme, Runnable onUpdate) {
            super(context);
            this.setting = setting;
            this.theme = theme;
            this.onUpdate = onUpdate;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = dp(getContext(), 4);
            float w = getWidth() - pad * 2;
            float cy = getHeight() / 2.0f;
            float trackH = dp(getContext(), 3.4f);
            float gap = dp(getContext(), 4.5f);
            float thumbW = dp(getContext(), 3.4f);
            float thumbH = dp(getContext(), 15.0f);

            float ratio = (setting.getValue() - setting.getMin()) / Math.max(0.0001f, setting.getMax() - setting.getMin());
            ratio = Math.max(0.0f, Math.min(1.0f, ratio));
            float thumbX = pad + w * ratio;

            paint.setStyle(Paint.Style.FILL);

            if (thumbX - gap > pad) {
                rect.set(pad, cy - trackH / 2f, thumbX - gap, cy + trackH / 2f);
                paint.setColor(theme.primary);
                canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint);
            }

            if (thumbX + gap < pad + w) {
                rect.set(thumbX + gap, cy - trackH / 2f, pad + w, cy + trackH / 2f);
                paint.setColor(0xFF2B2836);
                canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint);
            }

            rect.set(thumbX - thumbW / 2f, cy - thumbH / 2f, thumbX + thumbW / 2f, cy + thumbH / 2f);
            paint.setColor(theme.primary);
            canvas.drawRoundRect(rect, thumbW / 2f, thumbW / 2f, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                float pad = dp(getContext(), 4);
                float w = Math.max(1.0f, getWidth() - pad * 2);
                float ratio = Math.max(0.0f, Math.min(1.0f, (event.getX() - pad) / w));
                setting.setValue(setting.getMin() + ratio * (setting.getMax() - setting.getMin()));
                invalidate();
                if (onUpdate != null) onUpdate.run();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // ==================== 5. Dual-Thumb Range Slider Row ====================
    private static View buildRangeSliderRow(Context ctx, final RangeSliderSetting setting, OnyxTheme theme, final Runnable onAnyChange) {
        LinearLayout box = createCleanRowContainer(ctx);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);
        header.addView(createTitleSubtitleColumn(ctx, setting.getName(), setting.getDescription(), theme),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        final TextView rangeLabel = new TextView(ctx);
        rangeLabel.setText(setting.getFormattedRange());
        rangeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        rangeLabel.setTextColor(0xFFD6D2E2);
        header.addView(rangeLabel);
        box.addView(header);

        RangeVerticalThumbSliderView rangeView = new RangeVerticalThumbSliderView(ctx, setting, theme, () -> {
            rangeLabel.setText(setting.getFormattedRange());
            if (onAnyChange != null) onAnyChange.run();
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 24));
        rlp.topMargin = dp(ctx, 4);
        box.addView(rangeView, rlp);
        return box;
    }

    private static class RangeVerticalThumbSliderView extends View {
        private final RangeSliderSetting setting;
        private final OnyxTheme theme;
        private final Runnable onUpdate;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean draggingMin = true;

        public RangeVerticalThumbSliderView(Context context, RangeSliderSetting setting, OnyxTheme theme, Runnable onUpdate) {
            super(context);
            this.setting = setting;
            this.theme = theme;
            this.onUpdate = onUpdate;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = dp(getContext(), 4);
            float w = getWidth() - pad * 2;
            float cy = getHeight() / 2.0f;
            float trackH = dp(getContext(), 3.4f);
            float thumbW = dp(getContext(), 3.4f);
            float thumbH = dp(getContext(), 15.0f);
            float gap = dp(getContext(), 4.0f);
            float span = Math.max(0.0001f, setting.getAbsoluteMax() - setting.getAbsoluteMin());

            float xMin = pad + w * ((setting.getMinValue() - setting.getAbsoluteMin()) / span);
            float xMax = pad + w * ((setting.getMaxValue() - setting.getAbsoluteMin()) / span);

            paint.setStyle(Paint.Style.FILL);
            if (xMin - gap > pad) {
                rect.set(pad, cy - trackH / 2f, xMin - gap, cy + trackH / 2f);
                paint.setColor(0xFF2B2836);
                canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint);
            }
            if (xMax - gap > xMin + gap) {
                rect.set(xMin + gap, cy - trackH / 2f, xMax - gap, cy + trackH / 2f);
                paint.setColor(theme.primary);
                canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint);
            }
            if (xMax + gap < pad + w) {
                rect.set(xMax + gap, cy - trackH / 2f, pad + w, cy + trackH / 2f);
                paint.setColor(0xFF2B2836);
                canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint);
            }

            paint.setColor(theme.primary);
            rect.set(xMin - thumbW / 2f, cy - thumbH / 2f, xMin + thumbW / 2f, cy + thumbH / 2f);
            canvas.drawRoundRect(rect, thumbW / 2f, thumbW / 2f, paint);
            rect.set(xMax - thumbW / 2f, cy - thumbH / 2f, xMax + thumbW / 2f, cy + thumbH / 2f);
            canvas.drawRoundRect(rect, thumbW / 2f, thumbW / 2f, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            float pad = dp(getContext(), 4);
            float w = Math.max(1.0f, getWidth() - pad * 2);
            float ratio = Math.max(0.0f, Math.min(1.0f, (event.getX() - pad) / w));
            float touchVal = setting.getAbsoluteMin() + ratio * (setting.getAbsoluteMax() - setting.getAbsoluteMin());

            if (action == MotionEvent.ACTION_DOWN) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                draggingMin = Math.abs(touchVal - setting.getMinValue()) <= Math.abs(touchVal - setting.getMaxValue());
            }
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (draggingMin) {
                    setting.setRange(Math.min(touchVal, setting.getMaxValue()), setting.getMaxValue());
                } else {
                    setting.setRange(setting.getMinValue(), Math.max(touchVal, setting.getMinValue()));
                }
                invalidate();
                if (onUpdate != null) onUpdate.run();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // ==================== 6. 1:1 2D Color Picker (2D Sat-Val Gradient Box + Vertical Hue Pill + "Use accent colour") ====================
    private static View build2DColorPickerRow(Context ctx, final ColorPickerSetting setting, final OnyxTheme theme, final Runnable onAnyChange) {
        LinearLayout box = createCleanRowContainer(ctx);
        box.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8));
        if (setting.isExpanded()) {
            header.setBackground(roundedRect(0xFF252331, 10, 0, 0, ctx));
        }

        header.addView(createTitleSubtitleColumn(ctx, setting.getName(), setting.getDescription(), theme),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        final TextView hexTv = new TextView(ctx);
        hexTv.setText(setting.getHexString());
        hexTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        hexTv.setTextColor(0xFFD6D2E2);
        hexTv.setPadding(0, 0, dp(ctx, 10), 0);
        header.addView(hexTv);

        final View circleSwatch = new View(ctx);
        circleSwatch.setBackground(circleDrawable(setting.getArgbColor(), dp(ctx, 1.2f), 0x44FFFFFF));
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20));
        swLp.rightMargin = dp(ctx, 8);
        header.addView(circleSwatch, swLp);

        final VectorIconView chevron = new VectorIconView(ctx,
                setting.isExpanded() ? IconType.CHEVRON_UP : IconType.CHEVRON_DOWN, 0xFFB0ACC0);
        header.addView(chevron, new LinearLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16)));
        box.addView(header);

        // Expandable 2D HSV Picker + Vertical Rainbow Hue Strip + "Use accent colour" row 
        final LinearLayout drawer = new LinearLayout(ctx);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setVisibility(setting.isExpanded() ? View.VISIBLE : View.GONE);
        drawer.setPadding(dp(ctx, 6), dp(ctx, 10), dp(ctx, 6), dp(ctx, 4));

        Color2DPickerCanvasView picker2D = new Color2DPickerCanvasView(ctx, setting, () -> {
            hexTv.setText(setting.getHexString());
            circleSwatch.setBackground(circleDrawable(setting.getArgbColor(), dp(ctx, 1.2f), 0x44FFFFFF));
            if (onAnyChange != null) onAnyChange.run();
        });
        drawer.addView(picker2D, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 128)));

        // "Use accent colour" checkbox row right underneath the 2D picker 
        LinearLayout accentRow = new LinearLayout(ctx);
        accentRow.setOrientation(LinearLayout.HORIZONTAL);
        accentRow.setGravity(Gravity.CENTER_VERTICAL);
        accentRow.setPadding(dp(ctx, 4), dp(ctx, 12), dp(ctx, 4), dp(ctx, 4));

        TextView useAccentTv = new TextView(ctx);
        useAccentTv.setText("Use accent colour");
        useAccentTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        useAccentTv.setTypeface(Typeface.DEFAULT_BOLD);
        useAccentTv.setTextColor(theme.onSurface);
        accentRow.addView(useAccentTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        final AnimatedCheckmarkBoxView useAccentCb = new AnimatedCheckmarkBoxView(ctx, setting.isUseAccentColor(), theme);
        accentRow.addView(useAccentCb, new LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)));

        accentRow.setOnClickListener(v -> {
            boolean next = !setting.isUseAccentColor();
            setting.setUseAccentColor(next);
            useAccentCb.setCheckedAnimated(next);
            if (next) {
                setting.setArgbColor(theme.getSeedColor());
                hexTv.setText(setting.getHexString());
                circleSwatch.setBackground(circleDrawable(setting.getArgbColor(), dp(ctx, 1.2f), 0x44FFFFFF));
                picker2D.syncHsvFromSetting();
                picker2D.invalidate();
            }
            if (onAnyChange != null) onAnyChange.run();
        });
        drawer.addView(accentRow);
        box.addView(drawer);

        header.setOnClickListener(v -> {
            boolean show = drawer.getVisibility() != View.VISIBLE;
            setting.setExpanded(show);
            chevron.setIconType(show ? IconType.CHEVRON_UP : IconType.CHEVRON_DOWN);
            if (show) {
                header.setBackground(roundedRect(0xFF252331, 10, 0, 0, ctx));
                drawer.setVisibility(View.VISIBLE);
                drawer.setAlpha(0f);
                drawer.setTranslationY(-dp(ctx, 8));
                drawer.animate().alpha(1f).translationY(0f).setDuration(200)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else {
                header.setBackground(null);
                drawer.animate().alpha(0f).translationY(-dp(ctx, 6)).setDuration(160)
                        .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
            }
        });

        return box;
    }

    /**
     * 1:1 2D Saturation-Value Rounded Box on Left + Vertical Rainbow Hue Pill Bar on Right 
     */
    private static class Color2DPickerCanvasView extends View {
        private final ColorPickerSetting setting;
        private final Runnable onUpdate;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF svRect = new RectF();
        private final RectF hueRect = new RectF();
        private final RectF handleRect = new RectF();

        private float hue = 14f;
        private float sat = 0.85f;
        private float val = 0.72f;
        private boolean draggingHue = false;

        public Color2DPickerCanvasView(Context context, ColorPickerSetting setting, Runnable onUpdate) {
            super(context);
            this.setting = setting;
            this.onUpdate = onUpdate;
            syncHsvFromSetting();
        }

        public void syncHsvFromSetting() {
            float[] hsv = new float[3];
            Color.colorToHSV(setting.getArgbColor(), hsv);
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float hueW = dp(getContext(), 18);
            float gap = dp(getContext(), 14);
            float padV = dp(getContext(), 2);

            svRect.set(0, padV, w - hueW - gap, h - padV);
            hueRect.set(w - hueW, padV, w, h - padV);

            float corner = dp(getContext(), 10);

            // 1. Draw 2D Saturation-Value Box (White->Hue horizontal + Transparent->Black vertical)
            int pureHueColor = Color.HSVToColor(new float[]{hue, 1f, 1f});
            Shader valShader = new LinearGradient(
                    svRect.left, svRect.top, svRect.left, svRect.bottom,
                    Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP);
            Shader satShader = new LinearGradient(
                    svRect.left, svRect.top, svRect.right, svRect.top,
                    Color.WHITE, pureHueColor, Shader.TileMode.CLAMP);
            ComposeShader compose = new ComposeShader(valShader, satShader, PorterDuff.Mode.MULTIPLY);

            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(compose);
            canvas.drawRoundRect(svRect, corner, corner, paint);
            paint.setShader(null);

            // Subtle outline around 2D box
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1f));
            paint.setColor(0x33FFFFFF);
            canvas.drawRoundRect(svRect, corner, corner, paint);

            // 2. Draw white ring cursor inside 2D box
            float cx = svRect.left + sat * svRect.width();
            float cy = svRect.top + (1f - val) * svRect.height();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 2.0f));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, dp(getContext(), 6.0f), paint);

            // 3. Draw Vertical Rainbow Hue Pill Bar on the right 
            int[] rainbow = new int[]{
                    Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
            };
            Shader hueShader = new LinearGradient(
                    hueRect.left, hueRect.top, hueRect.left, hueRect.bottom,
                    rainbow, null, Shader.TileMode.CLAMP);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(hueShader);
            canvas.drawRoundRect(hueRect, hueW / 2f, hueW / 2f, paint);
            paint.setShader(null);

            // 4. Draw white horizontal pill slider handle on the vertical Hue bar
            float hy = hueRect.top + (hue / 360f) * hueRect.height();
            float hw = hueW + dp(getContext(), 6);
            float hh = dp(getContext(), 5.5f);
            handleRect.set(hueRect.centerX() - hw / 2f, hy - hh / 2f, hueRect.centerX() + hw / 2f, hy + hh / 2f);
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(handleRect, hh / 2f, hh / 2f, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            float x = event.getX();
            float y = event.getY();

            if (action == MotionEvent.ACTION_DOWN) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                draggingHue = (x >= hueRect.left - dp(getContext(), 8));
            }
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (draggingHue) {
                    float r = (y - hueRect.top) / Math.max(1f, hueRect.height());
                    hue = Math.max(0f, Math.min(360f, r * 360f));
                } else {
                    sat = Math.max(0f, Math.min(1f, (x - svRect.left) / Math.max(1f, svRect.width())));
                    val = 1f - Math.max(0f, Math.min(1f, (y - svRect.top) / Math.max(1f, svRect.height())));
                }
                int newArgb = Color.HSVToColor(255, new float[]{hue, sat, val});
                setting.setArgbColor(newArgb);
                invalidate();
                if (onUpdate != null) onUpdate.run();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // ==================== 7. Vertical Radio Button List  ====================
    private static View buildRadioListRow(Context ctx, final RadioListSetting setting, final OnyxTheme theme, final Runnable onAnyChange) {
        LinearLayout box = createCleanRowContainer(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(createTitleSubtitleColumn(ctx, setting.getName(), setting.getDescription(), theme));

        final LinearLayout listContainer = new LinearLayout(ctx);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(ctx, 6), 0, 0);

        final Runnable rebuildRadios = new Runnable() {
            @Override
            public void run() {
                listContainer.removeAllViews();
                for (final String opt : setting.getOptions()) {
                    boolean active = opt.equals(setting.getSelected());

                    LinearLayout itemRow = new LinearLayout(ctx);
                    itemRow.setOrientation(LinearLayout.HORIZONTAL);
                    itemRow.setGravity(Gravity.CENTER_VERTICAL);
                    itemRow.setPadding(dp(ctx, 8), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7));
                    if (active) {
                        itemRow.setBackground(roundedRect(0xFF242230, 8, 0, 0, ctx));
                    }

                    AnimatedRadioDotView radioDot = new AnimatedRadioDotView(ctx, active, theme);
                    itemRow.addView(radioDot, new LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)));

                    TextView label = new TextView(ctx);
                    label.setText(opt);
                    label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                    label.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                    label.setTextColor(theme.onSurface);
                    label.setPadding(dp(ctx, 12), 0, 0, 0);
                    itemRow.addView(label);

                    itemRow.setOnClickListener(v -> {
                        setting.setSelected(opt);
                        this.run();
                        if (onAnyChange != null) onAnyChange.run();
                    });

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = dp(ctx, 2);
                    listContainer.addView(itemRow, lp);
                }
            }
        };
        rebuildRadios.run();
        box.addView(listContainer);
        return box;
    }

    // ==================== 8. Text Input Setting  ====================
    private static View buildTextInputRow(Context ctx, final TextInputSetting setting, OnyxTheme theme, final Runnable onAnyChange) {
        LinearLayout box = createCleanRowContainer(ctx);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout inputBox = new LinearLayout(ctx);
        inputBox.setOrientation(LinearLayout.VERTICAL);
        inputBox.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
        inputBox.setBackground(roundedRect(0xFF1D1C26, 8, dp(ctx, 1.2f), 0xFF565266, ctx));

        TextView labelTv = new TextView(ctx);
        labelTv.setText(setting.getName());
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        labelTv.setTextColor(theme.onSurfaceVariant);
        inputBox.addView(labelTv);

        EditText editText = new EditText(ctx);
        editText.setText(setting.getText());
        editText.setHint(setting.getPlaceholder());
        editText.setHintTextColor(0xFF6E6A7C);
        editText.setTextColor(theme.onSurface);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        editText.setSingleLine(true);
        editText.setBackgroundColor(Color.TRANSPARENT);
        editText.setPadding(0, dp(ctx, 2), 0, 0);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                setting.setText(s.toString());
                if (onAnyChange != null) onAnyChange.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        inputBox.addView(editText);
        box.addView(inputBox);

        if (setting.getDescription() != null && !setting.getDescription().isEmpty()) {
            TextView sub = new TextView(ctx);
            sub.setText(setting.getDescription());
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            sub.setTextColor(theme.onSurfaceVariant);
            sub.setPadding(dp(ctx, 2), dp(ctx, 4), 0, 0);
            box.addView(sub);
        }

        return box;
    }

    // ==================== 9. Block Filter Grid ====================
    private static View buildBlockFilterRow(Context ctx, final BlockFilterSetting setting, final OnyxTheme theme, final Runnable onAnyChange) {
        LinearLayout box = createCleanRowContainer(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(createTitleSubtitleColumn(ctx, setting.getName(), setting.getDescription(), theme));

        final LinearLayout gridRows = new LinearLayout(ctx);
        gridRows.setOrientation(LinearLayout.VERTICAL);
        gridRows.setPadding(0, dp(ctx, 8), 0, 0);
        box.addView(gridRows);

        final Runnable renderBlocks = new Runnable() {
            @Override
            public void run() {
                gridRows.removeAllViews();
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                gridRows.addView(row);
                int col = 0;
                for (final String block : setting.getAvailableBlocks()) {
                    if (col >= 4) {
                        row = new LinearLayout(ctx);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        rlp.topMargin = dp(ctx, 6);
                        gridRows.addView(row, rlp);
                        col = 0;
                    }
                    boolean on = setting.isBlockActive(block);
                    TextView bChip = new TextView(ctx);
                    bChip.setText(block);
                    bChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                    bChip.setTextColor(on ? theme.onSurface : theme.onSurfaceVariant);
                    bChip.setPadding(dp(ctx, 12), dp(ctx, 5), dp(ctx, 12), dp(ctx, 5));
                    bChip.setBackground(roundedRect(on ? theme.activeTabPillColor : 0xFF1E1D26, 12, 0, 0, ctx));
                    bChip.setOnClickListener(v -> {
                        setting.toggleBlock(block);
                        this.run();
                        if (onAnyChange != null) onAnyChange.run();
                    });
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    clp.rightMargin = dp(ctx, 6);
                    row.addView(bChip, clp);
                    col++;
                }
            }
        };
        renderBlocks.run();
        return box;
    }

    private static LinearLayout createCleanRowContainer(Context ctx) {
        LinearLayout container = new LinearLayout(ctx);
        container.setPadding(0, dp(ctx, 10), 0, dp(ctx, 10));
        return container;
    }

    private static LinearLayout createTitleSubtitleColumn(Context ctx, String title, String subtitle, OnyxTheme theme) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(theme.onSurface);
        col.addView(t);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(ctx);
            sub.setText(subtitle);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            sub.setTextColor(theme.onSurfaceVariant);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(ctx, 2);
            col.addView(sub, lp);
        }
        return col;
    }
}
