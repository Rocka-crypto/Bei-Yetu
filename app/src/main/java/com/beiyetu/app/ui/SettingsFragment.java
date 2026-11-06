package com.beiyetu.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.beiyetu.app.R;
import com.beiyetu.app.data.AdminRepo;
import com.beiyetu.app.data.FirebaseRepo;

import java.util.HashMap;
import java.util.Map;

/** Admin: the numbers that control transport costs, price checking and glut warnings. */
public class SettingsFragment extends Fragment {

    private final AdminRepo admin = AdminRepo.get();
    private EditText etTransport, etWindow, etLow, etHigh, etMin, etGlut;
    private Button btn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        etTransport = v.findViewById(R.id.etTransport);
        etWindow = v.findViewById(R.id.etWindow);
        etLow = v.findViewById(R.id.etLow);
        etHigh = v.findViewById(R.id.etHigh);
        etMin = v.findViewById(R.id.etMin);
        etGlut = v.findViewById(R.id.etGlut);
        btn = v.findViewById(R.id.btnSaveSettings);

        // defaults first, then the values stored in the database
        etTransport.setText("0.04");
        etWindow.setText("24");
        etLow.setText("0.5");
        etHigh.setText("1.5");
        etMin.setText("3");
        etGlut.setText("50");

        admin.loadSettings(new FirebaseRepo.Result<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> m) {
                if (getView() == null) return;
                show(etTransport, m.get("transportCostPerKgKm"));
                show(etWindow, m.get("reportWindowHours"));
                show(etLow, m.get("outlierLow"));
                show(etHigh, m.get("outlierHigh"));
                show(etMin, m.get("minReportsForVerification"));
                show(etGlut, m.get("glutAcresThreshold"));
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });

        btn.setOnClickListener(x -> save());
    }

    private static void show(EditText e, Object value) {
        if (!(value instanceof Number)) return;
        double d = ((Number) value).doubleValue();
        e.setText(d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d));
    }

    private static double num(EditText e) {
        try {
            return Double.parseDouble(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private void save() {
        double transport = num(etTransport);
        double window = num(etWindow);
        double low = num(etLow);
        double high = num(etHigh);
        double min = num(etMin);
        double glut = num(etGlut);

        if (Double.isNaN(transport) || transport < 0) { toast("Transport cost must be 0 or more."); return; }
        if (Double.isNaN(window) || window < 1 || window > 720) { toast("Price window must be between 1 and 720 hours."); return; }
        if (Double.isNaN(low) || low <= 0 || low >= 1) { toast("Lower limit must be between 0 and 1 (for example 0.5)."); return; }
        if (Double.isNaN(high) || high <= 1) { toast("Upper limit must be more than 1 (for example 1.5)."); return; }
        if (Double.isNaN(min) || min < 1) { toast("Minimum reports must be at least 1."); return; }
        if (Double.isNaN(glut) || glut < 1) { toast("Glut warning must be at least 1 acre."); return; }

        Map<String, Object> m = new HashMap<>();
        m.put("transportCostPerKgKm", transport);
        m.put("reportWindowHours", (long) window);
        m.put("outlierLow", low);
        m.put("outlierHigh", high);
        m.put("minReportsForVerification", (long) min);
        m.put("glutAcresThreshold", glut);

        btn.setEnabled(false);
        admin.saveSettings(m, new FirebaseRepo.Result<String>() {
            @Override
            public void onSuccess(String message) {
                if (getView() == null) return;
                btn.setEnabled(true);
                toast(message);
            }

            @Override
            public void onError(String message) {
                if (getView() == null) return;
                btn.setEnabled(true);
                toast(message);
            }
        });
    }

    private void toast(String m) {
        if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_LONG).show();
    }
}