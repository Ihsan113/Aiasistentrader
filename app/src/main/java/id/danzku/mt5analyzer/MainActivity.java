package id.danzku.mt5analyzer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String ACTION_FROM_OVERLAY = "id.danzku.mt5analyzer.OVERLAY_CAPTURE";
    private static final int REQ_CAPTURE = 301;
    private static final int REQ_IMAGE = 302;
    private TextView status, result, keyHint;
    private EditText apiKey, symbol, price, rsi, atr, ema20, ema50, macd, notes;
    private Spinner structure;
    private Button analyzeButton;
    private Snapshot lastOcr = null;
    private String lastLoadedJson = "";
    private boolean startedReceiver = false;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refreshState(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(UiKit.BG);
        getWindow().setNavigationBarColor(UiKit.BG);
        buildUi();
        refreshState();
        if (ACTION_FROM_OVERLAY.equals(getIntent().getAction())) {
            getWindow().getDecorView().postDelayed(this::requestCapture, 300);
        }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (ACTION_FROM_OVERLAY.equals(intent.getAction())) getWindow().getDecorView().postDelayed(this::requestCapture, 250);
    }
    @Override protected void onStart() {
        super.onStart();
        if (!startedReceiver) {
            IntentFilter filter = new IntentFilter(AnalysisState.ACTION_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(receiver, filter);
            startedReceiver = true;
        }
        refreshState();
    }
    @Override protected void onStop() {
        if (startedReceiver) { unregisterReceiver(receiver); startedReceiver = false; }
        super.onStop();
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(UiKit.BG);
        LinearLayout root = UiKit.vertical(this);
        root.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 22), UiKit.dp(this, 18), UiKit.dp(this, 32));
        scroll.addView(root); setContentView(scroll);

        TextView brand = UiKit.text(this, "◈  DANZKU LAB  /  TRADING TOOLS", 12, UiKit.CYAN, true);
        root.addView(brand); gap(root, 10);
        root.addView(UiKit.text(this, "MT5 Groq Analyzer", 29, UiKit.TEXT, true));
        gap(root, 5);
        root.addView(UiKit.text(this, "XAUUSD  ·  M5  ·  GPT-OSS 120B", 13, UiKit.SUB, false));
        gap(root, 18);

        LinearLayout info = card(root);
        info.addView(UiKit.text(this, "STATUS PEMINDAIAN", 11, UiKit.CYAN, true)); gap(info, 8);
        status = UiKit.text(this, "Siap", 15, UiKit.TEXT, true); info.addView(status);
        gap(info, 8);
        info.addView(UiKit.text(this, "Screen capture butuh persetujuan Android setiap sesi. Hanya teks indikator yang dikirim ke Groq; gambar tidak diunggah.", 12, UiKit.SUB, false));
        gap(root, 16);

        LinearLayout tools = card(root);
        tools.addView(UiKit.text(this, "01  /  AMBIL DATA MT5", 14, UiKit.TEXT, true)); gap(tools, 12);
        Button capture = UiKit.button(this, "◎  Capture layar (izin Android)", UiKit.CYAN);
        tools.addView(capture); capture.setOnClickListener(v -> requestCapture()); gap(tools, 9);
        Button gallery = UiKit.button(this, "▧  Impor screenshot galeri", UiKit.GREEN);
        tools.addView(gallery); gallery.setOnClickListener(v -> chooseImage()); gap(tools, 9);
        Button overlay = UiKit.button(this, "◈  Aktifkan floating overlay", 0xFFC8D4ED);
        tools.addView(overlay); overlay.setOnClickListener(v -> toggleOverlay());
        gap(root, 16);

        LinearLayout keyBox = card(root);
        keyBox.addView(UiKit.text(this, "02  /  GROQ API KEY", 14, UiKit.TEXT, true)); gap(keyBox, 9);
        keyHint = UiKit.text(this, "Belum tersimpan", 12, UiKit.SUB, false); keyBox.addView(keyHint); gap(keyBox, 10);
        apiKey = UiKit.input(this, "gsk_... (disimpan terenkripsi)", false);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyBox.addView(apiKey); gap(keyBox, 10);
        Button save = UiKit.button(this, "Simpan API key", UiKit.CYAN);
        keyBox.addView(save); save.setOnClickListener(v -> saveKey()); gap(keyBox, 7);
        Button remove = UiKit.button(this, "Hapus API key lokal", 0xFFAFB9CA);
        keyBox.addView(remove); remove.setOnClickListener(v -> { KeyVault.clear(this); apiKey.setText(""); updateKeyHint(); toast("API key lokal dihapus"); });
        gap(root, 16);

        LinearLayout fields = card(root);
        fields.addView(UiKit.text(this, "03  /  KONFIRMASI DATA OCR", 14, UiKit.TEXT, true)); gap(fields, 8);
        fields.addView(UiKit.text(this, "Periksa angka OCR sebelum analisis. Kolom kosong berarti belum terverifikasi, bukan nol.", 12, UiKit.SUB, false)); gap(fields, 11);
        symbol = inputField(fields, "Simbol", "XAUUSDm", false);
        symbol.setText("XAUUSDm");
        price = inputField(fields, "Harga terakhir (screenshot)", "contoh: 4100.273", true);
        rsi = inputField(fields, "RSI (14)", "contoh: 62.36", true);
        atr = inputField(fields, "ATR (14)", "contoh: 7.056", true);
        ema20 = inputField(fields, "EMA 20 (opsional; angka dari Data Window MT5)", "contoh: 4088.500", true);
        ema50 = inputField(fields, "EMA 50 (opsional)", "contoh: 4099.200", true);
        macd = inputField(fields, "MACD histogram (opsional)", "contoh: -2.14", true);
        fields.addView(UiKit.text(this, "Struktur harga (jika kamu sudah cek manual)", 12, UiKit.SUB, true)); gap(fields, 5);
        structure = new Spinner(this);
        String[] choices = {"UNKNOWN", "BEARISH", "BULLISH", "SIDEWAYS"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices);
        structure.setAdapter(adapter); structure.setBackground(UiKit.bg(UiKit.CARD2, 9, this));
        structure.setPadding(UiKit.dp(this, 10), 0, UiKit.dp(this, 10), 0);
        fields.addView(structure); gap(fields, 12);
        notes = inputField(fields, "Catatan opsional (contoh: EMA20 < EMA50)", "Tidak ada data EMA? Kosongkan.", false);
        gap(fields, 10);
        analyzeButton = UiKit.button(this, "✦  ANALISIS DENGAN GROQ", UiKit.GREEN);
        fields.addView(analyzeButton); analyzeButton.setOnClickListener(v -> manualAnalyze());
        gap(root, 16);

        LinearLayout outcome = card(root);
        outcome.addView(UiKit.text(this, "04  /  HASIL ANALISIS", 14, UiKit.TEXT, true)); gap(outcome, 10);
        result = UiKit.text(this, "Belum ada analisis. Masukkan API key, pilih screenshot lalu periksa angka.", 14, UiKit.SUB, false);
        outcome.addView(result);
        gap(root, 12);
        root.addView(UiKit.text(this, "⚠  Pendamping keputusan, bukan bot auto-trading. Tidak mengirim order ke MT5. Hindari leverage berlebihan; risiko maksimal per transaksi ditentukan sendiri.", 12, UiKit.SUB, false));
        updateKeyHint();
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = UiKit.vertical(this);
        c.setBackground(UiKit.bg(UiKit.CARD, 17, this));
        UiKit.pad(c, this, 17);
        parent.addView(c, new LinearLayout.LayoutParams(-1,-2));
        return c;
    }
    private void gap(LinearLayout p, int dp) { View v = new View(this); p.addView(v, new LinearLayout.LayoutParams(1,UiKit.dp(this,dp))); }
    private EditText inputField(LinearLayout parent, String name, String placeholder, boolean numeric) {
        parent.addView(UiKit.text(this, name, 12, UiKit.SUB, true)); gap(parent, 5);
        EditText e = UiKit.input(this, placeholder, numeric);
        parent.addView(e, new LinearLayout.LayoutParams(-1,-2)); gap(parent, 11);
        return e;
    }
    private void updateKeyHint() { if (keyHint != null) keyHint.setText(KeyVault.load(this).isEmpty() ? "● Belum ada API key" : "● Tersimpan di Android Keystore (tidak ditampilkan)"); }
    private void saveKey() {
        try { KeyVault.save(this, apiKey.getText().toString()); apiKey.setText(""); updateKeyHint(); toast("API key berhasil disimpan lokal"); }
        catch (Exception ex) { toast("Gagal menyimpan: " + ex.getMessage()); }
    }
    private void toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Izinkan 'Tampilkan di atas aplikasi lain', lalu tekan tombol overlay lagi.");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        startService(new Intent(this, OverlayService.class));
        toast("Floating overlay aktif. Buka MT5.");
    }
    private void requestCapture() {
        try {
            MediaProjectionManager mgr = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mgr == null) throw new IllegalStateException("MediaProjection tidak tersedia pada perangkat ini");
            startActivityForResult(mgr.createScreenCaptureIntent(), REQ_CAPTURE);
        } catch (Exception ex) { toast("Capture tidak tersedia: " + ex.getMessage()); }
    }
    private void chooseImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*");
        startActivityForResult(i, REQ_IMAGE);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQ_CAPTURE) AnalysisState.status(this, "Izin capture dibatalkan.");
            return;
        }
        if (requestCode == REQ_CAPTURE) {
            AnalysisState.status(this, "Izin disetujui. Beralih ke MT5; pemindaian dimulai dalam ~3 detik.");
            Intent scan = new Intent(this, CaptureService.class)
                    .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(CaptureService.EXTRA_RESULT_DATA, data);
            try { startForegroundService(scan); }
            catch (Exception e) { AnalysisState.status(this, "Tidak dapat memulai capture: " + e.getMessage()); }
        } else if (requestCode == REQ_IMAGE) {
            final Uri uri = data.getData();
            if (uri == null) return;
            AnalysisState.status(this, "Membaca screenshot galeri...");
            new Thread(() -> {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    final Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap == null) throw new IllegalArgumentException("Format gambar gagal dibaca");
                    runOnUiThread(() -> OcrEngine.read(bitmap, new OcrEngine.Callback() {
                        @Override public void success(Snapshot snap) { bitmap.recycle(); applySnapshot(snap); }
                        @Override public void error(String err) { bitmap.recycle(); AnalysisState.status(MainActivity.this, err); }
                    }));
                } catch (Exception e) { runOnUiThread(() -> AnalysisState.status(MainActivity.this, "Gagal impor: " + e.getMessage())); }
            }).start();
        }
    }
    private void applySnapshot(Snapshot snap) {
        lastOcr = snap;
        symbol.setText(snap.symbol);
        price.setText(value(snap.price));
        rsi.setText(value(snap.rsi14));
        atr.setText(value(snap.atr14));
        ema20.setText(value(snap.ema20));
        ema50.setText(value(snap.ema50));
        macd.setText(value(snap.macdHistogram));
        AnalysisState.snapshot(this, snap);
        AnalysisState.status(this, "OCR selesai. Verifikasi harga, RSI, ATR sebelum klik Analisis.");
    }
    private String value(Double d) { return d == null ? "" : String.format(Locale.US, "%.3f", d); }
    private Double parseEdit(EditText input, Double min, Double max, String field) {
        String s = input.getText().toString().trim().replace(',', '.');
        if (s.isEmpty()) return null;
        try {
            double number = Double.parseDouble(s);
            if (!Double.isFinite(number) || number < min || number > max) throw new Exception();
            return number;
        } catch (Exception e) { throw new IllegalArgumentException(field + " tidak valid."); }
    }
    private Snapshot buildSnapshot() {
        Snapshot s = new Snapshot();
        if (lastOcr != null) { s.source = "manual_confirmed_ocr"; s.ocrLines.addAll(lastOcr.ocrLines); }
        s.symbol = symbol.getText().toString().trim();
        if (s.symbol.isEmpty()) s.symbol = "UNKNOWN";
        s.price = parseEdit(price, 0d, 999999d, "Harga");
        s.rsi14 = parseEdit(rsi, 0d, 100d, "RSI");
        s.atr14 = parseEdit(atr, 0.0000001d, 99999d, "ATR");
        s.ema20 = parseEdit(ema20, 0d, 999999d, "EMA 20");
        s.ema50 = parseEdit(ema50, 0d, 999999d, "EMA 50");
        s.macdHistogram = parseEdit(macd, -99999d, 99999d, "MACD");
        s.marketStructure = structure.getSelectedItem().toString();
        s.notes = notes.getText().toString().trim();
        s.priceConfirmed = s.price != null;
        return s;
    }
    private void manualAnalyze() {
        if (KeyVault.load(this).isEmpty()) { toast("Simpan API key Groq dulu."); return; }
        final Snapshot s;
        try { s = buildSnapshot(); }
        catch (Exception e) { toast(e.getMessage()); return; }
        if (s.price == null && s.rsi14 == null && s.atr14 == null && s.notes.isEmpty()) {
            toast("Belum ada data. Impor screenshot atau masukkan indikator."); return;
        }
        analyzeButton.setEnabled(false);
        AnalysisState.status(this, "Mengirim JSON indikator ke Groq...");
        new Thread(() -> {
            try {
                JSONObject answer = GroqClient.analyze(KeyVault.load(this), s);
                AnalysisState.result(this, answer, s);
            } catch (Exception e) { AnalysisState.status(this, "Gagal AI: " + e.getMessage()); }
            finally { runOnUiThread(() -> analyzeButton.setEnabled(true)); }
        }, "groq-manual").start();
    }
    private void refreshState() {
        if (status == null || result == null) return;
        status.setText(AnalysisState.status(this));
        String stored = AnalysisState.snapshot(this);
        if (!stored.isEmpty() && !stored.equals(lastLoadedJson)) {
            try {
                lastOcr = Snapshot.fromJson(stored);
                symbol.setText(lastOcr.symbol);
                price.setText(value(lastOcr.price));
                rsi.setText(value(lastOcr.rsi14));
                atr.setText(value(lastOcr.atr14));
                ema20.setText(value(lastOcr.ema20));
                ema50.setText(value(lastOcr.ema50));
                macd.setText(value(lastOcr.macdHistogram));
                lastLoadedJson = stored;
            } catch (Exception ignored) { }
        }
        String raw = AnalysisState.result(this);
        if (raw.isEmpty()) return;
        try {
            JSONObject data = new JSONObject(raw);
            StringBuilder b = new StringBuilder();
            b.append("KEPUTUSAN: ").append(data.optString("decision", "WAIT"));
            b.append("   •   ").append(data.optString("confidence", "LOW")).append(" confidence\n");
            b.append("Tren: ").append(data.optString("trend", "UNKNOWN")).append("\n\n");
            b.append(data.optString("summary", "")).append("\n\n");
            section(b, "Alasan", data.optJSONArray("reasons"));
            b.append("\nKONDISI ENTRY\n").append(data.optString("entry_condition", "-")).append("\n\n");
            b.append("STOP LOSS\n").append(data.optString("stop_loss_guidance", "-")).append("\n\n");
            b.append("TAKE PROFIT\n").append(data.optString("take_profit_guidance", "-")).append("\n\n");
            section(b, "RISIKO", data.optJSONArray("risks"));
            section(b, "DATA BELUM ADA", data.optJSONArray("missing_data"));
            result.setText(b.toString());
            String decision = data.optString("decision", "WAIT");
            result.setTextColor("BUY".equals(decision) ? UiKit.GREEN : "SELL".equals(decision) ? UiKit.RED : UiKit.TEXT);
        } catch (Exception ignored) { result.setText("Format hasil AI tidak terbaca."); }
    }
    private void section(StringBuilder b, String heading, JSONArray items) {
        if (items == null || items.length() == 0) return;
        b.append(heading).append("\n");
        for (int i = 0; i < items.length(); i++) b.append("• ").append(items.optString(i)).append("\n");
        b.append("\n");
    }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
}
