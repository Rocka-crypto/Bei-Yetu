package com.beiyetu.app.ui;

import androidx.fragment.app.Fragment;

import com.beiyetu.app.R;

/** Opens a screen on top of the current one (the Back button returns). */
final class Nav {
    private Nav() { }

    static void open(Fragment from, Fragment to) {
        from.requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, to)
                .addToBackStack(null)
                .commit();
    }
}