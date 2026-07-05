package dev.twilitrealm.dusk;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class HudView extends View {
    private static final String TAG = "HudView";
    private static final boolean DEBUG_IDS = false;
    private GameState mState;
    private GameState mPrevState;
    private long mLastUpdateTime;
    private static final long UPDATE_INTERVAL_MS = 100; // 6 frames at 60fps

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mHeartPath = new Path();
    private final Path mDrawPath = new Path();
    
    private int mMapZoomLevel = 0; 
    private static final float[] ZOOM_FACTORS = {1.0f, 1.5f, 1.0f};
    private final float MAP_X = 20;
    private final float MAP_Y = 210;
    private final float MAP_SIZE = 720;

    private int mDisplayRupees = -1;
    private int mRupeeTimer = 0;

    public HudView(Context context) {
        super(context);
    }

    public void update(GameState state) {
        if (mState == null || !mState.stageName.equals(state.stageName)) {
            // Default zoom: Full Map (2) for Field/Room, Room View (0) for Dungeons
            mMapZoomLevel = state.isDungeon ? 0 : 2;
        }
        mPrevState = mState;
        mState = state;
        mLastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    private void resetPaint() {
        mPaint.reset();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        mPaint.setShadowLayer(0, 0, 0, 0);
        mPaint.setShader(null);
        mPaint.setStrokeWidth(0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float tx = event.getX(), ty = event.getY();
            float scale = Math.min((float) getWidth() / 1280f, (float) getHeight() / 1080f);
            float logicalX = (tx - (getWidth() - 1280 * scale) / 2) / scale;
            float logicalY = (ty - (getHeight() - 1080 * scale) / 2) / scale;

            if (logicalX >= MAP_X && logicalX <= MAP_X + MAP_SIZE &&
                logicalY >= MAP_Y && logicalY <= MAP_Y + MAP_SIZE) {
                mMapZoomLevel = (mMapZoomLevel + 1) % ZOOM_FACTORS.length;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        if (mState == null) return;

        long now = System.currentTimeMillis();
        float t = (now - mLastUpdateTime) / (float) UPDATE_INTERVAL_MS;
        t = Math.max(0, Math.min(1.2f, t)); // Allow slight overshoot for smoothness if frames drop

        float interMapX = mState.mapX;
        float interMapY = mState.mapY;
        float interMapAngle = mState.mapAngle;

        if (mPrevState != null && mPrevState.stageName.equals(mState.stageName)) {
            interMapX = mPrevState.mapX + (mState.mapX - mPrevState.mapX) * t;
            interMapY = mPrevState.mapY + (mState.mapY - mPrevState.mapY) * t;
            
            float diff = mState.mapAngle - mPrevState.mapAngle;
            while (diff < -180) diff += 360;
            while (diff > 180) diff -= 360;
            interMapAngle = mPrevState.mapAngle + diff * t;
        }

        float scale = Math.min((float) getWidth() / 1280f, (float) getHeight() / 1080f);
        canvas.save();
        canvas.translate((getWidth() - 1280 * scale) / 2, (getHeight() - 1080 * scale) / 2);
        canvas.scale(scale, scale);

        updateRupeeAnimation();

        drawHearts(canvas, 20, 20);
        drawMagicBar(canvas, 20, 150);
        
        if (mState.showOxygen) drawOxygenBar(canvas, 780, 150);
        else {
            boolean hasLantern = (mState.itemXResId == 0x48 || mState.itemYResId == 0x48 || 
                                 mState.itemDDownId == 0x48 || mState.itemDLeftId == 0x48 || 
                                 mState.itemDRightId == 0x48);
            
            // Show oil if lantern is in the active item wheel
            if (!hasLantern && mState.ringStatus > 0) {
                for (int id : mState.ringItemIds) {
                    if (id == 0x48) { hasLantern = true; break; }
                }
            }

            if (hasLantern) drawOilBar(canvas, 780, 150);
        }
        drawItems(canvas, 1280, 20);

        if (mState.isDungeon) {
            drawDungeonIcons(canvas, 1280 - 60, 150);
        }

        drawRupeeCounter(canvas, 20, 1060);
        
        if (mState.ringStatus > 0) {
            drawRingMenu(canvas, MAP_X + MAP_SIZE/2f, MAP_Y + MAP_SIZE/2f);
        } else {
            if (mState.showLightDrops && mState.maxLightDrops > 0) {
                float width = (mState.maxLightDrops - 1) * 45;
                drawLightDropZigZag(canvas, 640 - (width / 2), 1055);
            } else if (mState.isRiding) {
                float width = (6 - 1) * 68 + 57;
                drawHorseSpurs(canvas, 640 - (width / 2), 1060);
            }
            
            drawMiniMap(canvas, MAP_X, MAP_Y, interMapX, interMapY, interMapAngle);
        }
        
        drawContextButtons(canvas, 820, 280);
        drawStatusInfo(canvas, 1260, 1060);

        canvas.restore();
        if (mState.midnaCalling || t < 1.0f) postInvalidateOnAnimation();
    }

    private int getRupeeStep(int diff) {
        if (diff > 200) return 10;
        if (diff > 50)  return 5;
        if (diff > 20)  return 2;
        return 1;
    }

    private void updateRupeeAnimation() {
        if (mDisplayRupees == -1) { mDisplayRupees = mState.rupees; return; }
        int delta = mState.rupees - mDisplayRupees;
        if (delta == 0) return;
        postInvalidateOnAnimation();
        if (mRupeeTimer-- > 0) return;
        int absDelta = Math.abs(delta);
        int step = getRupeeStep(absDelta);
        if (delta < 0) step = Math.min(step * 2, absDelta);
        else step = Math.min(step, absDelta);
        mDisplayRupees += (delta > 0) ? step : -step;
        mRupeeTimer = (absDelta > 100) ? 1 : 2;
    }

    private void drawDungeonIcons(Canvas canvas, float x, float y) {
        resetPaint();
        float iconSize = 64, spacing = 80;
        
        // Vertical list restored to Map -> Compass -> Boss Key
        drawZeldaMap(canvas, x, y, iconSize, mState.hasMap);
        drawZeldaCompass(canvas, x, y + spacing, iconSize, mState.hasCompass);
        drawZeldaBossKey(canvas, x, y + (spacing * 2), iconSize, mState.hasBossKey);
    }

    private void drawZeldaBossKey(Canvas canvas, float cx, float cy, float size, boolean collected) {
        canvas.save();
        float scale = size / 48f;
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        canvas.translate(-24, -24);

        if (!collected) {
            resetPaint();
            mPaint.setColor(Color.argb(60, 100, 100, 100));
            // Unified silhouette derived from the provided SVG code (Single-Pass drawing)
            Path p = new Path();
            p.moveTo(35, 0); 
            p.cubicTo(35.33f, 0.99f, 35.66f, 1.98f, 36, 3);
            p.cubicTo(35.67f, 3.66f, 35.34f, 4.32f, 35, 5); 
            p.cubicTo(36.485f, 5.495f, 36.485f, 5.495f, 38, 6);
            p.cubicTo(39.192f, 17.032f, 39.192f, 17.032f, 38, 22); 
            p.cubicTo(35.779f, 24.31f, 33.577f, 25.396f, 30.75f, 26.882f);
            p.cubicTo(25.761f, 30.723f, 23.973f, 36.653f, 21.593f, 42.332f); 
            p.cubicTo(20, 46, 20, 46, 18, 48);
            p.cubicTo(15.722f, 48.046f, 15.722f, 48.046f, 13.062f, 47.75f); 
            p.cubicTo(12.187f, 47.662f, 11.312f, 47.574f, 10.41f, 47.484f);
            p.cubicTo(8, 47, 8, 47, 5, 45); 
            p.cubicTo(4, 43.25f, 4, 43.25f, 4, 41);
            p.cubicTo(5.22f, 39.581f, 6.447f, 38.166f, 7.718f, 36.792f); 
            p.cubicTo(10.008f, 33.588f, 10.429f, 29.831f, 11, 26);
            p.cubicTo(13.333f, 26, 15.666f, 26, 18, 26); 
            p.cubicTo(17.86f, 25.128f, 17.721f, 24.257f, 17.578f, 23.359f);
            p.cubicTo(17.057f, 17.636f, 16.811f, 13.035f, 20.496f, 8.398f); 
            p.cubicTo(21.531f, 7.28f, 22.596f, 6.188f, 23.687f, 5.125f);
            p.cubicTo(24.216f, 4.607f, 24.746f, 4.09f, 25.291f, 3.557f); 
            p.cubicTo(28.512f, 0.68f, 30.654f, -0.661f, 35, 0); 
            p.close();
            canvas.drawPath(p, mPaint);
        } else {
            // Detailed drawing with separate handle and teeth colors
            Path p1 = new Path();
            p1.moveTo(35, 0); p1.cubicTo(35.33f, 0.99f, 35.66f, 1.98f, 36, 3);
            p1.cubicTo(35.67f, 3.66f, 35.34f, 4.32f, 35, 5); p1.cubicTo(36.485f, 5.495f, 36.485f, 5.495f, 38, 6);
            p1.cubicTo(39.192f, 17.032f, 39.192f, 17.032f, 38, 22); p1.cubicTo(35.779f, 24.31f, 33.577f, 25.396f, 30.75f, 26.882f);
            p1.cubicTo(25.761f, 30.723f, 23.973f, 36.653f, 21.593f, 42.332f); p1.cubicTo(20, 46, 20, 46, 18, 48);
            p1.cubicTo(15.722f, 48.046f, 15.722f, 48.046f, 13.062f, 47.75f); p1.cubicTo(12.187f, 47.662f, 11.312f, 47.574f, 10.41f, 47.484f);
            p1.cubicTo(8, 47, 8, 47, 5, 45); p1.cubicTo(4, 43.25f, 4, 43.25f, 4, 41);
            p1.cubicTo(5.22f, 39.581f, 6.447f, 38.166f, 7.718f, 36.792f); p1.cubicTo(10.008f, 33.588f, 10.429f, 29.831f, 11, 26);
            p1.cubicTo(13.333f, 26, 15.666f, 26, 18, 26); p1.cubicTo(17.86f, 25.128f, 17.721f, 24.257f, 17.578f, 23.359f);
            p1.cubicTo(17.057f, 17.636f, 16.811f, 13.035f, 20.496f, 8.398f); p1.cubicTo(21.531f, 7.28f, 22.596f, 6.188f, 23.687f, 5.125f);
            p1.cubicTo(24.216f, 4.607f, 24.746f, 4.09f, 25.291f, 3.557f); p1.cubicTo(28.512f, 0.68f, 30.654f, -0.661f, 35, 0); p1.close();

            Path p3 = new Path();
            p3.moveTo(22, 27); p3.lineTo(24, 27); p3.lineTo(24, 29); p3.lineTo(27, 29);
            p3.lineTo(22, 48); p3.lineTo(13, 47.6f); p3.lineTo(10.3f, 47.4f); p3.lineTo(6, 45);
            p3.lineTo(8, 44); p3.lineTo(8, 42); p3.lineTo(10, 42); p3.lineTo(10, 40); p3.lineTo(14, 41);
            p3.lineTo(14, 40); p3.lineTo(12, 39); p3.lineTo(12, 37); p3.lineTo(16, 36); p3.lineTo(13, 36);
            p3.lineTo(14, 30); p3.lineTo(19, 32); p3.lineTo(20, 31); p3.lineTo(22, 27); p3.close();

            resetPaint();
            mPaint.setColor(Color.parseColor("#25150A"));
            canvas.drawPath(p1, mPaint);
            mPaint.setColor(Color.parseColor("#605F5E"));
            canvas.drawPath(p3, mPaint);
        }

        canvas.restore();
    }

    private void drawZeldaCompass(Canvas canvas, float cx, float cy, float size, boolean collected) {
        canvas.save();
        float scale = size / 48f;
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        canvas.translate(-24, -24);

        Path p1 = new Path();
        p1.moveTo(42, 7); p1.cubicTo(42.8f, 7.68f, 43.6f, 8.36f, 44.43f, 9.06f);
        p1.cubicTo(47.78f, 13.21f, 47.21f, 18.82f, 47.25f, 23.93f); p1.cubicTo(47.27f, 24.62f, 47.29f, 25.31f, 47.31f, 26.02f);
        p1.cubicTo(47.35f, 31.84f, 46.05f, 35.75f, 42.13f, 40.04f); p1.cubicTo(41.47f, 40.6f, 40.8f, 41.17f, 40.12f, 41.75f);
        p1.cubicTo(39.46f, 42.32f, 38.81f, 42.89f, 38.13f, 43.48f); p1.cubicTo(31.67f, 48.07f, 24.63f, 47.67f, 17, 47);
        p1.cubicTo(11.1f, 45.53f, 7.54f, 42.08f, 4.18f, 37.12f); p1.cubicTo(0.44f, 30.43f, 0.06f, 22.49f, 1, 15);
        p1.cubicTo(3.13f, 8.52f, 7.76f, 5.27f, 13.59f, 2.12f); p1.cubicTo(23.48f, -2.5f, 34.29f, -0.35f, 42, 7); p1.close();

        if (!collected) {
            resetPaint();
            mPaint.setColor(Color.argb(60, 100, 100, 100));
            canvas.drawPath(p1, mPaint);
        } else {
            resetPaint();
            mPaint.setColor(Color.parseColor("#604F37"));
            canvas.drawPath(p1, mPaint);
            Path p2 = new Path();
            p2.moveTo(34, 1); p2.lineTo(42, 7); p2.lineTo(47.25f, 23.93f); p2.lineTo(42.13f, 40.04f);
            p2.lineTo(38.13f, 43.48f); p2.lineTo(24, 47.25f); p2.lineTo(12, 47.26f); p2.lineTo(34, 1); p2.close();
            mPaint.setColor(Color.parseColor("#392518"));
            canvas.drawPath(p2, mPaint);
        }

        canvas.restore();
    }

    private void drawZeldaMap(Canvas canvas, float cx, float cy, float size, boolean collected) {
        canvas.save();
        float scale = size / 56f;
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        canvas.translate(-28, -23.5f);

        Path p1 = new Path();
        p1.moveTo(11.43f, 3.25f); p1.cubicTo(12.92f, 3.68f, 14.41f, 4.13f, 15.9f, 4.6f);
        p1.cubicTo(18.32f, 5.06f, 19.64f, 4.72f, 22, 4.06f); p1.cubicTo(27.48f, 2.85f, 32.84f, 2.77f, 38.43f, 2.75f);
        p1.lineTo(51, 5); p1.lineTo(50, 20); p1.lineTo(56, 20); p1.lineTo(56, 24); p1.lineTo(48, 29);
        p1.lineTo(48, 44); p1.lineTo(11.43f, 40.75f); p1.lineTo(0, 38); p1.lineTo(3.3f, 16); p1.lineTo(11.43f, 3.25f); p1.close();

        if (!collected) {
            resetPaint();
            mPaint.setColor(Color.argb(60, 100, 100, 100));
            canvas.drawPath(p1, mPaint);
        } else {
            resetPaint();
            mPaint.setColor(Color.parseColor("#60544A"));
            canvas.drawPath(p1, mPaint);
            Path p2 = new Path();
            p2.moveTo(38, 8); p2.cubicTo(40.96f, 11.12f, 41.38f, 13.31f, 41.31f, 17.56f);
            p2.lineTo(39, 35); p2.lineTo(21.8f, 37); p2.lineTo(10.8f, 33.7f); p2.lineTo(14, 13); p2.lineTo(38, 8); p2.close();
            mPaint.setColor(Color.parseColor("#736C56"));
            canvas.drawPath(p2, mPaint);
        }

        canvas.restore();
    }

    private void drawRingMenu(Canvas canvas, float cx, float cy) {
        float radius = 280;
        int total = Math.max(1, mState.ringItemsTotal);
        resetPaint();
        mPaint.setColor(Color.argb(160, 0, 0, 0));
        canvas.drawCircle(cx, cy, radius + 70, mPaint);
        
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(5);
        mPaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, radius + 70, mPaint);

        for (int s = 0; s < total; s++) {
            int itemId = mState.ringItemIds[s];
            if (itemId == 0xFF) continue;

            double angle = (s * (360.0 / total) - 90.0) * (Math.PI / 180.0);
            float x = (float) (cx + radius * Math.cos(angle));
            float y = (float) (cy + radius * Math.sin(angle));

            boolean isSelected = (s == mState.ringCurrentSlot);
            
            resetPaint();
            if (isSelected) {
                mPaint.setColor(Color.argb(220, 255, 255, 255));
                canvas.drawCircle(x, y, 70, mPaint);
            }

            mPaint.setColor(Color.BLACK);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(isSelected ? 5 : 2);
            canvas.drawCircle(x, y, 55, mPaint);

            String name = (itemId == 0) ? "(Empty Slot)" : getItemName(itemId);
            mPaint.setTextAlign(Paint.Align.CENTER);
            mPaint.setTextSize(isSelected ? 36 : 24); 
            mPaint.setColor(isSelected ? Color.WHITE : Color.rgb(180, 180, 180));
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            
            // For unselected slots, only draw if they aren't empty, or if we want to see holes
            if (itemId != 0 || isSelected) {
                canvas.drawText(name, x, y + 10, mPaint);
                
                // Draw ammo count if applicable
                int count = mState.ringItemCounts[s];
                if (count > 0 && itemId != 0x48) { // Don't show count for Lantern (0x48)
                    resetPaint();
                    mPaint.setTextAlign(Paint.Align.RIGHT);
                    mPaint.setTextSize(isSelected ? 24 : 18);
                    mPaint.setColor(Color.YELLOW);
                    mPaint.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
                    canvas.drawText(String.valueOf(count), x + 45, y + 45, mPaint);
                }

                // Draw oil bar for Lantern
                if (itemId == 0x48 && mState.maxOil > 0) {
                    float barW = isSelected ? 60 : 40;
                    float barH = isSelected ? 8 : 6;
                    float bx = x - barW/2f;
                    float by = y + (isSelected ? 25 : 18);
                    
                    resetPaint();
                    mPaint.setColor(Color.argb(120, 50, 40, 0));
                    canvas.drawRect(bx, by, bx + barW, by + barH, mPaint);
                    
                    mPaint.setColor(Color.rgb(255, 220, 0));
                    float fillW = barW * ((float)mState.oil / mState.maxOil);
                    canvas.drawRect(bx, by, bx + fillW, by + barH, mPaint);
                    
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setStrokeWidth(1.5f);
                    mPaint.setColor(Color.WHITE);
                    canvas.drawRect(bx, by, bx + barW, by + barH, mPaint);
                }
            }

            if (isSelected) {
                resetPaint();
                mPaint.setTextAlign(Paint.Align.CENTER);
                mPaint.setTextSize(92);
                mPaint.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
                mPaint.setColor(Color.WHITE);
                
                if (mState.ringStatus == 3) { // Explain state (STATUS_EXPLAIN + 1)
                    // Draw a simple info box in the center
                    mPaint.setColor(Color.argb(240, 15, 15, 25));
                    mPaint.setStyle(Paint.Style.FILL);
                    canvas.drawRoundRect(cx - 300, cy - 180, cx + 300, cy + 180, 25, 25, mPaint);
                    
                    mPaint.setColor(Color.WHITE);
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setStrokeWidth(4);
                    canvas.drawRoundRect(cx - 300, cy - 180, cx + 300, cy + 180, 25, 25, mPaint);
                    
                    resetPaint();
                    mPaint.setTextAlign(Paint.Align.CENTER);
                    mPaint.setTextSize(58);
                    mPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    mPaint.setColor(Color.rgb(255, 210, 80));
                    String title = (mState.itemTitle != null && !mState.itemTitle.isEmpty()) ? mState.itemTitle : name;
                    canvas.drawText(title, cx, cy - 80, mPaint);
                    
                    mPaint.setTextSize(28);
                    mPaint.setColor(Color.WHITE);
                    mPaint.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL));
                    if (mState.itemDesc != null && !mState.itemDesc.isEmpty()) {
                        String resolvedDesc = mState.itemDesc
                            .replace("{{A}}", mState.labelA)
                            .replace("{{B}}", mState.labelB)
                            .replace("{{X}}", mState.labelX)
                            .replace("{{Y}}", mState.labelY)
                            .replace("{{Z}}", mState.labelZ)
                            .replace("{{L}}", mState.labelL)
                            .replace("{{R}}", mState.labelR)
                            .replace("{{STICK}}", "Stick")
                            .replace("{{XORY}}", mState.labelX);

                        String[] linesArr = resolvedDesc.split("\n");
                        float lineY = cy - 20;
                        for (String line : linesArr) {
                             canvas.drawText(line, cx, lineY, mPaint);
                             lineY += 35;
                        }
                    } else {
                        canvas.drawText("ITEM INFO", cx, cy + 60, mPaint);
                    }
                } else {
                    canvas.drawText(name, cx, cy + 25, mPaint);
                }
            }
        }
    }

    private void drawMiniMap(Canvas canvas, float x, float y, float interX, float interY, float interAngle) {
        resetPaint();
        mPaint.setColor(Color.argb(120, 15, 15, 50));
        canvas.drawRect(x, y, x + MAP_SIZE, y + MAP_SIZE, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(3); mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + MAP_SIZE, y + MAP_SIZE, mPaint);
        if (mState.mapMaxX <= mState.mapMinX || mState.mapMaxZ <= mState.mapMinZ) return;
        
        float targetMinX = (mMapZoomLevel == 0) ? mState.roomMinX : mState.mapMinX;
        float targetMaxX = (mMapZoomLevel == 0) ? mState.roomMaxX : mState.mapMaxX;
        float targetMinZ = (mMapZoomLevel == 0) ? mState.roomMinZ : mState.mapMinZ;
        float targetMaxZ = (mMapZoomLevel == 0) ? mState.roomMaxZ : mState.mapMaxZ;

        // Ideal Room View: Use 65% occupancy instead of 90% to give more context
        float diffX = Math.max(2000f, targetMaxX - targetMinX);
        float diffZ = Math.max(2000f, targetMaxZ - targetMinZ);
        float baseScale = (MAP_SIZE * 0.65f) / Math.max(diffX, diffZ);
        
        if (mMapZoomLevel == 2) { // Full Map mode (Old Default)
            baseScale = (MAP_SIZE * 0.90f) / Math.max(mState.mapMaxX - mState.mapMinX, mState.mapMaxZ - mState.mapMinZ);
        }

        float mS = baseScale * ZOOM_FACTORS[mMapZoomLevel];
        
        float sCX, sCZ;
        if (mMapZoomLevel == 0) { // Room center
            sCX = (targetMaxX + targetMinX)/2f;
            sCZ = (targetMaxZ + targetMinZ)/2f;
        } else if (mMapZoomLevel == 2) { // Floor center
            sCX = (mState.mapMaxX + mState.mapMinX)/2f;
            sCZ = (mState.mapMaxZ + mState.mapMinZ)/2f;
        } else { // Player center
            sCX = interX;
            sCZ = interY;
        }
        
        float cX = x + MAP_SIZE/2, cY = y + MAP_SIZE/2;
        
        canvas.save(); canvas.clipRect(x, y, x + MAP_SIZE, y + MAP_SIZE);

        int colorMint = Color.parseColor("#9FE173");
        int colorWater = Color.parseColor("#3984C5");
        int colorTerrain = Color.parseColor("#3E8E1F");
        int colorHazard = Color.rgb(100, 0, 180); // Matches center of dungeon icon
        int colorLava = Color.parseColor("#8E1F1F");   // Red for Lava/Heat

        if (mState.mapLines != null) {
            float[] strip = new float[32768];
            int vCount = 0; int i = 0;
            while (i < mState.mapLines.length - 1) {
                float val = mState.mapLines[i];
                if (Float.isNaN(val)) {
                    int id0 = (int)mState.mapLines[i+1], id1 = (int)mState.mapLines[i+2];
                    if (id1 > 1000) {
                        int pId = id0 & 0x3F;
                        // Mirror retail palette selection (indices into l_dungeon_Color tables)
                        if (pId != 3) { 
                            mPaint.setStyle(Paint.Style.FILL_AND_STROKE); mPaint.setStrokeWidth(0.8f);
                            if (pId == 2 || pId == 5) mPaint.setColor(colorWater);
                            else if (pId == 6 || pId == 7) mPaint.setColor(colorHazard);
                            else mPaint.setColor(colorTerrain);

                            for (int j = 0; j < vCount - 2; j++) {
                                mDrawPath.reset(); mDrawPath.moveTo(strip[j*2], strip[j*2+1]);
                                mDrawPath.lineTo(strip[(j+1)*2], strip[(j+1)*2+1]);
                                mDrawPath.lineTo(strip[(j+2)*2], strip[(j+2)*2+1]);
                                mDrawPath.close(); canvas.drawPath(mDrawPath, mPaint);
                            }
                        }
                    }
                    vCount = 0; i += 4; continue;
                }
                if (vCount * 2 < strip.length - 1) {
                    strip[vCount*2] = cX + (val - sCX) * mS; strip[vCount*2+1] = cY + (mState.mapLines[i+1] - sCZ) * mS;
                    vCount++;
                }
                i += 2;
            }
            vCount = 0; i = 0;
            while (i < mState.mapLines.length - 1) {
                float val = mState.mapLines[i];
                if (Float.isNaN(val)) {
                    int id0 = (int)mState.mapLines[i+1], id1 = (int)mState.mapLines[i+2];
                    if (id1 == 1 || id1 == 2) {
                        int pId = id0 & 0x3F;
                        mPaint.setStyle(Paint.Style.STROKE);
                        mPaint.setStrokeWidth(id1 == 2 ? 4.0f : 2.5f);
                        mPaint.setStrokeJoin(Paint.Join.ROUND); mPaint.setStrokeCap(Paint.Cap.ROUND);
                        
                        mPaint.setColor(colorMint);
                        
                        mDrawPath.reset();
                        for (int j = 0; j < vCount; j++) {
                            if (j == 0) mDrawPath.moveTo(strip[j*2], strip[j*2+1]);
                            else mDrawPath.lineTo(strip[j*2], strip[j*2+1]);
                        }
                        canvas.drawPath(mDrawPath, mPaint);
                    }
                    vCount = 0; i += 4; continue;
                }
                if (vCount * 2 < strip.length - 1) {
                    strip[vCount*2] = cX + (val - sCX) * mS; strip[vCount*2+1] = cY + (mState.mapLines[i+1] - sCZ) * mS;
                    vCount++;
                }
                i += 2;
            }
        }
        if (mState.mapIcons != null) {
            for (int i = 0; i < mState.mapIcons.length; i += 4) {
                drawMapIcon(canvas, cX + (mState.mapIcons[i+1] - sCX) * mS, cY + (mState.mapIcons[i+2] - sCZ) * mS, (int)mState.mapIcons[i]);
            }
        }

        if (mState.mapDoors != null) {
            for (int i = 0; i < mState.mapDoors.length; i += 4) {
                drawMapDoor(canvas, cX + (mState.mapDoors[i] - sCX) * mS, cY + (mState.mapDoors[i+1] - sCZ) * mS, mState.mapDoors[i+2]);
            }
        }
        
        // Restart / Entrance Marker (Cyan)
        if (mState.showRestart) {
            drawMapPointer(canvas, cX + (mState.restartX - sCX) * mS, cY + (mState.restartY - sCZ) * mS, mState.restartAngle, Color.CYAN);
        }

        // Player Marker (Yellow)
        drawMapPointer(canvas, cX + (interX - sCX) * mS, cY + (interY - sCZ) * mS, interAngle, Color.YELLOW);
        
        canvas.restore();
        resetPaint(); mPaint.setTextAlign(Paint.Align.CENTER); mPaint.setTextSize(32); mPaint.setColor(Color.WHITE);
        String mapLabel = mState.stageName;
        if (mMapZoomLevel == 0) mapLabel += " (Room View)";
        else if (mMapZoomLevel == 2) mapLabel += " (Full Map)";
        else mapLabel += " (" + ZOOM_FACTORS[mMapZoomLevel] + "x)";
        canvas.drawText(mapLabel, x + MAP_SIZE/2, y + MAP_SIZE + 40, mPaint);
    }

    private void drawMapPointer(Canvas canvas, float x, float y, float angle, int color) {
        canvas.save(); canvas.translate(x, y);
        canvas.rotate(180 - angle); mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(color);
        mDrawPath.reset(); mDrawPath.moveTo(0, -22); mDrawPath.lineTo(-13, 13); mDrawPath.lineTo(13, 13); mDrawPath.close();
        canvas.drawPath(mDrawPath, mPaint); canvas.restore();
    }

    private void drawMapDoor(Canvas canvas, float x, float y, float angle) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(180 - angle);
        resetPaint();
        mPaint.setColor(Color.YELLOW);
        canvas.drawRect(-8, -2, 8, 2, mPaint);
        canvas.restore();
    }

    private void drawMapIcon(Canvas canvas, float x, float y, int type) {
        resetPaint();
        if (type == 3 || type == 7) { // Boss / Major Target -> Magenta Circle
            mPaint.setColor(Color.rgb(220, 0, 255));
            canvas.drawCircle(x, y, 12, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(Color.WHITE);
            mPaint.setStrokeWidth(3);
            canvas.drawCircle(x, y, 12, mPaint);
        } else if (type == 1 || type == 8) { // Entrance (Dungeon) -> Purple Stylized Star
            // Reference image shows a darker purple fill with a lighter magenta border
            mPaint.setColor(Color.rgb(100, 0, 180)); // Darker Purple Fill
            mPaint.setStyle(Paint.Style.FILL);
            float outer = 16, inner = 12; // Adjusted inner radius to make spikes shallower
            mDrawPath.reset();
            for (int i = 0; i < 16; i++) {
                float r = (i % 2 == 0) ? outer : inner;
                float angle = (float) (i * Math.PI / 8.0f);
                float px = (float) (x + r * Math.cos(angle - Math.PI/2));
                float py = (float) (y + r * Math.sin(angle - Math.PI/2));
                if (i == 0) mDrawPath.moveTo(px, py); else mDrawPath.lineTo(px, py);
            }
            mDrawPath.close();
            canvas.drawPath(mDrawPath, mPaint);
            
            mPaint.setColor(Color.rgb(220, 100, 255)); // Lighter Magenta Border
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(2.5f);
            canvas.drawPath(mDrawPath, mPaint);
        } else if (type == 0 || type == 2 || type == 16) { // Treasure Chest / Keys -> Yellow
            mPaint.setColor(Color.YELLOW);
            canvas.drawRect(x-9, y-9, x+9, y+9, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(Color.BLACK);
            mPaint.setStrokeWidth(2.5f);
            canvas.drawRect(x-9, y-9, x+9, y+9, mPaint);
            canvas.drawLine(x-9, y, x+9, y, mPaint);
        } else if (type == 4) { // Light Drop (Dark Area) - Tear of Light
            mPaint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, 6, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(Color.YELLOW);
            mPaint.setStrokeWidth(2);
            canvas.drawCircle(x, y, 6, mPaint);
        } else if (type == 5) { // Destination (Group 5 - Batsumark) -> Red Target Reticle
            mPaint.setColor(Color.RED);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(3);
            canvas.drawCircle(x, y, 12, mPaint); // Outer Ring
            mPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, y, 5, mPaint);  // Inner Dot
        } else if (type == 6 || type == 11) { // Special Objectives (Golden Wolf, Story) -> Cyan
            mPaint.setColor(Color.CYAN);
            canvas.drawCircle(x, y, 8, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(Color.WHITE);
            mPaint.setStrokeWidth(2);
            canvas.drawCircle(x, y, 8, mPaint);
        } else {
            mPaint.setColor(Color.RED);
            canvas.drawCircle(x, y, 8, mPaint);
        }
    }

    private void drawHearts(Canvas canvas, float startX, float startY) {
        int maxHearts = mState.maxHealth / 5; float heartSize = 58, gap = 12;
        for (int i = 0; i < maxHearts; i++) {
            float x = startX + (i % 10) * (heartSize + gap), y = startY + (i / 10) * (heartSize + gap);
            int fill = Math.min(4, Math.max(0, mState.health - (i * 4)));
            drawZeldaHeart(canvas, x, y, heartSize, fill);
        }
    }

    private void drawZeldaHeart(Canvas canvas, float x, float y, float size, int fill) {
        resetPaint(); mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(3); mPaint.setColor(Color.WHITE); mHeartPath.reset();
        float mid = size / 2;
        mHeartPath.moveTo(x + mid, y + size * 0.25f);
        mHeartPath.cubicTo(x + mid + size * 0.1f, y + size * 0.05f, x + size, y + size * 0.05f, x + size * 0.95f, y + size * 0.45f);
        mHeartPath.cubicTo(x + size * 0.9f, y + size * 0.7f, x + mid + size * 0.1f, y + size * 0.9f, x + mid, y + size * 0.95f);
        mHeartPath.cubicTo(x + mid - size * 0.1f, y + size * 0.9f, x + size * 0.1f, y + size * 0.7f, x + size * 0.05f, y + size * 0.45f);
        mHeartPath.cubicTo(x, y + size * 0.05f, x + mid - size * 0.1f, y + size * 0.05f, x + mid, y + size * 0.25f);
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.argb(80, 50, 0, 0));
        canvas.drawPath(mHeartPath, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.WHITE);
        canvas.drawPath(mHeartPath, mPaint);
        if (fill > 0) {
            float splitY = y + size * 0.55f;
            mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.RED);
            canvas.save(); canvas.clipPath(mHeartPath);
            if (fill >= 1) canvas.drawRect(x - size * 0.2f, y - size * 0.2f, x + mid, splitY, mPaint);
            if (fill >= 2) canvas.drawRect(x - size * 0.2f, splitY, x + mid, y + size * 1.2f, mPaint);
            if (fill >= 3) canvas.drawRect(x + mid, splitY, x + size * 1.2f, y + size * 1.2f, mPaint);
            if (fill >= 4) canvas.drawRect(x + mid, y - size * 0.2f, x + size * 1.2f, splitY, mPaint);
            canvas.restore();
            mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(3); mPaint.setColor(Color.WHITE); canvas.drawPath(mHeartPath, mPaint);
        }
    }

    private void drawMagicBar(Canvas canvas, float x, float y) {
        if (mState.maxMagic <= 0) return;
        resetPaint(); mPaint.setColor(Color.argb(100, 0, 50, 0));
        canvas.drawRect(x, y, x + 740, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(3); mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + 740, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.rgb(0, 255, 120));
        canvas.drawRect(x + 2, y + 2, x + (740 * (float)mState.magic / mState.maxMagic) - 2, y + 20, mPaint);
    }

    private void drawOilBar(Canvas canvas, float x, float y) {
        resetPaint(); mPaint.setColor(Color.argb(100, 50, 40, 0));
        canvas.drawRect(x, y, x + 300, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(3); mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + 300, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.rgb(255, 220, 0));
        canvas.drawRect(x + 2, y + 2, x + (300 * (float)mState.oil / mState.maxOil) - 2, y + 20, mPaint);
    }

    private void drawOxygenBar(Canvas canvas, float x, float y) {
        resetPaint(); mPaint.setColor(Color.argb(100, 0, 30, 50));
        canvas.drawRect(x, y, x + 300, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(3); mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + 300, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.rgb(0, 180, 255));
        canvas.drawRect(x + 2, y + 2, x + (300 * (float)mState.oxygen / mState.maxOxygen) - 2, y + 20, mPaint);
    }

    private void drawRupeeCounter(Canvas canvas, float x, float y) {
        resetPaint();
        float w = 40, h = 58, centerY = y - 24; 
        mPaint.setColor(Color.rgb(0, 180, 0)); mDrawPath.reset();
        mDrawPath.moveTo(x + w / 2f, centerY - h / 2f); mDrawPath.lineTo(x + w, centerY - h / 5f);
        mDrawPath.lineTo(x + w, centerY + h / 5f); mDrawPath.lineTo(x + w / 2f, centerY + h / 2f);
        mDrawPath.lineTo(x, centerY + h / 5f); mDrawPath.lineTo(x, centerY - h / 5f); mDrawPath.close();
        canvas.drawPath(mDrawPath, mPaint);
        mPaint.setColor(Color.rgb(100, 255, 100)); float iw = w * 0.5f, ih = h * 0.5f; mDrawPath.reset();
        mDrawPath.moveTo(x + w / 2f, centerY - ih / 2f); mDrawPath.lineTo(x + w / 2f + iw / 2f, centerY - ih / 4f);
        mDrawPath.lineTo(x + w / 2f + iw / 2f, centerY + ih / 4f); mDrawPath.lineTo(x + w / 2f, centerY + ih / 2f);
        mDrawPath.lineTo(x + w / 2f - iw / 2f, centerY + ih / 4f); mDrawPath.lineTo(x + w / 2f - iw / 2f, centerY - ih / 4f); mDrawPath.close();
        canvas.drawPath(mDrawPath, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(2); mPaint.setColor(Color.rgb(0, 80, 0));
        mDrawPath.reset(); mDrawPath.moveTo(x + w / 2f, centerY - h / 2f); mDrawPath.lineTo(x + w, centerY - h / 5f);
        mDrawPath.lineTo(x + w, centerY + h / 5f); mDrawPath.lineTo(x + w / 2f, centerY + h / 2f);
        mDrawPath.lineTo(x, centerY + h / 5f); mDrawPath.lineTo(x, centerY - h / 5f); mDrawPath.close();
        canvas.drawPath(mDrawPath, mPaint);

        String text = String.valueOf(mDisplayRupees); float textX = x + w + 15;
        mPaint.setTextAlign(Paint.Align.LEFT); mPaint.setTextSize(72);
        mPaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD));
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(6.0f); mPaint.setColor(Color.rgb(35, 26, 18));
        mPaint.setShadowLayer(4.0f, 3.0f, 3.0f, Color.rgb(74, 56, 40));
        canvas.drawText(text, textX, y, mPaint);
        mPaint.clearShadowLayer(); mPaint.setStyle(Paint.Style.FILL);
        int fT = Color.rgb(255, 249, 232), fB = Color.rgb(240, 232, 208);
        if (mState.rupees > mDisplayRupees) { fT = Color.WHITE; fB = Color.rgb(230, 230, 230); }
        mPaint.setShader(new android.graphics.LinearGradient(0, y - 60, 0, y, fT, fB, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawText(text, textX, y, mPaint);
    }

    private void drawLightDropZigZag(Canvas canvas, float x, float y) {
        resetPaint();
        float spacing = 45;
        for (int i = 0; i < mState.maxLightDrops; i++) {
            float dx = x + i * spacing, dy = y - (i % 2 == 0 ? 0 : 25);
            float radius = 13;
            if (i < mState.lightDrops) {
                mPaint.setColor(Color.WHITE); canvas.drawCircle(dx, dy, radius, mPaint);
                mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.YELLOW); mPaint.setStrokeWidth(3.5f); canvas.drawCircle(dx, dy, radius, mPaint);
                mPaint.setStyle(Paint.Style.FILL);
            } else {
                mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.GRAY); mPaint.setStrokeWidth(2.5f); canvas.drawCircle(dx, dy, radius, mPaint);
                mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.argb(40, 100, 100, 100)); canvas.drawCircle(dx, dy, radius, mPaint);
            }
        }
    }

    private void drawHorseSpurs(Canvas canvas, float x, float y) {
        resetPaint();
        float spacing = 68;
        float outerRadius = 28.5f;
        float innerRadius = 24.5f;
        for (int i = 0; i < 6; i++) {
            float cx = x + i * spacing, cy = y - 15;
            mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(3); mPaint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, outerRadius, mPaint);
            if (i < mState.horseSpurs) {
                mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.rgb(255, 140, 0));
                canvas.drawCircle(cx, cy, innerRadius, mPaint);
            }
        }
    }

    private void drawItems(Canvas canvas, float x, float y) {
        if (!mState.isDungeon) return;
        resetPaint();
        
        float keyH = 80;
        // The key in the SVG is approx 266px wide by 562px tall.
        float keyW = keyH * (266f / 562f); 
        
        // 20px padding from top/right edges to match Rupee/Heart logic
        float iconRightX = x - 20;
        float iconTopY = y; 
        float iconCenterX = iconRightX - (keyW / 2);
        float iconCenterY = iconTopY + (keyH / 2);

        // Key Count (Grouped tightly with the icon)
        String text = String.valueOf(mState.keys);
        mPaint.setTextAlign(Paint.Align.RIGHT); mPaint.setTextSize(72); // Matched to Rupee font size
        mPaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD));
        
        float textGap = 15; // Exact distance matching the Rupee counter
        float textX = iconRightX - keyW - textGap;
        float textY = iconCenterY + 25; // Adjusted vertical centering for 72pt font
        
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(6.0f); mPaint.setColor(Color.rgb(35, 26, 18));
        canvas.drawText(text, textX, textY, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
        int fT = Color.rgb(255, 249, 232), fB = Color.rgb(240, 232, 208);
        mPaint.setShader(new android.graphics.LinearGradient(0, textY - 60, 0, textY, fT, fB, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawText(text, textX, textY, mPaint);
        mPaint.setShader(null);
        
        drawZeldaKey(canvas, iconCenterX, iconCenterY, keyH);
    }

    private void drawZeldaKey(Canvas canvas, float cx, float cy, float size) {
        canvas.save();
        // Scale and Center using the actual key bounds (X[294, 560], Y[11, 573])
        float svgScale = size / 562f; 
        canvas.translate(cx, cy);
        canvas.scale(-svgScale, svgScale); // Horizontally flip (teeth point RIGHT)
        canvas.translate(-427, -292); // Center of the actual key geometry

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD); 
        
        // Key silhouette (Sub-path starting at M523.43)
        path.moveTo(523.43f, 507.94f);
        path.cubicTo(523.78f, 507.76f, 523.91f, 507.49f, 523.89f, 506.40f);
        path.cubicTo(518.27f, 499.92f, 512.65f, 493.44f, 506.51f, 486.39f);
        path.cubicTo(504.10f, 485.98f, 501.70f, 485.23f, 499.28f, 485.20f);
        path.cubicTo(484.22f, 485.02f, 469.15f, 485.00f, 454.00f, 484.01f);
        path.lineTo(454.99f, 452.96f);
        path.cubicTo(481.08f, 452.96f, 507.17f, 453.07f, 533.26f, 452.81f);
        path.cubicTo(536.68f, 452.78f, 541.02f, 451.80f, 543.32f, 449.58f);
        path.cubicTo(548.82f, 444.25f, 553.37f, 437.93f, 558.83f, 431.65f);
        path.lineTo(541.55f, 411.36f);
        path.cubicTo(539.32f, 410.96f, 537.10f, 410.22f, 534.87f, 410.21f);
        path.cubicTo(507.94f, 410.05f, 481.01f, 410.01f, 454.00f, 409.01f);
        path.lineTo(454.17f, 323.33f);
        path.cubicTo(456.93f, 322.19f, 459.60f, 320.63f, 462.48f, 320.02f);
        path.cubicTo(465.92f, 319.30f, 468.18f, 318.53f, 468.08f, 314.20f);
        path.cubicTo(467.82f, 302.58f, 468.06f, 290.95f, 467.90f, 279.32f);
        path.cubicTo(467.88f, 277.86f, 466.66f, 276.42f, 465.34f, 274.56f);
        path.lineTo(454.04f, 271.08f);
        path.lineTo(454.08f, 248.11f);
        path.cubicTo(454.12f, 246.64f, 455.09f, 244.43f, 456.25f, 243.87f);
        path.cubicTo(464.65f, 239.83f, 473.17f, 236.03f, 481.76f, 232.40f);
        path.cubicTo(483.35f, 231.73f, 485.73f, 231.54f, 487.22f, 232.24f);
        path.cubicTo(495.89f, 236.29f, 504.41f, 240.68f, 513.67f, 245.16f);
        path.lineTo(530.38f, 229.35f);
        path.lineTo(516.27f, 198.27f);
        path.lineTo(517.84f, 196.42f);
        path.cubicTo(522.27f, 186.64f, 526.64f, 176.83f, 531.80f, 166.99f);
        path.lineTo(552.44f, 159.75f);
        path.lineTo(559.68f, 155.54f);
        path.lineTo(560.87f, 154.09f);
        path.lineTo(560.90f, 133.76f);
        path.cubicTo(560.88f, 132.86f, 559.99f, 131.51f, 559.17f, 131.16f);
        path.lineTo(532.00f, 119.08f);
        path.cubicTo(532.00f, 119.08f, 532.00f, 119.08f, 532.00f, 119.08f);
        path.cubicTo(529.35f, 106.93f, 525.20f, 95.38f, 518.45f, 84.83f);
        path.lineTo(518.53f, 81.62f);
        path.lineTo(530.90f, 57.09f);
        path.lineTo(516.45f, 43.08f);
        path.cubicTo(516.45f, 43.08f, 516.45f, 43.08f, 516.45f, 43.08f);
        path.lineTo(512.70f, 42.17f);
        path.lineTo(487.26f, 52.11f);
        path.lineTo(462.97f, 39.19f);
        path.lineTo(454.82f, 12.11f);
        path.lineTo(453.37f, 11.11f);
        path.lineTo(401.94f, 11.06f);
        path.lineTo(393.23f, 40.13f);
        path.lineTo(368.62f, 51.26f);
        path.lineTo(344.10f, 41.70f);
        path.cubicTo(342.61f, 41.16f, 340.04f, 41.49f, 338.90f, 42.48f);
        path.lineTo(326.85f, 54.44f);
        path.lineTo(325.94f, 58.17f);
        path.lineTo(338.89f, 82.95f);
        path.cubicTo(331.78f, 94.10f, 327.41f, 106.38f, 323.36f, 119.30f);
        path.lineTo(297.97f, 130.26f);
        path.cubicTo(296.48f, 130.92f, 294.29f, 132.34f, 294.23f, 133.51f);
        path.lineTo(294.18f, 153.84f);
        path.lineTo(296.36f, 156.58f);
        path.lineTo(300.17f, 158.82f);
        path.lineTo(326.09f, 168.80f);
        path.lineTo(339.13f, 199.49f);
        path.lineTo(326.91f, 226.67f);
        path.cubicTo(325.59f, 229.52f, 325.86f, 231.49f, 328.25f, 233.65f);
        path.lineTo(339.35f, 244.44f);
        path.cubicTo(340.80f, 245.65f, 343.78f, 246.55f, 345.32f, 245.89f);
        path.lineTo(370.30f, 233.99f);
        path.lineTo(373.04f, 230.79f);
        path.lineTo(385.16f, 237.79f);
        path.lineTo(398.21f, 243.62f);
        path.lineTo(402.07f, 248.93f);
        path.lineTo(402.16f, 265.90f);
        path.lineTo(396.19f, 274.78f);
        path.lineTo(394.37f, 274.69f);
        path.lineTo(388.89f, 279.06f);
        path.lineTo(389.43f, 318.70f);
        path.lineTo(394.47f, 320.04f);
        path.lineTo(402.19f, 323.85f);
        path.lineTo(402.19f, 538.07f);
        path.lineTo(404.39f, 544.56f);
        path.lineTo(428.80f, 560.92f);
        path.lineTo(451.76f, 544.53f);
        path.lineTo(453.89f, 539.48f);
        path.lineTo(454.96f, 527.98f);
        path.lineTo(501.38f, 527.91f);
        path.lineTo(506.64f, 526.81f);
        path.lineTo(523.00f, 507.96f);
        path.close();

        // Hex Cutout (Nested path from M378)
        Path hex = new Path();
        hex.moveTo(378.02f, 114.99f);
        hex.cubicTo(377.46f, 115.31f, 374.20f, 122.31f, 367.79f, 136.35f);
        hex.cubicTo(368.51f, 138.72f, 368.95f, 140.68f, 371.63f, 146.82f);
        hex.cubicTo(376.09f, 159.43f, 380.08f, 169.64f, 384.47f, 179.66f);
        hex.cubicTo(385.29f, 181.54f, 387.59f, 183.19f, 389.58f, 184.06f);
        hex.cubicTo(400.61f, 188.86f, 411.65f, 193.68f, 422.93f, 197.81f);
        hex.cubicTo(426.14f, 198.98f, 430.53f, 198.78f, 433.79f, 197.60f);
        hex.cubicTo(444.49f, 193.72f, 454.98f, 189.26f, 465.39f, 184.65f);
        hex.cubicTo(467.94f, 183.52f, 470.86f, 181.44f, 471.92f, 179.04f);
        hex.cubicTo(476.63f, 168.51f, 480.71f, 157.71f, 485.63f, 146.60f);
        hex.cubicTo(485.59f, 133.37f, 481.91f, 125.28f, 475.20f, 108.78f);
        hex.lineTo(468.68f, 97.22f);
        hex.lineTo(429.37f, 81.03f);
        hex.lineTo(388.96f, 94.28f);
        hex.close();
        path.addPath(hex);

        // Render ---
        resetPaint();
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(6.5f); mPaint.setColor(Color.rgb(35, 26, 18));
        mPaint.setStrokeJoin(Paint.Join.ROUND); canvas.drawPath(path, mPaint);
        
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setShader(new android.graphics.LinearGradient(0, 0, size, size, 
                         Color.rgb(235, 235, 240), Color.rgb(115, 115, 125), android.graphics.Shader.TileMode.CLAMP));
        canvas.drawPath(path, mPaint);
        
        canvas.restore();
    }

    private void drawContextButtons(Canvas canvas, float x, float startY) {
        resetPaint(); float spacing = 95;
        // 1. Shoulders/Triggers
        drawActionButton(canvas, x, startY, mState.labelL, Color.rgb(200, 200, 200), mState.buttonLText, mState.buttonLText != null && !mState.buttonLText.isEmpty());
        drawActionButton(canvas, x, startY + spacing, mState.labelR, Color.rgb(200, 200, 200), mState.buttonRText, mState.buttonRText != null && !mState.buttonRText.isEmpty());
        drawActionButton(canvas, x, startY + spacing * 2, mState.labelZ, Color.parseColor("#5A429B"), mState.buttonZText, mState.buttonZText != null && !mState.buttonZText.isEmpty());
        
        // 2. A (Action)
        drawActionButton(canvas, x, startY + spacing * 3, mState.labelA, Color.rgb(0, 200, 50), mState.buttonAText, mState.buttonAText != null && !mState.buttonAText.isEmpty());

        // 3. B (Sword/Attack)
        drawActionButton(canvas, x, startY + spacing * 4, mState.labelB, Color.RED, mState.buttonBText, mState.buttonBText != null && !mState.buttonBText.isEmpty());

        // 4. Y (Item Slot 1)
        String yT = mState.buttonYText; boolean yA = (yT != null && !yT.isEmpty());
        if (!yA) {
            yT = getItemName(mState.itemYResId);
            if (mState.itemYCount > 0) yT += " (" + mState.itemYCount + ")";
        }
        yA = yA || (mState.itemYResId != 0xFF);
        drawActionButton(canvas, x, startY + spacing * 5, mState.labelY, Color.rgb(200, 200, 200), yT, yA);
        
        // 5. X (Item Slot 2)
        String xT = mState.buttonXText; boolean xA = (xT != null && !xT.isEmpty());
        if (!xA) {
            xT = getItemName(mState.itemXResId);
            if (mState.itemXCount > 0) xT += " (" + mState.itemXCount + ")";
        }
        xA = xA || (mState.itemXResId != 0xFF);
        drawActionButton(canvas, x, startY + spacing * 6, mState.labelX, Color.rgb(200, 200, 200), xT, xA);
    }

    private void drawActionButton(Canvas canvas, float x, float y, String label, int color, String text, boolean active) {
        resetPaint(); mPaint.setTextAlign(Paint.Align.CENTER); mPaint.setTextSize(42);
        int circleColor = color; 
        boolean isMidna = active && "Midna".equals(text);
        boolean isMidnaContext = active && ("Z".equals(label) || "Z".equals(mState.labelZ)) && ("Check".equals(text) || "Warp".equals(text) || "Select Warp".equals(text));
        boolean isPulse = (isMidna || isMidnaContext) && mState.midnaCalling;
        
        if (isMidna || isMidnaContext) {
            circleColor = isPulse ? Color.rgb(255, 200, 30) : Color.parseColor("#5A429B");
        }

        mPaint.setColor(active ? circleColor : Color.argb(60, 100, 100, 100));
        float r = isPulse ? 42 : 38; canvas.drawCircle(x, y, r, mPaint);
        mPaint.setColor(active ? Color.BLACK : Color.argb(100, 200, 200, 200));
        canvas.drawText(label, x, y + 15, mPaint);
        if (active && text != null && !text.isEmpty()) {
            mPaint.setTextAlign(Paint.Align.LEFT); mPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            mPaint.setTextSize(42);
            float lx = x + 60, ly = y + 15;
            mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(5.0f); mPaint.setColor(Color.BLACK);
            canvas.drawText(text, lx, ly, mPaint);
            mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.WHITE);
            canvas.drawText(text, lx, ly, mPaint);
        }
    }

    private String getItemName(int id) {
        switch(id) {
            // Standard Selectable Items (TP Item IDs)
            case 0x3E: return "Hawkeye";
            case 0x3F: case 0x40: return "Gale Boomerang";
            case 0x41: return "Spinner";
            case 0x42: return "Ball and Chain";
            case 0x43: return "Hero's Bow";
            case 0x44: return "Clawshot";
            case 0x45: return "Iron Boots";
            case 0x46: case 0x4C: return "Dominion Rod";
            case 0x47: return "Double Clawshots";
            case 0x48: return "Lantern";
            case 0x49: return "Light Sword";
            case 0x4A: return "Fishing Rod";
            case 0x4B: return "Slingshot";
            
            case 0x4F: case 0x50: return "Bomb Bag";
            case 0x59: return "Bomb Arrow";

            // Equipment & Quest
            case 0x01: case 0x28: return "Ordon Sword";
            case 0x02: case 0x29: return "Master Sword";
            case 0x03: case 0x2B: return "Ordon Shield";
            case 0x04: case 0x2A: return "Wooden Shield";
            case 0x05: case 0x2C: return "Hylian Shield";
            case 0x0C: case 0x31: return "Zora Armor";
            case 0x0D: case 0x32: return "Magic Armor";
            case 0x14: case 0xA1: return "Shadow Crystal";
            case 0x17: case 0x83: return "Ancient Sky Book";
            
            // Bottle Items
            case 0x60: return "Empty Bottle";
            case 0x61: return "Red Potion";
            case 0x62: return "Green Potion";
            case 0x63: return "Blue Potion";
            case 0x64: return "Milk";
            case 0x65: return "Half Milk";
            case 0x66: case 0x68: case 0x6E: case 0x6F: return "Lantern Oil";
            case 0x67: return "Water";
            case 0x69: return "Red Chu Jelly";
            case 0x6A: return "Nasty Soup";
            case 0x6B: return "Hot Spring Water";
            case 0x6C: return "Fairy";
            
            // Ammo & Consumables
            case 0x70: return "Bomb";
            case 0x71: return "Water Bomb";
            case 0x72: return "Bombling";
            case 0x73: return "Fairy Drop";
            case 0x74: return "Worm";
            case 0x76: return "Bee Larva";
            case 0x77: return "Rare Chu Jelly";
            case 0x78: return "Red Chu Jelly";
            case 0x79: return "Blue Chu Jelly";
            case 0x7A: return "Green Chu Jelly";
            case 0x7B: case 0x9C: return "Yellow Chu Jelly";
            case 0x7C: return "Purple Chu Jelly";
            case 0x7D: return "Good Soup";
            case 0x7E: return "Simple Soup";
            case 0x7F: return "Superb Soup";
            
            // Quest Items
            case 0x80: return "Ooccoo";
            case 0x81: return "Ooccoo Jr.";
            case 0x82: return "Gate Pass";
            case 0x84: return "Horse Call";
            case 0x86: return "Fishing Hole Card";
            case 0x87: return "Ilia's Charm";
            case 0x88: return "Wooden Statue";
            case 0x89: return "Ilia's Memory";
            case 0x8A: return "Invoice";
            case 0x8B: return "Ashei's Sketch";
            case 0x8C: return "Auru's Memo";
            case 0x8D: return "Cannon Ball";
            
            case 0xBD: return "Mirror of Twilight";

            case 0xFF: return "";
            default: return "Item 0x" + Integer.toHexString(id).toUpperCase();
        }
    }

    private void drawStatusInfo(Canvas canvas, float x, float y) {
        resetPaint();

        int level = mState.batteryLevel;
        boolean charging = mState.isCharging;
        String batteryText = level >= 0 ? String.valueOf(level) : "??";

        // iOS-style battery cell proportions (Slightly larger)
        float pillW = 96, pillH = 46;
        float pillX = x - pillW - 40;
        float pillY = y - pillH / 2 - 10;
        float cornerRadius = 10f;
        float strokeWidth = 3.5f;

        // Fixed neutral outline/tip color - independent of battery state
        int outlineColor = Color.argb(230, 255, 255, 255);

        int statusColor;
        if (charging) {
            statusColor = Color.rgb(0, 160, 255);
        } else if (level > 50) {
            statusColor = Color.WHITE;
        } else if (level >= 20) {
            statusColor = Color.rgb(255, 193, 7);
        } else {
            statusColor = Color.rgb(232, 28, 28);
        }

        RectF outlineRect = new RectF(pillX, pillY, pillX + pillW, pillY + pillH);

        // 1. Outline (fixed color, stroked)
        resetPaint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(strokeWidth);
        mPaint.setColor(outlineColor);
        canvas.drawRoundRect(outlineRect, cornerRadius, cornerRadius, mPaint);

        // 2. Tip/nub (fixed color)
        mPaint.setStyle(Paint.Style.FILL);
        float tipW = 6, tipH = 16;
        canvas.drawRoundRect(new RectF(pillX + pillW, pillY + (pillH - tipH) / 2f,
                pillX + pillW + tipW, pillY + (pillH + tipH) / 2f), 2.5f, 2.5f, mPaint);

        // 3. Internal fill bar, proportional to level, colored by status
        float pad = strokeWidth + 3.5f;
        RectF innerRect = new RectF(pillX + pad, pillY + pad, pillX + pillW - pad, pillY + pillH - pad);
        float innerRadius = Math.max(cornerRadius - pad, 1.5f);

        if (level > 0) {
            float fillW = innerRect.width() * (level / 100f);
            mPaint.setColor(statusColor);
            canvas.save();
            mDrawPath.reset();
            mDrawPath.addRoundRect(innerRect, innerRadius, innerRadius, Path.Direction.CW);
            canvas.clipPath(mDrawPath);
            canvas.drawRect(innerRect.left, innerRect.top, innerRect.left + fillW, innerRect.bottom, mPaint);
            canvas.restore();
        }

        // 4. Percentage text, centered INSIDE the icon - stroke-then-fill so it stays
        // legible whether it's sitting over the colored fill or the unfilled track
        resetPaint();
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTextSize(30);
        mPaint.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));

        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float textY = pillY + (pillH - fm.ascent - fm.descent) / 2;
        float textX = pillX + pillW / 2f;

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(4.5f);
        mPaint.setColor(Color.argb(200, 0, 0, 0));
        canvas.drawText(batteryText, textX, textY, mPaint);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.WHITE);
        canvas.drawText(batteryText, textX, textY, mPaint);

        // 5. Bolt - bigger, centered over the nub, with outline for contrast against
        // both the light nub and the colored fill
        if (charging) {
            resetPaint();
            float bx = pillX + pillW + tipW / 2f;
            float by = pillY + pillH / 2f;
            float boltH = 22f;

            mDrawPath.reset();
            mDrawPath.moveTo(bx + 6, by - boltH);
            mDrawPath.lineTo(bx - 10, by + 2);
            mDrawPath.lineTo(bx - 3, by + 2);
            mDrawPath.lineTo(bx - 6, by + boltH);
            mDrawPath.lineTo(bx + 10, by - 2);
            mDrawPath.lineTo(bx + 3, by - 2);
            mDrawPath.close();

            // Dark outline pass first, for contrast against the light nub
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(3.5f);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
            mPaint.setColor(Color.argb(200, 0, 0, 0));
            canvas.drawPath(mDrawPath, mPaint);

            // Bright yellow fill on top - reads clearly against both the blue fill and background
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(Color.rgb(255, 214, 0));
            canvas.drawPath(mDrawPath, mPaint);
        }

        if (DEBUG_IDS) {
            resetPaint(); mPaint.setTextAlign(Paint.Align.RIGHT);
            mPaint.setTextSize(24); mPaint.setColor(Color.YELLOW);
            canvas.drawText("W:" + mState.windowStatus + " M:" + mState.mapStatus + " V:" + mState.visMask, x, y - 60, mPaint);
        }
    }
}
