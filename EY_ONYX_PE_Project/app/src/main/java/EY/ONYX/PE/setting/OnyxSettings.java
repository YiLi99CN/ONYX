package EY.ONYX.PE.setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete 1:1 Setting Models for EY.ONYX.PE:
 * - KeybindSetting (with real keyCode, keyName, and Hold vs Toggle mode!)
 * - BooleanSetting (Rounded square purple checkmark ✓ in settings, or pill switch on sub-groups like Avatar)
 * - NumberSliderSetting & RangeSliderSetting (Sleek 3dp track + vertical pill thumb bar with gap)
 * - ColorPickerSetting (2D Saturation-Value gradient box + vertical rainbow Hue bar + "Use accent colour" checkbox)
 * - RadioListSetting (Vertical radio button list: Style, Animation, Sort, Weight)
 * - TextInputSetting (Used by NameChanger custom nickname & TargetHUD text format templates)
 * - ActionButtonSetting (Full-width "Edit layout" button inside TargetHUD / CustomGui)
 */
public class OnyxSettings {

    public interface OnValueChangeListener {
        void onValueChanged();
    }

    public abstract static class Setting {
        private final String name;
        private final String description;
        protected OnValueChangeListener changeListener;

        public Setting(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }

        public void setChangeListener(OnValueChangeListener listener) {
            this.changeListener = listener;
        }

        public void notifyChanged() {
            if (changeListener != null) {
                changeListener.onValueChanged();
            }
        }
    }

    /** 1. Keybind Setting with BOTH Keyboard/Mouse KeyCode AND Mobile Floating Shortcut Capsule Button */
    public static class KeybindSetting extends Setting {
        private String keyName;
        private int keyCode;
        private boolean holdMode; // false = Toggle (单击切换), true = Hold (按住开启/松开关闭)
        private boolean shortcutButtonEnabled = false;
        private float shortcutX = -1f;
        private float shortcutY = -1f;

        public KeybindSetting(String name, String description, String defaultKeyName, int defaultKeyCode, boolean defaultHoldMode) {
            super(name, description);
            this.keyName = defaultKeyName;
            this.keyCode = defaultKeyCode;
            this.holdMode = defaultHoldMode;
        }

        public String getKeyName() { return keyName; }
        public int getKeyCode() { return keyCode; }
        public boolean isHoldMode() { return holdMode; }

        public void setKeyBinding(String newKeyName, int newKeyCode) {
            this.keyName = newKeyName == null ? "None" : newKeyName;
            this.keyCode = newKeyCode;
            notifyChanged();
        }

        public void setHoldMode(boolean holdMode) {
            if (this.holdMode != holdMode) {
                this.holdMode = holdMode;
                notifyChanged();
            }
        }

        public boolean isShortcutButtonEnabled() { return shortcutButtonEnabled; }
        public void setShortcutButtonEnabled(boolean enabled) {
            if (this.shortcutButtonEnabled != enabled) {
                this.shortcutButtonEnabled = enabled;
                notifyChanged();
            }
        }

        public float getShortcutX() { return shortcutX; }
        public float getShortcutY() { return shortcutY; }
        public void setShortcutPosition(float x, float y) {
            this.shortcutX = x;
            this.shortcutY = y;
        }
    }

    /** 2. Boolean Setting (Supports both Checkbox ✓ and Pill Switch styles, as seen in TargetHUD "Avatar" vs BedESP "Own bed") */
    public static class BooleanSetting extends Setting {
        private boolean value;
        private final boolean usePillSwitchStyle;

        public BooleanSetting(String name, String description, boolean defaultValue) {
            this(name, description, defaultValue, false);
        }

        public BooleanSetting(String name, String description, boolean defaultValue, boolean usePillSwitchStyle) {
            super(name, description);
            this.value = defaultValue;
            this.usePillSwitchStyle = usePillSwitchStyle;
        }

        public boolean getValue() { return value; }
        public boolean isUsePillSwitchStyle() { return usePillSwitchStyle; }

        public void setValue(boolean newValue) {
            if (this.value != newValue) {
                this.value = newValue;
                notifyChanged();
            }
        }

        public void toggle() { setValue(!value); }
    }

    /** 3. Single Value Slider */
    public static class NumberSliderSetting extends Setting {
        private float value;
        private final float min;
        private final float max;
        private final float step;
        private final String suffix;

        public NumberSliderSetting(String name, String description, float defaultVal, float min, float max, float step, String suffix) {
            super(name, description);
            this.value = defaultVal;
            this.min = min;
            this.max = max;
            this.step = step;
            this.suffix = suffix == null ? "" : suffix;
        }

        public float getValue() { return value; }

        public void setValue(float rawValue) {
            float clamped = Math.max(min, Math.min(max, rawValue));
            if (step > 0.0f) {
                clamped = Math.round((clamped - min) / step) * step + min;
                clamped = Math.max(min, Math.min(max, clamped));
            }
            if (Math.abs(this.value - clamped) > 0.0001f) {
                this.value = clamped;
                notifyChanged();
            }
        }

        public float getMin() { return min; }
        public float getMax() { return max; }
        public float getStep() { return step; }
        public String getSuffix() { return suffix; }

        public String getFormattedValue() {
            if (step >= 1.0f && Math.abs(step - Math.round(step)) < 0.001f && !suffix.equals("s")) {
                if (suffix.isEmpty() && max <= 60.0f) {
                    return String.format("%.2f", value);
                }
                return Math.round(value) + suffix;
            }
            return String.format("%.2f%s", value, suffix);
        }
    }

