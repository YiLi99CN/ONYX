package EY.ONYX.PE.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RadialGradient;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import EY.ONYX.PE.audio.OnyxHapticEngine;
import EY.ONYX.PE.hud.OnyxHudOverlayView;
import EY.ONYX.PE.module.OnyxModuleManager;
import EY.ONYX.PE.module.OnyxModuleManager.Category;
import EY.ONYX.PE.module.OnyxModuleManager.ConfigProfile;
import EY.ONYX.PE.module.OnyxModuleManager.OnyxModule;
import EY.ONYX.PE.setting.OnyxSettings.*;
import EY.ONYX.PE.theme.OnyxTheme;
import EY.ONYX.PE.ui.OnyxSettingViews.AnimatedPillSwitchView;
import EY.ONYX.PE.ui.OnyxVectorIcons.IconType;
import EY.ONYX.PE.ui.OnyxVectorIcons.VectorIconView;

import java.util.ArrayList;
import java.util.List;

import static EY.ONYX.PE.ui.OnyxSettingViews.dp;
import static EY.ONYX.PE.ui.OnyxSettingViews.roundedRect;

/**
 * Main ClickGUI PopupWindow controller featuring category navigation, module cards with
 * irregular liquid purple permeation, full-panel module settings, a dedicated Theme & Color Studio
 * with unlimited multi-color stops, and a clean preset Configs manager.
 */
public class OpenOnyxPopupWindow implements OnyxTheme.ThemeListener, OnyxModuleManager.ModuleEventListener {

    private final Activity activity;
    private final OnyxModuleManager moduleManager;
    private final OnyxTheme theme;
    private final PopupWindow popupWindow;
    private Runnable onDismissCallback;
    private OnyxHudOverlayView hudOverlayView;

    private FrameLayout fullScreenBackdrop;
    private DissolvePanelContainer rootCard;
    private final List<ModuleCardRowView> activeModuleCards = new ArrayList<>();
    private final List<SidebarItemHolder> sidebarHolders = new ArrayList<>();
    private boolean isClosing = false;

    // Screen 1: Main Category + Module List View
    private LinearLayout mainCategoryScreen;
    private LinearLayout topBar;
    private TextView brandTitleTv;
    private LinearLayout searchPillBox;
    private EditText searchInput;
    private VectorIconView searchClearIcon;
    private TextView topRightCategoryTv;
    private LinearLayout leftSidebar;
    private ScrollView moduleListScrollView;
    private LinearLayout moduleListContainer;

    // Screen 2: Dedicated Full-Panel Module Settings View
    private LinearLayout moduleSettingsScreen;
    private LinearLayout settingsTopBar;
    private VectorIconView backArrowIcon;
    private TextView settingsModuleTitleTv;
    private TextView settingsCategoryLabelTv;
    private ScrollView settingsScrollView;
    private LinearLayout settingsItemsContainer;

    // Screen 3: Dedicated Full-Panel Theme & Multi-Color Studio View
    private LinearLayout themeFullScreen;
    private LinearLayout themeTopBar;
    private LinearLayout presetButtonsContainer;
    private LinearLayout customColorStopsContainer;

    // Screen 4: Dedicated Full-Panel Configs View
    private LinearLayout configsFullScreen;
    private LinearLayout configsTopBar;
    private TextView configsCountLabelTv;
    private EditText configNameInput;
    private LinearLayout configsCardsContainer;

    private Category currentCategory = Category.COMBAT;
    private OnyxModule activeSettingsModule = null;
    private LinearLayout transitionFullScreen;
    private LinearLayout transitionModesContainer;
    private LinearLayout transitionSlidersContainer;
    private boolean inTransitionScreen = false;
    private boolean inThemeScreen = false;
    private boolean inConfigsScreen = false;
    private String currentSearchQuery = "";

    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartPopupX;
    private int dragStartPopupY;

    private static class SidebarItemHolder {
        final Category category;
        final FrameLayout iconPill;
        final VectorIconView iconView;
        final TextView label;

        SidebarItemHolder(Category category, FrameLayout iconPill, VectorIconView iconView, TextView label) {
            this.category = category;
            this.iconPill = iconPill;
            this.iconView = iconView;
            this.label = label;
        }
    }

    public OpenOnyxPopupWindow(Activity activity, OnyxModuleManager moduleManager, OnyxTheme theme) {
        this.activity = activity;
        this.moduleManager = moduleManager;
        this.moduleManager.setTransitionTuningCallback(this::openTransitionFullScreen);
        this.theme = theme;

        this.theme.addListener(this);
        this.moduleManager.addListener(this);

        View contentView = buildRootHierarchy(activity);

        this.popupWindow = new PopupWindow(
                contentView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true
        );
        this.popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        this.popupWindow.setOutsideTouchable(false);
        this.popupWindow.setFocusable(true);
        this.popupWindow.setClippingEnabled(false);
        this.popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        applyThemeStylingInPlace();
        rebuildLeftSidebar();
        rebuildModuleList(false);
    }

    public void setHudOverlayView(OnyxHudOverlayView hudView) {
        this.hudOverlayView = hudView;
    }

    public void setOnDismissCallback(final Runnable onDismiss) {
        this.onDismissCallback = onDismiss;
        popupWindow.setOnDismissListener(() -> {
            isClosing = false;
            if (onDismissCallback != null) onDismissCallback.run();
        });
    }

    /**
     * Computes enlarged responsive panel dimensions filling ~92% width and ~90% height
     * so the panel does not leave excessive empty margins on phone screens.
     */
    private int[] computeScaledDimensions() {
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        float scale = theme.getUiScale();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        int targetW = Math.round(dp(activity, 740) * scale);
        int targetH = Math.round(dp(activity, 450) * scale);

        int maxW = Math.round(screenW * 0.92f);
        int maxH = Math.round(screenH * 0.90f);
        int minW = Math.min(maxW, dp(activity, 460));
        int minH = Math.min(maxH, dp(activity, 280));

        return new int[]{
                Math.max(minW, Math.min(maxW, targetW)),
                Math.max(minH, Math.min(maxH, targetH))
        };
    }

