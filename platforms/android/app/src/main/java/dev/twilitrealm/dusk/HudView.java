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
    private boolean mSizeLogged = false;

    // Map Zoom State
    private int mMapZoomLevel = 0; // 0: Fit, 1: 2x, 2: 4x, 3: 8x
    private final float MAP_X = 20;
    private final float MAP_Y = 210;
    private final float MAP_SIZE = 720;

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
            float tx = event.getX();
            float ty = event.getY();

            // Reverse the workspace scaling/translation logic from onDraw
            float scaleX = (float) getWidth() / 1280.0f;
            float scaleY = (float) getHeight() / 1080.0f;
            float scale = Math.min(scaleX, scaleY);
            float translateX = (getWidth() - 1280 * scale) / 2;
            float translateY = (getHeight() - 1080 * scale) / 2;

            float logicalX = (tx - translateX) / scale;
            float logicalY = (ty - translateY) / scale;

            // Check if tap is inside minimap bounds
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
        
        if (!mSizeLogged) {
            Log.d(TAG, "HudView size: " + getWidth() + "x" + getHeight());
            mSizeLogged = true;
        }

        canvas.drawColor(Color.BLACK);

        if (mState == null) {
            mPaint.setColor(Color.RED);
            mPaint.setTextSize(50);
            canvas.drawText("Waiting for Game State...", 100, 100, mPaint);
            return;
        }

        // Safe area border (subtle)
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
        mPaint.setColor(Color.argb(40, 255, 255, 255));
        canvas.drawRect(20, 20, getWidth() - 20, getHeight() - 20, mPaint);

        float scaleX = (float) getWidth() / 1280.0f;
        float scaleY = (float) getHeight() / 1080.0f;
        float scale = Math.min(scaleX, scaleY);
        
        canvas.save();
        canvas.translate((getWidth() - 1280 * scale) / 2, (getHeight() - 1080 * scale) / 2);
        canvas.scale(scale, scale);

        // 1280x1080 Workspace Border (Diagnostic)
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(4);
        mPaint.setColor(Color.MAGENTA);
        canvas.drawRect(0, 0, 1280, 1080, mPaint);
        mPaint.setStrokeWidth(2); // Reset stroke width for other elements

        drawHearts(canvas, 20, 20);
        drawMagicBar(canvas, 20, 150);
        drawOilBar(canvas, 340, 150);
        drawOxygenBar(canvas, 660, 150);
        
        drawRupeeCounter(canvas, 20, 1040);
        drawLightDrops(canvas, 280, 1040);
        
        drawItems(canvas, 1260, 40);
        
        // Minimap
        drawMiniMap(canvas, MAP_X, MAP_Y);
        
        // Context Labels (A / B / Z / L / X / Y) in a vertical row to the right of the minimap
        drawContextButtons(canvas, 820, 280);
        
        drawStatusInfo(canvas, 1100, 1040);

        canvas.restore();

        if (mState.midnaCalling) {
            postInvalidateOnAnimation();
        }
    }

    private void drawHearts(Canvas canvas, float startX, float startY) {
        int maxHearts = mState.maxHealth / 5;
        int currentQuarters = mState.health;
        float heartSize = 58;
        float gap = 12;
        
        for (int i = 0; i < maxHearts; i++) {
            float x = startX + (i % 10) * (heartSize + gap);
            float y = startY + (i / 10) * (heartSize + gap);
            int fill = Math.min(4, Math.max(0, currentQuarters - (i * 4)));
            drawZeldaHeart(canvas, x, y, heartSize, fill);
        }
    }

    private void drawZeldaHeart(Canvas canvas, float x, float y, float size, int fill) {
        mPaint.setStrokeWidth(3);
        mPaint.setColor(Color.WHITE);
        
        mHeartPath.reset();
        float mid = size / 2;
        float splitY = y + size * 0.62f; // Adjusted horizontal split down to reduce "top heavy" feel
        
        mHeartPath.moveTo(x + mid, y + size * 0.35f);
        mHeartPath.cubicTo(x + size * 0.9f, y - size * 0.1f, x + size * 1.1f, y + size * 0.6f, x + mid, y + size);
        mHeartPath.cubicTo(x - size * 0.1f, y + size * 0.6f, x + size * 0.1f, y - size * 0.1f, x + mid, y + size * 0.35f);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.argb(80, 50, 0, 0));
        canvas.drawPath(mHeartPath, mPaint);
        
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(Color.WHITE);
        canvas.drawPath(mHeartPath, mPaint);

        if (fill > 0) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(Color.RED);
            canvas.save();
            canvas.clipPath(mHeartPath);
            // Draw 4 quarters, using adjusted splitY
            if (fill >= 1) canvas.drawRect(x - size * 0.2f, y - size * 0.2f, x + mid, splitY, mPaint);    // Top-Left
            if (fill >= 2) canvas.drawRect(x - size * 0.2f, splitY, x + mid, y + size * 1.2f, mPaint);   // Bottom-Left
            if (fill >= 3) canvas.drawRect(x + mid, y - size * 0.2f, x + size * 1.2f, splitY, mPaint);   // Top-Right
            if (fill >= 4) canvas.drawRect(x + mid, splitY, x + size * 1.2f, y + size * 1.2f, mPaint);  // Bottom-Right
            canvas.restore();
            
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(Color.WHITE);
            canvas.drawPath(mHeartPath, mPaint);
            
            if (fill > 0 && fill < 4) {
                mPaint.setColor(Color.argb(100, 255, 255, 255));
                mPaint.setStrokeWidth(1);
                // Vertical divider
                canvas.drawLine(x + mid, y + size * 0.1f, x + mid, y + size * 0.95f, mPaint);
                // Horizontal divider (using adjusted splitY)
                canvas.drawLine(x + size * 0.1f, splitY, x + size * 0.9f, splitY, mPaint);
            }
        }
    }

    private void drawMagicBar(Canvas canvas, float x, float y) {
        if (mState.maxMagic <= 0) return;
        float width = 300;
        float height = 22;
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.argb(100, 0, 50, 0));
        canvas.drawRect(x, y, x + width, y + height, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
        mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + width, y + height, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.rgb(0, 255, 120));
        float fillRatio = (float)mState.magic / mState.maxMagic;
        canvas.drawRect(x + 2, y + 2, x + (width * fillRatio) - 2, y + height - 2, mPaint);
    }

    private void drawOilBar(Canvas canvas, float x, float y) {
        if (mState.maxOil <= 0) return;
        float width = 300;
        float height = 22;
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.argb(100, 50, 40, 0));
        canvas.drawRect(x, y, x + width, y + height, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
        mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + width, y + height, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.rgb(255, 220, 0));
        float fillRatio = (float)mState.oil / mState.maxOil;
        canvas.drawRect(x + 2, y + 2, x + (width * fillRatio) - 2, y + height - 2, mPaint);
    }

    private void drawOxygenBar(Canvas canvas, float x, float y) {
        if (mState.maxOxygen <= 0) return;
        float width = 300;
        float height = 22;
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.argb(100, 0, 30, 50));
        canvas.drawRect(x, y, x + width, y + height, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
        mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + width, y + height, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.rgb(0, 180, 255));
        float fillRatio = (float)mState.oxygen / mState.maxOxygen;
        canvas.drawRect(x + 2, y + 2, x + (width * fillRatio) - 2, y + height - 2, mPaint);
    }

    private void drawRupeeCounter(Canvas canvas, float x, float y) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.GREEN);
        mPaint.setTextSize(65);
        mPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("◆ " + mState.rupees, x, y, mPaint);
    }

    private void drawLightDrops(Canvas canvas, float x, float y) {
        if (!mState.showLightDrops || mState.maxLightDrops <= 0) return;
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.rgb(200, 200, 255));
        mPaint.setTextSize(55);
        mPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("◌ " + mState.lightDrops + " / " + mState.maxLightDrops, x, y, mPaint);
    }

    private void drawItems(Canvas canvas, float x, float y) {
        mPaint.setTextSize(38);
        mPaint.setTextAlign(Paint.Align.RIGHT);
        mPaint.setColor(Color.WHITE);
        canvas.drawText("Arrows: " + mState.arrows, x, y, mPaint);
        canvas.drawText("Bombs:  " + mState.bombs, x, y + 45, mPaint);
        canvas.drawText("Keys:   " + mState.keys, x, y + 90, mPaint);
    }

    private void drawContextButtons(Canvas canvas, float x, float startY) {
        mPaint.setTextSize(42);
        float spacing = 95;
        
        // Action Buttons (L, R, Z, A, B, Y, X)
        boolean lActive = mState.buttonLText != null && !mState.buttonLText.isEmpty();
        drawActionButton(canvas, x, startY, "L", Color.rgb(200, 200, 200), mState.buttonLText, lActive);

        boolean rActive = mState.buttonRText != null && !mState.buttonRText.isEmpty();
        drawActionButton(canvas, x, startY + spacing, "R", Color.rgb(200, 200, 200), mState.buttonRText, rActive);

        boolean zActive = mState.buttonZText != null && !mState.buttonZText.isEmpty();
        drawActionButton(canvas, x, startY + spacing * 2, "Z", Color.argb(255, 100, 200, 255), mState.buttonZText, zActive);

        boolean aActive = mState.buttonAText != null && !mState.buttonAText.isEmpty();
        drawActionButton(canvas, x, startY + spacing * 3, "A", Color.rgb(0, 200, 50), mState.buttonAText, aActive);
        
        boolean bActive = mState.buttonBText != null && !mState.buttonBText.isEmpty();
        drawActionButton(canvas, x, startY + spacing * 4, "B", Color.RED, mState.buttonBText, bActive);

        String yText = mState.buttonYText;
        if (yText == null || yText.isEmpty()) {
            yText = getItemName(mState.itemYResId);
            if (mState.itemYCount > 0) yText += " (" + mState.itemYCount + ")";
        }
        boolean yActive = mState.itemYResId != 0xFF || (mState.buttonYText != null && !mState.buttonYText.isEmpty());
        drawActionButton(canvas, x, startY + spacing * 5, "Y", Color.rgb(255, 255, 0), yText, yActive);

        String xText = mState.buttonXText;
        if (xText == null || xText.isEmpty()) {
            xText = getItemName(mState.itemXResId);
            if (mState.itemXCount > 0) xText += " (" + mState.itemXCount + ")";
        }
        boolean xActive = mState.itemXResId != 0xFF || (mState.buttonXText != null && !mState.buttonXText.isEmpty());
        drawActionButton(canvas, x, startY + spacing * 6, "X", Color.rgb(255, 165, 0), xText, xActive);
    }

    private void drawActionButton(Canvas canvas, float x, float y, String label, int color, String text, boolean active) {
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setStyle(Paint.Style.FILL);
        
        int circleColor = color;
        boolean isMidna = active && "Midna".equals(text);
        boolean isPulse = isMidna && mState.midnaCalling;
        
        if (isMidna) {
            if (isPulse) {
                float pulse = (float)(Math.sin(System.currentTimeMillis() / 150.0) * 0.5 + 0.5);
                // Pulse between Midna's orange and a brighter yellow
                int r = 255;
                int g = (int)(140 + 80 * pulse);
                int b = (int)(30 * (1.0f - pulse));
                circleColor = Color.rgb(r, g, b);
            } else {
                // Midna's default purple
                circleColor = Color.rgb(180, 50, 255);
            }
        }

        if (active) {
            mPaint.setColor(circleColor);
        } else {
            mPaint.setColor(Color.argb(60, 100, 100, 100));
        }
        
        float radius = 38;
        if (isPulse) {
            radius += 4 * (float)(Math.sin(System.currentTimeMillis() / 150.0) * 0.5 + 0.5);
        }
        canvas.drawCircle(x, y, radius, mPaint);
        
        // Draw Button Label (A, B, X, Y, Z, L)
        if (active) {
            mPaint.setColor(Color.BLACK);
            // Use white text for labels on dark backgrounds
            if (circleColor == Color.RED || circleColor == Color.BLACK) {
                mPaint.setColor(Color.WHITE);
            }
        } else {
            mPaint.setColor(Color.argb(100, 200, 200, 200));
        }
        
        canvas.drawText(label, x, y + 15, mPaint);
        
        // Draw Descriptive Text
        mPaint.setTextAlign(Paint.Align.LEFT);
        if (active && text != null && !text.isEmpty() && !text.equals("None")) {
            mPaint.setColor(Color.WHITE);
            mPaint.setFakeBoldText(isPulse);
            canvas.drawText(text, x + 50, y + 15, mPaint);
            mPaint.setFakeBoldText(false);
        } else if (!active) {
             mPaint.setColor(Color.argb(60, 150, 150, 150));
        }
    }

    private String getItemName(int id) {
        switch(id) {
            case 0x43: return "Bow";
            case 0x44: return "Hookshot";
            case 0x47: return "Double Hookshot";
            case 0x40: return "Boomerang";
            case 0x42: return "Ball and Chain";
            case 0x46: return "Copy Rod";
            case 0x45: return "Iron Boots";
            case 0x41: return "Spinner";
            case 0x48: return "Lantern";
            case 0x4A: return "Fishing Rod";
            case 0x4B: return "Slingshot";
            case 0x3E: return "Hawk Eye";
            case 0x70: return "Bombs";
            case 0x71: return "Water Bombs";
            case 0x72: return "Bomblings";
            case 0x84: return "Horse Flute";
            case 0x60: return "Empty Bottle";
            case 0x61: return "Red Potion";
            case 0x62: return "Green Potion";
            case 0x63: return "Blue Potion";
            case 0x64: return "Milk";
            case 0x65: return "Half Milk";
            case 0x66: return "Oil";
            case 0x67: return "Water";
            case 0x6A: return "Ugly Soup";
            case 0x6B: return "Hot Spring Water";
            case 0x6C: return "Fairy";
            case 0x73: return "Fairy Drop";
            case 0x77: return "Rare Chu Jelly";
            case 0x78: return "Red Chu Jelly";
            case 0x79: return "Blue Chu Jelly";
            case 0x7A: return "Green Chu Jelly";
            case 0x7B: return "Yellow Chu Jelly";
            case 0x7C: return "Purple Chu Jelly";
            case 0x9C: return "Yellow Chu Jelly";
            case 0x9F: return "Black Chu Jelly";
            case 0xFF: return "None";
            default: return "Item " + String.format("0x%02X", id);
        }
    }

    private void drawMiniMap(Canvas canvas, float x, float y) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.argb(120, 15, 15, 50));
        canvas.drawRect(x, y, x + MAP_SIZE, y + MAP_SIZE, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(3);
        mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + MAP_SIZE, y + MAP_SIZE, mPaint);

        if (mState.mapMaxX <= mState.mapMinX || mState.mapMaxZ <= mState.mapMinZ) return;
        
        float stageW = mState.mapMaxX - mState.mapMinX;
        float stageH = mState.mapMaxZ - mState.mapMinZ;
        float paddedSize = MAP_SIZE * 0.92f;
        float baseScale = paddedSize / Math.max(stageW, stageH);
        
        float mapScale;
        float stageCenterX;
        float stageCenterZ;

        if (mMapZoomLevel > 0) {
            // Zoomed levels (2x, 4x, 8x) - center on Link
            mapScale = baseScale * (float) Math.pow(2, mMapZoomLevel);
            stageCenterX = mState.mapX;
            stageCenterZ = mState.mapY; // mapY is actually Z in 3D
        } else {
            // Default level (0) - center on room
            mapScale = baseScale;
            stageCenterX = (mState.mapMaxX + mState.mapMinX) / 2f;
            stageCenterZ = (mState.mapMaxZ + mState.mapMinZ) / 2f;
        }

        float centerX = x + MAP_SIZE / 2;
        float centerY = y + MAP_SIZE / 2;

        canvas.save();
        canvas.clipRect(x, y, x + MAP_SIZE, y + MAP_SIZE);
        
        if (mState.mapLines != null && mState.mapLines.length > 1) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(Color.CYAN);
            mPaint.setStrokeWidth(2);
            mDrawPath.reset();
            boolean newPoly = true;
            for (int i = 0; i < mState.mapLines.length; i += 2) {
                float lx = mState.mapLines[i];
                if (Float.isNaN(lx)) { newPoly = true; continue; }
                float lz = mState.mapLines[i+1];
                float screenX = centerX + (lx - stageCenterX) * mapScale;
                float screenY = centerY + (lz - stageCenterZ) * mapScale;
                if (newPoly) { mDrawPath.moveTo(screenX, screenY); newPoly = false; }
                else mDrawPath.lineTo(screenX, screenY);
            }
            canvas.drawPath(mDrawPath, mPaint);
        }
        
        if (mState.mapDoors != null) {
            for (int i = 0; i < mState.mapDoors.length; i += 4) {
                float dx = mState.mapDoors[i];
                float dz = mState.mapDoors[i+1];
                float angle = mState.mapDoors[i+2];
                int type = (int)mState.mapDoors[i+3];
                float doorScreenX = centerX + (dx - stageCenterX) * mapScale;
                float doorScreenY = centerY + (dz - stageCenterZ) * mapScale;
                drawDoorIcon(canvas, doorScreenX, doorScreenY, angle, type);
            }
        }
        
        if (mState.mapIcons != null) {
            for (int i = 0; i < mState.mapIcons.length; i += 4) {
                int type = (int)mState.mapIcons[i];
                float ix = mState.mapIcons[i+1];
                float iz = mState.mapIcons[i+2];
                int status = (int)mState.mapIcons[i+3];
                float iconScreenX = centerX + (ix - stageCenterX) * mapScale;
                float iconScreenY = centerY + (iz - stageCenterZ) * mapScale;
                drawMapIcon(canvas, iconScreenX, iconScreenY, type, status);
            }
        }

        // Player Arrow (Oriented north as per request)
        float playerScreenX = centerX + (mState.mapX - stageCenterX) * mapScale;
        float playerScreenY = centerY + (mState.mapY - stageCenterZ) * mapScale;
        
        canvas.save();
        canvas.translate(playerScreenX, playerScreenY);
        canvas.rotate(180 - mState.mapAngle); 
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.YELLOW);
        mDrawPath.reset();
        mDrawPath.moveTo(0, -22);
        mDrawPath.lineTo(-13, 13);
        mDrawPath.lineTo(13, 13);
        mDrawPath.close();
        canvas.drawPath(mDrawPath, mPaint);
        canvas.restore();
        
        canvas.restore();
        
        // Stage Info and Zoom Indicator
        mPaint.setTextSize(32);
        mPaint.setColor(Color.WHITE);
        mPaint.setTextAlign(Paint.Align.CENTER);
        String zoomText = (mMapZoomLevel == 0) ? "" : " (" + (1 << mMapZoomLevel) + "x)";
        canvas.drawText(mState.stageName + zoomText, x + MAP_SIZE/2, y + MAP_SIZE + 40, mPaint);
    }

    private void drawDoorIcon(Canvas canvas, float x, float y, float angle, int type) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.rgb(200, 200, 200));
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(180 - angle);
        // Draw a simple door rectangle
        canvas.drawRect(-12, -4, 12, 4, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1);
        mPaint.setColor(Color.BLACK);
        canvas.drawRect(-12, -4, 12, 4, mPaint);
        canvas.restore();
    }

    private void drawMapIcon(Canvas canvas, float x, float y, int type, int status) {
        mPaint.setStyle(Paint.Style.FILL);
        switch (type) {
            case 0: // Chest
            case 10: // Field Chest
                mPaint.setColor(Color.YELLOW);
                canvas.drawRect(x - 8, y - 8, x + 8, y + 8, mPaint);
                break;
            case 4: // Light Drop / Objective
                mPaint.setColor(Color.rgb(100, 150, 255));
                canvas.drawCircle(x, y, 6, mPaint);
                break;
            case 3: // Boss
                mPaint.setColor(Color.RED);
                canvas.drawCircle(x, y, 14, mPaint);
                break;
            case 2: // Heart Piece / Small Key
                mPaint.setColor(Color.rgb(255, 100, 100));
                canvas.drawCircle(x, y, 8, mPaint);
                break;
            default:
                mPaint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, 5, mPaint);
                break;
        }
    }

    private void drawStatusInfo(Canvas canvas, float x, float y) {
        mPaint.setTextSize(38);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setColor(mState.transform == 1 ? Color.CYAN : Color.WHITE);
        canvas.drawText("FORM: " + (mState.transform == 1 ? "WOLF" : "HUMAN"), x, y, mPaint);
    }
}
