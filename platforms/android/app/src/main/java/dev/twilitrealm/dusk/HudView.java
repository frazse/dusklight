package dev.twilitrealm.dusk;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;

public class HudView extends View {
    private static final String TAG = "HudView";
    private GameState mState;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mHeartPath = new Path();
    private final Path mDrawPath = new Path();
    private boolean mSizeLogged = false;

    public HudView(Context context) {
        super(context);
    }

    public void update(GameState state) {
        mState = state;
        invalidate();
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

        drawHearts(canvas, 80, 80);
        drawMagicBar(canvas, 80, 230);
        drawOilBar(canvas, 80, 275);
        drawOxygenBar(canvas, 480, 230);
        
        drawRupeeCounter(canvas, 100, 750);
        
        drawItems(canvas, 950, 80);
        
        // Minimap
        drawMiniMap(canvas, 340, 320);
        
        // Context Labels (A / B / Z / X / Y Buttons)
        drawContextButtons(canvas, 640, 960);
        
        drawStatusInfo(canvas, 640, 1040);

        canvas.restore();
    }

    private void drawHearts(Canvas canvas, float startX, float startY) {
        int maxHearts = mState.maxHealth / 4;
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
            if (fill >= 1) canvas.drawRect(x, y, x + mid, y + mid, mPaint);
            if (fill >= 2) canvas.drawRect(x, y + mid, x + mid, y + size, mPaint);
            if (fill >= 3) canvas.drawRect(x + mid, y, x + size, y + mid, mPaint);
            if (fill >= 4) canvas.drawRect(x + mid, y + mid, x + size, y + size, mPaint);
            canvas.restore();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(Color.WHITE);
            canvas.drawPath(mHeartPath, mPaint);
            if (fill > 0 && fill < 4) {
                mPaint.setColor(Color.argb(100, 255, 255, 255));
                mPaint.setStrokeWidth(1);
                canvas.drawLine(x + mid, y + size * 0.2f, x + mid, y + size * 0.9f, mPaint);
                canvas.drawLine(x + size * 0.1f, y + mid, x + size * 0.9f, y + mid, mPaint);
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

    private void drawItems(Canvas canvas, float x, float y) {
        mPaint.setTextSize(38);
        mPaint.setTextAlign(Paint.Align.LEFT);
        mPaint.setColor(Color.WHITE);
        canvas.drawText("Arrows: " + mState.arrows, x, y, mPaint);
        canvas.drawText("Bombs:  " + mState.bombs, x, y + 45, mPaint);
        canvas.drawText("Keys:   " + mState.keys, x, y + 90, mPaint);
    }

    private void drawContextButtons(Canvas canvas, float centerX, float centerY) {
        mPaint.setTextSize(42);
        
        // Midna (Z) Button - Top Left
        if (mState.buttonZText != null && !mState.buttonZText.isEmpty()) {
            float zX = centerX - 250;
            float zY = centerY - 120;
            drawActionButton(canvas, zX, zY, "Z", Color.argb(255, 100, 200, 255), mState.buttonZText);
        }

        // A Button - Bottom Left
        if (mState.buttonAText != null && !mState.buttonAText.isEmpty()) {
            drawActionButton(canvas, centerX - 250, centerY, "A", Color.rgb(0, 200, 50), mState.buttonAText);
        }
        
        // B Button - Bottom Right
        if (mState.buttonBText != null && !mState.buttonBText.isEmpty()) {
            drawActionButton(canvas, centerX + 150, centerY, "B", Color.RED, mState.buttonBText);
        }

        // Y Button - Inner Top Right
        float xY = centerY - 120;
        String yText = mState.buttonYText;
        if (yText == null || yText.isEmpty()) {
            yText = getItemName(mState.itemYResId);
            if (mState.itemYCount > 0) yText += " (" + mState.itemYCount + ")";
        }
        drawActionButton(canvas, centerX + 150, xY, "Y", Color.rgb(255, 255, 0), yText);

        // X Button - Outer Top Right
        String xText = mState.buttonXText;
        if (xText == null || xText.isEmpty()) {
            xText = getItemName(mState.itemXResId);
            if (mState.itemXCount > 0) xText += " (" + mState.itemXCount + ")";
        }
        drawActionButton(canvas, centerX + 450, xY, "X", Color.rgb(255, 165, 0), xText);
    }

    private void drawActionButton(Canvas canvas, float x, float y, String label, int color, String text) {
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(color);
        canvas.drawCircle(x, y, 38, mPaint);
        mPaint.setColor(Color.BLACK);
        if (color == Color.RED || color == Color.BLACK) mPaint.setColor(Color.WHITE);
        canvas.drawText(label, x, y + 15, mPaint);
        
        mPaint.setColor(Color.WHITE);
        mPaint.setTextAlign(Paint.Align.LEFT);
        if (text != null) {
            canvas.drawText(text, x + 50, y + 15, mPaint);
        }
    }

    private String getItemName(int id) {
        switch(id) {
            case 0x46: return "Bow";
            case 0x47: return "Hookshot";
            case 0x41: return "Boomerang";
            case 0x49: return "Iron Ball";
            case 0x4B: return "Copy Rod";
            case 0x48: return "Heavy Boots";
            case 0x42: return "Spinner";
            case 0x4C: return "Lantern";
            case 0x5E: return "Bomb";
            case 0x5F: return "Water Bomb";
            case 0x60: return "Bombling";
            case 0x32: return "Slingshot";
            case 0x64: return "Bottle";
            case 0x86: return "Fishing Rod";
            case 0x93: return "Horse Flute";
            case 0xFF: return "None";
            default: return "Item " + String.format("0x%02X", id);
        }
    }

    private void drawMiniMap(Canvas canvas, float x, float y) {
        float mapSize = 600; 
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.argb(120, 15, 15, 50));
        canvas.drawRect(x, y, x + mapSize, y + mapSize, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(3);
        mPaint.setColor(Color.WHITE);
        canvas.drawRect(x, y, x + mapSize, y + mapSize, mPaint);

        if (mState.mapMaxX <= mState.mapMinX || mState.mapMaxZ <= mState.mapMinZ) return;
        float stageW = mState.mapMaxX - mState.mapMinX;
        float stageH = mState.mapMaxZ - mState.mapMinZ;
        float paddedSize = mapSize * 0.92f;
        float mapScale = paddedSize / Math.max(stageW, stageH);
        float centerX = x + mapSize / 2;
        float centerY = y + mapSize / 2;
        float stageCenterX = (mState.mapMaxX + mState.mapMinX) / 2f;
        float stageCenterZ = (mState.mapMaxZ + mState.mapMinZ) / 2f;

        canvas.save();
        canvas.clipRect(x, y, x + mapSize, y + mapSize);
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
        float playerX = centerX + (mState.mapX - stageCenterX) * mapScale;
        float playerY = centerY + (mState.mapY - stageCenterZ) * mapScale;
        canvas.save();
        canvas.translate(playerX, playerY);
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
        
        mPaint.setTextSize(32);
        mPaint.setColor(Color.WHITE);
        mPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(mState.stageName + " - Room " + mState.roomNo, x + mapSize/2, y - 18, mPaint);
    }

    private void drawMapIcon(Canvas canvas, float x, float y, int type, int status) {
        mPaint.setStyle(Paint.Style.FILL);
        switch (type) {
            case 12: // Chest
                mPaint.setColor(status == 0 ? Color.YELLOW : Color.GRAY);
                canvas.drawRect(x - 7, y - 7, x + 7, y + 7, mPaint);
                break;
            case 4: // Boss
                mPaint.setColor(Color.RED);
                canvas.drawCircle(x, y, 12, mPaint);
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
