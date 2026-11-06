package com.beiyetu.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.beiyetu.app.R;
import com.beiyetu.app.data.FirebaseRepo;
import com.beiyetu.app.data.Fmt;
import com.beiyetu.app.data.PlanRepo;
import com.beiyetu.app.model.Crop;
import com.beiyetu.app.model.Origin;

import java.util.ArrayList;
import java.util.List;

/** "Plan my planting": records the plan and warns if too many farmers plan the same crop. */
public class PlanFragment extends Fragment {

    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};

    private final FirebaseRepo repo = FirebaseRepo.get();
    private final PlanRepo plans = new PlanRepo();
    private final List<Crop> crops = new ArrayList<>();
    private final List<Origin> origins = new ArrayList<>();
    private Spinner spCrop, spOrigin, spMonth;
    private EditText etAcres;
    private Button btn;
    private TextView tvResult;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_plan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        spCrop = v.findViewById(R.id.spCrop);
        spOrigin = v.findViewById(R.id.spOrigin);
        spMonth = v.findViewById(R.id.spMonth);
        etAcres = v.findViewById(R.id.etAcres);
        btn = v.findViewById(R.id.btnSavePlan);
        tvResult = v.findViewById(R.id.tvResult);

        fill(spMonth, java.util.Arrays.asList(MONTHS));
        btn.setOnClickListener(x -> save());

        repo.whenReady(() -> {
            if (getView() == null) return;
            repo.loadCrops(new FirebaseRepo.Result<List<Crop>>() {
                @Override
                public void onSuccess(List<Crop> list) {
                    if (getView() == null) return;
                    crops.clear();
                    crops.addAll(list);
                    List<String> labels = new ArrayList<>();
                    for (Crop c : list) labels.add(c.label());
                    fill(spCrop, labels);
                }

                @Override
                public void onError(String message) {
                    toast(message);
                }
            });
            repo.loadOrigins(new FirebaseRepo.Result<List<Origin>>() {
                @Override
                public void onSuccess(List<Origin> list) {
                    if (getView() == null) return;
                    origins.clear();
                    origins.addAll(list);
                    List<String> labels = new ArrayList<>();
                    for (Origin o : list) labels.add(o.name);
                    fill(spOrigin, labels);
                }

                @Override
                public void onError(String message) {
                    toast(message);
                }
            });
        });
    }

    private void fill(Spinner sp, List<String> labels) {
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
    }

    private void save() {
        if (crops.isEmpty() || origins.isEmpty()) {
            toast("Still loading. Please try again in a moment.");
            return;
        }
        double acres;
        try {
            acres = Double.parseDouble(etAcres.getText().toString().trim());
        } catch (NumberFormatException e) {
            acres = -1;
        }
        if (acres <= 0) {
            toast("Enter how many acres you will plant.");
            return;
        }
        Crop crop = crops.get(spCrop.getSelectedItemPosition());
        Origin origin = origins.get(spOrigin.getSelectedItemPosition());
        int month = spMonth.getSelectedItemPosition() + 1;

        btn.setEnabled(false);
        btn.setText("Saving...");
        plans.savePlan(crop, origin, acres, month, new FirebaseRepo.Result<PlanRepo.PlanOutcome>() {
            @Override
            public void onSuccess(PlanRepo.PlanOutcome o) {
                if (getView() == null) return;
                done();
                String monthName = MONTHS[month - 1];
                String msg = o.glut
                        ? "⚠️ Warning: " + Fmt.money(o.totalAcres) + " acres of " + crop.name + " are planned in "
                          + origin.name + " for " + monthName + ". Prices may fall. Consider another crop."
                        : "✅ Thank you! Your plan is saved. Planned so far for " + crop.name + " in "
                          + origin.name + " (" + monthName + "): " + Fmt.money(o.totalAcres) + " acres.";
                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText(msg);
                tvResult.setBackgroundResource(o.glut ? R.drawable.bg_chip_warn : R.drawable.bg_chip_ok);
                tvResult.setTextColor(ContextCompat.getColor(requireContext(), o.glut ? R.color.red : R.color.green_800));
                etAcres.setText("");
            }

            @Override
            public void onError(String message) {
                if (getView() == null) return;
                done();
                toast(message);
            }
        });
    }

    private void done() {
        btn.setEnabled(true);
        btn.setText("Save my plan");
    }

    private void toast(String m) {
        if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_LONG).show();
    }
}