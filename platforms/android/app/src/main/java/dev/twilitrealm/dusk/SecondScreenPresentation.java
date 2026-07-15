package dev.twilitrealm.dusk;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.view.Gravity;

public class SecondScreenPresentation extends Presentation {
    private HudView mHudView;

    public SecondScreenPresentation(Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Prevent physical controller 'B' button from dismissing the second screen
        setCancelable(false);
        
        // Allow touches while preventing focus stealing from the main game screen
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        mHudView = new HudView(getContext());
        setContentView(mHudView);
    }

    public void updateHud(GameState state) {
        if (mHudView != null) {
            mHudView.update(state);
        }
    }

    public void onItemIconLoaded(int id, int width, int height, int[] pixels) {
        if (mHudView != null) {
            mHudView.onItemIconLoaded(id, width, height, pixels);
        }
    }
}
