package id.danzku.mt5analyzer;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Movable SYSTEM_ALERT_WINDOW bubble; tapping SCAN requests Android capture consent again. */
public final class OverlayService extends Service {
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private LinearLayout root;
    private TextView bubble, detail;
    private LinearLayout panel;
    private boolean registered = false;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (AnalysisState.ACTION_CAPTURE.equals(action)) {
                if (root != null) root.setVisibility(View.INVISIBLE);
            } else {
                if (root != null) root.setVisibility(View.VISIBLE);
                updateDetail();
            }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        root = UiKit.vertical(this); root.setBackground(UiKit.bg(UiKit.CARD, 17, this));
        root.setElevation(UiKit.dp(this, 9));
        root.setPadding(UiKit.dp(this, 7), UiKit.dp(this, 7), UiKit.dp(this, 7), UiKit.dp(this, 7));
        bubble = UiKit.text(this, "◈ AI", 17, UiKit.CYAN, true);
        bubble.setGravity(Gravity.CENTER); bubble.setBackground(UiKit.bg(UiKit.BG, 13, this));
        bubble.setPadding(UiKit.dp(this, 13), UiKit.dp(this, 11), UiKit.dp(this, 13), UiKit.dp(this, 11));
        root.addView(bubble);
        panel = UiKit.vertical(this); panel.setVisibility(View.GONE);
        panel.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 12), UiKit.dp(this, 8), UiKit.dp(this, 4));
        TextView title = UiKit.text(this, "MT5  ·  GROQ", 13, UiKit.CYAN, true);
        panel.addView(title);
        detail = UiKit.text(this, "Belum ada hasil", 12, UiKit.TEXT, false);
        detail.setPadding(0,UiKit.dp(this, 7),0,UiKit.dp(this, 9));
        panel.addView(detail);
        Button scan = UiKit.button(this, "SCAN MT5", UiKit.GREEN);
        scan.setOnClickListener(v -> {
            panel.setVisibility(View.GONE); resize();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setAction(MainActivity.ACTION_FROM_OVERLAY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        });
        panel.addView(scan);
        Button close = UiKit.button(this, "Tutup overlay", 0xFFB4C1D6);
        close.setOnClickListener(v -> stopSelf()); panel.addView(close);
        root.addView(panel);
        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START; params.x = UiKit.dp(this, 16); params.y = UiKit.dp(this, 240);
        wm.addView(root, params);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            int initX, initY; float downX, downY; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x; initY = params.y;
                        downX = event.getRawX(); downY = event.getRawY(); moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(event.getRawX() - downX), dy = Math.round(event.getRawY() - downY);
                        if (Math.abs(dx) + Math.abs(dy) > UiKit.dp(OverlayService.this, 14)) moved = true;
                        if (moved) { params.x = Math.max(0, initX + dx); params.y = Math.max(0, initY + dy); resize(); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) { panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); updateDetail(); resize(); }
                        return true;
                    default: return false;
                }
            }
        });
        IntentFilter filter = new IntentFilter();
        filter.addAction(AnalysisState.ACTION_CHANGED); filter.addAction(AnalysisState.ACTION_CAPTURE); filter.addAction(AnalysisState.ACTION_FINISHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
        registered = true; updateDetail();
    }
    private void resize() { if (wm != null && root != null && params != null) { try { wm.updateViewLayout(root, params); } catch (Exception ignored) { } } }
    private void updateDetail() {
        if (detail == null) return;
        StringBuilder b = new StringBuilder(AnalysisState.status(this));
        String r = AnalysisState.result(this);
        if (!r.isEmpty()) {
            try {
                JSONObject j = new JSONObject(r);
                b.append("\n\n").append(j.optString("decision", "WAIT"));
                b.append(" • ").append(j.optString("confidence", "LOW"));
                b.append("\n").append(j.optString("summary", ""));
                b.append("\n\nSyarat: ").append(j.optString("entry_condition", "-"));
                b.append("\nRisiko: ").append(j.optJSONArray("risks") == null ? "-" : j.optJSONArray("risks").optString(0));
            } catch (Exception ignored) { }
        }
        detail.setText(b.toString());
        detail.setMaxWidth(UiKit.dp(this, 265));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public void onDestroy() {
        if (registered) { unregisterReceiver(receiver); registered = false; }
        if (wm != null && root != null) { try { wm.removeView(root); } catch (Exception ignored) {} }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
