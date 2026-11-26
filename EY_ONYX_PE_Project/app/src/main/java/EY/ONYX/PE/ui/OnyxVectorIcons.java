package EY.ONYX.PE.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * 100% Pure Geometric Vector Icon Renderer (Zero Emojis).
 * Includes every icon seen in the gameplay video:
 * - SHIELD, MOVEMENT, PLAYER, RENDER, HUD, CONFIGS (Left sidebar & Save button)
 * - SEARCH, CLOSE_X (Search bar)
 * - BACK_ARROW, CHEVRON_DOWN, CHEVRON_UP (Settings & Color Picker)
 * - PLANET (Dynamic Island onyx watermark)
 * - SPEEDOMETER, WIFI_PING (Top-left FPS & Ping pill, screenshot 2)
 * - KEYBOARD (Left Keybinds HUD card)
 * - PLUS, FOLDER, MORE_VERT (Configs screen buttons: + New, Save as, Open folder, ⋮ menu)
 * - CHECK_CIRCLE, WARNING_TRIANGLE (Notifications toast icons)
 */
public class OnyxVectorIcons {

    public enum IconType {
        SHIELD,
        MOVEMENT,
        PLAYER,
        RENDER,
        HUD,
        CONFIGS,
        SEARCH,
        CLOSE_X,
        BACK_ARROW,
        CHEVRON_DOWN,
        CHEVRON_UP,
        PLANET,
        SPEEDOMETER,
        WIFI_PING,
        KEYBOARD,
        PLUS,
        FOLDER,
        MORE_VERT,
        CHECK_MARK,
        WARNING_TRIANGLE,
        PALETTE,
        POTION_FLASK,
        HEART,
        INFO_CIRCLE
    }

    public static class VectorIconView extends View {
        private IconType iconType;
        private int iconColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        public VectorIconView(Context context, IconType iconType, int iconColor) {
            super(context);
            this.iconType = iconType;
            this.iconColor = iconColor;
        }

        public void setIconColor(int color) {
            if (this.iconColor != color) {
                this.iconColor = color;
                invalidate();
            }
        }

        public void setIconType(IconType type) {
            if (this.iconType != type) {
                this.iconType = type;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawIcon(canvas, iconType, 0, 0, getWidth(), getHeight(), iconColor, paint, path, rect);
        }
    }

    public static void drawIcon(Canvas canvas, IconType type, float left, float top, float width, float height,
                                int color, Paint paint, Path path, RectF rect) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(color);
        path.reset();

        float cx = left + width / 2.0f;
        float cy = top + height / 2.0f;
        float s = Math.min(width, height);

