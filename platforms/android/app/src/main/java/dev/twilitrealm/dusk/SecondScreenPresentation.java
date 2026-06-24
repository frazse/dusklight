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
        mHudView = new HudView(getContext());
        setContentView(mHudView);
    }

    public void updateHud(GameState state) {
        if (mHudView != null) {
            mHudView.update(state);
        }
    }
}
