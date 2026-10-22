package id.danzku.mt5analyzer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot explicit-consent capture; no continuous recording or automatic trades. */
public final class CaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "projection_code";
    public static final String EXTRA_RESULT_DATA = "projection_data";
    private static final int NOTIFICATION_ID = 5104;
    private static final String CHANNEL = "mt5_capture";
    private static final long DELAY_MS = 2800L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay display;
    private long captureStarted;
    private Runnable timeout;

    @Override public void onCreate() { super.onCreate(); createNotificationChannel(); }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null || !intent.hasExtra(EXTRA_RESULT_CODE)) { stopSelf(); return START_NOT_STICKY; }
        try {
            Notification note = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("MT5 Analyzer: capture layar")
                .setContentText("Screenshot sekali setelah izin Android; tidak merekam terus-menerus")
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(NOTIFICATION_ID, note);
            AnalysisState.broadcast(this, AnalysisState.ACTION_CAPTURE);
            MediaProjectionManager mgr = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent token;
            if (Build.VERSION.SDK_INT >= 33) token = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
            else token = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (token == null || mgr == null) throw new IllegalStateException("Token screen capture kosong");
            projection = mgr.getMediaProjection(intent.getIntExtra(EXTRA_RESULT_CODE, 0), token);
            if (projection == null) throw new IllegalStateException("MediaProjection ditolak Android");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    if (!completed.get()) fail("Capture berhenti sebelum screenshot didapat.");
                }
            }, main);
            WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            if (wm == null) throw new IllegalStateException("Layar tidak tersedia");
            wm.getDefaultDisplay().getRealMetrics(metrics);
            int width = metrics.widthPixels, height = metrics.heightPixels;
            if (width < 1 || height < 1) throw new IllegalStateException("Ukuran layar tidak valid");
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
            reader.setOnImageAvailableListener(this::onFrame, main);
            captureStarted = SystemClock.uptimeMillis();
            display = projection.createVirtualDisplay("MT5-OneShot-Scan", width, height, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, main);
            timeout = () -> fail("Capture timeout. Coba pilih screenshot galeri atau izin ulang.");
            main.postDelayed(timeout, 14_000);
            AnalysisState.status(this, "Capture dimulai. Buka MT5; mengambil frame dalam ~3 detik.");
        } catch (Exception e) { fail("Capture gagal: " + e.getMessage()); }
        return START_NOT_STICKY;
    }

    private void onFrame(ImageReader available) {
        if (completed.get()) return;
        Image img = null;
        try {
            img = available.acquireLatestImage();
            if (img == null || SystemClock.uptimeMillis() - captureStarted < DELAY_MS) return;
            if (!completed.compareAndSet(false, true)) return;
            Image.Plane plane = img.getPlanes()[0];
            int width = img.getWidth(), height = img.getHeight();
            int rowPadding = plane.getRowStride() - plane.getPixelStride() * width;
            int paddedWidth = width + rowPadding / plane.getPixelStride();
            Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
            ByteBuffer pixels = plane.getBuffer();
            padded.copyPixelsFromBuffer(pixels);
            Bitmap crop = Bitmap.createBitmap(padded, 0, 0, width, height);
            if (padded != crop) padded.recycle();
            // Release acquired image before closing ImageReader/VirtualDisplay.
            img.close();
            img = null;
            cleanupProjection();
            AnalysisState.broadcast(this, AnalysisState.ACTION_FINISHED);
            AnalysisState.status(this, "Capture selesai, membaca indikator lokal...");
            OcrEngine.read(crop, new OcrEngine.Callback() {
                @Override public void success(Snapshot snap) {
                    crop.recycle();
                    AnalysisState.snapshot(CaptureService.this, snap);
                    AnalysisState.status(CaptureService.this, "OCR selesai. Mengirim JSON ke Groq...");
                    new Thread(() -> {
                        try {
                            org.json.JSONObject result = GroqClient.analyze(KeyVault.load(CaptureService.this), snap);
                            AnalysisState.result(CaptureService.this, result, snap);
                        } catch (Exception e) { AnalysisState.status(CaptureService.this, "Analisis gagal: " + e.getMessage()); }
                        finally { main.post(CaptureService.this::finishService); }
                    }, "groq-capture").start();
                }
                @Override public void error(String message) {
                    crop.recycle(); AnalysisState.status(CaptureService.this, message); finishService();
                }
            });
        } catch (Exception e) {
            if (completed.get()) {
                AnalysisState.status(this, "Frame tidak terbaca: " + e.getMessage());
                cleanupProjection();
                AnalysisState.broadcast(this, AnalysisState.ACTION_FINISHED);
                finishService();
            } else fail("Frame tidak terbaca: " + e.getMessage());
        } finally { if (img != null) img.close(); }
    }
    private void fail(String msg) {
        if (!completed.compareAndSet(false, true)) return;
        AnalysisState.status(this, msg);
        cleanupProjection();
        AnalysisState.broadcast(this, AnalysisState.ACTION_FINISHED);
        finishService();
    }
    private void cleanupProjection() {
        if (timeout != null) main.removeCallbacks(timeout);
        if (reader != null) { reader.setOnImageAvailableListener(null, null); }
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) {
            MediaProjection p = projection; projection = null;
            try { p.stop(); } catch (Exception ignored) { }
        }
    }
    private void finishService() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }
    @Override public void onDestroy() { cleanupProjection(); super.onDestroy(); }
    private void createNotificationChannel() {
        NotificationManager mgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (mgr != null) mgr.createNotificationChannel(new NotificationChannel(CHANNEL, "MT5 screenshot permission", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
