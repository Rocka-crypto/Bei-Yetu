package com.beiyetu.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.beiyetu.app.R;
import com.google.android.material.button.MaterialButtonToggleGroup;

/** Menu for the extra screens, plus the Light / Dark switch. */
public class MoreFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_more, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        v.findViewById(R.id.cardPlan).setOnClickListener(x -> Nav.open(this, new PlanFragment()));
        v.findViewById(R.id.cardPlanReport).setOnClickListener(x -> Nav.open(this, new PlanReportFragment()));
        v.findViewById(R.id.cardUssd).setOnClickListener(x -> Nav.open(this, new UssdHelpFragment()));
        v.findViewById(R.id.cardAdmin).setOnClickListener(x -> Nav.open(this, new AdminFragment()));

        // appearance: System / Light / Dark
        MaterialButtonToggleGroup group = v.findViewById(R.id.themeToggle);
        int mode = ThemeHelper.get(requireContext());
        group.check(mode == ThemeHelper.LIGHT ? R.id.btnThemeLight
                : mode == ThemeHelper.DARK ? R.id.btnThemeDark : R.id.btnThemeSystem);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) return;
            int chosen = checkedId == R.id.btnThemeLight ? ThemeHelper.LIGHT
                    : checkedId == R.id.btnThemeDark ? ThemeHelper.DARK : ThemeHelper.SYSTEM;
            if (chosen != ThemeHelper.get(requireContext())) {
                ThemeHelper.set(requireContext(), chosen);
            }
        });
    }
}