package com.javaagent.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Lightweight native UI primitives; no AndroidX or design-library dependency. */
public final class Ui {
    public static final int BACKGROUND = Color.rgb(9, 11, 18);
    public static final int SURFACE = Color.rgb(17, 21, 32);
    public static final int SURFACE_2 = Color.rgb(24, 29, 43);
    public static final int BORDER = Color.rgb(43, 49, 68);
    public static final int TEXT = Color.rgb(239, 241, 248);
    public static final int MUTED = Color.rgb(151, 158, 180);
    public static final int ACCENT = Color.rgb(155, 140, 255);
    public static final int CYAN = Color.rgb(91, 216, 214);
    public static final int ERROR = Color.rgb(255, 139, 148);

    private Ui() { }

    public static int dp(int value) {
        return Math.round(value * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String value, float size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    public static TextView title(Context context, String value, float size) {
        TextView view = text(context, value, size, TEXT);
        view.setTypeface(Typeface.create("sans", Typeface.BOLD));
        return view;
    }

    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(primary ? Color.rgb(15, 13, 30) : TEXT);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
        button.setBackground(round(primary ? ACCENT : SURFACE_2, 14, primary ? ACCENT : BORDER));
        button.setBackgroundTintList(null);
        return button;
    }

    public static EditText input(Context context, String hint, boolean multiline) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setHintTextColor(Color.rgb(105, 113, 137));
        input.setTextColor(TEXT);
        input.setTextSize(15);
        input.setSingleLine(!multiline);
        input.setGravity(multiline ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setBackground(round(SURFACE, 14, BORDER));
        input.setHighlightColor(ACCENT);
        if (!multiline) input.setInputType(InputType.TYPE_CLASS_TEXT);
        return input;
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = column(context);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(round(SURFACE, 18, BORDER));
        return card;
    }

    public static GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    public static GradientDrawable gradient(int start, int end, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {start, end});
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    public static View space(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    public static void margins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        ViewGroup.MarginLayoutParams params;
        if (raw instanceof ViewGroup.MarginLayoutParams) params = (ViewGroup.MarginLayoutParams) raw;
        else if (raw != null) params = new ViewGroup.MarginLayoutParams(raw);
        else params = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        view.setLayoutParams(params);
    }

    public static TextView badge(Context context, String label) {
        TextView badge = text(context, label, 11, CYAN);
        badge.setTypeface(Typeface.create("sans", Typeface.BOLD));
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(round(Color.rgb(18, 43, 48), 20, Color.rgb(36, 83, 85)));
        return badge;
    }

    public static void setTint(Button button, int color) {
        button.setBackgroundTintList(ColorStateList.valueOf(color));
    }
}
