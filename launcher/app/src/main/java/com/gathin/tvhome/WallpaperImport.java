package com.gathin.tvhome;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** A picture picked on the TV (wallpaper_from_gallery), copied into the wallpapers/ collection at screen size. */
final class WallpaperImport {
    static final int MAX_WIDTH = 1920;
    static final int MAX_HEIGHT = 1080;

    private WallpaperImport() {}

    /** The largest power-of-two step that still decodes at least MAX_WIDTH x MAX_HEIGHT, so cropping keeps detail. */
    static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / (sample * 2) >= MAX_WIDTH && height / (sample * 2) >= MAX_HEIGHT) sample *= 2;
        return sample;
    }

    /** The factor that shrinks a decoded picture until it just covers the screen; 1 when it is no larger. */
    static float scale(int width, int height) {
        return Math.min(1f, Math.max((float) MAX_WIDTH / width, (float) MAX_HEIGHT / height));
    }

    /** Decodes uri sampled down, scales it to cover the screen and writes it to out as JPEG. False on any failure. */
    static boolean save(ContentResolver resolver, Uri uri, File out) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
            Bitmap bitmap;
            try (InputStream in = resolver.openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) return false;
            float scale = scale(bitmap.getWidth(), bitmap.getHeight());
            if (scale < 1f) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale),
                        Math.round(bitmap.getHeight() * scale), true);
                if (scaled != bitmap) bitmap.recycle();
                bitmap = scaled;
            }
            File dir = out.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return false;
            boolean written;
            try (FileOutputStream stream = new FileOutputStream(out)) {
                written = bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream);
            } finally {
                bitmap.recycle();
            }
            if (!written) out.delete();
            return written;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            out.delete();
            return false;
        }
    }
}
