package EY.ONYX.PE.module;

import android.view.KeyEvent;

import android.content.Context;
import EY.ONYX.PE.audio.OnyxHapticEngine;
import EY.ONYX.PE.audio.OnyxSoftSoundEngine;
import EY.ONYX.PE.setting.OnyxSettings.*;
import EY.ONYX.PE.theme.OnyxTheme;
import EY.ONYX.PE.ui.OnyxVectorIcons.IconType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1:1 Exact Module & Config Manager for EY.ONYX.PE (onyx client)
 * Manages module instances, categories, keybinds, and configuration presets:
 * - Combat (6): AntiBot, BedBreaker, BedDefender, JumpReset, KillAura, Velocity
 * - Movement (3): AutoSprint, NoSlow, SafeWalk (exact video 00:25!)
 * - Player (6): ChestStealer, Deposit, FastPlace, InventoryManager, NameChanger, Scaffold (exact video 00:27!)
 * - Render (34): Ambience, Animations, Arrows, BedESP, BlockOverlay, Boxes, Camera, CapeChanger, Chams, ChinaHat,
 *                ClickGUI, ContainerESP, Crosshair, ESP, Fog Blur, FogRemove, Freelook, Fullbright, GlowESP, Hand,
 *                Hurtcam, ItemPhysics, JumpCircles, NoRender, Particle Limiter, Particles, SeeInvisibles,
 *                SkeletonESP, SkinChanger, Skybox, TargetESP, TNTTimer, Trails, Trajectories, Wings, Zoom (exact video 00:50-01:05!)
 * - HUD (9): Array List, CustomGui, DiscordRPC, HUD Layout Editor, Keybinds, Notifications, PotionHUD, TargetHUD, Watermark (exact video 01:25!)
 */
public class OnyxModuleManager {

    public enum Category {
        COMBAT("Combat", IconType.SHIELD),
        MOVEMENT("Movement", IconType.MOVEMENT),
        PLAYER("Player", IconType.PLAYER),
        RENDER("Render", IconType.RENDER),
        HUD("HUD", IconType.HUD),
        THEME("Theme", IconType.PALETTE),
        CONFIGS("Configs", IconType.CONFIGS);

        public final String displayName;
        public final IconType iconType;

        Category(String displayName, IconType iconType) {
            this.displayName = displayName;
            this.iconType = iconType;
        }
    }

    public static class OnyxModule {
        private final String name;
        private final String description;
        private final Category category;
        private String arrayListSuffix;
        private boolean enabled;
        private boolean actionButtonOnly = false;
        private String actionButtonLabel = "Tune";
        private final KeybindSetting keybind;
        private final List<Setting> settings = new ArrayList<>();

        public OnyxModule(String name, String description, Category category, boolean defaultEnabled,
                          String defaultKeyName, int defaultKeyCode, String arrayListSuffix) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.enabled = defaultEnabled;
            this.arrayListSuffix = arrayListSuffix == null ? "" : arrayListSuffix;
            this.keybind = new KeybindSetting("Bind", "", defaultKeyName, defaultKeyCode, false);
        }

        public OnyxModule addSetting(Setting s) {
            settings.add(s);
            return this;
        }

        public boolean getBool(String settingName, boolean fallback) {
            for (Setting s : settings) {
                if (s instanceof BooleanSetting && s.getName().equalsIgnoreCase(settingName)) {
                    return ((BooleanSetting) s).getValue();
                }
            }
            return fallback;
        }

        public float getNumber(String settingName, float fallback) {
            for (Setting s : settings) {
                if (s instanceof NumberSliderSetting && s.getName().equalsIgnoreCase(settingName)) {
                    return ((NumberSliderSetting) s).getValue();
                }
            }
            return fallback;
        }

        public String getMode(String settingName, String fallback) {
            for (Setting s : settings) {
                if (s instanceof RadioListSetting && s.getName().equalsIgnoreCase(settingName)) {
                    return ((RadioListSetting) s).getSelected();
                }
            }
            return fallback;
        }

