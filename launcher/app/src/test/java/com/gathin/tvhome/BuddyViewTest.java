package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;

import org.junit.Test;

public class BuddyViewTest {

    @Test
    public void plainOnAnOrdinaryAfternoon() {
        assertEquals(0, BuddyView.outfit(LocalDate.of(2026, 3, 10), 15, ""));
    }

    @Test
    public void scarfFromSevenInTheEveningToSixInTheMorning() {
        LocalDate day = LocalDate.of(2026, 3, 10);
        assertEquals(0, BuddyView.outfit(day, 18, ""));
        assertEquals(BuddyView.SCARF, BuddyView.outfit(day, 19, ""));
        assertEquals(BuddyView.SCARF, BuddyView.outfit(day, 5, ""));
        assertEquals(0, BuddyView.outfit(day, 6, ""));
    }

    @Test
    public void diyaFromDhanterasToBhaiDooj() {
        assertEquals(0, BuddyView.outfit(LocalDate.of(2026, 11, 5), 12, ""));
        assertEquals(BuddyView.DIYA, BuddyView.outfit(LocalDate.of(2026, 11, 6), 12, ""));
        assertEquals(BuddyView.DIYA, BuddyView.outfit(LocalDate.of(2026, 11, 10), 12, ""));
        assertEquals(0, BuddyView.outfit(LocalDate.of(2026, 11, 11), 12, ""));
    }

    @Test
    public void aDiwaliThemeAlsoBringsTheDiya() {
        assertEquals(BuddyView.DIYA | BuddyView.SCARF, BuddyView.outfit(LocalDate.of(2026, 3, 10), 21, "diwali"));
    }

    @Test
    public void santaHatAndKiteBySeason() {
        assertEquals(BuddyView.SANTA_HAT, BuddyView.outfit(LocalDate.of(2026, 12, 25), 12, ""));
        assertEquals(0, BuddyView.outfit(LocalDate.of(2026, 12, 19), 12, ""));
        assertEquals(BuddyView.KITE, BuddyView.outfit(LocalDate.of(2027, 1, 14), 12, ""));
        assertEquals(0, BuddyView.outfit(LocalDate.of(2027, 1, 16), 12, ""));
    }
}
