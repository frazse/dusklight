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
    private GameState mState;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mHeartPath = new Path();
    private final Path mDrawPath = new Path();
    
    private int mMapZoomLevel = 0; 
    private final float MAP_X = 20;
    private final float MAP_Y = 210;
    private final float MAP_SIZE = 720;

    private int mDisplayRupees = -1;
    private int mRupeeTimer = 0;

    public HudView(Context context) {
        super(context);
    }

    public void update(GameState state) {
        mState = state;
        invalidate();
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
                mMapZoomLevel = (mMapZoomLevel + 1) % 4;
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

        float scale = Math.min((float) getWidth() / 1280f, (float) getHeight() / 1080f);
        canvas.save();
        canvas.translate((getWidth() - 1280 * scale) / 2, (getHeight() - 1080 * scale) / 2);
        canvas.scale(scale, scale);

        // Draw 1280x1080 Workspace Border
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(2.0f); mPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, 1280, 1080, mPaint);

        updateRupeeAnimation();

        drawHearts(canvas, 20, 20);
        drawMagicBar(canvas, 20, 150);
        
        if (mState.showOxygen) drawOxygenBar(canvas, 780, 150);
        else drawOilBar(canvas, 780, 150);
        drawItems(canvas, 1260, 58);

        drawRupeeCounter(canvas, 20, 1060);
        
        if (mState.showLightDrops && mState.maxLightDrops > 0) {
            float width = (mState.maxLightDrops - 1) * 32;
            drawLightDropZigZag(canvas, 640 - (width / 2), 1055);
        } else if (mState.isRiding) {
            float width = (6 - 1) * 53 + 45;
            drawHorseSpurs(canvas, 640 - (width / 2), 1060);
        }
        
        drawMiniMap(canvas, MAP_X, MAP_Y);
        drawContextButtons(canvas, 820, 280);
        
        drawStatusInfo(canvas, 1260, 1060);

        canvas.restore();
        if (mState.midnaCalling) postInvalidateOnAnimation();
    }

    private int getRupeeStep(int diff) {
        if (diff > 100) return 10;
        if (diff > 50)  return 5;
        if (diff > 10)  return 2;
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
        mRupeeTimer = (absDelta > 50) ? 0 : 1; 
    }

    private void drawMiniMap(Canvas canvas, float x, float y) {
        mPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.argb(120, 15, 15, 50));
        canvas.drawRect(x, y, x + MAP_SIZE, y + MAP_SIZE, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(3); mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + MAP_SIZE, y + MAP_SIZE, mPaint);
        if (mState.mapMaxX <= mState.mapMinX || mState.mapMaxZ <= mState.mapMinZ) return;
        
        float baseScale = (MAP_SIZE * 0.92f) / Math.max(mState.mapMaxX - mState.mapMinX, mState.mapMaxZ - mState.mapMinZ);
        float mS = (mMapZoomLevel > 0) ? baseScale * (float)Math.pow(2, mMapZoomLevel) : baseScale;
        float sCX = (mMapZoomLevel > 0) ? mState.mapX : (mState.mapMaxX + mState.mapMinX)/2f;
        float sCZ = (mMapZoomLevel > 0) ? mState.mapY : (mState.mapMaxZ + mState.mapMinZ)/2f;
        float cX = x + MAP_SIZE/2, cY = y + MAP_SIZE/2;
        
        canvas.save(); canvas.clipRect(x, y, x + MAP_SIZE, y + MAP_SIZE);

        int colorMint = Color.rgb(155, 205, 155);
        int colorWater = Color.rgb(65, 110, 220);
        int colorTerrain = Color.rgb(45, 75, 45);

        if (mState.mapLines != null) {
            float[] strip = new float[32768];
            
            // PASS 1: DRAW ALL POLYGONS (GROUND/WATER)
            int vCount = 0; int i = 0;
            while (i < mState.mapLines.length - 1) {
                float val = mState.mapLines[i];
                if (Float.isNaN(val)) {
                    int id0 = (int)mState.mapLines[i+1], id1 = (int)mState.mapLines[i+2];
                    if (id1 > 1000) {
                        int pId = id0 & 0x3F;
                        if (pId != 3 && pId != 4 && pId != 6) {
                            mPaint.setStyle(Paint.Style.FILL_AND_STROKE); mPaint.setStrokeWidth(0.8f);
                            if (pId == 5) mPaint.setColor(colorWater);
                            else if (pId == 1) mPaint.setColor(colorMint);
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

            // PASS 2: DRAW ALL LINES (WALLS/RIDGES) ON TOP
            vCount = 0; i = 0;
            while (i < mState.mapLines.length - 1) {
                float val = mState.mapLines[i];
                if (Float.isNaN(val)) {
                    int id0 = (int)mState.mapLines[i+1], id1 = (int)mState.mapLines[i+2];
                    if (id1 <= 1000) {
                        if (id1 == 1 || id1 == 2) {
                            mPaint.setStyle(Paint.Style.STROKE);
                            mPaint.setStrokeWidth(id1 == 2 ? 4.0f : 2.5f); // Bold walls, subtle ridges
                            mPaint.setStrokeJoin(Paint.Join.ROUND); mPaint.setStrokeCap(Paint.Cap.ROUND);
                            mPaint.setColor(colorMint); mDrawPath.reset();
                            for (int j = 0; j < vCount; j++) {
                                if (j == 0) mDrawPath.moveTo(strip[j*2], strip[j*2+1]);
                                else mDrawPath.lineTo(strip[j*2], strip[j*2+1]);
                            }
                            canvas.drawPath(mDrawPath, mPaint);
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
        }

        // Icons
        if (mState.mapIcons != null) {
            for (int i = 0; i < mState.mapIcons.length; i += 4) {
                float ix = cX + (mState.mapIcons[i+1] - sCX) * mS;
                float iy = cY + (mState.mapIcons[i+2] - sCZ) * mS;
                drawMapIcon(canvas, ix, iy, (int)mState.mapIcons[i]);
            }
        }

        // Player Cursor
        canvas.save(); canvas.translate(cX + (mState.mapX - sCX) * mS, cY + (mState.mapY - sCZ) * mS);
        canvas.rotate(180 - mState.mapAngle); mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.YELLOW);
        mDrawPath.reset(); mDrawPath.moveTo(0, -22); mDrawPath.lineTo(-13, 13); mDrawPath.lineTo(13, 13); mDrawPath.close();
        canvas.drawPath(mDrawPath, mPaint); canvas.restore();
        
        canvas.restore();
        mPaint.setTextAlign(Paint.Align.CENTER); mPaint.setTextSize(32); mPaint.setColor(Color.WHITE);
        canvas.drawText(mState.stageName, x + MAP_SIZE/2, y + MAP_SIZE + 40, mPaint);
    }

    private void drawMapIcon(Canvas canvas, float x, float y, int type) {
        mPaint.setStyle(Paint.Style.FILL);
        if (type == 4) { mPaint.setColor(Color.WHITE); canvas.drawCircle(x, y, 6, mPaint); mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.YELLOW); mPaint.setStrokeWidth(2); canvas.drawCircle(x, y, 6, mPaint); mPaint.setStyle(Paint.Style.FILL); }
        else if (type == 0 || type == 10) { mPaint.setColor(Color.YELLOW); canvas.drawRect(x-8, y-8, x+8, y+8, mPaint); }
        else { mPaint.setColor(Color.RED); canvas.drawCircle(x, y, 8, mPaint); }
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
        mPaint.setStrokeWidth(3); mPaint.setColor(Color.WHITE); mHeartPath.reset();
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
            mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.WHITE); canvas.drawPath(mHeartPath, mPaint);
        }
    }

    private void drawMagicBar(Canvas canvas, float x, float y) {
        if (mState.maxMagic <= 0) return;
        float width = 740;
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.argb(100, 0, 50, 0));
        canvas.drawRect(x, y, x + width, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + width, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.rgb(0, 255, 120));
        canvas.drawRect(x + 2, y + 2, x + (width * (float)mState.magic / mState.maxMagic) - 2, y + 20, mPaint);
    }

    private void drawOilBar(Canvas canvas, float x, float y) {
        if (mState.maxOil <= 0) return;
        float width = 300;
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.argb(100, 50, 40, 0));
        canvas.drawRect(x, y, x + 300, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + 300, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.rgb(255, 220, 0));
        canvas.drawRect(x + 2, y + 2, x + (300 * (float)mState.oil / mState.maxOil) - 2, y + 20, mPaint);
    }

    private void drawOxygenBar(Canvas canvas, float x, float y) {
        if (mState.maxOxygen <= 0) return;
        float width = 300;
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.argb(100, 0, 30, 50));
        canvas.drawRect(x, y, x + 300, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + 300, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.rgb(0, 180, 255));
        canvas.drawRect(x + 2, y + 2, x + (300 * (float)mState.oxygen / mState.maxOxygen) - 2, y + 20, mPaint);
    }

    private void drawRupeeCounter(Canvas canvas, float x, float y) {
        float w = 40, h = 58, centerY = y - 24; 
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.rgb(0, 180, 0)); mDrawPath.reset();
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

        // STYLIZED RUPEE TEXT
        String text = String.valueOf(mDisplayRupees);
        float textX = x + w + 15;
        mPaint.setTextAlign(Paint.Align.LEFT);
        mPaint.setTextSize(72);
        mPaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD));

        // 1. Draw heavy outline + shadow
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(6.0f);
        mPaint.setColor(Color.rgb(35, 26, 18)); // Outline: #231A12
        mPaint.setShadowLayer(4.0f, 3.0f, 3.0f, Color.rgb(74, 56, 40)); // Shadow: #4A3828
        canvas.drawText(text, textX, y, mPaint);
        mPaint.clearShadowLayer();

        // 2. Draw vertical gradient fill
        mPaint.setStyle(Paint.Style.FILL);
        int fillTop = Color.rgb(255, 249, 232); // Highlight: #FFF9E8
        int fillBottom = Color.rgb(240, 232, 208); // Main fill: #F0E8D0
        
        // Use standard cream look by default. 
        // Turns pure white (#FFFFFF) only when gaining rupees.
        if (mState.rupees > mDisplayRupees) {
            fillTop = Color.WHITE;
            fillBottom = Color.rgb(230, 230, 230);
        }

        mPaint.setShader(new android.graphics.LinearGradient(0, y - 60, 0, y, fillTop, fillBottom, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawText(text, textX, y, mPaint);
        mPaint.setShader(null);
        mPaint.setTypeface(android.graphics.Typeface.DEFAULT);
    }

    private void drawLightDropZigZag(Canvas canvas, float x, float y) {
        for (int i = 0; i < mState.maxLightDrops; i++) {
            float dx = x + i * 32, dy = y - (i % 2 == 0 ? 0 : 18);
            if (i < mState.lightDrops) {
                mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.WHITE); canvas.drawCircle(dx, dy, 9, mPaint);
                mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.YELLOW); mPaint.setStrokeWidth(3); canvas.drawCircle(dx, dy, 9, mPaint);
            } else {
                mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.GRAY); mPaint.setStrokeWidth(2); canvas.drawCircle(dx, dy, 9, mPaint);
                mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.argb(40, 100, 100, 100)); canvas.drawCircle(dx, dy, 9, mPaint);
            }
        }
    }

    private void drawHorseSpurs(Canvas canvas, float x, float y) {
        for (int i = 0; i < 6; i++) {
            float cx = x + i * 53, cy = y - 15;
            mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.WHITE); mPaint.setStrokeWidth(2);
            canvas.drawCircle(cx, cy, 22.5f, mPaint);
            if (i < mState.horseSpurs) {
                mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.rgb(255, 140, 0));
                canvas.drawCircle(cx, cy, 19.5f, mPaint);
            }
        }
    }

    private void drawItems(Canvas canvas, float x, float y) {
        mPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        mPaint.setTextAlign(Paint.Align.RIGHT); mPaint.setTextSize(38); mPaint.setColor(Color.WHITE);
        canvas.drawText("Arrows: " + mState.arrows, x, y, mPaint);
        canvas.drawText("Bombs:  " + mState.bombs, x, y + 45, mPaint);
        canvas.drawText("Keys:   " + mState.keys, x, y + 90, mPaint);
    }

    private void drawContextButtons(Canvas canvas, float x, float startY) {
        mPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        mPaint.setTextSize(42); float spacing = 95;
        boolean isWolf = (mState.transform == 1);
        drawActionButton(canvas, x, startY, "L", Color.rgb(200, 200, 200), mState.buttonLText, mState.buttonLText != null && !mState.buttonLText.isEmpty());
        drawActionButton(canvas, x, startY + spacing, "R", Color.rgb(200, 200, 200), mState.buttonRText, mState.buttonRText != null && !mState.buttonRText.isEmpty());
        drawActionButton(canvas, x, startY + spacing * 2, "Z", Color.argb(255, 100, 200, 255), mState.buttonZText, mState.buttonZText != null && !mState.buttonZText.isEmpty());
        drawActionButton(canvas, x, startY + spacing * 3, "A", Color.rgb(0, 200, 50), mState.buttonAText, mState.buttonAText != null && !mState.buttonAText.isEmpty());
        drawActionButton(canvas, x, startY + spacing * 4, "B", Color.RED, mState.buttonBText, mState.buttonBText != null && !mState.buttonBText.isEmpty());
        String yT = mState.buttonYText; boolean yA = (yT != null && !yT.isEmpty());
        if (!yA && !isWolf) { yT = getItemName(mState.itemYResId); yA = (mState.itemYResId != 0xFF); if (mState.itemYCount > 0) yT += " (" + mState.itemYCount + ")"; }
        drawActionButton(canvas, x, startY + spacing * 5, "Y", Color.rgb(255, 255, 0), yT, yA);
        String xT = mState.buttonXText; boolean xA = (xT != null && !xT.isEmpty());
        if (!xA && !isWolf) { xT = getItemName(mState.itemXResId); xA = (mState.itemXResId != 0xFF); if (mState.itemXCount > 0) xT += " (" + mState.itemXCount + ")"; }
        drawActionButton(canvas, x, startY + spacing * 6, "X", Color.rgb(255, 165, 0), xT, xA);
    }

    private void drawActionButton(Canvas canvas, float x, float y, String label, int color, String text, boolean active) {
        mPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mPaint.setTextAlign(Paint.Align.CENTER); mPaint.setStyle(Paint.Style.FILL);
        int circleColor = color; boolean isMidna = active && "Midna".equals(text); boolean isPulse = isMidna && mState.midnaCalling;
        if (isMidna) circleColor = isPulse ? Color.rgb(255, 200, 30) : Color.rgb(180, 50, 255);
        mPaint.setColor(active ? circleColor : Color.argb(60, 100, 100, 100));
        float r = isPulse ? 42 : 38; canvas.drawCircle(x, y, r, mPaint);
        mPaint.setColor(active ? Color.BLACK : Color.argb(100, 200, 200, 200));
        canvas.drawText(label, x, y + 15, mPaint);

        if (active && text != null && !text.isEmpty()) {
            mPaint.setTextAlign(Paint.Align.LEFT);
            float tx = x + 60, ty = y + 15;
            
            // DRAW "ROLL" STYLE: Heavy Black Outline + White Fill
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(5.0f);
            mPaint.setColor(Color.BLACK);
            canvas.drawText(text, tx, ty, mPaint);
            
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(Color.WHITE);
            canvas.drawText(text, tx, ty, mPaint);
        }
    }

    private String getItemName(int id) {
        switch(id) {
            case 0x43: return "Hero's Bow"; case 0x53: return "Light Arrow"; case 0x4B: return "Slingshot";
            case 0x40: return "Boomerang"; case 0x44: return "Hookshot"; case 0x47: return "Double Clawshots";
            case 0x70: return "Bomb"; case 0x71: return "Water Bomb"; case 0x72: return "Bombling";
            case 0x45: return "Iron Boots"; case 0x48: return "Lantern"; case 0x46: return "Dominion Rod";
            case 0x41: return "Spinner"; case 0x42: return "Ball and Chain"; case 0x4A: return "Fishing Rod";
            case 0x84: return "Horse Call"; case 0x80: return "Ooccoo"; case 0x3E: return "Hawkeye";
            case 0x60: return "Empty Bottle"; case 0x61: return "Red Potion"; case 0x62: return "Green Potion";
            case 0x63: return "Blue Potion"; case 0x64: return "Milk"; case 0x65: return "Half Milk";
            case 0x6F: return "Lantern Oil"; case 0x67: return "Water"; case 0x6C: return "Fairy";
            case 0x73: return "Fairy Drop"; case 0x74: return "Worm"; case 0x76: return "Bee Larva";
            case 0x79: return "Blue Chu Jelly"; case 0x78: return "Red Chu Jelly"; case 0x7B: return "Yellow Chu Jelly";
            case 0x7C: return "Purple Chu Jelly"; case 0x77: return "Rare Chu Jelly"; case 0x6B: return "Hot Spring Water";
            case 0x6A: return "Nasty Soup"; case 0x7D: return "Good Soup"; case 0x7F: return "Superb Soup";
            case 0xFF: return ""; default: return "Item 0x" + Integer.toHexString(id).toUpperCase();
        }
    }

    private void drawStatusInfo(Canvas canvas, float x, float y) {
        mPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        mPaint.setTextAlign(Paint.Align.RIGHT); mPaint.setTextSize(38);
        mPaint.setColor(mState.transform == 1 ? Color.CYAN : Color.WHITE);
        canvas.drawText("FORM: " + (mState.transform == 1 ? "WOLF" : "HUMAN"), x, y, mPaint);
    }
}
