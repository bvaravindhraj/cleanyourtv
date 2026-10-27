package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

/**
 * Puts a soundbar back on HDMI ARC after a cold boot (config.xml arc_rescue). Some soundbars are slow to answer the
 * TV's CEC questions while both are starting up; the TV then ends ARC, switches its system audio setting off and plays
 * through its own speakers until someone turns HDMI control off and on. Seen on a MiTV-AXSO0: the soundbar's
 * Initiate ARC was accepted, eight Give OSD Name / Give Device Vendor Id went unanswered, then Request ARC Termination
 * and hdmi_system_audio_status_enabled 1 to 0. Toggling hdmi_control_enabled re-runs discovery, and it answered at once.
 *
 * Once per boot, {@link #CHECK_AT_UPTIME_MS} after it, if HDMI control is on and system audio has been switched off,
 * this switches system audio back on and toggles HDMI control. Needs WRITE_SECURE_SETTINGS (build-and-install grants
 * it with the remote helper); without it nothing happens. Note that it also undoes a system audio switch-off made by
 * hand in TV Settings, at the next boot.
 */
final class ArcRescue {

    static final long CHECK_AT_UPTIME_MS = 40_000L;
    private static final long OFF_MS = 3_000L;
    private static final String CONTROL = "hdmi_control_enabled";
    private static final String SYSTEM_AUDIO = "hdmi_system_audio_status_enabled";

    /** Per process, and the process starts with the boot: one check per boot (a later Home restart may check again). */
    private static boolean scheduled;

    private ArcRescue() {
    }

    static void schedule(Context context) {
        if (scheduled) return;
        scheduled = true;
        Context app = context.getApplicationContext();
        Handler handler = new Handler(Looper.getMainLooper());
        long delay = Math.max(0, CHECK_AT_UPTIME_MS - SystemClock.elapsedRealtime());
        handler.postDelayed(() -> check(app.getContentResolver(), handler), delay);
    }

    /** Whether the TV ended ARC by itself: HDMI control on but system audio switched off. */
    static boolean needsRescue(String control, String systemAudio) {
        return "1".equals(control) && "0".equals(systemAudio);
    }

    private static void check(ContentResolver cr, Handler handler) {
        if (!needsRescue(Settings.Global.getString(cr, CONTROL), Settings.Global.getString(cr, SYSTEM_AUDIO))) return;
        try {
            Settings.Global.putString(cr, SYSTEM_AUDIO, "1");
            Settings.Global.putString(cr, CONTROL, "0");
        } catch (SecurityException e) {
            return;   // no WRITE_SECURE_SETTINGS
        }
        Log.i("TvHomeArc", "soundbar dropped off ARC at boot: toggling HDMI control");
        handler.postDelayed(() -> {
            try {
                Settings.Global.putString(cr, CONTROL, "1");
            } catch (SecurityException ignored) {
                // granted a moment ago; nothing more to do
            }
        }, OFF_MS);
    }
}
