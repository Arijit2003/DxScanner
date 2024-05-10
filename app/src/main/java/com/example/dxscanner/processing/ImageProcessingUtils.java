package com.example.dxscanner.processing;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;


public class ImageProcessingUtils {
    public static Bitmap getProcessedBitmapWithWhiteBackground(Bitmap bitmap){
        bitmap=adjustContrastBrightness(bitmap,80.0f,90.0f);
        bitmap=convertToGrayscale(bitmap);
        bitmap=applyThreshold(bitmap,70);
        bitmap=combineWithWhiteBackground(bitmap);
        return bitmap;
    }
    public static Bitmap convertToGrayscale(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = bitmap.getPixel(x, y);
                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);
                int gray = (int) (red * 0.3 + green * 0.59 + blue * 0.11); // Convert to grayscale
                int newPixel = Color.argb(Color.alpha(pixel), gray, gray, gray);
                grayscaleBitmap.setPixel(x, y, newPixel);
            }
        }
        return grayscaleBitmap;
    }



    public static Bitmap applyThreshold(Bitmap grayscaleBitmap, int threshold) {
        int width = grayscaleBitmap.getWidth();
        int height = grayscaleBitmap.getHeight();
        Bitmap thresholdBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = grayscaleBitmap.getPixel(x, y);
                int gray = Color.red(pixel); // Grayscale image, so red, green, and blue channels are the same
                if (gray > threshold) {
                    thresholdBitmap.setPixel(x, y, Color.WHITE); // Foreground
                } else {
                    thresholdBitmap.setPixel(x, y, pixel); // Background
                }
            }
        }
        return thresholdBitmap;
    }
    public static Bitmap combineWithWhiteBackground(Bitmap thresholdedBitmap) {
        int width = thresholdedBitmap.getWidth();
        int height = thresholdedBitmap.getHeight();

        // Create a new bitmap with a white background
        Bitmap combinedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(combinedBitmap);
        canvas.drawColor(Color.WHITE);

        // Overlay the thresholded bitmap onto the white background
        Paint paint = new Paint();
        canvas.drawBitmap(thresholdedBitmap, 0, 0, paint);
        return combinedBitmap;
    }
    public static Bitmap adjustContrastBrightness(Bitmap bitmap, float contrast, float brightness) {
        Bitmap adjustedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        Canvas canvas = new Canvas(adjustedBitmap);
        Paint paint = new Paint();

        ColorMatrix cm = new ColorMatrix(new float[] {
                contrast, 0, 0, 0, brightness,
                0, contrast, 0, 0, brightness,
                0, 0, contrast, 0, brightness,
                0, 0, 0, 1, 0
        });

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(filter);
        canvas.drawBitmap(bitmap, 0, 0, paint);

        return adjustedBitmap;
    }
}

