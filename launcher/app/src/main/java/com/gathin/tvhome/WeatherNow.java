package com.gathin.tvhome;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.Nullable;

/**
 * The weather now, read from the Weather add-on (addons/weather, NowProvider) for config.xml weather. The launcher
 * itself never touches the network: the add-on fetches when its last reading is old and tells us through the URI's
 * observers. No add-on, no place picked in it yet, or no reading so far all give null, and Home shows no weather.
 */
final class WeatherNow {

    static final Uri URI = Uri.parse("content://io.github.sumyabazar1.weather.now");

    final String place;
    final int tempC;
    final int code;
    final boolean day;
    final int windKmh;
    /** The add-on's words for the sky ("Light rain"), in the TV's language where the add-on has it. */
    final String sky;

    private WeatherNow(String place, int tempC, int code, boolean day, int windKmh, String sky) {
        this.place = place;
        this.tempC = tempC;
        this.code = code;
        this.day = day;
        this.windKmh = windKmh;
        this.sky = sky;
    }

    /** One query to the add-on: a binder call, so not on the main thread. */
    @Nullable
    static WeatherNow read(ContentResolver resolver) {
        try (Cursor c = resolver.query(URI, null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            return new WeatherNow(
                    Cards.cleanLabel(c.getString(c.getColumnIndexOrThrow("place")), ""),
                    (int) Math.round(c.getDouble(c.getColumnIndexOrThrow("temp_c"))),
                    c.getInt(c.getColumnIndexOrThrow("code")),
                    c.getInt(c.getColumnIndexOrThrow("is_day")) == 1,
                    c.getInt(c.getColumnIndexOrThrow("wind_kmh")),
                    Cards.cleanLabel(c.getString(c.getColumnIndexOrThrow("sky")), ""));
        } catch (RuntimeException e) {
            return null;   // no add-on (SecurityException / IllegalArgumentException), or a column it lacks
        }
    }

    /** The line under the date: "29° · Light rain · Chennai". */
    String line() {
        StringBuilder b = new StringBuilder().append(tempC).append('°');
        if (!sky.isEmpty()) b.append(" · ").append(sky);
        if (!place.isEmpty()) b.append(" · ").append(place);
        return b.toString();
    }

    int effects() {
        return WeatherView.effects(code, day, windKmh);
    }
}
