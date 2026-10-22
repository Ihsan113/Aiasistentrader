package id.danzku.mt5analyzer;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Text-only GPT-OSS 120B. Only OCR/validated JSON is sent; never screenshot pixels. */
public final class GroqClient {
    public static final String MODEL = "openai/gpt-oss-120b";
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private GroqClient() {}

    public static JSONObject analyze(String apiKey, Snapshot snapshot) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) throw new IllegalStateException("Simpan Groq API key terlebih dahulu.");
        if (snapshot == null) throw new IllegalArgumentException("Snapshot belum ada.");
        JSONObject payload = createRequest(snapshot);
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(45000);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setDoOutput(true);
        try {
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            int status = connection.getResponseCode();
            String raw = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status < 200 || status >= 300) {
                String reason = "";
                try { reason = new JSONObject(raw).optJSONObject("error").optString("message", ""); }
                catch (Exception ignored) { /* API may return HTML */ }
                if (status == 401) throw new IllegalStateException("API key Groq tidak valid (401).");
                if (status == 429) throw new IllegalStateException("Batas request Groq tercapai (429). Coba lagi nanti.");
                if (status >= 500) throw new IllegalStateException("Server Groq sedang bermasalah (" + status + ").");
                throw new IllegalStateException("Groq HTTP " + status + ": " + truncate(reason, 240));
            }
            JSONObject json = new JSONObject(raw);
            JSONObject message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            String content = message.optString("content", "");
            if (content.isEmpty()) throw new IllegalStateException("Groq tidak menghasilkan analisis teks.");
            JSONObject answer = new JSONObject(content);
            ensureValid(answer, snapshot);
            return answer;
        } finally { connection.disconnect(); }
    }

    /** Public to unit-test request shape without requiring an API key or network. */
    public static JSONObject createRequest(Snapshot snapshot) throws Exception {
        JSONObject req = new JSONObject();
        req.put("model", MODEL);
        req.put("temperature", 0.1);
        req.put("max_completion_tokens", 1100);
        req.put("reasoning_effort", "low");
        JSONObject fmt = new JSONObject().put("type", "json_schema")
            .put("json_schema", new JSONObject()
                .put("name", "mt5_analysis")
                .put("strict", true)
                .put("schema", resultSchema()));
        req.put("response_format", fmt);

        String system = "Anda analis teknikal pasar, bukan eksekutor transaksi. Bahasa Indonesia singkat dan spesifik. "
            + "Input adalah teks OCR lokal dan nilai numerik yang mungkin tidak lengkap atau keliru. "
            + "JANGAN mengaku melihat candlestick, tren EMA, MACD, OHLC, breakout, rejection, atau support/resistance jika tidak ada bukti pada input. "
            + "JANGAN mengarang harga entry, stop-loss atau take-profit yang presisi tanpa level historis/struktur grafik. "
            + "Jika konteks candle dan EMA kurang, keputusan WAIT, keyakinan LOW; jelaskan data yang kurang. "
            + "RSI oversold/overbought bukan sinyal entry sendirian. "
            + "Market_structure_user_input adalah observasi user, bukan hasil verifikasi. "
            + "Prioritaskan risiko, jangan memberi jaminan profit, jangan mendesak entry. "
            + "BUY atau SELL hanya ketika kondisi cukup didukung bukti eksplisit; jika ragu WAIT. "
            + "Ingat screenshot hanya satu momen, bukan quote live. Hindari certainty palsu.";
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content",
                "Buat ringkasan teknikal berdasarkan JSON berikut, sesuai skema output: " + snapshot.toJson().toString()));
        req.put("messages", messages);
        return req;
    }

    private static JSONObject resultSchema() throws Exception {
        JSONObject props = new JSONObject();
        props.put("decision", fieldEnum("WAIT", "BUY", "SELL"));
        props.put("trend", fieldEnum("UNKNOWN", "BULLISH", "BEARISH", "SIDEWAYS"));
        props.put("confidence", fieldEnum("LOW", "MEDIUM", "HIGH"));
        props.put("summary", str());
        props.put("entry_condition", str());
        props.put("stop_loss_guidance", str());
        props.put("take_profit_guidance", str());
        props.put("reasons", strArray());
        props.put("risks", strArray());
        props.put("missing_data", strArray());
        JSONArray required = new JSONArray();
        for (String key : new String[]{"decision","trend","confidence","summary","entry_condition",
                "stop_loss_guidance","take_profit_guidance","reasons","risks","missing_data"}) required.put(key);
        return new JSONObject().put("type", "object").put("properties", props)
                .put("required", required).put("additionalProperties", false);
    }
    private static JSONObject str() throws Exception { return new JSONObject().put("type", "string"); }
    private static JSONObject fieldEnum(String... values) throws Exception {
        JSONArray array = new JSONArray(); for (String s : values) array.put(s);
        return str().put("enum", array);
    }
    private static JSONObject strArray() throws Exception {
        return new JSONObject().put("type", "array").put("items", str());
    }
    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null && sb.length() < 500_000) sb.append(line).append('\n');
        }
        return sb.toString();
    }
    private static void ensureValid(JSONObject answer, Snapshot snapshot) throws Exception {
        String action = answer.optString("decision", "").toUpperCase(Locale.US);
        if (!"WAIT".equals(action) && !"BUY".equals(action) && !"SELL".equals(action))
            throw new IllegalStateException("Output AI tidak memenuhi skema keputusan.");
        // Deterministic guard: text OCR alone is insufficient for a trade signal.
        if (!"WAIT".equals(action) && (!snapshot.priceConfirmed
                || "UNKNOWN".equals(snapshot.marketStructure)
                || (snapshot.rsi14 == null && snapshot.atr14 == null))) {
            answer.put("decision", "WAIT");
            answer.put("confidence", "LOW");
            answer.put("summary", "Data belum cukup untuk entry terkonfirmasi. " + answer.optString("summary", ""));
            answer.getJSONArray("missing_data").put("Perlu validasi harga dan struktur candle/tren sebelum entry.");
        }
    }
    private static String truncate(String s, int limit) { return s.length() > limit ? s.substring(0, limit) : s; }
}
