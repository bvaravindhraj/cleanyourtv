package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArcRescueTest {

    @Test
    public void onlyWhenHdmiControlIsOnAndSystemAudioWasSwitchedOff() {
        assertTrue(ArcRescue.needsRescue("1", "0"));
        assertFalse(ArcRescue.needsRescue("1", "1"));
        assertFalse(ArcRescue.needsRescue("0", "0"));    // HDMI control off by choice: leave it alone
        assertFalse(ArcRescue.needsRescue(null, null));  // a TV without these settings
    }
}
