package com.beiyetu.app.data;

import java.util.Locale;

public final class Fmt {
    private Fmt() { }

    public static String money(double v) {
        return String.format(Locale.US, "%,.0f", v);
    }
}