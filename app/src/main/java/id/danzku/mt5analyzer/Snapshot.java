package id.danzku.mt5analyzer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Snapshot = OCR text + user-confirmed numerical inputs. Never fabricates candle/OHLC features. */
public final class Snapshot {
    public String source = "manual";
    public String symbol = "XAUUSDm";
    public String timeframe = "M5";
    public Double price, rsi14, atr14, ema20, ema50, macdHistogram;
    public String marketStructure = "UNKNOWN";
    public String notes = "";
    public boolean priceConfirmed = false;
    public final List<ChartTextParser.Line> ocrLines = new ArrayList<>();

    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("source", source);
        j.put("symbol", symbol);
        j.put("timeframe", timeframe);
        // An OCR price is unverified and must never be treated as an executable quote.
        j.put("price", priceConfirmed ? value(price) : JSONObject.NULL);
        j.put("unverified_price_from_ocr", priceConfirmed ? JSONObject.NULL : value(price));
        j.put("rsi14", value(rsi14));
        j.put("atr14", value(atr14));
        j.put("ema20", value(ema20));
        j.put("ema50", value(ema50));
        j.put("macd_histogram", value(macdHistogram));
        j.put("market_structure_user_input", marketStructure);
        j.put("notes_user_input", notes);
        j.put("price_confirmed_by_user", priceConfirmed);
        j.put("timestamp_utc_ms", System.currentTimeMillis());
        JSONArray arr = new JSONArray();
        int max = Math.min(ocrLines.size(), 65);
        for (int i = 0; i < max; i++) {
            ChartTextParser.Line line = ocrLines.get(i);
            // Whitelist chart labels: never upload unrelated app chats/account text.
            if (!line.text.matches("(?i).*\\b(XAUUSD|RSI|ATR|MACD|EMA|SELL|BUY|M5|M15)\\b.*")
                && !line.text.matches("(?i).*XAUUSD.*")) continue;
            JSONObject item = new JSONObject();
            item.put("text", line.text);
            item.put("x", line.x);
            item.put("y", line.y);
            arr.put(item);
        }
        j.put("ocr_lines_normalized", arr);
        return j;
    }

    public static Snapshot fromJson(String raw) throws JSONException {
        JSONObject j = new JSONObject(raw);
        Snapshot s = new Snapshot();
        s.source = j.optString("source", "manual");
        s.symbol = j.optString("symbol", "XAUUSDm");
        s.timeframe = j.optString("timeframe", "M5");
        s.priceConfirmed = j.optBoolean("price_confirmed_by_user", false);
        s.price = number(j, "price");
        if (s.price == null) s.price = number(j, "unverified_price_from_ocr");
        s.rsi14 = number(j, "rsi14");
        s.atr14 = number(j, "atr14");
        s.ema20 = number(j, "ema20");
        s.ema50 = number(j, "ema50");
        s.macdHistogram = number(j, "macd_histogram");
        s.marketStructure = j.optString("market_structure_user_input", "UNKNOWN");
        s.notes = j.optString("notes_user_input", "");
        return s;
    }
    private static Double number(JSONObject j, String name) { return j.isNull(name) ? null : j.optDouble(name); }
    private static Object value(Double val) { return val == null ? JSONObject.NULL : val; }
}
