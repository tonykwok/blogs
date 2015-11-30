package com.google.zxing;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DefaultGridSampler;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.qrcode.decoder.Version;
import com.google.zxing.qrcode.detector.Detector;

import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;

/**
 * Created by 28851063 on 11/19/15.
 */
public class ColoredMatrixCodeDecoder {

    public static BitMatrix detect(BitMatrix bitMatrix, float[] out) throws NotFoundException {
        DetectorResult result;
        try {
            result = new Detector(bitMatrix).detect(null, out);
        } catch (ReaderException re) {
            return null;
        }

        return result.getBits();
    }

    private static DebugViewer sDebugViewer = null;
    public static void setDebugViewer(DebugViewer viewer) {
        sDebugViewer = viewer;
    }

    public interface DebugViewer {
        public void draw(BitMatrix matrix, String file, float[] point) throws NotFoundException;
        public void draw(int[] rgb, int w, int h, String file, float[] point) throws NotFoundException;
        public void log(String tag, String message);
    }

    public static int[] buildHistogram(int[] result) {
        int max = 0;
        for (int i : result) {
            if (i > max) {
                max = i;
            }
        }
        int[] histogram = new int[max + 1];
        for (int i : result) {
            histogram[i]++;
        }

        return histogram;
    }

    private static String sReferenceFile = null;
    private static int[] sReferenceHistogram = null;
    private static int[] sReferenceArray = null;
    private static int sColorCount = 0;
    private static int sCodeDimension = 0;

    // pre-allocated color pallet arrays
    private final static float[][] pallet = new float[][] {
            new float[] {0, 0, 0}, // 000
            new float[] {0, 0, 0}, // 001
            new float[] {0, 0, 0}, // 010
            new float[] {0, 0, 0}, // 011
            new float[] {0, 0, 0}, // 100
            new float[] {0, 0, 0}, // 101
            new float[] {0, 0, 0}, // 110
            new float[] {0, 0, 0}, // 111
    };