    private void syncCardLayoutParams() {
        if (rootCard == null) return;
        int[] dims = computeScaledDimensions();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) rootCard.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(dims[0], dims[1], Gravity.CENTER);
        } else {
            lp.width = dims[0];
            lp.height = dims[1];
            lp.gravity = Gravity.CENTER;
        }
        rootCard.setLayoutParams(lp);
    }

    public void showAtCenter(View anchorDecorView) {
        if (popupWindow.isShowing()) return;
        isClosing = false;
        syncCardLayoutParams();
        for (ModuleCardRowView card : activeModuleCards) {
            card.syncStateImmediate();
        }
        popupWindow.showAtLocation(anchorDecorView, Gravity.CENTER, 0, 0);
        rootCard.startForwardAppearAnimation();
    }

    /**
     * Plays the 3D backwards recede and dissolve close animation before dismissing the PopupWindow.
     */
    public void dismissWithGenieAnimation() {
        dismissWithDissolveAnimation();
    }

    public void dismissWithDissolveAnimation() {
        if (!popupWindow.isShowing()) return;
        if (isClosing) return;
        isClosing = true;
        rootCard.startBackwardDissolveAnimation(() -> {
            isClosing = false;
            if (popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
        });
    }

    public void dismiss() {
        if (popupWindow.isShowing()) {
            isClosing = false;
            popupWindow.dismiss();
        }
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    private boolean isAnyTextFieldFocused() {
        return (searchInput != null && searchInput.hasFocus())
                || (configNameInput != null && configNameInput.hasFocus());
    }

    private View buildRootHierarchy(Context ctx) {
        fullScreenBackdrop = new FrameLayout(ctx) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    int code = event.getKeyCode();
                    if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                        if (activeSettingsModule != null) {
                            closeModuleSettingsScreen();
                            return true;
                        }
                        if (inTransitionScreen) {
                            closeTransitionFullScreen();
                            return true;
                        }
                        if (inThemeScreen) {
                            closeThemeFullScreen();
                            return true;
                        }
                        if (inConfigsScreen) {
                            closeConfigsFullScreen();
                            return true;
                        }
                        dismissWithDissolveAnimation();
                        return true;
                    }
                    // Keyboard RSHIFT / LSHIFT / U toggles the menu closed when not typing in a text field
                    if (code == KeyEvent.KEYCODE_SHIFT_RIGHT
                            || code == KeyEvent.KEYCODE_SHIFT_LEFT
                            || (code == KeyEvent.KEYCODE_U && !isAnyTextFieldFocused())) {
                        dismissWithDissolveAnimation();
                        return true;
                    }
                }
                if (event != null && !isAnyTextFieldFocused()
                        && moduleManager.handleKeyEvent(event.getKeyCode(), event.getAction())) {
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        fullScreenBackdrop.setBackgroundColor(0x2406050B);
        fullScreenBackdrop.setClipChildren(false);
        fullScreenBackdrop.setClipToPadding(false);
        // Intentionally do NOT close when clicking outside rootCard so players never close by accidental touch!

        int[] dims = computeScaledDimensions();
        rootCard = new DissolvePanelContainer(ctx);
        rootCard.setFocusable(true);
        rootCard.setFocusableInTouchMode(true);
        rootCard.setOnClickListener(v -> {});
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(dims[0], dims[1], Gravity.CENTER);
        fullScreenBackdrop.addView(rootCard, cardLp);

        // ==================== 1. Main Category Screen ====================
        mainCategoryScreen = new LinearLayout(ctx);
        mainCategoryScreen.setOrientation(LinearLayout.VERTICAL);
        rootCard.addView(mainCategoryScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        topBar = new LinearLayout(ctx);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(ctx, 10), dp(ctx, 20), dp(ctx, 8));

        // Enlarged uppercase "ONYX" header centered above the 96dp sidebar; tapping closes the main panel
        brandTitleTv = new TextView(ctx);
        brandTitleTv.setText("ONYX");
        brandTitleTv.setSingleLine(true);
        brandTitleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19.5f);
        brandTitleTv.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            brandTitleTv.setLetterSpacing(0.06f);
        }
        brandTitleTv.setGravity(Gravity.CENTER);
        brandTitleTv.setPadding(0, dp(ctx, 2), 0, dp(ctx, 2));
        brandTitleTv.setOnClickListener(v -> dismissWithDissolveAnimation());
        topBar.addView(brandTitleTv, new LinearLayout.LayoutParams(
                dp(ctx, 96), ViewGroup.LayoutParams.WRAP_CONTENT));

        searchPillBox = new LinearLayout(ctx);
        searchPillBox.setOrientation(LinearLayout.HORIZONTAL);
        searchPillBox.setGravity(Gravity.CENTER_VERTICAL);
        searchPillBox.setPadding(dp(ctx, 12), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4));

        VectorIconView searchIcon = new VectorIconView(ctx, IconType.SEARCH, 0xFF9E9AA8);
        searchPillBox.addView(searchIcon, new LinearLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16)));

        searchInput = new EditText(ctx);
        searchInput.setHint("Search modules");
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        searchInput.setSingleLine(true);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchInput.setPadding(dp(ctx, 8), dp(ctx, 2), dp(ctx, 8), dp(ctx, 2));

        searchClearIcon = new VectorIconView(ctx, IconType.CLOSE_X, 0xFF9E9AA8);
        searchClearIcon.setVisibility(View.GONE);
        searchClearIcon.setOnClickListener(v -> searchInput.setText(""));

        searchInput.setOnFocusChangeListener((v, hasFocus) -> updateSearchBarBorder(hasFocus));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                searchClearIcon.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                updateSearchBarBorder(searchInput.hasFocus() || !currentSearchQuery.isEmpty());
                updateSidebarSelectionInPlace();
                rebuildModuleList(false);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchPillBox.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        searchPillBox.addView(searchClearIcon, new LinearLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16)));

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, dp(ctx, 34), 1.0f);
        searchLp.leftMargin = dp(ctx, 8);
        searchLp.rightMargin = dp(ctx, 16);
        topBar.addView(searchPillBox, searchLp);

        topRightCategoryTv = new TextView(ctx);
        topRightCategoryTv.setText(currentCategory.displayName);
        topRightCategoryTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        topRightCategoryTv.setTypeface(Typeface.DEFAULT_BOLD);
        topBar.addView(topRightCategoryTv);

        mainCategoryScreen.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 52)));

        LinearLayout bodyRow = new LinearLayout(ctx);
        bodyRow.setOrientation(LinearLayout.HORIZONTAL);
        mainCategoryScreen.addView(bodyRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        ScrollView leftSidebarScroll = new ScrollView(ctx);
        leftSidebarScroll.setFillViewport(true);
        leftSidebarScroll.setVerticalScrollBarEnabled(false);
        leftSidebarScroll.setHorizontalScrollBarEnabled(false);
        leftSidebarScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        leftSidebar = new LinearLayout(ctx);
        leftSidebar.setOrientation(LinearLayout.VERTICAL);
        leftSidebar.setGravity(Gravity.CENTER_HORIZONTAL);
        leftSidebar.setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 14));
        leftSidebarScroll.addView(leftSidebar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bodyRow.addView(leftSidebarScroll, new LinearLayout.LayoutParams(
                dp(ctx, 96), ViewGroup.LayoutParams.MATCH_PARENT));

        moduleListScrollView = new ScrollView(ctx);
        moduleListScrollView.setFillViewport(true);
        moduleListScrollView.setVerticalScrollBarEnabled(false);
        moduleListScrollView.setHorizontalScrollBarEnabled(false);
        moduleListScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        moduleListContainer = new LinearLayout(ctx);
        moduleListContainer.setOrientation(LinearLayout.VERTICAL);
        moduleListContainer.setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 18), dp(ctx, 18));
        moduleListScrollView.addView(moduleListContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bodyRow.addView(moduleListScrollView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        // ==================== 2. Dedicated Full-Panel Module Settings Screen ====================
        moduleSettingsScreen = new LinearLayout(ctx);
        moduleSettingsScreen.setOrientation(LinearLayout.VERTICAL);
        moduleSettingsScreen.setVisibility(View.GONE);
        rootCard.addView(moduleSettingsScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        settingsTopBar = new LinearLayout(ctx);
        settingsTopBar.setOrientation(LinearLayout.HORIZONTAL);
        settingsTopBar.setGravity(Gravity.CENTER_VERTICAL);
        settingsTopBar.setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 22), dp(ctx, 10));

        LinearLayout backArea = new LinearLayout(ctx);
        backArea.setOrientation(LinearLayout.HORIZONTAL);
        backArea.setGravity(Gravity.CENTER_VERTICAL);
        backArea.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 12), dp(ctx, 4));

        backArrowIcon = new VectorIconView(ctx, IconType.BACK_ARROW, 0xFFF2EFF7);
        backArea.addView(backArrowIcon, new LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)));

        settingsModuleTitleTv = new TextView(ctx);
        settingsModuleTitleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        settingsModuleTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
        settingsModuleTitleTv.setPadding(dp(ctx, 12), 0, 0, 0);
        backArea.addView(settingsModuleTitleTv);

        backArea.setOnClickListener(v -> closeModuleSettingsScreen());
        settingsTopBar.addView(backArea);

        settingsTopBar.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1.0f));

        settingsCategoryLabelTv = new TextView(ctx);
        settingsCategoryLabelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        settingsCategoryLabelTv.setTypeface(Typeface.DEFAULT_BOLD);
        settingsTopBar.addView(settingsCategoryLabelTv);

        enablePanelDragging(settingsTopBar);
        moduleSettingsScreen.addView(settingsTopBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48)));

        settingsScrollView = new ScrollView(ctx);
        settingsScrollView.setFillViewport(true);
        settingsScrollView.setVerticalScrollBarEnabled(false);
        settingsScrollView.setHorizontalScrollBarEnabled(false);
        settingsScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        settingsItemsContainer = new LinearLayout(ctx);
        settingsItemsContainer.setOrientation(LinearLayout.VERTICAL);
        settingsItemsContainer.setPadding(dp(ctx, 42), dp(ctx, 6), dp(ctx, 42), dp(ctx, 20));
        settingsScrollView.addView(settingsItemsContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        moduleSettingsScreen.addView(settingsScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        // ==================== 3. Dedicated Full-Panel Transition Effect Tuning Screen ====================
        buildFullTransitionScreen(ctx);

        // ==================== 4. Dedicated Full-Panel Theme & Color Studio Screen ====================
        buildFullThemeScreen(ctx);

        // ==================== 4. Dedicated Full-Panel Configs Screen ====================
        buildFullConfigsScreen(ctx);

        return fullScreenBackdrop;
    }

    /**
     * Builds the dedicated Theme & Color Studio screen with built-in multi-color presets
     * (Aurora Borealis, Cyber Neon, Sunset Vapor, Ocean Breeze, Sakura Dream, Native Rainbow)
     * and an unlimited Custom Multi-Color stop list where users can keep adding colors.
     */
    /**
     * Builds the dedicated Convergence & Dispersion Effect Tuning Screen ("聚散效果调试面板")
     * with 4 selectable modes and 3 dedicated tuning sliders per mode + live preview button.
     */
    private void buildFullTransitionScreen(Context ctx) {
        transitionFullScreen = new LinearLayout(ctx);
        transitionFullScreen.setOrientation(LinearLayout.VERTICAL);
        transitionFullScreen.setVisibility(View.GONE);
        rootCard.addView(transitionFullScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout transTopBar = new LinearLayout(ctx);
        transTopBar.setOrientation(LinearLayout.HORIZONTAL);
        transTopBar.setGravity(Gravity.CENTER_VERTICAL);
        transTopBar.setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 22), dp(ctx, 10));

        LinearLayout backTransArea = new LinearLayout(ctx);
        backTransArea.setOrientation(LinearLayout.HORIZONTAL);
        backTransArea.setGravity(Gravity.CENTER_VERTICAL);
        backTransArea.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 12), dp(ctx, 4));

        VectorIconView transBackIcon = new VectorIconView(ctx, IconType.BACK_ARROW, 0xFFF2EFF7);
        backTransArea.addView(transBackIcon, new LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)));

        TextView transTitleTv = new TextView(ctx);
        transTitleTv.setText("Transition Effect");
        transTitleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        transTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
        transTitleTv.setTextColor(theme.onSurface);
        transTitleTv.setPadding(dp(ctx, 12), 0, 0, 0);
        backTransArea.addView(transTitleTv);

        backTransArea.setOnClickListener(v -> closeTransitionFullScreen());
        transTopBar.addView(backTransArea);
        transTopBar.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1.0f));

        // Live Preview Button to test close convergence & reopen dispersion immediately
        TextView previewBtn = new TextView(ctx);
        previewBtn.setText("Preview Effect");
        previewBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        previewBtn.setTypeface(Typeface.DEFAULT_BOLD);
        previewBtn.setTextColor(0xFF181426);
        previewBtn.setPadding(dp(ctx, 14), dp(ctx, 6), dp(ctx, 14), dp(ctx, 6));
        previewBtn.setBackground(roundedRect(theme.primary, 14, 0, 0, ctx));
        previewBtn.setOnClickListener(v -> {
            rootCard.startBackwardDissolveAnimation(() -> {
                rootCard.postDelayed(() -> rootCard.startForwardAppearAnimation(), 160);
            });
        });
        transTopBar.addView(previewBtn);

        enablePanelDragging(transTopBar);
        transitionFullScreen.addView(transTopBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48)));

        ScrollView transScroll = new ScrollView(ctx);
        transScroll.setFillViewport(true);
        transScroll.setVerticalScrollBarEnabled(false);
        transScroll.setHorizontalScrollBarEnabled(false);
        transScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout transBody = new LinearLayout(ctx);
        transBody.setOrientation(LinearLayout.VERTICAL);
        transBody.setPadding(dp(ctx, 32), dp(ctx, 6), dp(ctx, 32), dp(ctx, 24));

        TextView modeHeader = new TextView(ctx);
        modeHeader.setText("Convergence-Dispersion Mode");
        modeHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        modeHeader.setTypeface(Typeface.DEFAULT_BOLD);
        modeHeader.setTextColor(theme.onSurface);
        transBody.addView(modeHeader);

        transitionModesContainer = new LinearLayout(ctx);
        transitionModesContainer.setOrientation(LinearLayout.VERTICAL);
        transitionModesContainer.setPadding(0, dp(ctx, 8), 0, dp(ctx, 10));
        transBody.addView(transitionModesContainer);

        transBody.addView(createThinDivider());

        transitionSlidersContainer = new LinearLayout(ctx);
        transitionSlidersContainer.setOrientation(LinearLayout.VERTICAL);
        transitionSlidersContainer.setPadding(0, dp(ctx, 6), 0, 0);
        transBody.addView(transitionSlidersContainer);

        transScroll.addView(transBody, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        transitionFullScreen.addView(transScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        refreshTransitionTuningUI();
    }

    private void refreshTransitionTuningUI() {
        if (transitionModesContainer == null || transitionSlidersContainer == null) return;
        transitionModesContainer.removeAllViews();
        transitionSlidersContainer.removeAllViews();

        final String[][] modeDefs = new String[][]{
                {OnyxTheme.MODE_NORMAL, "Default smooth depth scale & dissolve transition"},
                {OnyxTheme.MODE_LIQUID_FLOW, "Irregular water-flow wave shrinking into a central liquid droplet"},
                {OnyxTheme.MODE_MACOS_WARP, "Pure continuous 2D mesh S-curve warp without horizontal slices"},
                {OnyxTheme.MODE_ISLAND_FLOW, "Shrinks, twists and streams into the top Dynamic Island with elastic bounce"}
        };

        for (final String[] def : modeDefs) {
            final String mKey = def[0];
            final String mDesc = def[1];
            boolean selected = mKey.equals(theme.getTransitionMode());

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
            card.setBackground(roundedRect(
                    selected ? theme.moduleCardOnBackground : theme.moduleCardOffBackground,
                    12, selected ? dp(activity, 1.3f) : 0, theme.primary, activity
            ));

            OnyxSettingViews.AnimatedRadioDotView dot = new OnyxSettingViews.AnimatedRadioDotView(activity, selected, theme);
            card.addView(dot, new LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18)));

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(dp(activity, 12), 0, 0, 0);

            TextView titleTv = new TextView(activity);
            titleTv.setText(mKey);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            titleTv.setTypeface(Typeface.DEFAULT_BOLD);
            titleTv.setTextColor(theme.onSurface);
            textCol.addView(titleTv);

            TextView descTv = new TextView(activity);
            descTv.setText(mDesc);
            descTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            descTv.setTextColor(theme.onSurfaceVariant);
            textCol.addView(descTv);

            card.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            card.setOnClickListener(v -> {
                theme.setTransitionMode(mKey);
                refreshTransitionTuningUI();
            });

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(activity, 6);
            transitionModesContainer.addView(card, clp);
        }

        // Render the 3 dedicated tuning sliders for the currently selected mode
        String activeMode = theme.getTransitionMode();
        if (OnyxTheme.MODE_LIQUID_FLOW.equals(activeMode)) {
            NumberSliderSetting s1 = new NumberSliderSetting(
                    "Delay / Duration",
                    "How long the irregular liquid water-flow convergence takes",
                    theme.getLiquidDurationMs(), 200f, 1200f, 20f, "ms"
            );
            s1.setChangeListener(() -> theme.setLiquidDurationMs(s1.getValue()));
            transitionSlidersContainer.addView(OnyxSettingViews.createSettingRowView(activity, s1, theme, null));
            transitionSlidersContainer.addView(createThinDivider());

            NumberSliderSetting s2 = new NumberSliderSetting(
                    "Water Wave Irregularity",
                    "Amplitude of organic multi-harmonic water ripples along the shrinking edge",
                    theme.getLiquidIrregularity(), 0.20f, 2.50f, 0.05f, "x"
            );
            s2.setChangeListener(() -> theme.setLiquidIrregularity(s2.getValue()));
            transitionSlidersContainer.addView(OnyxSettingViews.createSettingRowView(activity, s2, theme, null));
            transitionSlidersContainer.addView(createThinDivider());

            NumberSliderSetting s3 = new NumberSliderSetting(
                    "Center Vortex Tension",
                    "Rotational swirl and surface tension pulling the liquid into a central droplet",
                    theme.getLiquidTensionSwirl(), 0.20f, 2.50f, 0.05f, "x"
            );
            s3.setChangeListener(() -> theme.setLiquidTensionSwirl(s3.getValue()));
            transitionSlidersContainer.addView(OnyxSettingViews.createSettingRowView(activity, s3, theme, null));

        } else if (OnyxTheme.MODE_MACOS_WARP.equals(activeMode)) {
            NumberSliderSetting s1 = new NumberSliderSetting(
                    "Delay / Duration",
                    "Duration of the continuous slice-free macOS warp transition",
                    theme.getMacosDurationMs(), 200f, 1200f, 20f, "ms"
            );
            s1.setChangeListener(() -> theme.setMacosDurationMs(s1.getValue()));
            transitionSlidersContainer.addView(OnyxSettingViews.createSettingRowView(activity, s1, theme, null));
            transitionSlidersContainer.addView(createThinDivider());

            NumberSliderSetting s2 = new NumberSliderSetting(
                    "Pure Warp Curvature",
                    "Continuous S-curve lateral and vertical surface distortion intensity",
                    theme.getMacosWarpCurvature(), 0.20f, 2.50f, 0.05f, "x"
            );
            s2.setChangeListener(() -> theme.setMacosWarpCurvature(s2.getValue()));
            transitionSlidersContainer.addView(OnyxSettingViews.createSettingRowView(activity, s2, theme, null));
            transitionSlidersContainer.addView(createThinDivider());

            NumberSliderSetting s3 = new NumberSliderSetting(
                    "Funnel Pinch Tightness",
                    "How tightly the continuous mesh necks down toward the focal point",
                    theme.getMacosFunnelPinch(), 0.20f, 2.50f, 0.05f, "x"
            );
            s3.setChangeListener(() -> theme.setMacosFunnelPinch(s3.getValue()));
            transitionSlidersContainer.addView(OnyxSettingViews.createSettingRowView(activity, s3, theme, null));

        } else if (OnyxTheme.MODE_ISLAND_FLOW.equals(activeMode)) {
            NumberSliderSetting s1 = new NumberSliderSetting(
                    "Delay / Duration",
                    "How fast the twisted panel stream flows into the top Dynamic Island",
                    theme.getIslandDurationMs(), 220f, 1200f, 20f, "ms"
            );
            s1.setChangeListener(() -> theme.setIslandDurationMs(s1.getValue()));
            transitionSlidersContainer.addView(OnyxSettingViews.createSettingRowView(activity, s1, theme, null));
            transitionSlidersContainer.addView(createThinDivider());

            NumberSliderSetting s2 = new NumberSliderSetting(
                    "Stream Twist",
                    "Helical spiral and funnel twist while streaming upward into the Dynamic Island",
                    theme.getIslandStreamTwist(), 0.20f, 2.50f, 0.05f, "x"
            );
            s2.setChangeListener(() -> theme.setIslandStreamTwist(s2.getValue()));
            transitionSlidersContainer.addView(OnyxSettingViews.createSettingRowView(activity, s2, theme, null));
            transitionSlidersContainer.addView(createThinDivider());

            NumberSliderSetting s3 = new NumberSliderSetting(
                    "Island Bounce",
                    "Elastic Q-bounce and shockwave impulse when the stream enters the Dynamic Island",
                    theme.getIslandBounceStrength(), 0.20f, 2.50f, 0.05f, "x"
            );
            s3.setChangeListener(() -> theme.setIslandBounceStrength(s3.getValue()));
            transitionSlidersContainer.addView(OnyxSettingViews.createSettingRowView(activity, s3, theme, null));

        } else {
            NumberSliderSetting s1 = new NumberSliderSetting(
                    "Delay / Duration",
                    "Duration of the normal depth dissolve transition",
                    theme.getNormalDurationMs(), 100f, 800f, 20f, "ms"
            );
            s1.setChangeListener(() -> theme.setNormalDurationMs(s1.getValue()));
            transitionSlidersContainer.addView(OnyxSettingViews.createSettingRowView(activity, s1, theme, null));
        }
    }

    private void openTransitionFullScreen() {
        inTransitionScreen = true;
        inThemeScreen = false;
        inConfigsScreen = false;
        activeSettingsModule = null;
        refreshTransitionTuningUI();

        final float shift = dp(activity, 32);
        mainCategoryScreen.animate().alpha(0f).translationX(-shift).setDuration(170).withEndAction(() -> {
            mainCategoryScreen.setVisibility(View.GONE);
            mainCategoryScreen.setTranslationX(0f);
            themeFullScreen.setVisibility(View.GONE);
            if (transitionFullScreen != null) transitionFullScreen.setVisibility(View.GONE);
            configsFullScreen.setVisibility(View.GONE);
            moduleSettingsScreen.setVisibility(View.GONE);

            transitionFullScreen.setVisibility(View.VISIBLE);
            transitionFullScreen.setAlpha(0f);
            transitionFullScreen.setTranslationX(shift);
            transitionFullScreen.animate().alpha(1f).translationX(0f).setDuration(190)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }).start();
    }

    private void closeTransitionFullScreen() {
        if (!inTransitionScreen) return;
        inTransitionScreen = false;
        final float shift = dp(activity, 32);
        transitionFullScreen.animate().alpha(0f).translationX(shift).setDuration(170).withEndAction(() -> {
            transitionFullScreen.setVisibility(View.GONE);
            transitionFullScreen.setTranslationX(0f);
            mainCategoryScreen.setVisibility(View.VISIBLE);
            mainCategoryScreen.setAlpha(0f);
            mainCategoryScreen.setTranslationX(-shift);
            mainCategoryScreen.animate().alpha(1f).translationX(0f).setDuration(190)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }).start();
    }

    private void buildFullThemeScreen(Context ctx) {
        themeFullScreen = new LinearLayout(ctx);
        themeFullScreen.setOrientation(LinearLayout.VERTICAL);
        themeFullScreen.setVisibility(View.GONE);
        rootCard.addView(themeFullScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        themeTopBar = new LinearLayout(ctx);
        themeTopBar.setOrientation(LinearLayout.HORIZONTAL);
        themeTopBar.setGravity(Gravity.CENTER_VERTICAL);
        themeTopBar.setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 22), dp(ctx, 10));

        LinearLayout backThemeArea = new LinearLayout(ctx);
        backThemeArea.setOrientation(LinearLayout.HORIZONTAL);
        backThemeArea.setGravity(Gravity.CENTER_VERTICAL);
        backThemeArea.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 12), dp(ctx, 4));

        VectorIconView themeBackIcon = new VectorIconView(ctx, IconType.BACK_ARROW, 0xFFF2EFF7);
        backThemeArea.addView(themeBackIcon, new LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)));

        TextView themeTitleTv = new TextView(ctx);
        themeTitleTv.setText("Theme");
        themeTitleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        themeTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
        themeTitleTv.setTextColor(theme.onSurface);
        themeTitleTv.setPadding(dp(ctx, 12), 0, 0, 0);
        backThemeArea.addView(themeTitleTv);

        backThemeArea.setOnClickListener(v -> closeThemeFullScreen());
        themeTopBar.addView(backThemeArea);
        themeTopBar.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1.0f));

        enablePanelDragging(themeTopBar);
        themeFullScreen.addView(themeTopBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48)));

        ScrollView themeScroll = new ScrollView(ctx);
        themeScroll.setFillViewport(true);
        themeScroll.setVerticalScrollBarEnabled(false);
        themeScroll.setHorizontalScrollBarEnabled(false);
        themeScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout themeBody = new LinearLayout(ctx);
        themeBody.setOrientation(LinearLayout.VERTICAL);
        themeBody.setPadding(dp(ctx, 32), dp(ctx, 6), dp(ctx, 32), dp(ctx, 24));

        // 1. Preset Modes Header & Chip Grid
        TextView presetTitle = new TextView(ctx);
        presetTitle.setText("Color Presets");
        presetTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        presetTitle.setTypeface(Typeface.DEFAULT_BOLD);
        presetTitle.setTextColor(theme.onSurface);
        themeBody.addView(presetTitle);

        presetButtonsContainer = new LinearLayout(ctx);
        presetButtonsContainer.setOrientation(LinearLayout.VERTICAL);
        presetButtonsContainer.setPadding(0, dp(ctx, 8), 0, dp(ctx, 8));
        themeBody.addView(presetButtonsContainer);

        themeBody.addView(createThinDivider());

        // 2. Cycle Speed Slider
        NumberSliderSetting speedSetting = new NumberSliderSetting(
                "Cycle speed", "", theme.getCycleSpeed(), 0.20f, 3.50f, 0.10f, "x"
        );
        speedSetting.setChangeListener(() -> theme.setCycleSpeed(speedSetting.getValue()));
        themeBody.addView(OnyxSettingViews.createSettingRowView(ctx, speedSetting, theme, null));

        themeBody.addView(createThinDivider());

        // 3. Static Seed Color Picker
        ColorPickerSetting seedPicker = new ColorPickerSetting("Seed colour", "", theme.getSeedColor());
        seedPicker.setChangeListener(() -> theme.setSeedColor(seedPicker.getArgbColor()));
        themeBody.addView(OnyxSettingViews.createSettingRowView(ctx, seedPicker, theme, null));

        themeBody.addView(createThinDivider());

        // 4. Unlimited Custom Color Stops Section ("+ Add Color")
        LinearLayout customHeaderRow = new LinearLayout(ctx);
        customHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        customHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        customHeaderRow.setPadding(0, dp(ctx, 10), 0, dp(ctx, 6));

        TextView customListTitle = new TextView(ctx);
        customListTitle.setText("Custom Color Sequence");
        customListTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        customListTitle.setTypeface(Typeface.DEFAULT_BOLD);
        customListTitle.setTextColor(theme.onSurface);
        customHeaderRow.addView(customListTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        LinearLayout addColorBtn = createIconTextPillButton(ctx, IconType.PLUS, "Add Color",
                theme.activeTabPillColor, 0, 0, theme.onSurface, () -> {
                    int[] palette = new int[]{0xFF8E6BFF, 0xFFFF6EC7, 0xFF00E5FF, 0xFF5CE1E6, 0xFFFF9966, 0xFF70E000};
                    int nextColor = palette[theme.getCustomColorStops().size() % palette.length];
                    theme.addCustomColorStop(nextColor);
                    theme.setColorMode("Custom Multi-Color");
                    refreshPresetChipsUI();
                    rebuildCustomColorStopsUI();
                });
        customHeaderRow.addView(addColorBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 32)));

        themeBody.addView(customHeaderRow);

        customColorStopsContainer = new LinearLayout(ctx);
        customColorStopsContainer.setOrientation(LinearLayout.VERTICAL);
        themeBody.addView(customColorStopsContainer);

        themeBody.addView(createThinDivider());

        // 5. Background Tint & UI Scale Sliders
        NumberSliderSetting tintSetting = new NumberSliderSetting(
                "Background tint", "", theme.getBackgroundTintPercent(), 0f, 100f, 1f, "%"
        );
        tintSetting.setChangeListener(() -> theme.setBackgroundTintPercent(tintSetting.getValue()));
        themeBody.addView(OnyxSettingViews.createSettingRowView(ctx, tintSetting, theme, null));

        themeBody.addView(createThinDivider());

        NumberSliderSetting scaleSetting = new NumberSliderSetting(
                "Scale", "", theme.getUiScale(), 0.70f, 1.30f, 0.05f, "x"
        );
        scaleSetting.setChangeListener(() -> {
            theme.setUiScale(scaleSetting.getValue());
            syncCardLayoutParams();
        });
        themeBody.addView(OnyxSettingViews.createSettingRowView(ctx, scaleSetting, theme, null));

        themeScroll.addView(themeBody, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        themeFullScreen.addView(themeScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        refreshPresetChipsUI();
        rebuildCustomColorStopsUI();
    }

    private void refreshPresetChipsUI() {
        if (presetButtonsContainer == null) return;
        presetButtonsContainer.removeAllViews();
        String[] presets = OnyxTheme.COLOR_PRESETS;
        LinearLayout row = null;
        for (int i = 0; i < presets.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rlp.topMargin = dp(activity, 8);
                presetButtonsContainer.addView(row, rlp);
            }
            final String presetName = presets[i];
            final boolean selected = presetName.equalsIgnoreCase(theme.getColorMode());
            final int[] stops = "Custom Multi-Color".equalsIgnoreCase(presetName)
                    ? toIntArray(theme.getCustomColorStops())
                    : OnyxTheme.getPresetStops(presetName);

            ThemePresetCardView cardView = new ThemePresetCardView(activity, presetName, stops, selected);
            cardView.setOnClickListener(v -> {
                OnyxHapticEngine.vibrateClick(activity);
                theme.setColorMode(presetName);
                refreshPresetChipsUI();
            });

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    0, dp(activity, 68), 1.0f);
            if (i % 3 != 0) clp.leftMargin = dp(activity, 8);
            row.addView(cardView, clp);
        }
    }

    private static class ThemePresetCardView extends View {
        private final String label;
        private final int[] stops;
        private final boolean selected;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clipPath = new Path();

        public ThemePresetCardView(Context ctx, String label, int[] stops, boolean selected) {
            super(ctx);
            this.label = label;
            this.stops = (stops != null && stops.length >= 2) ? stops : new int[]{0xFF8E6BFF, 0xFF5CE1E6};
            this.selected = selected;
            this.strokePaint.setStyle(Paint.Style.STROKE);
            this.textPaint.setTextAlign(Paint.Align.CENTER);
            this.textPaint.setTextSize(dp(ctx, 11.0f));
            this.textPaint.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            float strokeW = selected ? dp(getContext(), 1.8f) : dp(getContext(), 0.9f);
            float half = strokeW * 0.5f + 0.5f;
            float r = dp(getContext(), 10f);
            float innerR = Math.max(2f, r - half);
            float labelH = dp(getContext(), 24f);
            float gradBottom = h - labelH;

            rect.set(half, half, w - half, h - half);
            canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(rect, innerR, innerR, Path.Direction.CW);
            canvas.clipPath(clipPath);

            // Top gradient swatch
            fillPaint.setShader(new LinearGradient(half, half, w - half, half, stops, null, Shader.TileMode.CLAMP));
            canvas.drawRect(half, half, w - half, gradBottom, fillPaint);
            fillPaint.setShader(null);

            // Bottom dark label bar
            fillPaint.setColor(0xFF1B1A24);
            canvas.drawRect(half, gradBottom, w - half, h - half, fillPaint);

            // Centered label text
            textPaint.setColor(selected ? 0xFFFFFFFF : 0xFFCFCBE0);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = gradBottom + (labelH - (fm.descent - fm.ascent)) * 0.5f - fm.ascent;
            canvas.drawText(label, w * 0.5f, textY, textPaint);

            // Crisp top-right indicator dot when selected
            if (selected) {
                float dotR = dp(getContext(), 4.2f);
                fillPaint.setColor(0xFFFFFFFF);
                canvas.drawCircle(w - dp(getContext(), 10f), dp(getContext(), 10f), dotR, fillPaint);
            }
            canvas.restore();

            // Strictly inset rounded border (zero corner clipping/bleed)
            strokePaint.setStrokeWidth(strokeW);
            strokePaint.setColor(selected ? 0xFFFFFFFF : 0x26FFFFFF);
            canvas.drawRoundRect(rect, innerR, innerR, strokePaint);
        }
    }

    private static int[] toIntArray(java.util.List<Integer> list) {
        if (list == null || list.size() < 2) return new int[]{0xFF9E6BFF, 0xFF5CE1E6};
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private void rebuildCustomColorStopsUI() {
        if (customColorStopsContainer == null) return;
        customColorStopsContainer.removeAllViews();
        List<Integer> stops = theme.getCustomColorStops();

        for (int i = 0; i < stops.size(); i++) {
            final int stopIdx = i;
            int colVal = stops.get(i);

            LinearLayout stopWrapper = new LinearLayout(activity);
            stopWrapper.setOrientation(LinearLayout.VERTICAL);

            ColorPickerSetting stopPicker = new ColorPickerSetting("Color #" + (i + 1), "", colVal);
            stopPicker.setChangeListener(() -> {
                theme.updateCustomColorStop(stopIdx, stopPicker.getArgbColor());
            });
            View pickerView = OnyxSettingViews.createSettingRowView(activity, stopPicker, theme, null);
            stopWrapper.addView(pickerView);

            if (stops.size() > 2) {
                TextView removeTv = new TextView(activity);
                removeTv.setText("Remove Color #" + (i + 1));
                removeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                removeTv.setTextColor(0xFFFF8A9B);
                removeTv.setPadding(dp(activity, 10), 0, dp(activity, 10), dp(activity, 6));
                removeTv.setOnClickListener(v -> {
                    theme.removeCustomColorStop(stopIdx);
                    rebuildCustomColorStopsUI();
                });
                stopWrapper.addView(removeTv);
            }

            customColorStopsContainer.addView(stopWrapper);
        }
    }

    private void openThemeFullScreen() {
        inThemeScreen = true;
        inConfigsScreen = false;
        activeSettingsModule = null;
        refreshPresetChipsUI();
        rebuildCustomColorStopsUI();
        updateSidebarSelectionInPlace();

        final float shift = dp(activity, 32);
        mainCategoryScreen.animate().alpha(0f).translationX(-shift).setDuration(170).withEndAction(() -> {
            mainCategoryScreen.setVisibility(View.GONE);
            mainCategoryScreen.setTranslationX(0f);
            if (transitionFullScreen != null) transitionFullScreen.setVisibility(View.GONE);
            configsFullScreen.setVisibility(View.GONE);
            moduleSettingsScreen.setVisibility(View.GONE);

            themeFullScreen.setVisibility(View.VISIBLE);
            themeFullScreen.setAlpha(0f);
            themeFullScreen.setTranslationX(shift);
            themeFullScreen.animate().alpha(1f).translationX(0f).setDuration(190)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }).start();
    }

    private void closeThemeFullScreen() {
        if (!inThemeScreen) return;
        inThemeScreen = false;
        updateSidebarSelectionInPlace();
        final float shift = dp(activity, 32);
        themeFullScreen.animate().alpha(0f).translationX(shift).setDuration(170).withEndAction(() -> {
            themeFullScreen.setVisibility(View.GONE);
            themeFullScreen.setTranslationX(0f);
            mainCategoryScreen.setVisibility(View.VISIBLE);
            mainCategoryScreen.setAlpha(0f);
            mainCategoryScreen.setTranslationX(-shift);
            mainCategoryScreen.animate().alpha(1f).translationX(0f).setDuration(190)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }).start();
    }

    /**
     * Builds the full-panel Configs preset screen without redundant explanatory subtitles.
     */
    private void buildFullConfigsScreen(Context ctx) {
        configsFullScreen = new LinearLayout(ctx);
        configsFullScreen.setOrientation(LinearLayout.VERTICAL);
        configsFullScreen.setVisibility(View.GONE);
        rootCard.addView(configsFullScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        configsTopBar = new LinearLayout(ctx);
        configsTopBar.setOrientation(LinearLayout.HORIZONTAL);
        configsTopBar.setGravity(Gravity.CENTER_VERTICAL);
        configsTopBar.setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 22), dp(ctx, 10));

        LinearLayout backCfgArea = new LinearLayout(ctx);
        backCfgArea.setOrientation(LinearLayout.HORIZONTAL);
        backCfgArea.setGravity(Gravity.CENTER_VERTICAL);
        backCfgArea.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 12), dp(ctx, 4));

        VectorIconView cfgBackIcon = new VectorIconView(ctx, IconType.BACK_ARROW, 0xFFF2EFF7);
        backCfgArea.addView(cfgBackIcon, new LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)));

        TextView cfgTitleTv = new TextView(ctx);
        cfgTitleTv.setText("Configs");
        cfgTitleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        cfgTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
        cfgTitleTv.setTextColor(theme.onSurface);
        cfgTitleTv.setPadding(dp(ctx, 12), 0, 0, 0);
        backCfgArea.addView(cfgTitleTv);

        backCfgArea.setOnClickListener(v -> closeConfigsFullScreen());
        configsTopBar.addView(backCfgArea);

        configsTopBar.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1.0f));

        configsCountLabelTv = new TextView(ctx);
        configsCountLabelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        configsCountLabelTv.setTextColor(theme.onSurfaceVariant);
        configsTopBar.addView(configsCountLabelTv);

        enablePanelDragging(configsTopBar);
        configsFullScreen.addView(configsTopBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48)));

        ScrollView cfgScroll = new ScrollView(ctx);
        cfgScroll.setFillViewport(true);
        cfgScroll.setVerticalScrollBarEnabled(false);
        cfgScroll.setHorizontalScrollBarEnabled(false);
        cfgScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout cfgBody = new LinearLayout(ctx);
        cfgBody.setOrientation(LinearLayout.VERTICAL);
        cfgBody.setPadding(dp(ctx, 20), dp(ctx, 6), dp(ctx, 20), dp(ctx, 20));

        LinearLayout actionRow = new LinearLayout(ctx);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        configNameInput = new EditText(ctx);
        configNameInput.setHint("Config name");
        configNameInput.setHintTextColor(0xFF8E8A9D);
        configNameInput.setTextColor(theme.onSurface);
        configNameInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        configNameInput.setSingleLine(true);
        configNameInput.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        configNameInput.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        configNameInput.setBackground(roundedRect(0xFF1A1923, 6, dp(ctx, 1.2f), 0xFF565266, ctx));

        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(0, dp(ctx, 42), 1.0f);
        inLp.rightMargin = dp(ctx, 10);
        actionRow.addView(configNameInput, inLp);

        LinearLayout newBtn = createIconTextPillButton(ctx, IconType.PLUS, "New",
                theme.activeTabPillColor, 0, 0, theme.onSurface, () -> {
                    String name = configNameInput.getText().toString();
                    moduleManager.createOrSaveAsConfig(name, false);
                    configNameInput.setText("");
                    refreshConfigsList();
                });
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 36));
        newLp.rightMargin = dp(ctx, 8);
        actionRow.addView(newBtn, newLp);

        LinearLayout saveAsBtn = createIconTextPillButton(ctx, IconType.CONFIGS, "Save as",
                0xFF1A1923, dp(ctx, 1.2f), 0xFF5D596E, 0xFFC5B3FF, () -> {
                    String name = configNameInput.getText().toString();
                    moduleManager.createOrSaveAsConfig(name, true);
                    configNameInput.setText("");
                    refreshConfigsList();
                });
        LinearLayout.LayoutParams saLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 36));
        saLp.rightMargin = dp(ctx, 8);
        actionRow.addView(saveAsBtn, saLp);

        LinearLayout openFolderBtn = createIconTextPillButton(ctx, IconType.FOLDER, "Open folder",
                0x00000000, 0, 0, 0xFFC5B3FF, () -> {
                    Toast.makeText(ctx, ctx.getFilesDir().getAbsolutePath() + "/configs", Toast.LENGTH_SHORT).show();
                });
        actionRow.addView(openFolderBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 36)));

        cfgBody.addView(actionRow);

        configsCardsContainer = new LinearLayout(ctx);
        configsCardsContainer.setOrientation(LinearLayout.VERTICAL);
        configsCardsContainer.setPadding(0, dp(ctx, 12), 0, 0);
        cfgBody.addView(configsCardsContainer);

        cfgScroll.addView(cfgBody, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        configsFullScreen.addView(cfgScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
    }

    private LinearLayout createIconTextPillButton(Context ctx, IconType icon, String text,
                                                  int bgColor, int strokeW, int strokeColor, int contentColor, Runnable onClick) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(ctx, 14), dp(ctx, 6), dp(ctx, 14), dp(ctx, 6));
        if (bgColor != 0 || strokeW > 0) {
            box.setBackground(roundedRect(bgColor, 18, strokeW, strokeColor, ctx));
        }

        VectorIconView iv = new VectorIconView(ctx, icon, contentColor);
        box.addView(iv, new LinearLayout.LayoutParams(dp(ctx, 15), dp(ctx, 15)));

        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(contentColor);
        tv.setPadding(dp(ctx, 7), 0, 0, 0);
        box.addView(tv);

        box.setOnClickListener(v -> onClick.run());
        return box;
    }

    private void refreshConfigsList() {
        configsCardsContainer.removeAllViews();
        List<ConfigProfile> list = moduleManager.getConfigs();
        configsCountLabelTv.setText(list.size() + (list.size() == 1 ? " config" : " configs"));

        for (final ConfigProfile profile : list) {
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 12), dp(activity, 14));
            card.setBackground(roundedRect(
                    profile.loaded ? theme.moduleCardOnBackground : theme.moduleCardOffBackground,
                    12, 0, 0, activity));

            TextView nameTv = new TextView(activity);
            nameTv.setText(profile.name);
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            nameTv.setTextColor(theme.onSurface);
            card.addView(nameTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            LinearLayout saveBtn = createIconTextPillButton(activity, IconType.CONFIGS, "Save",
                    0x00000000, 0, 0, 0xFFC5B3FF, () -> {
                        moduleManager.saveToConfig(profile);
                        refreshConfigsList();
                    });
            card.addView(saveBtn);

            FrameLayout moreBtn = new FrameLayout(activity);
            VectorIconView dotsIcon = new VectorIconView(activity, IconType.MORE_VERT, theme.onSurface);
            moreBtn.addView(dotsIcon, new FrameLayout.LayoutParams(dp(activity, 18), dp(activity, 18), Gravity.CENTER));
            moreBtn.setOnClickListener(v -> {
                String[] actions = new String[]{"Load", "Save", "Delete"};
                new AlertDialog.Builder(activity)
                        .setTitle(profile.name)
                        .setItems(actions, (d, which) -> {
                            if (which == 0) {
                                moduleManager.loadConfig(profile);
                            } else if (which == 1) {
                                moduleManager.saveToConfig(profile);
                            } else if (which == 2) {
                                moduleManager.deleteConfig(profile);
                            }
                            refreshConfigsList();
                        })
                        .show();
            });
            card.addView(moreBtn, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));

            card.setOnClickListener(v -> {
                moduleManager.loadConfig(profile);
                refreshConfigsList();
            });

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(activity, 8);
            configsCardsContainer.addView(card, clp);
        }
    }

    private void openConfigsFullScreen() {
        inConfigsScreen = true;
        inThemeScreen = false;
        activeSettingsModule = null;
        refreshConfigsList();
        updateSidebarSelectionInPlace();

        final float shift = dp(activity, 32);
        mainCategoryScreen.animate().alpha(0f).translationX(-shift).setDuration(170).withEndAction(() -> {
            mainCategoryScreen.setVisibility(View.GONE);
            mainCategoryScreen.setTranslationX(0f);
            if (transitionFullScreen != null) transitionFullScreen.setVisibility(View.GONE);
            themeFullScreen.setVisibility(View.GONE);
            moduleSettingsScreen.setVisibility(View.GONE);

            configsFullScreen.setVisibility(View.VISIBLE);
            configsFullScreen.setAlpha(0f);
            configsFullScreen.setTranslationX(shift);
            configsFullScreen.animate().alpha(1f).translationX(0f).setDuration(190)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }).start();
    }

    private void closeConfigsFullScreen() {
        if (!inConfigsScreen) return;
        inConfigsScreen = false;
        updateSidebarSelectionInPlace();
        final float shift = dp(activity, 32);
        configsFullScreen.animate().alpha(0f).translationX(shift).setDuration(170).withEndAction(() -> {
            configsFullScreen.setVisibility(View.GONE);
            configsFullScreen.setTranslationX(0f);
            mainCategoryScreen.setVisibility(View.VISIBLE);
            mainCategoryScreen.setAlpha(0f);
            mainCategoryScreen.setTranslationX(-shift);
            mainCategoryScreen.animate().alpha(1f).translationX(0f).setDuration(190)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }).start();
    }

    private void updateSearchBarBorder(boolean focusedOrHasText) {
        if (focusedOrHasText) {
            searchPillBox.setBackground(roundedRect(theme.searchBarBackground, 16, dp(activity, 1.4f), 0xFF9E9AA8, activity));
        } else {
            searchPillBox.setBackground(roundedRect(theme.searchBarBackground, 16, 0, 0, activity));
        }
    }

    private void enablePanelDragging(View handleView) {
        handleView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartRawX = event.getRawX();
                    dragStartRawY = event.getRawY();
                    dragStartPopupX = Math.round(rootCard.getTranslationX());
                    dragStartPopupY = Math.round(rootCard.getTranslationY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - dragStartRawX;
                    float dy = event.getRawY() - dragStartRawY;
                    rootCard.setTranslationX(dragStartPopupX + dx);
                    rootCard.setTranslationY(dragStartPopupY + dy);
                    return true;
            }
            return false;
        });
    }

    /**
     * Updates colors in-place without destroying or rebuilding any view hierarchy,
     * eliminating UI flicker when dragging color pickers or cycling colors.
     */
    private void applyThemeStylingInPlace() {
        if (rootCard != null) rootCard.invalidate();
        if (brandTitleTv != null) brandTitleTv.setTextColor(theme.onSurface);
        if (searchPillBox != null) {
            updateSearchBarBorder(searchInput != null && (searchInput.hasFocus() || !currentSearchQuery.isEmpty()));
        }
        if (searchInput != null) {
            searchInput.setTextColor(theme.onSurface);
            searchInput.setHintTextColor(theme.onSurfaceVariant);
        }
        if (topRightCategoryTv != null) topRightCategoryTv.setTextColor(theme.onSurfaceVariant);
        if (settingsModuleTitleTv != null) settingsModuleTitleTv.setTextColor(theme.onSurface);
        if (settingsCategoryLabelTv != null) settingsCategoryLabelTv.setTextColor(theme.onSurfaceVariant);
        if (backArrowIcon != null) backArrowIcon.setIconColor(theme.onSurface);
        updateSidebarSelectionInPlace();
        for (ModuleCardRowView card : activeModuleCards) {
            card.invalidate();
            card.pillSwitch.invalidate();
        }
    }

    private void rebuildLeftSidebar() {
        leftSidebar.removeAllViews();
        sidebarHolders.clear();
        Category[] allCategories = new Category[]{
                Category.COMBAT, Category.MOVEMENT, Category.PLAYER, Category.RENDER,
                Category.HUD, Category.THEME, Category.CONFIGS
        };
        for (Category cat : allCategories) {
            if (cat == Category.THEME) {
                View sep = new View(activity);
                sep.setBackgroundColor(theme.dividerColor);
                LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 1));
                sepLp.topMargin = dp(activity, 6);
                sepLp.bottomMargin = dp(activity, 6);
                leftSidebar.addView(sep, sepLp);
            }
            leftSidebar.addView(buildSidebarItem(cat));
        }
        updateSidebarSelectionInPlace();
    }

    private boolean isCategorySelected(Category cat) {
        if (inThemeScreen) return cat == Category.THEME;
        if (inConfigsScreen) return cat == Category.CONFIGS;
        return (cat == currentCategory) && (currentSearchQuery == null || currentSearchQuery.trim().isEmpty());
    }

    private void updateSidebarSelectionInPlace() {
        for (SidebarItemHolder h : sidebarHolders) {
            boolean sel = isCategorySelected(h.category);
            if (sel) {
                h.iconPill.setBackground(roundedRect(theme.activeTabPillColor, 13, 0, 0, activity));
            } else {
                h.iconPill.setBackground(null);
            }
            h.iconView.setIconColor(sel ? theme.onSurface : theme.onSurfaceVariant);
            h.label.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            h.label.setTextColor(sel ? theme.onSurface : theme.onSurfaceVariant);
        }
    }

    private View buildSidebarItem(final Category cat) {
        LinearLayout itemCol = new LinearLayout(activity);
        itemCol.setOrientation(LinearLayout.VERTICAL);
        itemCol.setGravity(Gravity.CENTER_HORIZONTAL);
        itemCol.setPadding(0, dp(activity, 4), 0, dp(activity, 5));

        final FrameLayout iconPill = new FrameLayout(activity);
        final VectorIconView iconView = new VectorIconView(activity, cat.iconType, theme.onSurfaceVariant);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(activity, 17), dp(activity, 17), Gravity.CENTER);
        iconPill.addView(iconView, iconLp);

        itemCol.addView(iconPill, new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 24)));

        TextView label = new TextView(activity);
        label.setText(cat.displayName);
        label.setSingleLine(true);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(activity, 2);
        itemCol.addView(label, labelLp);

        sidebarHolders.add(new SidebarItemHolder(cat, iconPill, iconView, label));

        itemCol.setOnClickListener(v -> {
            if (cat == Category.THEME) {
                openThemeFullScreen();
                return;
            }
            if (cat == Category.CONFIGS) {
                openConfigsFullScreen();
                return;
            }
            inThemeScreen = false;
            inConfigsScreen = false;
            if (currentCategory != cat || (currentSearchQuery != null && !currentSearchQuery.isEmpty())) {
                currentCategory = cat;
                if (searchInput.getText().length() > 0) {
                    searchInput.setText("");
                }
                updateSidebarSelectionInPlace();
                iconPill.setScaleX(0.76f);
                iconPill.setScaleY(0.76f);
                iconPill.animate().scaleX(1.0f).scaleY(1.0f).setDuration(240)
                        .setInterpolator(new OvershootInterpolator(1.6f)).start();
                rebuildModuleList(true);
            }
        });

        return itemCol;
    }

    private void rebuildModuleList(boolean animate) {
        moduleListContainer.removeAllViews();
        activeModuleCards.clear();

        if (currentSearchQuery != null && !currentSearchQuery.trim().isEmpty()) {
            List<OnyxModule> results = moduleManager.searchModules(currentSearchQuery);
            topRightCategoryTv.setText(results.size() + (results.size() == 1 ? " result" : " results"));
            for (OnyxModule m : results) {
                ModuleCardRowView card = new ModuleCardRowView(activity, m);
                activeModuleCards.add(card);
                moduleListContainer.addView(card);
            }
        } else {
            topRightCategoryTv.setText(currentCategory.displayName);
            List<OnyxModule> list = moduleManager.getModulesByCategory(currentCategory);
            for (OnyxModule m : list) {
                ModuleCardRowView card = new ModuleCardRowView(activity, m);
                activeModuleCards.add(card);
                moduleListContainer.addView(card);
            }
        }

        if (animate) {
            int count = moduleListContainer.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = moduleListContainer.getChildAt(i);
                child.setAlpha(0f);
                child.setTranslationX(dp(activity, 26));
                child.setTranslationY(dp(activity, 8));
                child.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .translationY(0f)
                        .setStartDelay(Math.min(i * 16L, 140L))
                        .setDuration(230)
                        .setInterpolator(new DecelerateInterpolator(1.5f))
                        .start();
            }
        }
    }

    /**
     * Module card row with left-to-right irregular organic liquid purple permeation animation.
     */
    private class ModuleCardRowView extends LinearLayout {
        final OnyxModule module;
        final AnimatedPillSwitchView pillSwitch;
        private float permeateProgress;
        private float wavePhase = 0f;
        private ValueAnimator waveAnimator;

        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint permeatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tendrilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint crestPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final Path clipPath = new Path();
        private final Path wavePath = new Path();
        private final Path tendrilPath = new Path();
        private final Path crestPath = new Path();

        ModuleCardRowView(Context ctx, final OnyxModule module) {
            super(ctx);
            this.module = module;
            this.permeateProgress = module.isEnabled() ? 1.0f : 0.0f;

            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(ctx, 16), dp(ctx, 11), dp(ctx, 16), dp(ctx, 11));
            setWillNotDraw(false);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(ctx, 1.0f));

            crestPaint.setStyle(Paint.Style.STROKE);
            crestPaint.setStrokeWidth(dp(ctx, 2.2f));
            crestPaint.setStrokeCap(Paint.Cap.ROUND);

            LinearLayout textCol = new LinearLayout(ctx);
            textCol.setOrientation(VERTICAL);

            TextView titleTv = new TextView(ctx);
            titleTv.setText(module.getName());
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            titleTv.setTypeface(Typeface.DEFAULT_BOLD);
            titleTv.setTextColor(theme.onSurface);
            textCol.addView(titleTv);

            TextView descTv = new TextView(ctx);
            descTv.setText(module.getDescription());
            descTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.2f);
            descTv.setTextColor(theme.onSurfaceVariant);
            LayoutParams dlp = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = dp(ctx, 2);
            textCol.addView(descTv, dlp);

            addView(textCol, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            pillSwitch = new AnimatedPillSwitchView(ctx, module.isEnabled(), theme);
            if (module.isActionButtonOnly()) {
                pillSwitch.setVisibility(View.GONE);
                TextView actionBtn = new TextView(ctx);
                actionBtn.setText(module.getActionButtonLabel());
                actionBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                actionBtn.setTypeface(Typeface.DEFAULT_BOLD);
                actionBtn.setTextColor(0xFF181426);
                actionBtn.setGravity(Gravity.CENTER);
                actionBtn.setPadding(dp(ctx, 14), dp(ctx, 6), dp(ctx, 14), dp(ctx, 6));
                actionBtn.setBackground(roundedRect(theme.primary, 14, 0, 0, ctx));
                LayoutParams btnLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                btnLp.leftMargin = dp(ctx, 12);
                addView(actionBtn, btnLp);

                OnClickListener openTune = v -> {
                    OnyxHapticEngine.vibrateClick(ctx);
                    if ("HUDEditor".equalsIgnoreCase(module.getName())) {
                        for (Setting st : module.getSettings()) {
                            if (st instanceof ActionButtonSetting) {
                                ((ActionButtonSetting) st).performAction();
                                break;
                            }
                        }
                    } else {
                        openTransitionFullScreen();
                    }
                };
                actionBtn.setOnClickListener(openTune);
                setOnClickListener(openTune);
            } else {
                LayoutParams swLp = new LayoutParams(dp(ctx, 46), dp(ctx, 24));
                swLp.leftMargin = dp(ctx, 12);
                addView(pillSwitch, swLp);

                pillSwitch.setOnClickListener(v -> moduleManager.toggleModule(module));
                setOnClickListener(v -> openModuleSettingsScreen(module));
            }

            LayoutParams cardLp = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = dp(ctx, 8);
            setLayoutParams(cardLp);
        }

        void syncStateImmediate() {
            if (waveAnimator != null) waveAnimator.cancel();
            permeateProgress = module.isEnabled() ? 1.0f : 0.0f;
            pillSwitch.setChecked(module.isEnabled(), false);
            invalidate();
        }

        void syncStateAnimated() {
            boolean enabled = module.isEnabled();
            pillSwitch.setChecked(enabled, true);
            float target = enabled ? 1.0f : 0.0f;
            if (Math.abs(permeateProgress - target) < 0.005f && (waveAnimator == null || !waveAnimator.isRunning())) {
                return;
            }
            if (waveAnimator != null) waveAnimator.cancel();
            final float start = permeateProgress;
            waveAnimator = ValueAnimator.ofFloat(0f, 1f);
            waveAnimator.setDuration(450);
            waveAnimator.setInterpolator(new DecelerateInterpolator(1.35f));
            waveAnimator.addUpdateListener(a -> {
                float frac = (Float) a.getAnimatedValue();
                permeateProgress = start + (target - start) * frac;
                wavePhase += 0.25f;
                invalidate();
            });
            waveAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float r = dp(getContext(), 12);
            cardRect.set(0, 0, w, h);

            basePaint.setColor(theme.moduleCardOffBackground);
            canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(cardRect, r, r, Path.Direction.CW);
            canvas.clipPath(clipPath);
            canvas.drawRect(cardRect, basePaint);

            if (permeateProgress > 0.002f) {
                if (permeateProgress >= 0.996f) {
                    permeatePaint.setShader(new LinearGradient(
                            0, 0, w, h,
                            0xFF443868, theme.moduleCardOnBackground, Shader.TileMode.CLAMP
                    ));
                    canvas.drawRect(cardRect, permeatePaint);
                } else {
                    float edgeFactor = (float) Math.sin(permeateProgress * Math.PI);
                    float maxAmp = dp(getContext(), 22f) * edgeFactor;
                    float baseFrontX = -maxAmp + (w + maxAmp * 2.4f) * permeateProgress;

                    wavePath.reset();
                    tendrilPath.reset();
                    crestPath.reset();

                    wavePath.moveTo(0, 0);
                    tendrilPath.moveTo(0, 0);

                    int steps = 28;
                    for (int i = 0; i <= steps; i++) {
                        float t = i / (float) steps;
                        float y = t * h;

                        float w1 = (float) Math.sin(t * Math.PI * 2.3f + wavePhase) * maxAmp * 0.55f;
                        float w2 = (float) Math.sin(t * Math.PI * 5.2f - wavePhase * 1.45f) * maxAmp * 0.30f;
                        float w3 = (float) Math.cos(t * Math.PI * 8.5f + wavePhase * 2.15f) * maxAmp * 0.18f;
                        float streamBulge = (float) Math.sin(t * Math.PI * 1.7f) * dp(getContext(), 8.5f) * edgeFactor;

                        float xMain = baseFrontX + w1 + w2 + w3 + streamBulge;
                        float xTendril = xMain + dp(getContext(), 13f) * edgeFactor
                                + (float) Math.cos(t * Math.PI * 6.4f - wavePhase * 1.8f) * dp(getContext(), 7.5f) * edgeFactor;

                        wavePath.lineTo(xMain, y);
                        tendrilPath.lineTo(xTendril, y);
                        if (i == 0) {
                            crestPath.moveTo(xMain, y);
                        } else {
                            crestPath.lineTo(xMain, y);
                        }
                    }

                    wavePath.lineTo(0, h);
                    wavePath.close();

                    tendrilPath.lineTo(0, h);
                    tendrilPath.close();

                    tendrilPaint.setColor(0x585A4692);
                    canvas.drawPath(tendrilPath, tendrilPaint);

                    permeatePaint.setShader(new LinearGradient(
                            0, 0, Math.max(dp(getContext(), 36), baseFrontX + maxAmp), 0,
                            new int[]{0xFF473A6D, theme.moduleCardOnBackground, 0xFF564588},
                            new float[]{0f, 0.72f, 1f},
                            Shader.TileMode.CLAMP
                    ));
                    canvas.drawPath(wavePath, permeatePaint);

                    crestPaint.setColor(OnyxTheme.withAlpha(theme.primary, Math.round(155 * edgeFactor)));
                    canvas.drawPath(crestPath, crestPaint);
                }
            }
            canvas.restore();

            float bHalf = borderPaint.getStrokeWidth() * 0.5f + 0.5f;
            cardRect.set(bHalf, bHalf, w - bHalf, h - bHalf);
            borderPaint.setColor(OnyxTheme.blendColors(0x1AFFFFFF, OnyxTheme.withAlpha(theme.getDynamicColor(0f), 115), permeateProgress));
            canvas.drawRoundRect(cardRect, r - bHalf, r - bHalf, borderPaint);
            super.onDraw(canvas);
        }
    }

    private void openModuleSettingsScreen(final OnyxModule module) {
        this.activeSettingsModule = module;
        settingsModuleTitleTv.setText(module.getName());
        settingsCategoryLabelTv.setText(module.getCategory().displayName);

        settingsItemsContainer.removeAllViews();

        settingsItemsContainer.addView(OnyxSettingViews.createSettingRowView(
                activity, module.getKeybind(), theme, null));
        settingsItemsContainer.addView(createThinDivider());

        List<Setting> settings = module.getSettings();
        for (int i = 0; i < settings.size(); i++) {
            Setting s = settings.get(i);
            View rowView = OnyxSettingViews.createSettingRowView(activity, s, theme, null);
            settingsItemsContainer.addView(rowView);
            if (i < settings.size() - 1) {
                settingsItemsContainer.addView(createThinDivider());
            }
        }

        settingsScrollView.scrollTo(0, 0);

        final float shift = dp(activity, 32);
        mainCategoryScreen.animate()
                .alpha(0f)
                .translationX(-shift)
                .setDuration(170)
                .withEndAction(() -> {
                    mainCategoryScreen.setVisibility(View.GONE);
                    mainCategoryScreen.setTranslationX(0f);
                    moduleSettingsScreen.setVisibility(View.VISIBLE);
                    moduleSettingsScreen.setAlpha(0f);
                    moduleSettingsScreen.setTranslationX(shift);
                    moduleSettingsScreen.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(190)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    private void closeModuleSettingsScreen() {
        if (activeSettingsModule == null) return;
        activeSettingsModule = null;
        final float shift = dp(activity, 32);
        moduleSettingsScreen.animate()
                .alpha(0f)
                .translationX(shift)
                .setDuration(170)
                .withEndAction(() -> {
                    moduleSettingsScreen.setVisibility(View.GONE);
                    moduleSettingsScreen.setTranslationX(0f);
                    mainCategoryScreen.setVisibility(View.VISIBLE);
                    mainCategoryScreen.setAlpha(0f);
                    mainCategoryScreen.setTranslationX(-shift);
                    for (ModuleCardRowView card : activeModuleCards) {
                        card.syncStateImmediate();
                    }
                    mainCategoryScreen.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(190)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    private View createThinDivider() {
        View div = new View(activity);
        div.setBackgroundColor(theme.dividerColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
        div.setLayoutParams(lp);
        return div;
    }

    @Override
    public void onThemeChanged(OnyxTheme updatedTheme) {
        // Update styling IN-PLACE without calling removeAllViews() so adjusting colors never flickers!
        applyThemeStylingInPlace();
    }

    @Override
    public void onModuleToggled(OnyxModule module) {
        for (ModuleCardRowView card : activeModuleCards) {
            if (card.module == module) {
                card.syncStateAnimated();
            }
        }
    }

    @Override
    public void onConfigApplied(String configName) {
        if (inConfigsScreen) {
            refreshConfigsList();
        } else {
            for (ModuleCardRowView card : activeModuleCards) {
                card.syncStateAnimated();
            }
        }
    }

    @Override
    public void onSettingsChanged() {}

    /**
     * Continuous 2D Mesh (Canvas.drawBitmapMesh) Convergence-Dispersion Panel Container:
     * Implements all 4 transition modes with zero slice lines:
     * 1. Normal: Smooth 3D depth recede & dissolve.
     * 2. Liquid Flow (流体聚散): Irregular multi-harmonic water wave shrinking into a central liquid droplet.
     * 3. macOS Fluid Warp (MACOS流体聚散): Pure continuous 2D mesh S-curve funnel warp without horizontal slices.
     * 4. Dynamic Island Flow (灵动聚散): Shrinks, twists, and streams completely into the top Dynamic Island,
     *    triggering an elastic Q-bounce on the Dynamic Island when absorbed.
     */
    private class DissolvePanelContainer extends FrameLayout {
        private static final int MESH_COLS = 28;
        private static final int MESH_ROWS = 28;
        private static final int VERT_COUNT = (MESH_COLS + 1) * (MESH_ROWS + 1);

        private final float[] meshVerts = new float[VERT_COUNT * 2];
        private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint meshPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint dropletGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clipPath = new Path();

        private Bitmap snapshotBitmap;
        private boolean isMeshAnimating = false;
        private float collapseProgress = 0f; // 0 = full normal panel, 1 = fully converged/vanished
        private boolean bounceTriggeredThisCycle = false;
        private ValueAnimator activeAnimator;

        public DissolvePanelContainer(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(ctx, 1.2f));
        }

        private void capturePanelBitmap() {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            try {
                if (snapshotBitmap == null || snapshotBitmap.getWidth() != w || snapshotBitmap.getHeight() != h) {
                    if (snapshotBitmap != null) snapshotBitmap.recycle();
                    snapshotBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                } else {
                    snapshotBitmap.eraseColor(Color.TRANSPARENT);
                }
                Canvas offscreen = new Canvas(snapshotBitmap);
                drawStandardPanel(offscreen, w, h);
            } catch (Throwable ignored) {
            }
        }

        private void drawStandardPanel(Canvas canvas, int w, int h) {
            float r = dp(getContext(), 18f);
            rect.set(0, 0, w, h);
            boolean glass = theme.isFrostedGlass();

            canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
            canvas.clipPath(clipPath);

            // Clean Gaussian frosted dark glass base
            cardPaint.setShader(null);
            cardPaint.setColor(OnyxTheme.withAlpha(theme.panelBackground, glass ? 212 : 246));
            canvas.drawRoundRect(rect, r, r, cardPaint);

            super.dispatchDraw(canvas);
            canvas.restore();

            // Strictly inset 1dp neutral glass border (zero corner clipping)
            float strokeW = dp(getContext(), 1.0f);
            float half = strokeW * 0.5f + 0.5f;
            rect.set(half, half, w - half, h - half);
            borderPaint.setShader(null);
            borderPaint.setStrokeWidth(strokeW);
            borderPaint.setColor(0x28FFFFFF);
            canvas.drawRoundRect(rect, r - half, r - half, borderPaint);
        }

        private long getActiveModeDuration() {
            String mode = theme.getTransitionMode();
            if (OnyxTheme.MODE_LIQUID_FLOW.equals(mode)) return Math.round(theme.getLiquidDurationMs());
            if (OnyxTheme.MODE_MACOS_WARP.equals(mode)) return Math.round(theme.getMacosDurationMs());
            if (OnyxTheme.MODE_ISLAND_FLOW.equals(mode)) return Math.round(theme.getIslandDurationMs());
            return Math.round(theme.getNormalDurationMs());
        }

        public void startForwardAppearAnimation() {
            if (activeAnimator != null) activeAnimator.cancel();
            String mode = theme.getTransitionMode();
            long duration = getActiveModeDuration();

            if (OnyxTheme.MODE_NORMAL.equals(mode)) {
                isMeshAnimating = false;
                setAlpha(0f);
                setScaleX(0.86f);
                setScaleY(0.86f);
                activeAnimator = ValueAnimator.ofFloat(0f, 1f);
                activeAnimator.setDuration(duration);
                activeAnimator.setInterpolator(new DecelerateInterpolator(1.6f));
                activeAnimator.addUpdateListener(a -> {
                    float f = (Float) a.getAnimatedValue();
                    setAlpha(f);
                    float sc = 0.86f + 0.14f * f;
                    setScaleX(sc);
                    setScaleY(sc);
                });
                activeAnimator.start();
                return;
            }

            setAlpha(1f);
            setScaleX(1f);
            setScaleY(1f);
            isMeshAnimating = true;
            collapseProgress = 1f;
            bounceTriggeredThisCycle = false;

            post(() -> {
                capturePanelBitmap();
                if (activeAnimator != null) activeAnimator.cancel();
                if (OnyxTheme.MODE_ISLAND_FLOW.equals(mode) && hudOverlayView != null) {
                    hudOverlayView.triggerDynamicIslandAbsorbBounce(theme.getIslandBounceStrength() * 0.75f);
                }
                activeAnimator = ValueAnimator.ofFloat(1f, 0f);
                activeAnimator.setDuration(duration);
                activeAnimator.setInterpolator(new DecelerateInterpolator(1.45f));
                activeAnimator.addUpdateListener(a -> {
                    collapseProgress = (Float) a.getAnimatedValue();
                    invalidate();
                });
                activeAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isMeshAnimating = false;
                        collapseProgress = 0f;
                        invalidate();
                    }
                });
                activeAnimator.start();
            });
        }

        public void startBackwardDissolveAnimation(final Runnable onComplete) {
            if (activeAnimator != null) activeAnimator.cancel();
            String mode = theme.getTransitionMode();
            long duration = getActiveModeDuration();

            if (OnyxTheme.MODE_NORMAL.equals(mode)) {
                isMeshAnimating = false;
                final float startAlpha = getAlpha();
                final float startScale = getScaleX();
                activeAnimator = ValueAnimator.ofFloat(0f, 1f);
                activeAnimator.setDuration(duration);
                activeAnimator.setInterpolator(new AccelerateInterpolator(1.3f));
                activeAnimator.addUpdateListener(a -> {
                    float f = (Float) a.getAnimatedValue();
                    setAlpha(startAlpha * (1f - f));
                    float sc = startScale - (startScale - 0.82f) * f;
                    setScaleX(sc);
                    setScaleY(sc);
                });
                activeAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (onComplete != null) onComplete.run();
                    }
                });
                activeAnimator.start();
                return;
            }

            capturePanelBitmap();
            setAlpha(1f);
            setScaleX(1f);
            setScaleY(1f);
            isMeshAnimating = true;
            collapseProgress = 0f;
            bounceTriggeredThisCycle = false;

            activeAnimator = ValueAnimator.ofFloat(0f, 1f);
            activeAnimator.setDuration(duration);
            activeAnimator.addUpdateListener(a -> {
                collapseProgress = (Float) a.getAnimatedValue();
                if (OnyxTheme.MODE_ISLAND_FLOW.equals(mode) && !bounceTriggeredThisCycle && collapseProgress >= 0.62f) {
                    bounceTriggeredThisCycle = true;
                    if (hudOverlayView != null) {
                        hudOverlayView.triggerDynamicIslandAbsorbBounce(theme.getIslandBounceStrength());
                    }
                }
                invalidate();
            });
            activeAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    isMeshAnimating = false;
                    collapseProgress = 0f;
                    if (onComplete != null) onComplete.run();
                }
            });
            activeAnimator.start();
        }

        private float sCurve(float t) {
            float c = Math.max(0f, Math.min(1f, t));
            return c * c * (3f - 2f * c);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            if (!isMeshAnimating || snapshotBitmap == null) {
                drawStandardPanel(canvas, w, h);
                return;
            }

            String mode = theme.getTransitionMode();
            float p = Math.max(0f, Math.min(1f, collapseProgress));

            // -----------------------------------------------------------------
            // MODE 2: LIQUID FLOW (流体聚散 - Irregular water-flow shrinking into center droplet)
            // -----------------------------------------------------------------
            if (OnyxTheme.MODE_LIQUID_FLOW.equals(mode)) {
                float cx = w * 0.5f;
                float cy = h * 0.5f;
                float irregularity = theme.getLiquidIrregularity();
                float swirlStrength = theme.getLiquidTensionSwirl();
                float phase = p * (float) (Math.PI * 3.8);
                float envelope = (float) Math.sin(p * Math.PI);

                int idx = 0;
                for (int ry = 0; ry <= MESH_ROWS; ry++) {
                    float v = ry / (float) MESH_ROWS;
                    float y0 = v * h;
                    for (int cxIdx = 0; cxIdx <= MESH_COLS; cxIdx++) {
                        float u = cxIdx / (float) MESH_COLS;
                        float x0 = u * w;

                        float dx = x0 - cx;
                        float dy = y0 - cy;
                        float normDist = (float) Math.hypot(dx / (w * 0.5f), dy / (h * 0.5f));
                        float angle = (float) Math.atan2(dy, dx);

                        // Multi-harmonic organic water-flow ripple waves around the perimeter
                        float wave1 = (float) Math.sin(angle * 3.0f + phase) * 0.16f;
                        float wave2 = (float) Math.sin(angle * 5.0f - phase * 1.45f) * 0.11f;
                        float wave3 = (float) Math.cos(angle * 7.0f + phase * 2.15f) * 0.07f;
                        float radialRipple = (float) Math.sin(normDist * 6.0f - phase * 1.8f) * 0.08f;
                        float organicWave = (wave1 + wave2 + wave3 + radialRipple) * irregularity * envelope;

                        // Outer corners collapse faster into a rounded irregular liquid droplet
                        float cornerPull = Math.min(1f, p * (1.05f + 0.32f * normDist));
                        float scale = Math.max(0.0f, (1.0f - sCurve(cornerPull)) + organicWave * (1.0f - p * 0.65f));

                        // Vortex swirl rotation as the water gathers into the central droplet
                        float swirlAngle = p * 0.85f * swirlStrength * (1.0f - normDist * 0.35f) * envelope;
                        float cosA = (float) Math.cos(swirlAngle);
                        float sinA = (float) Math.sin(swirlAngle);

                        // Morph rectangular aspect ratio toward a spherical droplet as p -> 1
                        float rDroplet = (float) Math.hypot(dx, dy * (w / (float) Math.max(1, h))) * 0.68f;
                        float dropDx = (float) Math.cos(angle) * rDroplet;
                        float dropDy = (float) Math.sin(angle) * rDroplet;
                        float morphToDrop = sCurve(Math.min(1f, p * 1.35f));

                        float bx = dx + (dropDx - dx) * morphToDrop;
                        float by = dy + (dropDy - dy) * morphToDrop;

                        float rotX = bx * cosA - by * sinA;
                        float rotY = bx * sinA + by * cosA;

                        meshVerts[idx++] = cx + rotX * scale;
                        meshVerts[idx++] = cy + rotY * scale;
                    }
                }

                int alpha = Math.round(255 * (1.0f - Math.max(0f, (p - 0.80f) / 0.20f)));
                meshPaint.setAlpha(Math.max(0, Math.min(255, alpha)));
                canvas.drawBitmapMesh(snapshotBitmap, MESH_COLS, MESH_ROWS, meshVerts, 0, null, 0, meshPaint);

                // Central glowing water droplet core as it condenses and vanishes
                if (p > 0.35f) {
                    float dropFactor = (float) Math.sin(((p - 0.35f) / 0.65f) * Math.PI);
                    float rad = dp(getContext(), 34f) * dropFactor * (1.15f - p * 0.6f) + dp(getContext(), 4f);
                    dropletGlowPaint.setShader(new RadialGradient(
                            cx, cy, Math.max(1f, rad),
                            new int[]{
                                    OnyxTheme.withAlpha(0xFFE8DCFF, Math.round(210 * dropFactor)),
                                    OnyxTheme.withAlpha(theme.getDynamicColor(0f), Math.round(145 * dropFactor)),
                                    0x00000000
                            },
                            new float[]{0f, 0.55f, 1f},
                            Shader.TileMode.CLAMP
                    ));
                    canvas.drawCircle(cx, cy, rad, dropletGlowPaint);
                }
                return;
            }

            // -----------------------------------------------------------------
            // MODE 3: MACOS FLUID WARP (MACOS流体聚散 - Pure continuous 2D mesh warp, ZERO slices!)
            // -----------------------------------------------------------------
            if (OnyxTheme.MODE_MACOS_WARP.equals(mode)) {
                float targetX = w * 0.50f;
                float targetY = h * 0.98f;
                float curvature = theme.getMacosWarpCurvature();
                float pinchStrength = theme.getMacosFunnelPinch();

                float suctionTop = sCurve(Math.max(0f, (p - 0.10f) / 0.90f));
                float topEdgeY = (targetY - dp(getContext(), 12)) * suctionTop;
                float bottomEdgeY = h + (targetY - h) * sCurve(Math.min(1f, p * 1.15f));

                int idx = 0;
                for (int ry = 0; ry <= MESH_ROWS; ry++) {
                    float v = ry / (float) MESH_ROWS; // 0 = top, 1 = bottom
                    float pinchPhase = Math.max(0f, Math.min(1f, (p * (1.25f + 0.35f * pinchStrength)) - (1f - v) * 0.50f));
                    float sPinch = sCurve(pinchPhase);

                    float targetNeck = Math.max(0.02f, (0.04f + (1f - v) * (1f - sPinch) * 0.38f) / Math.max(0.4f, pinchStrength));
                    float funnelRatio = 1f + (targetNeck - 1f) * sPinch;
                    float finalCollapse = sCurve(Math.max(0f, (p - 0.68f) / 0.32f));
                    float widthRatio = funnelRatio * (1f - finalCollapse * 0.94f);

                    float sBendOffset = (float) Math.sin(v * Math.PI * 2.0f - p * Math.PI * 2.5f)
                            * dp(getContext(), 24f) * curvature * (float) Math.sin(p * Math.PI) * (0.30f + 0.70f * v);

                    float rowCenterX = targetX + sBendOffset;
                    float rowW = w * widthRatio;
                    float curvedV = (float) Math.pow(v, 1.0f + p * 1.30f * curvature);
                    float vy = topEdgeY + (bottomEdgeY - topEdgeY) * curvedV;

                    for (int cxIdx = 0; cxIdx <= MESH_COLS; cxIdx++) {
                        float u = cxIdx / (float) MESH_COLS; // 0..1
                        float vx = rowCenterX + (u - 0.5f) * rowW;
                        // Subtle vertical arch across horizontal span during suction
                        float archY = (float) Math.sin(u * Math.PI) * dp(getContext(), 10f) * curvature * sPinch * (1f - finalCollapse);
                        meshVerts[idx++] = vx;
                        meshVerts[idx++] = vy + archY;
                    }
                }

                int alpha = Math.round(255 * (1.0f - Math.max(0f, (p - 0.80f) / 0.20f)));
                meshPaint.setAlpha(Math.max(0, Math.min(255, alpha)));
                canvas.drawBitmapMesh(snapshotBitmap, MESH_COLS, MESH_ROWS, meshVerts, 0, null, 0, meshPaint);
                return;
            }

            // -----------------------------------------------------------------
            // MODE 4: DYNAMIC ISLAND FLOW (灵动聚散 - Shrinks, twists & streams into Top Dynamic Island!)
            // -----------------------------------------------------------------
            if (OnyxTheme.MODE_ISLAND_FLOW.equals(mode)) {
                // Compute Dynamic Island target center in this view's local coordinate space
                float islandScreenX = hudOverlayView != null ? hudOverlayView.getDynamicIslandCenterX() : (getLeft() + w * 0.5f);
                float islandScreenY = hudOverlayView != null ? hudOverlayView.getDynamicIslandCenterY() : dp(getContext(), 25);
                float targetX = islandScreenX - getLeft() - getTranslationX();
                float targetY = islandScreenY - getTop() - getTranslationY();

                float twistAmp = theme.getIslandStreamTwist();

                int idx = 0;
                for (int ry = 0; ry <= MESH_ROWS; ry++) {
                    float v = ry / (float) MESH_ROWS; // 0 = top (closest to Dynamic Island!), 1 = bottom
                    float y0 = v * h;

                    // Top rows (v near 0) lead the stream upward into the Dynamic Island; bottom rows follow
                    float rowFlowPhase = Math.max(0f, Math.min(1f, (p * 1.52f) - v * 0.48f));
                    float sFlow = sCurve(rowFlowPhase);

                    // Width pinches into a narrow liquid stream as it approaches the Dynamic Island
                    float streamPinch = sCurve(Math.max(0f, Math.min(1f, (p * 1.40f) - v * 0.28f)));
                    float widthScale = (1.0f - streamPinch * 0.94f);

                    // Helical S-twist along the stream path toward the Dynamic Island
                    float twistWaveX = (float) Math.sin((1f - v) * Math.PI * 2.4f + p * Math.PI * 3.2f)
                            * dp(getContext(), 28f) * twistAmp * (float) Math.sin(rowFlowPhase * Math.PI);
                    float twistWaveY = (float) Math.cos((1f - v) * Math.PI * 2.0f + p * Math.PI * 2.5f)
                            * dp(getContext(), 10f) * twistAmp * (float) Math.sin(rowFlowPhase * Math.PI);

                    for (int cxIdx = 0; cxIdx <= MESH_COLS; cxIdx++) {
                        float u = cxIdx / (float) MESH_COLS;
                        float x0 = u * w;

                        // Local rotational vortex twist around the stream spine
                        float localDx = (x0 - w * 0.5f) * widthScale;
                        float localDy = (float) Math.sin((u - 0.5f) * Math.PI) * dp(getContext(), 14f)
                                * twistAmp * (float) Math.sin(rowFlowPhase * Math.PI);

                        float spineX = (w * 0.5f) + (targetX - w * 0.5f) * sFlow + twistWaveX * (1f - sFlow * 0.6f);
                        float spineY = y0 + (targetY - y0) * sFlow + twistWaveY * (1f - sFlow * 0.6f);

                        meshVerts[idx++] = spineX + localDx;
                        meshVerts[idx++] = spineY + localDy * (1f - sFlow);
                    }
                }

                int alpha = Math.round(255 * (1.0f - Math.max(0f, (p - 0.82f) / 0.18f)));
                meshPaint.setAlpha(Math.max(0, Math.min(255, alpha)));
                canvas.drawBitmapMesh(snapshotBitmap, MESH_COLS, MESH_ROWS, meshVerts, 0, null, 0, meshPaint);

                // Luminous stream droplet entering the Dynamic Island
                if (p > 0.30f && p < 0.96f) {
                    float orbFactor = (float) Math.sin(((p - 0.30f) / 0.66f) * Math.PI);
                    float rOrb = dp(getContext(), 24f) * orbFactor + dp(getContext(), 6f);
                    dropletGlowPaint.setShader(new RadialGradient(
                            targetX, targetY, Math.max(1f, rOrb),
                            new int[]{
                                    OnyxTheme.withAlpha(0xFFE6DAFF, Math.round(215 * orbFactor)),
                                    OnyxTheme.withAlpha(theme.getDynamicColor(0f), Math.round(145 * orbFactor)),
                                    0x00000000
                            },
                            new float[]{0f, 0.55f, 1f},
                            Shader.TileMode.CLAMP
                    ));
                    canvas.drawCircle(targetX, targetY, rOrb, dropletGlowPaint);
                }
                return;
            }

            drawStandardPanel(canvas, w, h);
        }
    }
}
