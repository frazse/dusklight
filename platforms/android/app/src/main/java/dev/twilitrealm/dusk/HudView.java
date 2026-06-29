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

        // Draw Workspace Border
        resetPaint();
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(2.0f); mPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, 1280, 1080, mPaint);

        updateRupeeAnimation();

        drawHearts(canvas, 20, 20);
        drawMagicBar(canvas, 20, 150);
        
        if (mState.showOxygen) drawOxygenBar(canvas, 780, 150);
        else {
            boolean hasLantern = (mState.itemXResId == 0x48 || mState.itemYResId == 0x48 || 
                                 mState.itemDDownId == 0x48 || mState.itemDLeftId == 0x48 || 
                                 mState.itemDRightId == 0x48);
            if (hasLantern) drawOilBar(canvas, 780, 150);
        }
        drawItems(canvas, 1280, 20);

        drawRupeeCounter(canvas, 20, 1060);
        
        if (mState.showLightDrops && mState.maxLightDrops > 0) {
            float width = (mState.maxLightDrops - 1) * 45;
            drawLightDropZigZag(canvas, 640 - (width / 2), 1055);
        } else if (mState.isRiding) {
            float width = (6 - 1) * 68 + 57;
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
        resetPaint();
        mPaint.setColor(Color.argb(120, 15, 15, 50));
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

        int colorMint = Color.parseColor("#9FE173");
        int colorWater = Color.parseColor("#3984C5");
        int colorTerrain = Color.parseColor("#3E8E1F");

        if (mState.mapLines != null) {
            float[] strip = new float[32768];
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
            vCount = 0; i = 0;
            while (i < mState.mapLines.length - 1) {
                float val = mState.mapLines[i];
                if (Float.isNaN(val)) {
                    int id0 = (int)mState.mapLines[i+1], id1 = (int)mState.mapLines[i+2];
                    if (id1 <= 1000) {
                        if (id1 == 1 || id1 == 2) {
                            mPaint.setStyle(Paint.Style.STROKE);
                            mPaint.setStrokeWidth(id1 == 2 ? 4.0f : 2.5f);
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
        if (mState.mapIcons != null) {
            for (int i = 0; i < mState.mapIcons.length; i += 4) {
                drawMapIcon(canvas, cX + (mState.mapIcons[i+1] - sCX) * mS, cY + (mState.mapIcons[i+2] - sCZ) * mS, (int)mState.mapIcons[i]);
            }
        }
        
        // Restart / Entrance Marker (Cyan)
        if (mState.showRestart) {
            drawMapPointer(canvas, cX + (mState.restartX - sCX) * mS, cY + (mState.restartY - sCZ) * mS, mState.restartAngle, Color.CYAN);
        }

        // Player Marker (Yellow)
        drawMapPointer(canvas, cX + (mState.mapX - sCX) * mS, cY + (mState.mapY - sCZ) * mS, mState.mapAngle, Color.YELLOW);
        
        canvas.restore();
        resetPaint(); mPaint.setTextAlign(Paint.Align.CENTER); mPaint.setTextSize(32); mPaint.setColor(Color.WHITE);
        canvas.drawText(mState.stageName, x + MAP_SIZE/2, y + MAP_SIZE + 40, mPaint);
    }

    private void drawMapPointer(Canvas canvas, float x, float y, float angle, int color) {
        canvas.save(); canvas.translate(x, y);
        canvas.rotate(180 - angle); mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(color);
        mDrawPath.reset(); mDrawPath.moveTo(0, -22); mDrawPath.lineTo(-13, 13); mDrawPath.lineTo(13, 13); mDrawPath.close();
        canvas.drawPath(mDrawPath, mPaint); canvas.restore();
    }

    private void drawMapIcon(Canvas canvas, float x, float y, int type) {
        resetPaint();
        if (type == 4) { mPaint.setColor(Color.WHITE); canvas.drawCircle(x, y, 6, mPaint); mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.YELLOW); mPaint.setStrokeWidth(2); canvas.drawCircle(x, y, 6, mPaint); }
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
            mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.WHITE); canvas.drawPath(mHeartPath, mPaint);
        }
    }

    private void drawMagicBar(Canvas canvas, float x, float y) {
        if (mState.maxMagic <= 0) return;
        resetPaint(); mPaint.setColor(Color.argb(100, 0, 50, 0));
        canvas.drawRect(x, y, x + 740, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + 740, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.rgb(0, 255, 120));
        canvas.drawRect(x + 2, y + 2, x + (740 * (float)mState.magic / mState.maxMagic) - 2, y + 20, mPaint);
    }

    private void drawOilBar(Canvas canvas, float x, float y) {
        resetPaint(); mPaint.setColor(Color.argb(100, 50, 40, 0));
        canvas.drawRect(x, y, x + 300, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + 300, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.rgb(255, 220, 0));
        canvas.drawRect(x + 2, y + 2, x + (300 * (float)mState.oil / mState.maxOil) - 2, y + 20, mPaint);
    }

    private void drawOxygenBar(Canvas canvas, float x, float y) {
        resetPaint(); mPaint.setColor(Color.argb(100, 0, 30, 50));
        canvas.drawRect(x, y, x + 300, y + 22, mPaint);
        mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.WHITE);
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
            mPaint.setStyle(Paint.Style.STROKE); mPaint.setColor(Color.WHITE); mPaint.setStrokeWidth(2.5f);
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
        path.cubicTo(529.35f, 106.93f, 525.20f, 95.38f, 518.45f, 84.83f);
        path.lineTo(518.53f, 81.62f);
        path.lineTo(530.90f, 57.09f);
        path.lineTo(516.45f, 43.08f);
        path.cubicTo(515.60f, 42.30f, 513.71f, 41.78f, 512.70f, 42.17f);
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
        drawActionButton(canvas, x, startY, "L", Color.rgb(200, 200, 200), mState.buttonLText, mState.buttonLText != null && !mState.buttonLText.isEmpty());
        drawActionButton(canvas, x, startY + spacing, "R", Color.rgb(200, 200, 200), mState.buttonRText, mState.buttonRText != null && !mState.buttonRText.isEmpty());
        drawActionButton(canvas, x, startY + spacing * 2, "Z", Color.argb(255, 100, 200, 255), mState.buttonZText, mState.buttonZText != null && !mState.buttonZText.isEmpty());
        drawActionButton(canvas, x, startY + spacing * 3, "A", Color.rgb(0, 200, 50), mState.buttonAText, mState.buttonAText != null && !mState.buttonAText.isEmpty());
        drawActionButton(canvas, x, startY + spacing * 4, "B", Color.RED, mState.buttonBText, mState.buttonBText != null && !mState.buttonBText.isEmpty());
        String yT = mState.buttonYText; boolean yA = (yT != null && !yT.isEmpty());
        if (!yA && (mState.transform != 1)) { yT = getItemName(mState.itemYResId); yA = (mState.itemYResId != 0xFF); if (mState.itemYCount > 0) yT += " (" + mState.itemYCount + ")"; }
        drawActionButton(canvas, x, startY + spacing * 5, "Y", Color.rgb(255, 255, 0), yT, yA);
        String xT = mState.buttonXText; boolean xA = (xT != null && !xT.isEmpty());
        if (!xA && (mState.transform != 1)) { xT = getItemName(mState.itemXResId); xA = (mState.itemXResId != 0xFF); if (mState.itemXCount > 0) xT += " (" + mState.itemXCount + ")"; }
        drawActionButton(canvas, x, startY + spacing * 6, "X", Color.rgb(255, 165, 0), xT, xA);
    }

    private void drawActionButton(Canvas canvas, float x, float y, String label, int color, String text, boolean active) {
        resetPaint(); mPaint.setTextAlign(Paint.Align.CENTER); mPaint.setTextSize(42);
        int circleColor = color; boolean isMidna = active && "Midna".equals(text); boolean isPulse = isMidna && mState.midnaCalling;
        if (isMidna) circleColor = isPulse ? Color.rgb(255, 200, 30) : Color.rgb(180, 50, 255);
        mPaint.setColor(active ? circleColor : Color.argb(60, 100, 100, 100));
        float r = isPulse ? 42 : 38; canvas.drawCircle(x, y, r, mPaint);
        mPaint.setColor(active ? Color.BLACK : Color.argb(100, 200, 200, 200));
        canvas.drawText(label, x, y + 15, mPaint);
        if (active && text != null && !text.isEmpty()) {
            mPaint.setTextAlign(Paint.Align.LEFT); mPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            mPaint.setTextSize(42);
            float tx = x + 60, ty = y + 15;
            mPaint.setStyle(Paint.Style.STROKE); mPaint.setStrokeWidth(5.0f); mPaint.setColor(Color.BLACK);
            canvas.drawText(text, tx, ty, mPaint);
            mPaint.setStyle(Paint.Style.FILL); mPaint.setColor(Color.WHITE);
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
        resetPaint(); mPaint.setTextAlign(Paint.Align.RIGHT); mPaint.setTextSize(38);
        mPaint.setColor(mState.transform == 1 ? Color.CYAN : Color.WHITE);
        canvas.drawText("FORM: " + (mState.transform == 1 ? "WOLF" : "HUMAN"), x, y, mPaint);
    }
}
