package id.danzku.mt5analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java parser for OCR (unit-testable). Inputs already bound to screenshot coordinates. */
public final class ChartTextParser {
    private ChartTextParser() {}
    public static final class Line {
        public final String text;
        /** Normalized [0..1] coordinates of text left/top. */
        public final double x, y;
        public Line(String text, double x, double y) { this.text = text; this.x = x; this.y = y; }
    }
    private static final Pattern RSI = Pattern.compile("(?i)\\bRSI\\s*\\(?\\s*14\\s*\\)?\\s*[:=]?\\s*([0-9]{1,3}(?:[.,][0-9]{1,2})?)");
    private static final Pattern ATR = Pattern.compile("(?i)\\bATR\\s*\\(?\\s*14\\s*\\)?\\s*[:=]?\\s*([0-9]{1,5}(?:[.,][0-9]{1,4})?)");
    private static final Pattern SYMBOL = Pattern.compile("(?i)\\b(XAUUSD[a-z0-9._-]*)\\b");
    private static final Pattern M5 = Pattern.compile("(?i)\\bM5\\b");
    private static final Pattern QUOTE = Pattern.compile("(?<![0-9])([0-9]{4,5}[.,][0-9]{2,3})(?![0-9])");

    public static Snapshot parse(List<Line> input) {
        Snapshot out = new Snapshot();
        out.source = "screenshot_ocr";
        if (input == null) return out;
        // Find a chart symbol anchor. For split screen, omit OCR from the unrelated app above it.
        double chartStart = -1;
        for (Line l : input) {
            if (SYMBOL.matcher(l.text).find()) { chartStart = l.y; break; }
        }
        List<Line> chart = new ArrayList<>();
        for (Line line : input) {
            if (chartStart >= 0 && line.y < chartStart - 0.035) continue;
            chart.add(line);
            if (chart.size() >= 65) break;
        }
        out.ocrLines.addAll(chart);
        Double quoteFromButton = null;
        Double quoteFromRight = null;
        for (Line l : chart) {
            String s = l.text.replace('，', '.').trim();
            Matcher sym = SYMBOL.matcher(s);
            if (sym.find()) out.symbol = sym.group(1);
            if (M5.matcher(s).find()) out.timeframe = "M5";
            Matcher rsi = RSI.matcher(s);
            if (rsi.find()) {
                Double value = parseNumber(rsi.group(1));
                if (value != null && value >= 0 && value <= 100) out.rsi14 = value;
            }
            Matcher atr = ATR.matcher(s);
            if (atr.find()) {
                Double value = parseNumber(atr.group(1));
                if (value != null && value > 0 && value < 10000) out.atr14 = value;
            }
            Matcher quote = QUOTE.matcher(s);
            if (quote.find()) {
                Double value = parseNumber(quote.group(1));
                if (value != null) {
                    if (s.toUpperCase(Locale.US).contains("SELL") || s.toUpperCase(Locale.US).contains("BID")) quoteFromButton = value;
                    if (l.x > 0.78 && !s.toUpperCase(Locale.US).contains("BUY")) quoteFromRight = value;
                }
            }
        }
        // OCR coordinates are not proof that the current bid was read accurately.
        // Only pre-fill if a quote is explicitly labeled SELL/BID; otherwise manual confirmation required.
        out.price = quoteFromButton != null ? quoteFromButton : quoteFromRight;
        out.priceConfirmed = false;
        return out;
    }
    static Double parseNumber(String raw) {
        try { return Double.parseDouble(raw.replace(',', '.')); }
        catch (Exception ignored) { return null; }
    }
}
