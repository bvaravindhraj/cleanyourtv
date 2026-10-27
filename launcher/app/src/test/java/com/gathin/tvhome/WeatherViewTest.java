package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherViewTest {

    @Test
    public void clearIsTheSunByDayAndStarsAtNight() {
        assertEquals(WeatherView.SUN, WeatherView.effects(0, true, 5));
        assertEquals(WeatherView.STARS, WeatherView.effects(1, false, 5));
    }

    @Test
    public void partlyCloudyKeepsTheSunBehindClouds() {
        assertEquals(WeatherView.CLOUDS | WeatherView.SUN, WeatherView.effects(2, true, 5));
        assertEquals(WeatherView.CLOUDS, WeatherView.effects(2, false, 5));
    }

    @Test
    public void overcastAndFogAreGreyClouds() {
        assertEquals(WeatherView.CLOUDS | WeatherView.OVERCAST, WeatherView.effects(3, true, 5));
        assertEquals(WeatherView.CLOUDS | WeatherView.OVERCAST, WeatherView.effects(45, true, 5));
    }

    @Test
    public void drizzleShowersAndRainAllRain() {
        for (int code : new int[]{51, 61, 65, 80, 82}) {
            assertEquals(WeatherView.CLOUDS | WeatherView.OVERCAST | WeatherView.RAIN, WeatherView.effects(code, true, 5));
        }
    }

    @Test
    public void thunderstormsFlashAndSnowFalls() {
        assertTrue((WeatherView.effects(95, true, 5) & WeatherView.STORM) != 0);
        assertTrue((WeatherView.effects(73, true, 5) & WeatherView.SNOW) != 0);
    }

    @Test
    public void windFromTheThresholdOn() {
        assertEquals(WeatherView.SUN, WeatherView.effects(0, true, WeatherView.WINDY_KMH - 1));
        assertEquals(WeatherView.SUN | WeatherView.WIND, WeatherView.effects(0, true, WeatherView.WINDY_KMH));
    }
}
