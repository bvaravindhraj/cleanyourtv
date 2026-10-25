package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService):
 * a few glass rows under the Settings pill, over Home or whatever app is playing. Translucent and in a
 * task of its own, so the app behind keeps running and Back returns to it. Pressing Settings again
 * closes the panel.
 */
public final class QuickSettingsActivity extends Activity {
    private Config config;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        config = Config.load(this);
        if (config.quickPanelGlass) {
            View panel = findViewById(R.id.panel);
            // The pills' glass (pill_bg): white at 18% fading to 8%, a 22% white edge. No elevation, whose
            // shadow would show through as a dark halo.
            GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x2EFFFFFF, 0x14FFFFFF});
            glass.setCornerRadius(dp(28));
            glass.setStroke(Math.max(1, Math.round(dp(1))), 0x38FFFFFF);
            panel.setBackground(glass);
            panel.setElevation(0);
            int pad = Math.round(dp(8));
            panel.setPadding(pad, pad, pad, pad);
        }
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);
        addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, SystemSettings.intent(this, Settings.ACTION_SETTINGS));
        if (config.quickPanelDisplay) addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, SystemSettings.intent(this, Settings.ACTION_DISPLAY_SETTINGS));
        addRow(rows, R.drawable.ic_wifi, R.string.quick_network, 0, SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
        addRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                SystemSettings.intent(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        rows.getChildAt(0).requestFocus();
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hint = row.findViewById(R.id.status);
        if (status != 0) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        if (config.quickPanelGlass || config.quickPanelAccent != 0) styleRow(row);
        rows.addView(row);
    }

    /** Pill-shaped rows; with quick_panel_accent the focused one fills with that colour and keeps white text. */
    private void styleRow(View row) {
        int accent = config.quickPanelAccent;
        float radius = dp(config.quickPanelGlass ? 21 : 13);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(accent != 0 ? (accent & 0x00FFFFFF) | 0xE6000000 : 0xF5FFFFFF);
        focused.setCornerRadius(radius);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[0], new GradientDrawable());
        row.setBackground(bg);
        if (config.quickPanelGlass) row.getLayoutParams().height = Math.round(dp(46));
        if (accent == 0) return;
        ColorStateList white = ColorStateList.valueOf(0xFFFFFFFF);
        ((TextView) row.findViewById(R.id.title)).setTextColor(white);
        ((ImageView) row.findViewById(R.id.icon)).setImageTintList(white);
        ((TextView) row.findViewById(R.id.status)).setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xD9FFFFFF, 0x99FFFFFF}));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
