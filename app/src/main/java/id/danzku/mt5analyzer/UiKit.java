package id.danzku.mt5analyzer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UiKit {
    public static final int BG = Color.rgb(11, 16, 32);
    public static final int CARD = Color.rgb(22, 31, 52);
    public static final int CARD2 = Color.rgb(30, 43, 68);
    public static final int TEXT = Color.rgb(234, 241, 252);
    public static final int SUB = Color.rgb(155, 173, 196);
    public static final int CYAN = Color.rgb(62, 205, 236);
    public static final int GREEN = Color.rgb(96, 224, 165);
    public static final int RED = Color.rgb(250, 110, 127);
    private UiKit() {}
    public static int dp(Context ctx, int val) { return (int)(ctx.getResources().getDisplayMetrics().density * val + 0.5f); }
    public static GradientDrawable bg(int color, int radius, Context ctx) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(ctx, radius)); return d;
    }
    public static TextView text(Context ctx, String content, int size, int color, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setText(content); tv.setTextSize(size); tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return tv;
    }
    public static Button button(Context ctx, String label, int bg) {
        Button b = new Button(ctx);
        b.setText(label); b.setTextColor(BG); b.setTextSize(14);
        b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(bg(bg, 13, ctx));
        b.setPadding(dp(ctx, 10), dp(ctx, 9), dp(ctx, 10), dp(ctx, 9));
        return b;
    }
    public static EditText input(Context ctx, String hint, boolean decimal) {
        EditText e = new EditText(ctx); e.setTextSize(16); e.setSingleLine(true);
        e.setTextColor(TEXT); e.setHintTextColor(SUB); e.setHint(hint);
        e.setBackground(bg(CARD2, 10, ctx));
        e.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12));
        if (decimal) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }
    public static LinearLayout vertical(Context ctx) { LinearLayout l = new LinearLayout(ctx); l.setOrientation(LinearLayout.VERTICAL); return l; }
    public static void pad(View view, Context ctx, int n) { view.setPadding(dp(ctx,n),dp(ctx,n),dp(ctx,n),dp(ctx,n)); }
}
