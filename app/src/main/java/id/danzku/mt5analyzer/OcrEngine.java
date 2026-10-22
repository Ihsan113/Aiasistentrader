package id.danzku.mt5analyzer;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** ML Kit on-device OCR. Called only after user grants capture or picks a screenshot. */
public final class OcrEngine {
    public interface Callback { void success(Snapshot snapshot); void error(String error); }
    private OcrEngine() {}

    public static void read(Bitmap original, Callback callback) {
        if (original == null) { callback.error("Gambar kosong"); return; }
        final Bitmap bitmap;
        int max = Math.max(original.getWidth(), original.getHeight());
        if (max > 2200) {
            double scale = 2200d / max;
            bitmap = Bitmap.createScaledBitmap(original,
                    (int)(original.getWidth() * scale), (int)(original.getHeight() * scale), true);
        } else bitmap = original;
        var recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener(text -> {
                try {
                    List<ChartTextParser.Line> lines = new ArrayList<>();
                    for (Text.TextBlock block : text.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            Rect rect = line.getBoundingBox();
                            if (rect == null) continue;
                            lines.add(new ChartTextParser.Line(line.getText(),
                                Math.max(0, Math.min(1, rect.left / (double) bitmap.getWidth())),
                                Math.max(0, Math.min(1, rect.top / (double) bitmap.getHeight()))));
                        }
                    }
                    lines.sort(Comparator.comparingDouble((ChartTextParser.Line a) -> a.y).thenComparingDouble(a -> a.x));
                    callback.success(ChartTextParser.parse(lines));
                } catch (Exception e) { callback.error("OCR gagal: " + e.getMessage()); }
                finally { recognizer.close(); if (bitmap != original) bitmap.recycle(); }
            })
            .addOnFailureListener(ex -> {
                recognizer.close(); if (bitmap != original) bitmap.recycle();
                callback.error("OCR gagal: " + ex.getMessage());
            });
    }
}