    public static void setReferenceFile(String referenceFile) {
        if (referenceFile == null || referenceFile.length() == 0) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Illegal argument: " + sReferenceFile);
            }
            return;
        }

        File file = new File(referenceFile);
        if (file == null || !file.exists()) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Reference file does not exist! " + referenceFile);
            }
            return;
        }

        byte[] b = null;
        try {
            b = readAll(new FileInputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        String str = new String(b, Charset.forName("utf8"));
        int[] referenceArray = str2Array(str);
        int max = 0;
        for (int i : referenceArray) {
            if (i > max) {
                max = i;
            }
        }

        if (max <= 0) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Illegal content of reference file, only contains one color index!");
            }
            return;
        }

        int colorCount = max + 1;

        int[] histogram = new int[colorCount];
        for (int i : referenceArray) {
            histogram[i]++;
        }

        int dim = 0;
        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "Reference color count: " + colorCount);
            sDebugViewer.log("TONY", "Reference histogram: " + Arrays.toString(histogram));
            try {
                dim = Version.getProvisionalVersionForDimension((int) Math.sqrt(referenceArray.length)).getDimensionForVersion();
                sDebugViewer.log("TONY", "Reference dimension: " + dim + "X" + dim);
            } catch (FormatException e) {
                sDebugViewer.log("TONY", "Reference dimension: FormatException!");
                return;
            }
        }

        // everything is ok
        sReferenceFile = referenceFile;
        sReferenceArray = referenceArray;
        sReferenceHistogram = histogram;
        sColorCount = colorCount;
        sCodeDimension = dim;
        clearColorPalletArray();
    }

    private static void clearColorPalletArray() {
        for (int i = 0; i < pallet.length; i++) {
            pallet[i][0] = 0;
            pallet[i][1] = 0;
            pallet[i][2] = 0;
        }
    }

    public static boolean decode(int[] rgb, int w, int h /* the original rgb pixels, width and height */,
            BitMatrix bitMatrixR, BitMatrix bitMatrixG, BitMatrix bitMatrixB,
            float[] out) {
        BitMatrix mR;
        BitMatrix mG;
        BitMatrix mB;

        // try using R-layer to decode the image"
        try {
            mR = ColoredMatrixCodeDecoder.detect(bitMatrixR, out);
            if (sDebugViewer != null) {
                sDebugViewer.draw(rgb, w, h, "pr", out);
                sDebugViewer.draw(mR, "mr", null);
                sDebugViewer.draw(bitMatrixR, "br", out);
            }
        } catch (NotFoundException e) {
            e.printStackTrace();
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 1: can not detect mapping points in R layer!");
            }
            return false;
        }

        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "\r");
            sDebugViewer.log("TONY", "Step 1: try using R-layer to decode the image[Binary]: start!");
        }
        int[] resultR = ColoredMatrixCodeDecoder.mapping(bitMatrixR, bitMatrixG, bitMatrixB, out);
        if (ColoredMatrixCodeDecoder.checkResult(resultR, out)) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 1: try using R-layer to decode the image[Binary]: success!");
            }
        } else {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 1: try using R-layer to decode the image[Binary]: fail!");
            }
        }

        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "\r");
            sDebugViewer.log("TONY", "Step 2: try using R-layer to decode the image[Colour]: start!");
        }
        int[] resultR2 = ColoredMatrixCodeDecoder.mapping(rgb, w, h, out);
        if (ColoredMatrixCodeDecoder.checkResult(resultR2, out)) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 2: try using R-layer to decode the image[Colour]: success!");
            }
        } else {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 2: try using R-layer to decode the image[Colour]: fail!");
            }
        }

        // try using G-layer to decode the image
        try {
            mG = ColoredMatrixCodeDecoder.detect(bitMatrixG, out);
            if (sDebugViewer != null) {
                sDebugViewer.draw(rgb, w, h, "pg", out);
                sDebugViewer.draw(mG, "mg", null);
                sDebugViewer.draw(bitMatrixG, "bg", out);
            }
        } catch (NotFoundException e) {
            e.printStackTrace();
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 3: can not detect mapping points in G layer!");
            }
            return false;
        }

        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "\r");
            sDebugViewer.log("TONY", "Step 3: try using G-layer to decode the image[Binary]: start!");
        }
        int[] resultG = ColoredMatrixCodeDecoder.mapping(bitMatrixR, bitMatrixG, bitMatrixB, out);
        if (ColoredMatrixCodeDecoder.checkResult(resultG, out)) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 3: try using G-layer to decode the image[Binary]: success!");
            }
        } else {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 3: try using G-layer to decode the image[Binary]: fail!");
            }
        }

        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "\r");
            sDebugViewer.log("TONY", "Step 4: try using G-layer to decode the image[Colour]: start!");
        }
        int[] resultG2 = ColoredMatrixCodeDecoder.mapping(rgb, w, h, out);
        if (ColoredMatrixCodeDecoder.checkResult(resultG2, out)) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 4: try using G-layer to decode the image[Colour]: success!");
            }
        } else {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 4: try using G-layer to decode the image[Colour]: fail!");
            }
        }

        // try using B-layer to decode the image
        try {
            mB = ColoredMatrixCodeDecoder.detect(bitMatrixB, out);
            if (sDebugViewer != null) {
                sDebugViewer.draw(rgb, w, h, "pb", out);
                sDebugViewer.draw(mB, "mb", null);
                sDebugViewer.draw(bitMatrixB, "bb", out);
            }
        } catch (NotFoundException e) {
            e.printStackTrace();
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 5: can not detect mapping points in B layer!");
            }
            return false;
        }

        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "\r");
            sDebugViewer.log("TONY", "Step 5: try using B-layer to decode the image[Binary]: start!");
        }
        int[] resultB = ColoredMatrixCodeDecoder.mapping(bitMatrixR, bitMatrixG, bitMatrixB, out);
        if (ColoredMatrixCodeDecoder.checkResult(resultB, out)) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 5: try using B-layer to decode the image[Binary]: success!");
            }
        } else {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 5: try using B-layer to decode the image[Binary]: fail!");
            }
        }

        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "\r");
            sDebugViewer.log("TONY", "Step 6: try using R-layer to decode the image[Colour]: start!");
        }
        int[] resultB2 = ColoredMatrixCodeDecoder.mapping(rgb, w, h, out);
        if (ColoredMatrixCodeDecoder.checkResult(resultB2, out)) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 6: try using R-layer to decode the image[Colour]: success!");
            }
        } else {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 6: try using R-layer to decode the image[Colour]: fail!");
            }
        }

        // try using RGB-layer to decode the image
        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "\r");
            sDebugViewer.log("TONY", "Step 7: try using RGB-layer to decode the image[Binary]: start!");
        }

        try {
            int[] result = ColoredMatrixCodeDecoder.compose(mR, mG, mB);
            if (ColoredMatrixCodeDecoder.checkResult(result, out)) {
                if (sDebugViewer != null) {
                    sDebugViewer.log("TONY", "Step 7: try using RGB-layer to decode the image[Binary]: success!");
                }
            } else {
                if (sDebugViewer != null) {
                    sDebugViewer.log("TONY", "Step 7: try using RGB-layer to decode the image[Binary]: fail!");
                }
            }
        } catch (NotFoundException e) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Step 7: Can not compose final result from mR, mG, and mB!");
            }
        }

        return false;
    }

    public static int[] mapping(int[] rgb, int w, int h, float[] points) {
        int dimensionX = (int) points[points.length - 1];
        int dimensionY = (int) points[points.length - 2];

        int[] result = new int[dimensionX * dimensionY];
        int max = 2 * dimensionX;

        // update pallet
        clearColorPalletArray();
        int c = 0;
        int[] count =  new int[8];
        for (int x = max - 2; x >= 0; x -= 2) {
            int xx = (int) points[(dimensionY - 1) * max + x];
            int yy = (int) points[(dimensionY - 1) * max + x + 1];

            // excludes function area, finder pattern
            if (x / 2 < 9) {
                break;
            }
            int colorIndex = c % sColorCount;

            int pixels = rgb[yy * w + xx];

            int r = (pixels >> 16) & 0xFF;
            int g = (pixels >> 8) & 0xFF;
            int b = pixels & 0xFF;

            float[] hsb = new float[3];
            Color.RGBtoHSB(r, g, b, hsb);

            float ch = hsb[0];
            float cs = hsb[1];
            float cb = hsb[2];

            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "index = " + colorIndex + ": (x, y) = (" + xx + ", " + yy + ") : (" + r + ", " + g + ", " + b + ") vs (" + ch + ", " + cs + ", " + cb + ")");
            }

            pallet[colorIndex][0] += ch;
            pallet[colorIndex][1] += cs;
            pallet[colorIndex][2] += cb;

            count[colorIndex] = count[colorIndex] + 1;
            c++;
        }

        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "Color pallet count: " + Arrays.toString(count));
        }
        for (int j = 0; j < 8; j++) {
            if (count[j] == 0) {
                if (sDebugViewer != null) {
                    sDebugViewer.log("TONY", "Color pallet not found!");
                }
                return null;
            }
            pallet[j][0] = pallet[j][0] / count[j];
            pallet[j][1] = pallet[j][1] / count[j];
            pallet[j][2] = pallet[j][2] / count[j];
        }

        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "Color pallet: " + Arrays.deepToString(pallet));
        }

        int radius = 1; // radius
        int square = radius * radius * 4; // square
        for (int y = 0; y < dimensionY; y++) {
            for (int x = 0; x < max; x += 2) {
                int xx = (int)points[y * max + x];
                int yy = (int)points[y * max + x + 1];

                int pixels = rgb[yy * w + xx]; // sum(rgb, w, h, xx, yy, radius) / square;

                int r = (pixels >> 16) & 0xFF;
                int g = (pixels >> 8) & 0xFF;
                int b = pixels & 0xFF;
                result[y * dimensionX + (x / 2)] = findNearestColorIndex(r, g, b);
            }
        }

        return result;
    }

    private static int findNearestColorIndex(int r, int g, int b) {
        double distance = Double.MAX_VALUE;
        float[] hsb = new float[3];
        Color.RGBtoHSB(r, g, b, hsb);

        float ch = hsb[0];
        float cs = hsb[1];
        float cb = hsb[2];

        int j = -1;
        for (int i = 0; i < pallet.length; i++) {
            double d = Math.pow(ch - pallet[i][0], 2) + Math.pow(cs - pallet[i][1], 2) + Math.pow(cb - pallet[i][2], 2);
            if (d < distance) {
                distance = d;
                j = i;
            }
        }

        if (j == -1) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Illegal nearest color index!");
            }
        }

        return j;
    }

    public static int[] mapping(BitMatrix r, BitMatrix g, BitMatrix b, float[] points) {
        int dimensionX = (int) points[points.length - 1];
        int dimensionY = (int) points[points.length - 2];
//        if (sDebugViewer != null) {
//            sDebugViewer.log("TONY", "dimension: " + dimensionX + "X" + dimensionY);
//        }
        int[] result = new int[dimensionX * dimensionY];
        int max = 2 * dimensionX;

        int radius = 2; // radius
        int square = radius * radius * 4; // square
        for (int y = 0; y < dimensionY; y++) {
            for (int x = 0; x < max; x += 2) {
                int xx = (int)points[y * max + x];
                int yy = (int)points[y * max + x + 1];

                int sumR = DefaultGridSampler.sum(r, xx, yy, radius);
                int sumG = DefaultGridSampler.sum(g, xx, yy, radius);
                int sumB = DefaultGridSampler.sum(b, xx, yy, radius);

                result[y * dimensionX + (x / 2)] = 4 * percent(sumR, square) + 2 * percent(sumG, square) + percent(sumB, square);
            }
        }

        return result;
    }

    public static int percent(int value, int max) {
        if (value * 1.0f / max > 0.6f) {
            // most pixels are black
            return 0;
        }
        return 1; // most pixels are white
    }

    public static final int[] compose(BitMatrix R, BitMatrix G, BitMatrix B) throws NotFoundException {
        if (!validate(R, G, B)) {
            throw NotFoundException.getNotFoundInstance();
        }

        int w = R.getWidth();
        int h = R.getHeight();
        int[] ret = new int[w * h];
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ret[i++] = (R.get(x, y) ? 0 : 1) * 4 + (G.get(x, y) ? 0 : 1) * 2 + (B.get(x, y) ? 0 : 1) * 1;
            }
        }
        return ret;
    }

    private static boolean validate(BitMatrix m1, BitMatrix m2, BitMatrix m3) {
        if (m1 == null || m2 == null || m3 == null) {
            return false;
        }

        if (m1.getWidth() != m2.getWidth()) {
            return false;
        }

        if (m2.getWidth() != m3.getWidth()) {
            return false;
        }

        if (m1.getHeight() != m2.getHeight()) {
            return false;
        }

        if (m2.getHeight() != m3.getHeight()) {
            return false;
        }

        return true;
    }

    public static int[] str2Array(String s) {
        s = s.substring(1, s.length() - 1);
        String[] chars = s.split(",");
        int[] data = new int[chars.length];
        for (int i = 0; i < chars.length; i++) {
            data[i] = Integer.valueOf(chars[i].trim());
        }

        return data;
    }

    public static boolean checkResult(int[] result, float[] out) {
        if (sReferenceFile == null || sReferenceFile.length() == 0 || result == null
                || sReferenceArray == null || sReferenceHistogram == null || sCodeDimension == 0
                || sColorCount == 0) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Illegal argument: " + sReferenceFile + ", " + result);
            }
            return false;
        }

        int[] histogram = buildHistogram(result);
        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "Decoded histogram: " + Arrays.toString(histogram));
        }

        if (!Arrays.equals(sReferenceHistogram, histogram)) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Histogram is different! expected: " + Arrays.toString(histogram));
            }
            return false;
        }

        if (sReferenceArray.length != result.length) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Length is different! expected: " + sReferenceArray.length + ", decode: " + result.length);
            }
            return false;
        }

        int dimensionX = (int) out[out.length - 1];
        int dimensionY = (int) out[out.length - 2];
        if (sDebugViewer != null) {
            sDebugViewer.log("TONY", "Decoded dimension: " + dimensionX + "X" + dimensionY);
        }

        if (dimensionX != dimensionY || dimensionX != sCodeDimension) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Decoded dimension is different! expected: " + sCodeDimension + ", decode: " + dimensionY);
            }
            return false;
        }

        int totalDiffCount = 0;
        int length = sReferenceArray.length;
        StringBuffer line = new StringBuffer();
        for (int i = 0; i < length; i++) {
            line.append(" " + sReferenceArray[i]);
            if (sReferenceArray[i] != result[i]) {
                totalDiffCount++;
                line.append("x");
            } else {
                line.append(":");
            }
            line.append(result[i]);

            if ((i + 1) % dimensionX == 0) {
                if (sDebugViewer != null) {
                    // sDebugViewer.log("TONY", line.toString());
                }
                line.setLength(0);
            }
        }

        if (totalDiffCount != 0) {
            if (sDebugViewer != null) {
                sDebugViewer.log("TONY", "Decoded fail: " + totalDiffCount + "/" + length);
            }
            return false;
        }

        return true;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int count;
        byte[] buffer = new byte[1024];
        while ((count = in.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
}