        public int getColor(String settingName, int fallback) {
            for (Setting s : settings) {
                if (s instanceof ColorPickerSetting && s.getName().equalsIgnoreCase(settingName)) {
                    ColorPickerSetting cps = (ColorPickerSetting) s;
                    return cps.getArgbColor();
                }
            }
            return fallback;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Category getCategory() { return category; }
        public String getArrayListSuffix() { return arrayListSuffix; }
        public void setArrayListSuffix(String suffix) { this.arrayListSuffix = suffix == null ? "" : suffix; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isActionButtonOnly() { return actionButtonOnly; }
        public OnyxModule setActionButtonOnly(boolean buttonOnly, String btnLabel) {
            this.actionButtonOnly = buttonOnly;
            if (btnLabel != null) this.actionButtonLabel = btnLabel;
            return this;
        }
        public String getActionButtonLabel() { return actionButtonLabel; }
        public void toggle() { this.enabled = !this.enabled; }
        public KeybindSetting getKeybind() { return keybind; }
        public List<Setting> getSettings() { return settings; }
    }

    public static class ConfigProfile {
        public String name;
        public boolean loaded;
        public final Map<String, Boolean> moduleStates = new LinkedHashMap<>();
        public final Map<String, String> keybindNames = new LinkedHashMap<>();
        public final Map<String, Integer> keybindCodes = new LinkedHashMap<>();
        public final Map<String, Boolean> keybindHoldModes = new LinkedHashMap<>();
        public int seedColor;
        public float bgTint;
        public String customPlayerName;

        public ConfigProfile(String name, boolean loaded, int seedColor, float bgTint, String customPlayerName) {
            this.name = name;
            this.loaded = loaded;
            this.seedColor = seedColor;
            this.bgTint = bgTint;
            this.customPlayerName = customPlayerName;
        }
    }

    public interface ModuleEventListener {
        void onModuleToggled(OnyxModule module);
        void onConfigApplied(String configName);
        void onSettingsChanged();
    }

    public interface HudEditorCallback {
        void openHudEditorFor(OnyxModule module);
    }

    private final OnyxTheme theme;
    private final List<OnyxModule> modules = new ArrayList<>();
    private final List<ConfigProfile> configs = new ArrayList<>();
    private final List<ModuleEventListener> listeners = new ArrayList<>();
    private HudEditorCallback hudEditorCallback;
    private Runnable transitionTuningCallback;
    private Context appContext;

    public void setAppContext(Context ctx) {
        this.appContext = ctx != null ? ctx.getApplicationContext() : null;
    }

    public void setTransitionTuningCallback(Runnable cb) {
        this.transitionTuningCallback = cb;
    }

    private String customDisplayName = "i_use_pastes";

    // Array List live settings references
    private RadioListSetting arrayListStyleSetting;
    private RadioListSetting arrayListAnimSetting;
    private RadioListSetting arrayListSortSetting;

    public OnyxModuleManager(OnyxTheme theme) {
        this.theme = theme;
        initModules();
        initDefaultConfig();
    }

    public void setHudEditorCallback(HudEditorCallback cb) {
        this.hudEditorCallback = cb;
    }

    public void addListener(ModuleEventListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public String getCustomDisplayName() {
        OnyxModule nc = getModule("NameChanger");
        if (nc != null && nc.isEnabled() && customDisplayName != null && !customDisplayName.trim().isEmpty()) {
            return customDisplayName.trim();
        }
        return "i_use_pastes";
    }

    public String getArrayListStyle() {
        return arrayListStyleSetting != null ? arrayListStyleSetting.getSelected() : "Accent Bar";
    }

    public String getArrayListAnimation() {
        return arrayListAnimSetting != null ? arrayListAnimSetting.getSelected() : "Slide + Fade";
    }

    public String getArrayListSort() {
        return arrayListSortSetting != null ? arrayListSortSetting.getSelected() : "Longest first";
    }

    private void notifySettingsChanged() {
        for (ModuleEventListener l : new ArrayList<>(listeners)) {
            l.onSettingsChanged();
        }
    }

    private void initModules() {
        // ==================== 1. COMBAT  ====================
        OnyxModule antiBot = new OnyxModule("AntiBot", "Stops the combat modules from targeting bots", Category.COMBAT, false, "None", 0, "");
        antiBot.addSetting(new BooleanSetting("Tab check", "Also flags any player whose name is absent from the tab list", false))
               .addSetting(new BooleanSetting("Hide visuals", "Also keep the render modules off flagged bots - no ESP, glow, chams, boxes, skeleton or nam...", true));
        modules.add(antiBot);

        OnyxModule bedBreaker = new OnyxModule("BedBreaker", "Breaks nearby beds with the best tool", Category.COMBAT, false, "None", 0, "");
        bedBreaker.addSetting(new NumberSliderSetting("Range", "Maximum reach distance for mining beds", 4.50f, 3.00f, 6.00f, 0.10f, "m"))
                  .addSetting(new BooleanSetting("Through walls", "Break beds behind defense blocks", true))
                  .addSetting(new BooleanSetting("Auto tool", "Switch to the fastest tool automatically", true));
        modules.add(bedBreaker);

        OnyxModule bedDefender = new OnyxModule("BedDefender", "Walls your bed in with a preset defense pattern", Category.COMBAT, false, "None", 0, "");
        bedDefender.addSetting(new NumberSliderSetting("Place speed", "Blocks placed per second", 8.00f, 1.00f, 20.00f, 1.00f, " bps"))
                   .addSetting(new RadioListSetting("Pattern", "Defense layer pattern", "Standard", "Standard", "Butterfly", "Two Layer"));
        modules.add(bedDefender);

        OnyxModule jumpReset = new OnyxModule("JumpReset", "Jump resets and reduces knockback in combat", Category.COMBAT, false, "None", 0, "");
        jumpReset.addSetting(new NumberSliderSetting("Chance", "Activation probability when hit", 85.00f, 0.00f, 100.00f, 5.00f, "%"))
                 .addSetting(new BooleanSetting("Sprinting only", "Only jump while actively sprinting", true));
        modules.add(jumpReset);

        OnyxModule killAura = new OnyxModule("KillAura", "Automatically aims and kills enemies", Category.COMBAT, false, "K", KeyEvent.KEYCODE_K, "Switch");
        killAura.addSetting(new RadioListSetting("Mode", "Target selection strategy", "Switch", "Single", "Switch", "Multi"))
                .addSetting(new NumberSliderSetting("Reach", "Maximum attack reach distance", 3.30f, 3.00f, 6.00f, 0.05f, "m"))
                .addSetting(new RangeSliderSetting("CPS", "Attack clicks per second", 11.0f, 14.5f, 1.0f, 20.0f, 0.5f, " cps"))
                .addSetting(new BooleanSetting("Keep sprint", "Maintain sprint while attacking", true))
                .addSetting(new BooleanSetting("AutoBlock", "Automatically block with sword between hits", true));
        modules.add(killAura);

        OnyxModule velocity = new OnyxModule("Velocity", "Delays and counter-hit reduces the velocity received", Category.COMBAT, false, "None", 0, "");
        velocity.addSetting(new RadioListSetting("Mode", "Velocity reduction method", "Lag", "Simple", "Lag", "JumpReset"))
                .addSetting(new NumberSliderSetting("Horizontal", "Horizontal knockback retained", 0.00f, 0.00f, 100.00f, 5.00f, "%"))
                .addSetting(new NumberSliderSetting("Vertical", "Vertical knockback retained", 100.00f, 0.00f, 100.00f, 5.00f, "%"));
        modules.add(velocity);

        // ==================== 2. MOVEMENT  ====================
        OnyxModule autoSprint = new OnyxModule("AutoSprint", "Automatically sprints for you", Category.MOVEMENT, false, "None", 0, "");
        autoSprint.addSetting(new BooleanSetting("Omni sprint", "Sprint in all movement directions", false));
        modules.add(autoSprint);

        OnyxModule noSlow = new OnyxModule("NoSlow", "Stops KillAura's blocking from slowing you down", Category.MOVEMENT, false, "None", 0, "");
        noSlow.addSetting(new NumberSliderSetting("Forward", "Forward speed multiplier", 100.00f, 20.00f, 100.00f, 5.00f, "%"))
              .addSetting(new NumberSliderSetting("Strafe", "Sideways speed multiplier", 100.00f, 20.00f, 100.00f, 5.00f, "%"));
        modules.add(noSlow);

        OnyxModule safeWalk = new OnyxModule("SafeWalk", "Prevents you from falling down as if you were sneaking", Category.MOVEMENT, false, "None", 0, "");
        safeWalk.addSetting(new RadioListSetting("Mode", "Edge behavior", "Safe", "Safe", "Eagle sneak"))
                .addSetting(new BooleanSetting("Blocks only", "Only active while holding blocks", true));
        modules.add(safeWalk);

        // ==================== 3. PLAYER  ====================
        OnyxModule chestStealer = new OnyxModule("ChestStealer", "Takes the useful items out of a chest", Category.PLAYER, false, "None", 0, "");
        chestStealer.addSetting(new RangeSliderSetting("Delay", "Time between looting items", 65.0f, 115.0f, 0.0f, 300.0f, 5.0f, "ms"))
                    .addSetting(new BooleanSetting("Auto close", "Close chest automatically when empty", true));
        modules.add(chestStealer);

        OnyxModule deposit = new OnyxModule("Deposit", "Scroll at a chest to put your items in it", Category.PLAYER, false, "None", 0, "");
        deposit.addSetting(new NumberSliderSetting("Delay", "Transfer delay between stacks", 40.00f, 0.00f, 200.00f, 10.00f, "ms"));
        modules.add(deposit);

        OnyxModule fastPlace = new OnyxModule("FastPlace", "Places, interacts and jumps without vanilla's cooldowns", Category.PLAYER, false, "None", 0, "");
        fastPlace.addSetting(new NumberSliderSetting("Delay", "Right-click tick delay", 0.00f, 0.00f, 4.00f, 1.00f, " ticks"))
                 .addSetting(new BooleanSetting("Blocks only", "Only speed up block placement", true));
        modules.add(fastPlace);

        OnyxModule invManager = new OnyxModule("InventoryManager", "Sorts your inventory the way you laid it out", Category.PLAYER, false, "None", 0, "");
        invManager.addSetting(new RangeSliderSetting("Delay", "Delay between inventory clicks", 70.0f, 120.0f, 0.0f, 300.0f, 5.0f, "ms"))
                  .addSetting(new BooleanSetting("Auto armor", "Equip best armor pieces automatically", true))
                  .addSetting(new BooleanSetting("Drop trash", "Drop useless items automatically", true));
        modules.add(invManager);

        // NameChanger: Both in Player (original entry at 00:27) with real-time Custom Nickname input!
        OnyxModule nameChanger = new OnyxModule("NameChanger", "Overrides your own displayed name locally", Category.PLAYER, false, "None", 0, "");
        TextInputSetting customNameInput = new TextInputSetting(
                "Custom name",
                "Local display name shown in TargetHUD, scoreboard and chat",
                customDisplayName,
                "Enter custom username..."
        );
        customNameInput.setChangeListener(() -> {
            customDisplayName = customNameInput.getText();
            notifySettingsChanged();
        });
        nameChanger.addSetting(customNameInput)
                   .addSetting(new BooleanSetting("Hide party members", "Also replace other players' names in tab", false));
        modules.add(nameChanger);

        OnyxModule scaffold = new OnyxModule("Scaffold", "Places blocks under you", Category.PLAYER, false, "R", KeyEvent.KEYCODE_R, "Falling");
        RadioListSetting scafMode = new RadioListSetting("Mode", "Bridging mode", "Falling", "Normal", "Falling", "HypixelKeepY", "Breezily", "GodBridge", "Telly");
        scafMode.setChangeListener(() -> {
            scaffold.setArrayListSuffix(scafMode.getSelected());
            notifySettingsChanged();
        });
        scaffold.addSetting(scafMode)
                .addSetting(new RangeSliderSetting("Place delay", "Delay between block placements", 0.0f, 25.0f, 0.0f, 200.0f, 5.0f, "ms"))
                .addSetting(new BooleanSetting("Consider inventory", "Use blocks from full inventory", true))
                .addSetting(new BooleanSetting("Safe walk", "Prevent stepping off edges", true))
                .addSetting(new BooleanSetting("Show block count", "Display remaining blocks in top Dynamic Island", true));
        modules.add(scaffold);

        // ==================== 4. RENDER  ====================
        OnyxModule ambience = new OnyxModule("Ambience", "Cinematic world lighting and color grading", Category.RENDER, false, "None", 0, "");
        ambience.addSetting(new NumberSliderSetting("Time", "World time of day", 18000.0f, 0.0f, 24000.0f, 500.0f, ""))
                .addSetting(new ColorPickerSetting("Light tint", "World lighting color grade", 0xFF8E6BFF));
        modules.add(ambience);

        OnyxModule animations = new OnyxModule("Animations", "Changes the swinging and blocking item animations", Category.RENDER, false, "None", 0, "Smooth");
        RadioListSetting animStyle = new RadioListSetting("Style", "Sword blocking animation", "Smooth", "Smooth", "1.7", "Exhibition", "Swank", "Slide");
        animStyle.setChangeListener(() -> {
            animations.setArrayListSuffix(animStyle.getSelected());
            notifySettingsChanged();
        });
        animations.addSetting(animStyle)
                  .addSetting(new NumberSliderSetting("Swing speed", "Arm swing speed multiplier", 1.00f, 0.20f, 2.00f, 0.05f, "x"));
        modules.add(animations);

        OnyxModule arrows = new OnyxModule("Arrows", "Points at the players around you", Category.RENDER, false, "None", 0, "Outline");
        arrows.addSetting(new RadioListSetting("Style", "Pointer style", "Outline", "Outline", "Filled"))
              .addSetting(new NumberSliderSetting("Radius", "Distance from crosshair", 48.0f, 20.0f, 150.0f, 2.0f, " px"))
              .addSetting(new ColorPickerSetting("Colour", "Arrow color", 0xFF8E6BFF));
        modules.add(arrows);

        // BedESP: 100% Exact match to Video 00:34 - 00:49 & Screenshot 1!
        OnyxModule bedEsp = new OnyxModule("BedESP", "Highlights beds through walls", Category.RENDER, false, "None", 0, "");
        bedEsp.addSetting(new BooleanSetting("Own bed", "Highlight the bed the tracker believes is yours", true))
              .addSetting(new BooleanSetting("Enemy beds", "Highlight every other bed", true))
              .addSetting(new ColorPickerSetting("Own color", "Colour of your own bed", 0xFFAD3209, true))
              .addSetting(new ColorPickerSetting("Enemy color", "Colour of enemy beds", 0xFF25D910, false))
              .addSetting(new BooleanSetting("Defense", "Outline the blocks walling each bed in, coloured by what they are made of", false))
              .addSetting(new BooleanSetting("Defense tags", "Float the blocks making up each bed's defense above the bed itself", false))
              .addSetting(new BooleanSetting("Beam", "Stand a light beam over every highlighted bed", false))
              .addSetting(new BooleanSetting("Fill", "", true))
              .addSetting(new BooleanSetting("Through walls", "", true))
              .addSetting(new NumberSliderSetting("Line width", "", 1.00f, 0.50f, 5.00f, 0.25f, " px"))
              .addSetting(new NumberSliderSetting("Refresh", "How often the world is rescanned for beds", 500.0f, 100.0f, 2000.0f, 50.0f, "ms"));
        modules.add(bedEsp);

        OnyxModule blockOverlay = new OnyxModule("BlockOverlay", "Highlights the block you are looking at", Category.RENDER, false, "None", 0, "Outline + fill");
        blockOverlay.addSetting(new RadioListSetting("Mode", "Block highlight style", "Outline + fill", "Outline + fill", "Outline", "Fill"))
                    .addSetting(new ColorPickerSetting("Colour", "Block overlay color", 0xFF8E6BFF));
        modules.add(blockOverlay);

        OnyxModule boxes = new OnyxModule("Boxes", "Outlines entities with a 2D or 3D box", Category.RENDER, false, "None", 0, "2D");
        boxes.addSetting(new RadioListSetting("Projection", "Box dimension mode", "2D", "2D", "2D Corner", "3D"))
             .addSetting(new ColorPickerSetting("Colour", "Box color", 0xFF8E6BFF));
        modules.add(boxes);

        OnyxModule camera = new OnyxModule("Camera", "Smooth trailing camera position and third-person orbit", Category.RENDER, false, "None", 0, "");
        camera.addSetting(new NumberSliderSetting("Distance", "Third-person camera distance", 4.00f, 2.00f, 12.00f, 0.50f, "m"))
              .addSetting(new BooleanSetting("Camera clip", "Pass camera through solid walls", true));
        modules.add(camera);

        OnyxModule capeChanger = new OnyxModule("CapeChanger", "Overrides your own cape texture locally", Category.RENDER, false, "None", 0, "Onyx logo");
        capeChanger.addSetting(new RadioListSetting("Cape", "Selected cape design", "Onyx logo", "Onyx logo", "Onyx dark", "Minecon 2015", "Custom file"));
        modules.add(capeChanger);

        OnyxModule chams = new OnyxModule("Chams", "Reskins entities with a graded color tint on the model itself", Category.RENDER, false, "None", 0, "");
        chams.addSetting(new ColorPickerSetting("Tint colour", "Model tint color", 0xFF8E6BFF))
             .addSetting(new BooleanSetting("Through walls", "Render shaded models behind walls", true));
        modules.add(chams);

        OnyxModule chinaHat = new OnyxModule("ChinaHat", "Floats a conical hat over your head", Category.RENDER, false, "None", 0, "Static");
        chinaHat.addSetting(new RadioListSetting("Color mode", "Cone coloring mode", "Static", "Static", "Accent", "Gradient"))
                .addSetting(new NumberSliderSetting("Radius", "Cone base radius", 0.65f, 0.30f, 1.20f, 0.05f, "m"));
        modules.add(chinaHat);

        OnyxModule clickGui = new OnyxModule("ClickGUI", "Opens click GUI", Category.RENDER, true, "RSHIFT", KeyEvent.KEYCODE_SHIFT_RIGHT, "");
        ColorPickerSetting accentSetting = new ColorPickerSetting("Accent", "Seed colour the whole theme is generated from", theme.getSeedColor());
        accentSetting.setChangeListener(() -> theme.setSeedColor(accentSetting.getArgbColor()));
        NumberSliderSetting tintSetting = new NumberSliderSetting("Background tint", "How much the accent colours the panel background", theme.getBackgroundTintPercent(), 0.0f, 100.0f, 5.0f, "%");
        tintSetting.setChangeListener(() -> theme.setBackgroundTintPercent(tintSetting.getValue()));
        NumberSliderSetting scaleSetting = new NumberSliderSetting("Scale", "Interface size", theme.getUiScale(), 0.50f, 1.50f, 0.05f, "x");
        scaleSetting.setChangeListener(() -> theme.setUiScale(scaleSetting.getValue()));
        BooleanSetting glassSetting = new BooleanSetting("Frosted glass", "Blur the world behind the panel instead of the whole screen", theme.isFrostedGlass());
        glassSetting.setChangeListener(() -> theme.setFrostedGlass(glassSetting.getValue()));
        ActionButtonSetting transBtn = new ActionButtonSetting("Transition Effect", () -> {
            if (transitionTuningCallback != null) transitionTuningCallback.run();
        });
        BooleanSetting hapticSetting = new BooleanSetting("Haptic feedback", "Vibrate on toggles and panel interactions", OnyxHapticEngine.isEnabled());
        hapticSetting.setChangeListener(() -> OnyxHapticEngine.setEnabled(hapticSetting.getValue()));
        NumberSliderSetting hapticStrengthSetting = new NumberSliderSetting("Vibration strength", "Tactile feedback intensity", OnyxHapticEngine.getStrength() * 100f, 10f, 100f, 5f, "%");
        hapticStrengthSetting.setChangeListener(() -> OnyxHapticEngine.setStrength(hapticStrengthSetting.getValue() / 100f));
        clickGui.addSetting(accentSetting)
                .addSetting(tintSetting)
                .addSetting(scaleSetting)
                .addSetting(glassSetting)
                .addSetting(transBtn)
                .addSetting(hapticSetting)
                .addSetting(hapticStrengthSetting);
        modules.add(clickGui);

        OnyxModule containerEsp = new OnyxModule("ContainerESP", "Highlights storage blocks", Category.RENDER, false, "None", 0, "");
        containerEsp.addSetting(new BooleanSetting("Chests", "Highlight chests and trapped chests", true))
                    .addSetting(new BooleanSetting("Ender chests", "Highlight ender chests", true))
                    .addSetting(new ColorPickerSetting("Colour", "Highlight color", 0xFFFFB74D));
        modules.add(containerEsp);

        OnyxModule crosshair = new OnyxModule("Crosshair", "Animated dynamic crosshair", Category.RENDER, false, "None", 0, "Accent");
        crosshair.addSetting(new RadioListSetting("Color", "Crosshair color mode", "Accent", "Accent", "Static", "Invert"))
                 .addSetting(new NumberSliderSetting("Gap", "Center gap radius", 3.00f, 0.00f, 12.00f, 0.50f, " px"));
        modules.add(crosshair);

        OnyxModule esp = new OnyxModule("ESP", "A player's name and equipment, positioned around them", Category.RENDER, false, "None", 0, "");
        esp.addSetting(new BooleanSetting("Nametags", "Render custom player name above head", true))
           .addSetting(new BooleanSetting("Equipment", "Show held item and armor icons beside player", true));
        modules.add(esp);

        OnyxModule fogBlur = new OnyxModule("Fog Blur", "Blurs the world based on distance from the camera", Category.RENDER, false, "None", 0, "");
        fogBlur.addSetting(new NumberSliderSetting("Start distance", "Distance where depth blur starts", 24.0f, 4.0f, 64.0f, 2.0f, "m"))
               .addSetting(new NumberSliderSetting("Strength", "Blur kernel strength", 4.0f, 1.0f, 12.0f, 0.5f, " px"));
        modules.add(fogBlur);

        OnyxModule fogRemove = new OnyxModule("FogRemove", "Removes first-person environmental and distance fog", Category.RENDER, false, "None", 0, "");
        fogRemove.addSetting(new BooleanSetting("Distance fog", "Remove terrain render fog", true))
                 .addSetting(new BooleanSetting("Water & lava fog", "Remove liquid fog overlay", true));
        modules.add(fogRemove);

        OnyxModule freelook = new OnyxModule("Freelook", "Look around without turning your player", Category.RENDER, false, "LMENU", KeyEvent.KEYCODE_ALT_LEFT, "");
        freelook.addSetting(new BooleanSetting("Invert pitch", "Invert vertical camera look", false));
        modules.add(freelook);

        OnyxModule fullbright = new OnyxModule("Fullbright", "Makes the world brighter", Category.RENDER, false, "None", 0, "");
        fullbright.addSetting(new RadioListSetting("Mode", "Brightness boost method", "Gamma", "Gamma", "Night vision"));
        modules.add(fullbright);

        OnyxModule glowEsp = new OnyxModule("GlowESP", "Outline glow or liquid-metal fill for selected entities", Category.RENDER, false, "None", 0, "Outline");
        glowEsp.addSetting(new RadioListSetting("Style", "Shader style", "Outline", "Outline", "Liquid-metal"))
               .addSetting(new NumberSliderSetting("Radius", "Glow halo radius", 3.00f, 1.00f, 8.00f, 0.50f, " px"))
               .addSetting(new ColorPickerSetting("Colour", "Glow color", 0xFF8E6BFF));
        modules.add(glowEsp);

        OnyxModule handMod = new OnyxModule("Hand", "Adjust the first-person hand and replace the sword swing", Category.RENDER, false, "None", 0, "");
        handMod.addSetting(new NumberSliderSetting("Scale", "Item model scale", 1.00f, 0.40f, 2.00f, 0.05f, "x"))
               .addSetting(new NumberSliderSetting("Offset X", "Horizontal hand offset", 0.00f, -1.50f, 1.50f, 0.05f, ""))
               .addSetting(new NumberSliderSetting("Offset Y", "Vertical hand offset", 0.00f, -1.50f, 1.50f, 0.05f, ""));
        modules.add(handMod);

        OnyxModule hurtcam = new OnyxModule("Hurtcam", "Soft red flash around the screen when you take damage", Category.RENDER, false, "None", 0, "");
        hurtcam.addSetting(new NumberSliderSetting("Intensity", "Screen flash and tilt intensity", 35.0f, 0.0f, 100.0f, 5.0f, "%"));
        modules.add(hurtcam);

        OnyxModule itemPhysics = new OnyxModule("ItemPhysics", "Makes dropped items lie and rotate on the ground", Category.RENDER, false, "None", 0, "");
        itemPhysics.addSetting(new NumberSliderSetting("Rotation speed", "Tumble speed in air", 1.00f, 0.10f, 3.00f, 0.10f, "x"));
        modules.add(itemPhysics);

        OnyxModule jumpCircles = new OnyxModule("JumpCircles", "Draws a circle where a jump started", Category.RENDER, false, "None", 0, "Ring");
        jumpCircles.addSetting(new RadioListSetting("Style", "Jump circle visual style", "Ring", "Ring", "Disc", "Pulse"))
                   .addSetting(new NumberSliderSetting("Duration", "Fade out time", 1.20f, 0.30f, 3.00f, 0.10f, "s"));
        modules.add(jumpCircles);

        OnyxModule noRender = new OnyxModule("NoRender", "Hides selected vanilla visual effects", Category.RENDER, false, "None", 0, "");
        noRender.addSetting(new BooleanSetting("Fire overlay", "Hide first-person fire", true))
                .addSetting(new BooleanSetting("Hurt shake", "Disable vanilla damage camera tilt", true))
                .addSetting(new BooleanSetting("Pumpkin blur", "Hide pumpkin overlay", true));
        modules.add(noRender);

        OnyxModule particleLimiter = new OnyxModule("Particle Limiter", "Thins vanilla particles in the Hypixel Bed Wars lobby", Category.RENDER, false, "None", 0, "");
        particleLimiter.addSetting(new NumberSliderSetting("Limit", "Maximum ambient particles kept", 25.0f, 0.0f, 100.0f, 5.0f, "%"));
        modules.add(particleLimiter);

        OnyxModule particles = new OnyxModule("Particles", "Glowing fireflies on attacks and while walking", Category.RENDER, false, "None", 0, "");
        particles.addSetting(new NumberSliderSetting("Count", "Fireflies spawned per hit", 8.0f, 1.0f, 25.0f, 1.0f, ""))
                 .addSetting(new ColorPickerSetting("Colour", "Firefly glow color", 0xFF8E6BFF));
        modules.add(particles);

        OnyxModule seeInvis = new OnyxModule("SeeInvisibles", "Renders invisible entities", Category.RENDER, false, "None", 0, "");
        seeInvis.addSetting(new NumberSliderSetting("Alpha", "Opacity of invisible players", 40.0f, 10.0f, 100.0f, 5.0f, "%"));
        modules.add(seeInvis);

        OnyxModule skeletonEsp = new OnyxModule("SkeletonESP", "Draws the bones of nearby players", Category.RENDER, false, "None", 0, "");
        skeletonEsp.addSetting(new NumberSliderSetting("Line width", "Bone line thickness", 1.25f, 0.50f, 3.00f, 0.25f, " px"))
                   .addSetting(new ColorPickerSetting("Colour", "Bone color", 0xFFFFFFFF));
        modules.add(skeletonEsp);

        OnyxModule skinChanger = new OnyxModule("SkinChanger", "Overrides your own skin texture locally", Category.RENDER, false, "None", 0, "Local file");
        skinChanger.addSetting(new RadioListSetting("Source", "Skin texture source", "Local file", "Local file", "Username", "Slim preset"));
        modules.add(skinChanger);

        OnyxModule skybox = new OnyxModule("Skybox", "Animated sky with a fixed custom color", Category.RENDER, false, "None", 0, "Clouds");
        skybox.addSetting(new RadioListSetting("Style", "Skybox atmosphere style", "Clouds", "Clouds", "Gradient", "Starfield"))
              .addSetting(new ColorPickerSetting("Sky colour", "Custom sky tint", 0xFF9C78D8));
        modules.add(skybox);

        OnyxModule targetEsp = new OnyxModule("TargetESP", "Marks the KillAura target", Category.RENDER, false, "None", 0, "Crystals");
        targetEsp.addSetting(new RadioListSetting("Style", "Target marker style", "Crystals", "Crystals", "Ring", "Ghosts"))
                 .addSetting(new ColorPickerSetting("Colour", "TargetESP color", 0xFF8E6BFF));
        modules.add(targetEsp);

        OnyxModule tntTimer = new OnyxModule("TNTTimer", "Shows time remaining before TNT explodes", Category.RENDER, false, "None", 0, "");
        tntTimer.addSetting(new BooleanSetting("Dynamic color", "Interpolate timer tag from green to red", true));
        modules.add(tntTimer);

        OnyxModule trails = new OnyxModule("Trails", "Leaves a fading trail behind you", Category.RENDER, false, "None", 0, "Wall");
        trails.addSetting(new RadioListSetting("Style", "Trail geometry", "Wall", "Wall", "Line", "Dots"))
              .addSetting(new NumberSliderSetting("Duration", "Trail fade duration", 800.0f, 200.0f, 2500.0f, 100.0f, "ms"));
        modules.add(trails);

        OnyxModule trajectories = new OnyxModule("Trajectories", "Predicts where thrown items will land", Category.RENDER, false, "None", 0, "");
        trajectories.addSetting(new BooleanSetting("Highlight landing", "Draw box at impact point", true))
                    .addSetting(new ColorPickerSetting("Colour", "Arc line color", 0xFF8E6BFF));
        modules.add(trajectories);

        OnyxModule wings = new OnyxModule("Wings", "Draws a pair of wings on player backs", Category.RENDER, false, "None", 0, "Angelic");
        wings.addSetting(new RadioListSetting("Style", "Wing design", "Angelic", "Angelic", "Dragon", "Demon"))
             .addSetting(new NumberSliderSetting("Scale", "Wing size", 1.00f, 0.50f, 2.00f, 0.10f, "x"));
        modules.add(wings);

        OnyxModule zoom = new OnyxModule("Zoom", "Smooth hold-to-zoom camera", Category.RENDER, false, "C", KeyEvent.KEYCODE_C, "");
        zoom.getKeybind().setHoldMode(true); // Hold-to-zoom by default!
        zoom.addSetting(new NumberSliderSetting("Magnification", "Zoom FOV multiplier", 4.00f, 1.50f, 10.00f, 0.50f, "x"))
            .addSetting(new BooleanSetting("Smooth camera", "Cinematic mouse smoothing while zoomed", true));
        modules.add(zoom);

        // ==================== 5. HUD  ====================
        // 0. HUDEditor (Top action button entry in HUD panel, NOT a toggleable module)
        OnyxModule hudEditorMod = new OnyxModule(
                "HUDEditor",
                "Drag HUD widgets and resize them freely on screen",
                Category.HUD, false, "None", 0, ""
        ).setActionButtonOnly(true, "Edit");
        hudEditorMod.addSetting(new ActionButtonSetting("Edit layout", () -> {
            if (hudEditorCallback != null) hudEditorCallback.openHudEditorFor(null);
        }));
        modules.add(hudEditorMod);

        // 1. Array List 
        OnyxModule arrayList = new OnyxModule("Array List", "Lists every enabled module", Category.HUD, false, "None", 0, "");
        arrayListStyleSetting = new RadioListSetting("Style", "How each row is painted", "Accent Bar", "Accent Bar", "Pills", "Text");
        arrayListStyleSetting.setChangeListener(this::notifySettingsChanged);
        arrayListAnimSetting = new RadioListSetting("Animation", "How rows enter and leave", "Slide + Fade", "Slide", "Fade", "Slide + Fade", "Scale");
        arrayListAnimSetting.setChangeListener(this::notifySettingsChanged);
        arrayListSortSetting = new RadioListSetting("Sort", "Row ordering", "Longest first", "Longest first", "Shortest first");
        arrayListSortSetting.setChangeListener(this::notifySettingsChanged);
        arrayList.addSetting(arrayListStyleSetting)
                 .addSetting(arrayListAnimSetting)
                 .addSetting(arrayListSortSetting);
        modules.add(arrayList);

        // 2. CustomGui
        OnyxModule customGui = new OnyxModule("CustomGui", "Reskins Minecraft's own HUD and screens", Category.HUD, false, "None", 0, "");
        customGui.addSetting(new BooleanSetting("Reskin hotbar", "Replace vanilla hotbar with rounded Onyx dock", true))
                 .addSetting(new BooleanSetting("Custom username in HUD", "Also sync NameChanger alias across HUD cards", true))
                 .addSetting(customNameInput);
        modules.add(customGui);

        // 3. DiscordRPC
        OnyxModule discordRpc = new OnyxModule("DiscordRPC", "Shows Onyx as your Discord status", Category.HUD, false, "None", 0, "");
        discordRpc.addSetting(new BooleanSetting("Show server", "Broadcast current server IP", false))
                  .addSetting(new BooleanSetting("Show elapsed time", "Display session timer", true));
        modules.add(discordRpc);

        // 4. Keybinds
        OnyxModule keybindsHud = new OnyxModule("Keybinds", "Shows enabled modules with assigned keybinds", Category.HUD, false, "None", 0, "");
        keybindsHud.addSetting(new ActionButtonSetting("Edit layout", () -> {
            if (hudEditorCallback != null) hudEditorCallback.openHudEditorFor(keybindsHud);
        }))
        .addSetting(new BooleanSetting("Show only active", "Only list modules that are currently enabled", true));
        modules.add(keybindsHud);

        // 5. Notifications
        OnyxModule notifications = new OnyxModule("Notifications", "Popups for module toggles and client events", Category.HUD, false, "None", 0, "");
        notifications.addSetting(new ActionButtonSetting("Edit layout", () -> {
            if (hudEditorCallback != null) hudEditorCallback.openHudEditorFor(notifications);
        }))
        .addSetting(new NumberSliderSetting("Duration", "How long popups stay visible", 2.00f, 0.50f, 5.00f, 0.25f, "s"))
        .addSetting(new BooleanSetting("Show progress bar", "Render shrinking timer bar along bottom of toast", true));
        modules.add(notifications);

        // 6. PotionHUD
        OnyxModule potionHud = new OnyxModule("PotionHUD", "Shows your active potion effects", Category.HUD, false, "None", 0, "Time left");
        potionHud.addSetting(new ActionButtonSetting("Edit layout", () -> {
            if (hudEditorCallback != null) hudEditorCallback.openHudEditorFor(potionHud);
        }))
        .addSetting(new BooleanSetting("Time left", "Show remaining effect duration", true));
        modules.add(potionHud);

        // 7. TargetHUD
        final OnyxModule targetHud = new OnyxModule("TargetHUD", "Card showing your target's health and distance", Category.HUD, false, "None", 0, "Screen");
        targetHud.addSetting(new ActionButtonSetting("Edit layout", () -> {
            if (hudEditorCallback != null) hudEditorCallback.openHudEditorFor(targetHud);
        }))
        .addSetting(new NumberSliderSetting("Scale", "Card size", 1.00f, 0.50f, 1.80f, 0.05f, "x"))
        .addSetting(new NumberSliderSetting("Linger", "How long the card holds a target it has lost", 1.00f, 0.00f, 5.00f, 0.25f, "s"))
        .addSetting(new BooleanSetting("Avatar", "", true, true))
        .addSetting(new NumberSliderSetting("Size", "", 1.00f, 0.50f, 2.00f, 0.05f, "x"))
        .addSetting(new NumberSliderSetting("Offset X", "", 0.00f, -20.00f, 20.00f, 1.00f, ""))
        .addSetting(new NumberSliderSetting("Offset Y", "", 0.00f, -20.00f, 20.00f, 1.00f, ""));
        modules.add(targetHud);

        // 8. Watermark (Top-Center Dynamic Island ONLY — completely independent from FPS & Ping!)
        OnyxModule watermark = new OnyxModule("Watermark", "Dynamic island that reacts to what the client is doing", Category.HUD, false, "None", 0, "");
        watermark.addSetting(new ActionButtonSetting("Edit layout", () -> {
            if (hudEditorCallback != null) hudEditorCallback.openHudEditorFor(watermark);
        }))
        .addSetting(new BooleanSetting("Dynamic island alerts", "Expand center onyx island when toggling modules", true))
        .addSetting(new BooleanSetting("Scaffold block counter", "Show remaining block count and progress bar when scaffolding", true))
        .addSetting(new BooleanSetting("BedBreaker status", "Show Bed Idle and breaking percentage inside dynamic island", true));
        modules.add(watermark);

        // 9. FPS & Ping (Top-Left Speedometer FPS & Wi-Fi Ping Pill — independent position & scale!)
        OnyxModule fpsPingMod = new OnyxModule("Stats", "Displays FPS and ping", Category.HUD, false, "None", 0, "");
        fpsPingMod.addSetting(new ActionButtonSetting("Edit layout", () -> {
            if (hudEditorCallback != null) hudEditorCallback.openHudEditorFor(fpsPingMod);
        }))
        .addSetting(new BooleanSetting("Show username", "Include player alias before FPS counter", false))
        .addSetting(new BooleanSetting("Show ping", "Include millisecond latency indicator", true));
        modules.add(fpsPingMod);
    }

    private void initDefaultConfig() {
        ConfigProfile def = new ConfigProfile("default", true, theme.getSeedColor(), theme.getBackgroundTintPercent(), customDisplayName);
        captureIntoProfile(def);
        configs.add(def);
    }

    private void captureIntoProfile(ConfigProfile profile) {
        profile.seedColor = theme.getSeedColor();
        profile.bgTint = theme.getBackgroundTintPercent();
        profile.customPlayerName = customDisplayName;
        profile.moduleStates.clear();
        profile.keybindNames.clear();
        profile.keybindCodes.clear();
        profile.keybindHoldModes.clear();
        for (final OnyxModule m : modules) {
            m.getKeybind().setChangeListener(this::notifySettingsChanged);
            for (final Setting s : m.getSettings()) {
                s.setChangeListener(() -> {
                    if (s instanceof RadioListSetting) {
                        String sn = s.getName();
                        if ("Mode".equalsIgnoreCase(sn) || "Style".equalsIgnoreCase(sn) || "Type".equalsIgnoreCase(sn)) {
                            if (!"Array List".equalsIgnoreCase(m.getName())) {
                                m.setArrayListSuffix(((RadioListSetting) s).getSelected());
                            }
                        }
                    }
                    if ("NameChanger".equalsIgnoreCase(m.getName()) && s instanceof TextInputSetting) {
                        customDisplayName = ((TextInputSetting) s).getText();
                    }
                    notifySettingsChanged();
                });
            }
            profile.moduleStates.put(m.getName(), m.isEnabled());
            profile.keybindNames.put(m.getName(), m.getKeybind().getKeyName());
            profile.keybindCodes.put(m.getName(), m.getKeybind().getKeyCode());
            profile.keybindHoldModes.put(m.getName(), m.getKeybind().isHoldMode());
        }
    }

    public List<OnyxModule> getModules() { return modules; }

    public List<OnyxModule> getModulesByCategory(Category cat) {
        List<OnyxModule> res = new ArrayList<>();
        for (OnyxModule m : modules) {
            if (m.getCategory() == cat) res.add(m);
        }
        return res;
    }

    public List<OnyxModule> searchModules(String query) {
        if (query == null || query.trim().isEmpty()) return new ArrayList<>(modules);
        String q = query.trim().toLowerCase();
        List<OnyxModule> res = new ArrayList<>();
        for (OnyxModule m : modules) {
            if (m.getName().toLowerCase().contains(q) || m.getDescription().toLowerCase().contains(q)) {
                res.add(m);
            }
        }
        return res;
    }

    public OnyxModule getModule(String name) {
        for (OnyxModule m : modules) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    private void handleSpecialModuleState(OnyxModule module) {
        if ("HUDEditor".equalsIgnoreCase(module.getName()) && module.isEnabled()) {
            module.setEnabled(false);
            if (hudEditorCallback != null) {
                hudEditorCallback.openHudEditorFor(null);
            }
        }
    }

    public void toggleModule(OnyxModule module) {
        module.toggle();
        OnyxSoftSoundEngine.playToggleSound(module.isEnabled());
        OnyxHapticEngine.vibrateToggle(appContext, module.isEnabled());
        handleSpecialModuleState(module);
        for (ModuleEventListener l : new ArrayList<>(listeners)) {
            l.onModuleToggled(module);
        }
    }

    public void setModuleEnabled(OnyxModule module, boolean enabled) {
        if (module.isEnabled() != enabled) {
            module.setEnabled(enabled);
            OnyxSoftSoundEngine.playToggleSound(module.isEnabled());
        OnyxHapticEngine.vibrateToggle(appContext, module.isEnabled());
            handleSpecialModuleState(module);
            for (ModuleEventListener l : new ArrayList<>(listeners)) {
                l.onModuleToggled(module);
            }
        }
    }

    /**
     * Real Keybind Dispatcher:
     * Handles physical keyboard / mouse / controller key press & release for both Toggle (单击) and Hold (长按) modes!
     */
    public boolean handleKeyEvent(int keyCode, int action) {
        if (keyCode <= 0) return false;
        boolean handled = false;
        for (OnyxModule m : modules) {
            if ("ClickGUI".equalsIgnoreCase(m.getName())) continue;
            KeybindSetting kb = m.getKeybind();
            if (kb.getKeyCode() == keyCode && !"None".equalsIgnoreCase(kb.getKeyName())) {
                if (kb.isHoldMode()) {
                    if (action == KeyEvent.ACTION_DOWN && !m.isEnabled()) {
                        setModuleEnabled(m, true);
                        handled = true;
                    } else if (action == KeyEvent.ACTION_UP && m.isEnabled()) {
                        setModuleEnabled(m, false);
                        handled = true;
                    }
                } else {
                    if (action == KeyEvent.ACTION_DOWN) {
                        toggleModule(m);
                        handled = true;
                    }
                }
            }
        }
        return handled;
    }

    public List<ConfigProfile> getConfigs() { return configs; }

    public ConfigProfile getLoadedConfig() {
        for (ConfigProfile c : configs) {
            if (c.loaded) return c;
        }
        return configs.isEmpty() ? null : configs.get(0);
    }

    public String createOrSaveAsConfig(String rawName, boolean copyCurrentState) {
        String name = (rawName == null || rawName.trim().isEmpty())
                ? ("config_" + (configs.size() + 1))
                : rawName.trim();
        for (ConfigProfile c : configs) c.loaded = false;

        ConfigProfile profile = new ConfigProfile(name, true, theme.getSeedColor(), theme.getBackgroundTintPercent(), customDisplayName);
        if (copyCurrentState) {
            captureIntoProfile(profile);
        } else {
            for (OnyxModule m : modules) {
                profile.moduleStates.put(m.getName(), "ClickGUI".equals(m.getName()));
                profile.keybindNames.put(m.getName(), m.getKeybind().getKeyName());
                profile.keybindCodes.put(m.getName(), m.getKeybind().getKeyCode());
                profile.keybindHoldModes.put(m.getName(), m.getKeybind().isHoldMode());
            }
        }
        configs.add(0, profile);
        loadConfig(profile);
        return profile.name;
    }

    public void saveToConfig(ConfigProfile profile) {
        captureIntoProfile(profile);
        for (ModuleEventListener l : new ArrayList<>(listeners)) {
            l.onConfigApplied(profile.name);
        }
    }

    public void deleteConfig(ConfigProfile profile) {
        configs.remove(profile);
        if (profile.loaded && !configs.isEmpty()) {
            configs.get(0).loaded = true;
        }
    }

    public void loadConfig(ConfigProfile profile) {
        for (ConfigProfile c : configs) {
            c.loaded = (c == profile);
        }
        for (OnyxModule m : modules) {
            Boolean st = profile.moduleStates.get(m.getName());
            if (st != null) m.setEnabled(st);
            String kName = profile.keybindNames.get(m.getName());
            Integer kCode = profile.keybindCodes.get(m.getName());
            Boolean kHold = profile.keybindHoldModes.get(m.getName());
            if (kName != null && kCode != null) {
                m.getKeybind().setKeyBinding(kName, kCode);
            }
            if (kHold != null) {
                m.getKeybind().setHoldMode(kHold);
            }
        }
        customDisplayName = profile.customPlayerName != null ? profile.customPlayerName : "i_use_pastes";
        theme.setSeedColor(profile.seedColor);
        theme.setBackgroundTintPercent(profile.bgTint);
        for (ModuleEventListener l : new ArrayList<>(listeners)) {
            l.onConfigApplied(profile.name);
        }
    }
}
