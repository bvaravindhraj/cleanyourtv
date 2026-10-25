package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WallpaperImportTest {

    @Test
    public void aScreenSizedPictureIsDecodedWhole() {
        assertEquals(1, WallpaperImport.sampleSize(1920, 1080));
        assertEquals(1, WallpaperImport.sampleSize(1280, 720));
    }

    @Test
    public void aCameraPhotoIsSampledDownButStillCoversTheScreen() {
        // 4000x3000 / 2 = 2000x1500 still covers 1920x1080; / 4 = 1000x750 would not.
        assertEquals(2, WallpaperImport.sampleSize(4000, 3000));
        // A 4K frame halves exactly to the screen.
        assertEquals(2, WallpaperImport.sampleSize(3840, 2160));
        assertEquals(4, WallpaperImport.sampleSize(8000, 4500));
    }

    @Test
    public void aTallPictureIsSampledByItsNarrowSide() {
        // Portrait 3000x4000: halving gives 1500 wide, under 1920, so it stays whole.
        assertEquals(1, WallpaperImport.sampleSize(3000, 4000));
    }

    @Test
    public void scaleShrinksUntilThePictureJustCoversTheScreen() {
        assertEquals(1f, WallpaperImport.scale(1920, 1080), 0f);
        assertEquals(0.5f, WallpaperImport.scale(3840, 2160), 1e-6f);
        // 2000x1500 becomes 1920x1440: the width reaches the screen first, the height keeps its overflow to crop.
        assertEquals(0.96f, WallpaperImport.scale(2000, 1500), 1e-6f);
    }

    @Test
    public void aSmallPictureIsNeverEnlarged() {
        assertEquals(1f, WallpaperImport.scale(1280, 720), 0f);
        assertEquals(1f, WallpaperImport.scale(640, 480), 0f);
    }
}
