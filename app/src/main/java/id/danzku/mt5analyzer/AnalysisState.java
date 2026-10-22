package id.danzku.mt5analyzer;

import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

/** Local state bridge shared by Activity, foreground capture service, floating overlay. */
public final class AnalysisState {
    private static final String PREF = "analysis_state";
    public static final String ACTION_CHANGED = "id.danzku.mt5analyzer.STATE_CHANGED";
    public static final String ACTION_CAPTURE = "id.danzku.mt5analyzer.CAPTURE_STARTED";
    public static final String ACTION_FINISHED = "id.danzku.mt5analyzer.CAPTURE_FINISHED";
    private AnalysisState() {}

    public static void status(Context ctx, String msg) {
        ctx.getSharedPreferences(PREF, 0).edit().putString("status", msg).apply();
        broadcast(ctx, ACTION_CHANGED);
    }
    public static void result(Context ctx, JSONObject answer, Snapshot snapshot) {
        ctx.getSharedPreferences(PREF, 0).edit()
            .putString("status", "Analisis selesai")
            .putString("result", answer.toString())
            .putString("snapshot", snapshot == null ? "" : safeJson(snapshot))
            .putLong("updated", System.currentTimeMillis()).apply();
        broadcast(ctx, ACTION_CHANGED);
    }
    public static void snapshot(Context ctx, Snapshot snap) {
        ctx.getSharedPreferences(PREF, 0).edit().putString("snapshot", safeJson(snap)).apply();
        broadcast(ctx, ACTION_CHANGED);
    }
    public static String status(Context ctx) { return ctx.getSharedPreferences(PREF, 0).getString("status", "Siap analisis M5"); }
    public static String result(Context ctx) { return ctx.getSharedPreferences(PREF, 0).getString("result", ""); }
    public static String snapshot(Context ctx) { return ctx.getSharedPreferences(PREF, 0).getString("snapshot", ""); }
    public static void broadcast(Context ctx, String action) {
        ctx.sendBroadcast(new Intent(action).setPackage(ctx.getPackageName()));
    }
    private static String safeJson(Snapshot snapshot) { try { return snapshot.toJson().toString(); } catch (Exception ex) { return ""; } }
}
