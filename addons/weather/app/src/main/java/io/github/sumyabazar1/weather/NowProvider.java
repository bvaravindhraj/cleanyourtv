package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * The weather now, for a home screen to show: one row with the place, the temperature, the WMO code, day or night,
 * the wind and the words for the sky ({@code content://io.github.sumyabazar1.weather.now}). It holds the last
 * reading the app made; a query finds it older than {@link Forecast#REFRESH_MS} and a fresh one is fetched in the
 * background, then observers of the URI are told. So the network is read only when a home screen asks, never on a
 * timer, and nothing runs while nobody looks. No place chosen yet gives an empty cursor.
 */
public final class NowProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://io.github.sumyabazar1.weather.now");
    private static final String PREFS = "now";
    private static final String[] COLUMNS = {"place", "temp_c", "code", "is_day", "wind_kmh", "sky", "updated_ms"};

    private volatile boolean fetching;
    private volatile long lastTryMs;

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Keeps {@code r} as the reading the provider hands out; the app page calls it after each refresh too. */
    static void save(Context context, Forecast.Report r) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("temp", Double.doubleToRawLongBits(r.tempC))
                .putInt("code", r.code)
                .putBoolean("day", r.day)
                .putInt("wind", r.windKmh)
                .putLong("at", System.currentTimeMillis())
                .apply();
        context.getContentResolver().notifyChange(URI, null);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context context = getContext();
        MatrixCursor cursor = new MatrixCursor(COLUMNS, 1);
        SharedPreferences place = context.getSharedPreferences("place", Context.MODE_PRIVATE);
        if (!place.contains("name")) return cursor;
        SharedPreferences now = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long at = now.getLong("at", 0);
        long time = System.currentTimeMillis();
        if (time - at > Forecast.REFRESH_MS && time - lastTryMs > Forecast.RETRY_MS && !fetching) {
            fetch(context, place);
        }
        if (at == 0) return cursor;
        int code = now.getInt("code", 3);
        boolean day = now.getBoolean("day", true);
        cursor.addRow(new Object[]{place.getString("name", ""), Double.longBitsToDouble(now.getLong("temp", 0)),
                code, day ? 1 : 0, now.getInt("wind", 0), context.getString(Forecast.sky(code, day)), at});
        cursor.setNotificationUri(context.getContentResolver(), URI);
        return cursor;
    }

    private void fetch(Context context, SharedPreferences place) {
        fetching = true;
        lastTryMs = System.currentTimeMillis();
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        new Thread(() -> {
            try {
                save(context, Forecast.fetch(lat, lon, Forecast.MIN_DAYS));
            } catch (Exception e) {
                // offline: the last reading stays, and the next query after RETRY_MS tries again
            } finally {
                fetching = false;
            }
        }, "weather-now").start();
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