        switch (type) {
            case SHIELD: {
                paint.setStyle(Paint.Style.FILL);
                float w = s * 0.62f;
                float h = s * 0.72f;
                float x0 = cx - w / 2f;
                float y0 = cy - h / 2f;
                path.moveTo(cx, y0);
                path.lineTo(x0 + w, y0 + h * 0.18f);
                path.lineTo(x0 + w, y0 + h * 0.52f);
                path.quadTo(x0 + w, y0 + h * 0.84f, cx, y0 + h);
                path.quadTo(x0, y0 + h * 0.84f, x0, y0 + h * 0.52f);
                path.lineTo(x0, y0 + h * 0.18f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            }
            case MOVEMENT: {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx + s * 0.08f, cy - s * 0.26f, s * 0.09f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.095f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                path.moveTo(cx + s * 0.04f, cy - s * 0.14f);
                path.lineTo(cx - s * 0.04f, cy + s * 0.06f);
                path.lineTo(cx + s * 0.16f, cy + s * 0.14f);
                path.lineTo(cx + s * 0.22f, cy + s * 0.32f);
                path.moveTo(cx - s * 0.04f, cy + s * 0.06f);
                path.lineTo(cx - s * 0.22f, cy + s * 0.26f);
                path.moveTo(cx - s * 0.18f, cy - s * 0.04f);
                path.lineTo(cx + s * 0.03f, cy - s * 0.13f);
                path.lineTo(cx + s * 0.22f, cy - s * 0.05f);
                canvas.drawPath(path, paint);
                break;
            }
            case PLAYER: {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy - s * 0.16f, s * 0.15f, paint);
                float bw = s * 0.58f;
                float bh = s * 0.30f;
                rect.set(cx - bw / 2f, cy + s * 0.06f, cx + bw / 2f, cy + s * 0.06f + bh * 1.4f);
                path.addArc(rect, 180f, 180f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            }
            case RENDER: {
                paint.setStyle(Paint.Style.FILL);
                float ew = s * 0.76f;
                float eh = s * 0.44f;
                path.moveTo(cx - ew / 2f, cy);
                path.quadTo(cx, cy - eh * 0.9f, cx + ew / 2f, cy);
                path.quadTo(cx, cy + eh * 0.9f, cx - ew / 2f, cy);
                path.close();
                canvas.drawPath(path, paint);
                paint.setColor(0xFF15141B);
                canvas.drawCircle(cx, cy, s * 0.15f, paint);
                paint.setColor(color);
                canvas.drawCircle(cx, cy, s * 0.08f, paint);
                break;
            }
            case HUD: {
                paint.setStyle(Paint.Style.FILL);
                float gap = s * 0.06f;
                float box = s * 0.28f;
                float r = s * 0.04f;
                rect.set(cx - box - gap / 2f, cy - box - gap / 2f, cx - gap / 2f, cy + gap / 2f);
                canvas.drawRoundRect(rect, r, r, paint);
                rect.set(cx + gap / 2f, cy - box - gap / 2f, cx + box + gap / 2f, cy - gap / 2f);
                canvas.drawRoundRect(rect, r, r, paint);
                rect.set(cx - box - gap / 2f, cy + gap * 1.5f, cx - gap / 2f, cy + box + gap / 2f);
                canvas.drawRoundRect(rect, r, r, paint);
                rect.set(cx + gap / 2f, cy + gap / 2f, cx + box + gap / 2f, cy + box + gap / 2f);
                canvas.drawRoundRect(rect, r, r, paint);
                break;
            }
            case CONFIGS: {
                paint.setStyle(Paint.Style.FILL);
                float sz = s * 0.64f;
                float x0 = cx - sz / 2f;
                float y0 = cy - sz / 2f;
                path.moveTo(x0, y0 + sz * 0.12f);
                path.quadTo(x0, y0, x0 + sz * 0.12f, y0);
                path.lineTo(x0 + sz * 0.76f, y0);
                path.lineTo(x0 + sz, y0 + sz * 0.24f);
                path.lineTo(x0 + sz, y0 + sz * 0.88f);
                path.quadTo(x0 + sz, y0 + sz, x0 + sz * 0.88f, y0 + sz);
                path.lineTo(x0 + sz * 0.12f, y0 + sz);
                path.quadTo(x0, y0 + sz, x0, y0 + sz * 0.88f);
                path.close();
                canvas.drawPath(path, paint);
                paint.setColor(0xFF15141B);
                rect.set(x0 + sz * 0.22f, y0 + sz * 0.12f, x0 + sz * 0.72f, y0 + sz * 0.40f);
                canvas.drawRoundRect(rect, sz * 0.05f, sz * 0.05f, paint);
                canvas.drawCircle(cx, y0 + sz * 0.70f, sz * 0.16f, paint);
                break;
            }
            case SEARCH: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.11f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                float r = s * 0.24f;
                canvas.drawCircle(cx - s * 0.05f, cy - s * 0.05f, r, paint);
                canvas.drawLine(cx + r * 0.65f - s * 0.05f, cy + r * 0.65f - s * 0.05f,
                        cx + s * 0.30f, cy + s * 0.30f, paint);
                break;
            }
            case CLOSE_X: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.11f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                float d = s * 0.24f;
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
                canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint);
                break;
            }
            case BACK_ARROW: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.10f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                float aw = s * 0.56f;
                canvas.drawLine(cx - aw / 2f, cy, cx + aw / 2f, cy, paint);
                path.moveTo(cx - aw * 0.08f, cy - aw * 0.40f);
                path.lineTo(cx - aw / 2f, cy);
                path.lineTo(cx - aw * 0.08f, cy + aw * 0.40f);
                canvas.drawPath(path, paint);
                break;
            }
            case CHEVRON_DOWN: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.11f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                float cw = s * 0.42f;
                float ch = s * 0.22f;
                path.moveTo(cx - cw / 2f, cy - ch / 2f);
                path.lineTo(cx, cy + ch / 2f);
                path.lineTo(cx + cw / 2f, cy - ch / 2f);
                canvas.drawPath(path, paint);
                break;
            }
            case CHEVRON_UP: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.11f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                float cw = s * 0.42f;
                float ch = s * 0.22f;
                path.moveTo(cx - cw / 2f, cy + ch / 2f);
                path.lineTo(cx, cy - ch / 2f);
                path.lineTo(cx + cw / 2f, cy + ch / 2f);
                canvas.drawPath(path, paint);
                break;
            }
            case PLANET: {
                // Saturn planet icon with top-left crescent highlight and tilted ring
                paint.setStyle(Paint.Style.FILL);
                float pr = s * 0.28f;
                canvas.drawCircle(cx, cy, pr, paint);

                // Top-left dark crescent highlight inside the planet sphere
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.055f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(0xFF1E1C2A);
                float cr = pr * 0.68f;
                rect.set(cx - cr, cy - cr, cx + cr, cy + cr);
                canvas.drawArc(rect, 195f, 75f, false, paint);

                // Tilted elliptical ring with dark inner cut line across the front of the sphere
                canvas.save();
                canvas.rotate(-24f, cx, cy);
                rect.set(cx - s * 0.45f, cy - s * 0.13f, cx + s * 0.45f, cy + s * 0.13f);
                paint.setStrokeWidth(s * 0.11f);
                paint.setColor(0xFF1E1C2A);
                canvas.drawArc(rect, -15f, 210f, false, paint);

                paint.setStrokeWidth(s * 0.068f);
                paint.setColor(color);
                canvas.drawOval(rect, paint);
                canvas.restore();
                break;
            }
            case SPEEDOMETER: {
                // Speedometer gauge with flat bottom chord and angled needle
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.095f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                float r = s * 0.35f;
                rect.set(cx - r, cy - r * 0.82f, cx + r, cy + r * 1.18f);
                canvas.drawArc(rect, 162f, 216f, false, paint);
                canvas.drawLine(cx - r * 0.86f, cy + r * 0.32f, cx + r * 0.86f, cy + r * 0.32f, paint);
                canvas.drawLine(cx, cy + r * 0.10f, cx + r * 0.42f, cy - r * 0.36f, paint);
                break;
            }
            case WIFI_PING: {
                // Dual signal arcs with central upward needle
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.095f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                float r1 = s * 0.37f;
                float r2 = s * 0.22f;
                rect.set(cx - r1, cy - r1 * 0.68f, cx + r1, cy + r1 * 1.32f);
                canvas.drawArc(rect, 212f, 116f, false, paint);
                rect.set(cx - r2, cy - r2 * 0.48f, cx + r2, cy + r2 * 1.52f);
                canvas.drawArc(rect, 218f, 104f, false, paint);
                canvas.drawLine(cx - s * 0.02f, cy + s * 0.24f, cx + s * 0.10f, cy - s * 0.22f, paint);
                break;
            }
            case KEYBOARD: {
                // Rounded keyboard body with 2 rows of 5 square keys and bottom spacebar
                paint.setStyle(Paint.Style.FILL);
                float kw = s * 0.80f;
                float kh = s * 0.56f;
                rect.set(cx - kw / 2f, cy - kh / 2f, cx + kw / 2f, cy + kh / 2f);
                canvas.drawRoundRect(rect, s * 0.10f, s * 0.10f, paint);

                paint.setColor(0xFF1E1C2A);
                float keySz = s * 0.072f;
                float spacingX = s * 0.125f;
                for (int row = 0; row < 2; row++) {
                    float ky = cy - kh * 0.22f + row * (keySz + s * 0.055f);
                    for (int col = -2; col <= 2; col++) {
                        float kx = cx + col * spacingX - keySz / 2f;
                        rect.set(kx, ky, kx + keySz, ky + keySz);
                        canvas.drawRoundRect(rect, s * 0.015f, s * 0.015f, paint);
                    }
                }
                // Spacebar slot at bottom center
                float sbW = s * 0.36f;
                float sbH = s * 0.068f;
                rect.set(cx - sbW / 2f, cy + kh * 0.18f, cx + sbW / 2f, cy + kh * 0.18f + sbH);
                canvas.drawRoundRect(rect, sbH / 2f, sbH / 2f, paint);
                break;
            }
            case PLUS: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.11f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                float d = s * 0.26f;
                canvas.drawLine(cx - d, cy, cx + d, cy, paint);
                canvas.drawLine(cx, cy - d, cx, cy + d, paint);
                break;
            }
            case FOLDER: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.10f);
                paint.setStrokeJoin(Paint.Join.ROUND);
                float fw = s * 0.72f;
                float fh = s * 0.52f;
                float x0 = cx - fw / 2f;
                float y0 = cy - fh / 2f;
                path.moveTo(x0, y0 + fh * 0.18f);
                path.lineTo(x0, y0 + fh);
                path.lineTo(x0 + fw, y0 + fh);
                path.lineTo(x0 + fw, y0 + fh * 0.18f);
                path.lineTo(x0 + fw * 0.46f, y0 + fh * 0.18f);
                path.lineTo(x0 + fw * 0.34f, y0);
                path.lineTo(x0, y0);
                path.close();
                canvas.drawPath(path, paint);
                break;
            }
            case MORE_VERT: {
                paint.setStyle(Paint.Style.FILL);
                float r = s * 0.075f;
                canvas.drawCircle(cx, cy - s * 0.24f, r, paint);
                canvas.drawCircle(cx, cy, r, paint);
                canvas.drawCircle(cx, cy + s * 0.24f, r, paint);
                break;
            }
            case CHECK_MARK: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.12f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                path.moveTo(cx - s * 0.26f, cy + s * 0.02f);
                path.lineTo(cx - s * 0.06f, cy + s * 0.22f);
                path.lineTo(cx + s * 0.28f, cy - s * 0.18f);
                canvas.drawPath(path, paint);
                break;
            }
            case PALETTE: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.10f);
                canvas.drawCircle(cx, cy, s * 0.34f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx - s * 0.12f, cy - s * 0.10f, s * 0.055f, paint);
                canvas.drawCircle(cx + s * 0.04f, cy - s * 0.16f, s * 0.055f, paint);
                canvas.drawCircle(cx + s * 0.16f, cy - s * 0.02f, s * 0.055f, paint);
                canvas.drawCircle(cx - s * 0.06f, cy + s * 0.14f, s * 0.065f, paint);
                break;
            }
            case POTION_FLASK: {
                paint.setStyle(Paint.Style.FILL);
                float neckW = s * 0.16f;
                float topY = cy - s * 0.32f;
                rect.set(cx - neckW * 0.8f, topY, cx + neckW * 0.8f, topY + s * 0.08f);
                canvas.drawRoundRect(rect, s * 0.03f, s * 0.03f, paint);
                path.moveTo(cx - neckW / 2f, topY + s * 0.08f);
                path.lineTo(cx + neckW / 2f, topY + s * 0.08f);
                path.lineTo(cx + neckW / 2f, cy - s * 0.08f);
                path.lineTo(cx + s * 0.28f, cy + s * 0.24f);
                path.quadTo(cx + s * 0.30f, cy + s * 0.34f, cx + s * 0.18f, cy + s * 0.34f);
                path.lineTo(cx - s * 0.18f, cy + s * 0.34f);
                path.quadTo(cx - s * 0.30f, cy + s * 0.34f, cx - s * 0.28f, cy + s * 0.24f);
                path.lineTo(cx - neckW / 2f, cy - s * 0.08f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            }
            case HEART: {
                paint.setStyle(Paint.Style.FILL);
                float hr = s * 0.32f;
                path.moveTo(cx, cy + hr * 0.95f);
                path.cubicTo(cx - hr * 1.4f, cy + hr * 0.2f, cx - hr * 1.1f, cy - hr * 0.95f, cx, cy - hr * 0.3f);
                path.cubicTo(cx + hr * 1.1f, cy - hr * 0.95f, cx + hr * 1.4f, cy + hr * 0.2f, cx, cy + hr * 0.95f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            }
            case INFO_CIRCLE: {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, s * 0.36f, paint);
                paint.setColor(0xFF1A1924);
                canvas.drawCircle(cx, cy - s * 0.14f, s * 0.045f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.09f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(cx, cy - s * 0.02f, cx, cy + s * 0.18f, paint);
                break;
            }
            case WARNING_TRIANGLE: {
                paint.setStyle(Paint.Style.FILL);
                float tw = s * 0.72f;
                float th = s * 0.64f;
                path.moveTo(cx, cy - th / 2f);
                path.lineTo(cx + tw / 2f, cy + th / 2f);
                path.lineTo(cx - tw / 2f, cy + th / 2f);
                path.close();
                canvas.drawPath(path, paint);
                paint.setColor(0xFF1E1D27);
                paint.setStrokeWidth(s * 0.08f);
                canvas.drawLine(cx, cy - th * 0.12f, cx, cy + th * 0.16f, paint);
                canvas.drawCircle(cx, cy + th * 0.32f, s * 0.045f, paint);
                break;
            }
        }
    }
}