    /** 4. Dual-Thumb Range Slider */
    public static class RangeSliderSetting extends Setting {
        private float minValue;
        private float maxValue;
        private final float absoluteMin;
        private final float absoluteMax;
        private final float step;
        private final String suffix;

        public RangeSliderSetting(String name, String description, float defaultMin, float defaultMax,
                                  float absoluteMin, float absoluteMax, float step, String suffix) {
            super(name, description);
            this.minValue = defaultMin;
            this.maxValue = defaultMax;
            this.absoluteMin = absoluteMin;
            this.absoluteMax = absoluteMax;
            this.step = step;
            this.suffix = suffix == null ? "" : suffix;
        }

        public float getMinValue() { return minValue; }
        public float getMaxValue() { return maxValue; }
        public float getAbsoluteMin() { return absoluteMin; }
        public float getAbsoluteMax() { return absoluteMax; }

        public void setRange(float newMin, float newMax) {
            float cMin = snap(newMin);
            float cMax = snap(newMax);
            if (cMin > cMax) {
                float t = cMin;
                cMin = cMax;
                cMax = t;
            }
            if (Math.abs(this.minValue - cMin) > 0.0001f || Math.abs(this.maxValue - cMax) > 0.0001f) {
                this.minValue = cMin;
                this.maxValue = cMax;
                notifyChanged();
            }
        }

        private float snap(float v) {
            float c = Math.max(absoluteMin, Math.min(absoluteMax, v));
            if (step > 0.0f) {
                c = Math.round((c - absoluteMin) / step) * step + absoluteMin;
            }
            return Math.max(absoluteMin, Math.min(absoluteMax, c));
        }

        public String getFormattedRange() {
            return String.format("%.1f - %.1f%s", minValue, maxValue, suffix);
        }
    }

    /** 5. 2D Color Picker Setting (with HSV 2D box + Vertical Hue strip + "Use accent colour" checkbox, matching BedESP at 00:43!) */
    public static class ColorPickerSetting extends Setting {
        private int argbColor;
        private boolean useAccentColor;
        private boolean expanded;

        public ColorPickerSetting(String name, String description, int defaultArgb) {
            this(name, description, defaultArgb, false);
        }

        public ColorPickerSetting(String name, String description, int defaultArgb, boolean defaultExpanded) {
            super(name, description);
            this.argbColor = defaultArgb;
            this.useAccentColor = false;
            this.expanded = defaultExpanded;
        }

        public int getArgbColor() { return argbColor; }

        public void setArgbColor(int newArgb) {
            if (this.argbColor != newArgb) {
                this.argbColor = newArgb;
                notifyChanged();
            }
        }

        public boolean isUseAccentColor() { return useAccentColor; }

        public void setUseAccentColor(boolean useAccent) {
            if (this.useAccentColor != useAccent) {
                this.useAccentColor = useAccent;
                notifyChanged();
            }
        }

        public boolean isExpanded() { return expanded; }
        public void setExpanded(boolean expanded) { this.expanded = expanded; }

        public String getHexString() {
            return String.format("#%06X", (0xFFFFFF & argbColor));
        }
    }

    /** 6. Vertical Radio Button List Setting (1:1 matching Array List Style/Animation/Sort at 01:16 & TargetHUD Weight at 01:36!) */
    public static class RadioListSetting extends Setting {
        private final List<String> options;
        private String selected;

        public RadioListSetting(String name, String description, String defaultOption, String... options) {
            super(name, description);
            this.options = new ArrayList<>(Arrays.asList(options));
            this.selected = defaultOption;
        }

        public List<String> getOptions() { return options; }
        public String getSelected() { return selected; }

        public void setSelected(String option) {
            if (options.contains(option) && !option.equals(selected)) {
                this.selected = option;
                notifyChanged();
            }
        }
    }

    /** 7. Text Input Field Setting (1:1 matching NameChanger custom name & TargetHUD Text field at 01:36!) */
    public static class TextInputSetting extends Setting {
        private String text;
        private final String placeholder;

        public TextInputSetting(String name, String description, String defaultText, String placeholder) {
            super(name, description);
            this.text = defaultText;
            this.placeholder = placeholder;
        }

        public String getText() { return text; }

        public void setText(String newText) {
            if (newText != null && !newText.equals(this.text)) {
                this.text = newText;
                notifyChanged();
            }
        }

        public String getPlaceholder() { return placeholder; }
    }

    /** 8. Full-Width Action Button Setting (1:1 matching "Edit layout" button in TargetHUD at 01:28!) */
    public static class ActionButtonSetting extends Setting {
        private final Runnable action;

        public ActionButtonSetting(String buttonLabel, Runnable action) {
            super(buttonLabel, "");
            this.action = action;
        }

        public void performAction() {
            if (action != null) action.run();
        }
    }

    /** 9. Block Filter Grid Setting */
    public static class BlockFilterSetting extends Setting {
        private final List<String> availableBlocks;
        private final Set<String> activeBlocks = new LinkedHashSet<>();

        public BlockFilterSetting(String name, String description, String[] availableBlocks, String[] defaultBlocks) {
            super(name, description);
            this.availableBlocks = new ArrayList<>(Arrays.asList(availableBlocks));
            if (defaultBlocks != null) {
                this.activeBlocks.addAll(Arrays.asList(defaultBlocks));
            }
        }

        public List<String> getAvailableBlocks() { return availableBlocks; }
        public boolean isBlockActive(String block) { return activeBlocks.contains(block); }

        public void toggleBlock(String block) {
            if (activeBlocks.contains(block)) activeBlocks.remove(block);
            else activeBlocks.add(block);
            notifyChanged();
        }
    }
}
