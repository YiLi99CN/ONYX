package EY.ONYX.PE.auth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import EY.ONYX.PE.audio.OnyxHapticEngine;
import EY.ONYX.PE.theme.OnyxTheme;

/**
 * Clean, spacious Onyx login gate with custom rounded download progress bar + percentage indicator.
 */
public class OnyxLoginGateView extends FrameLayout {

    public interface LoginSuccessListener {
        void onLoginSuccess();
    }

    private final EditText userInput;
    private final EditText passInput;
    private final TextView errorTv;
    private final LinearLayout progressBox;
    private final SmoothProgressBarView progressBar;
    private final TextView progressPctTv;
    private final TextView loginBtn;

    public OnyxLoginGateView(final Context ctx, final OnyxTheme theme, final LoginSuccessListener listener) {
        super(ctx);
        setBackgroundColor(0xA80B0A12);
        setClickable(true);

        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int cardW = Math.min(dp(ctx, 430), Math.round(dm.widthPixels * 0.62f));
        cardW = Math.max(cardW, Math.min(dp(ctx, 360), dm.widthPixels - dp(ctx, 32)));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 32), dp(ctx, 28), dp(ctx, 32), dp(ctx, 28));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xE8161520);
        cardBg.setCornerRadius(dp(ctx, 20));
        cardBg.setStroke(dp(ctx, 1.0f), 0x26FFFFFF);
        card.setBackground(cardBg);

        TextView title = new TextView(ctx);
        title.setText("ONYX");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            title.setLetterSpacing(0.10f);
        }
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp(ctx, 22);
        card.addView(title, titleLp);

        userInput = buildInput(ctx, "Username", false);
        card.addView(userInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48)));

        passInput = buildInput(ctx, "Password", true);
        LinearLayout.LayoutParams passLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48));
        passLp.topMargin = dp(ctx, 14);
        card.addView(passInput, passLp);

        loginBtn = new TextView(ctx);
        loginBtn.setText("Login");
        loginBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        loginBtn.setTypeface(Typeface.DEFAULT_BOLD);
        loginBtn.setTextColor(0xFF14121D);
        loginBtn.setGravity(Gravity.CENTER);
        loginBtn.setPadding(dp(ctx, 16), dp(ctx, 13), dp(ctx, 16), dp(ctx, 13));
        GradientDrawable lbBg = new GradientDrawable();
        lbBg.setColor(theme.primary);
        lbBg.setCornerRadius(dp(ctx, 12));
        loginBtn.setBackground(lbBg);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(ctx, 20);
        card.addView(loginBtn, btnLp);

        // Visible rounded download progress bar + percentage text
        progressBox = new LinearLayout(ctx);
        progressBox.setOrientation(LinearLayout.HORIZONTAL);
        progressBox.setGravity(Gravity.CENTER_VERTICAL);
        progressBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams pboxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pboxLp.topMargin = dp(ctx, 14);

        progressBar = new SmoothProgressBarView(ctx, theme);
        progressBox.addView(progressBar, new LinearLayout.LayoutParams(0, dp(ctx, 8), 1.0f));

        progressPctTv = new TextView(ctx);
        progressPctTv.setText("0%");
        progressPctTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        progressPctTv.setTypeface(Typeface.DEFAULT_BOLD);
        progressPctTv.setTextColor(0xFFDAD6E8);
        LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pctLp.leftMargin = dp(ctx, 10);
        progressBox.addView(progressPctTv, pctLp);
        card.addView(progressBox, pboxLp);

        errorTv = new TextView(ctx);
        errorTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        errorTv.setTextColor(0xFFFF6B8B);
        errorTv.setGravity(Gravity.CENTER);
        errorTv.setVisibility(View.GONE);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errLp.topMargin = dp(ctx, 10);
        card.addView(errorTv, errLp);

        loginBtn.setOnClickListener(v -> {
            OnyxHapticEngine.vibrateClick(ctx);
            errorTv.setVisibility(View.GONE);
            loginBtn.setEnabled(false);
            loginBtn.setText("Loading...");
            progressBox.setVisibility(View.VISIBLE);
            progressBar.setProgress(5);
            progressPctTv.setText("5%");

            OnyxCloudAuthClient.authenticateAndLoadInMemory(
                    ctx,
                    OnyxCloudAuthClient.DEFAULT_SERVER_URL,
                    userInput.getText().toString(),
                    passInput.getText().toString(),
                    new OnyxCloudAuthClient.AuthCallback() {
                        @Override
                        public void onProgress(int percent) {
                            post(() -> {
                                progressBar.setProgress(percent);
                                progressPctTv.setText(percent + "%");
                            });
                        }

                        @Override
                        public void onSuccess(String username, double balance) {
                            post(() -> {
                                progressBar.setProgress(100);
                                progressPctTv.setText("100%");
                                OnyxHapticEngine.vibrateToggle(ctx, true);
                                loginBtn.setEnabled(true);
                                loginBtn.setText("Login");
                                if (listener != null) listener.onLoginSuccess();
                            });
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            post(() -> {
                                OnyxHapticEngine.vibrateToggle(ctx, false);
                                loginBtn.setEnabled(true);
                                loginBtn.setText("Login");
                                progressBox.setVisibility(View.GONE);
                                errorTv.setText(errorMessage);
                                errorTv.setVisibility(View.VISIBLE);
                            });
                        }
                    }
            );
        });

        LayoutParams cardLp = new LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(card, cardLp);
    }

    private EditText buildInput(Context ctx, String hint, boolean password) {
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setHintTextColor(0xFF7D7991);
        et.setTextColor(0xFFFFFFFF);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        et.setSingleLine(true);
        et.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10));
        if (password) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF211F2E);
        bg.setCornerRadius(dp(ctx, 12));
        bg.setStroke(dp(ctx, 0.9f), 0x26FFFFFF);
        et.setBackground(bg);
        return et;
    }

    private static class SmoothProgressBarView extends View {
        private final OnyxTheme theme;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int progress = 0;

        public SmoothProgressBarView(Context ctx, OnyxTheme theme) {
            super(ctx);
            this.theme = theme;
            trackPaint.setColor(0xFF28263A);
        }

        public void setProgress(int p) {
            this.progress = Math.max(0, Math.min(100, p));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float r = h * 0.5f;
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            float fillW = w * (progress / 100f);
            if (fillW > 0f) {
                fillPaint.setColor(theme.getDynamicColor(0f));
                rect.set(0, 0, Math.max(h, fillW), h);
                canvas.drawRoundRect(rect, r, r, fillPaint);
            }
        }
    }

    private static int dp(Context ctx, float dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
