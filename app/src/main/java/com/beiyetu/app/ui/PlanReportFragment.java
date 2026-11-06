package com.beiyetu.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
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
import com.beiyetu.app.data.PlanReportRepo;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Planting report: what farmers plan to plant, where, and when it will be harvested. */
public class PlanReportFragment extends Fragment {

    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};

    /** All plans for the same crop, place and harvest month. */
    private static class Group {
        String cropName, originName;
        int year, month;
        double acres;
        final Set<String> farmers = new HashSet<>();
    }

    private final PlanReportRepo repo = new PlanReportRepo();
    private PlanReportRepo.Data data;
    private final List<String> cropIds = new ArrayList<>();    // index 0 = all crops
    private final List<String> placeIds = new ArrayList<>();   // index 0 = all places
    private Spinner spCrop, spPlace;
    private TextView tvTotal, tvPlans, tvFarmers, empty;
    private LinearLayout rows;
    private String shareText = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_plan_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        spCrop = v.findViewById(R.id.spCrop);
        spPlace = v.findViewById(R.id.spPlace);
        tvTotal = v.findViewById(R.id.tvTotalAcres);
        tvPlans = v.findViewById(R.id.tvPlans);
        tvFarmers = v.findViewById(R.id.tvFarmers);
        empty = v.findViewById(R.id.empty);
        rows = v.findViewById(R.id.rows);

        v.findViewById(R.id.btnShare).setOnClickListener(x -> share());

        empty.setText("Loading the planting plans...");
        empty.setVisibility(View.VISIBLE);

        FirebaseRepo.get().whenReady(() -> repo.load(new FirebaseRepo.Result<PlanReportRepo.Data>() {
            @Override
            public void onSuccess(PlanReportRepo.Data d) {
                if (getView() == null) return;
                data = d;
                setupFilters();
                render();
            }

            @Override
            public void onError(String message) {
                if (getView() == null) return;
                empty.setText(message);
                empty.setVisibility(View.VISIBLE);
            }
        }));
    }

    // ---------------------------------------------------------------- filters
    private void setupFilters() {
        Map<String, String> crops = new LinkedHashMap<>();
        Map<String, String> places = new LinkedHashMap<>();
        for (PlanReportRepo.Entry e : data.entries) {
            if (e.cropId != null) crops.put(e.cropId, e.cropName == null ? e.cropId : e.cropName);
            if (e.originId != null) places.put(e.originId, e.originName == null ? e.originId : e.originName);
        }
        fillFilter(spCrop, cropIds, crops, "All crops");
        fillFilter(spPlace, placeIds, places, "All places");

        AdapterView.OnItemSelectedListener l = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                render();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        spCrop.setOnItemSelectedListener(l);
        spPlace.setOnItemSelectedListener(l);
    }

    private void fillFilter(Spinner sp, List<String> ids, Map<String, String> items, String allLabel) {
        List<Map.Entry<String, String>> sorted = new ArrayList<>(items.entrySet());
        Collections.sort(sorted, (a, b) -> a.getValue().compareToIgnoreCase(b.getValue()));
        ids.clear();
        ids.add(null);
        List<String> labels = new ArrayList<>();
        labels.add(allLabel);
        for (Map.Entry<String, String> e : sorted) {
            ids.add(e.getKey());
            labels.add(e.getValue());
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
    }

    // ---------------------------------------------------------------- report
    private void render() {
        if (getView() == null || data == null) return;
        String cropId = cropIds.isEmpty() ? null : cropIds.get(Math.max(0, spCrop.getSelectedItemPosition()));
        String placeId = placeIds.isEmpty() ? null : placeIds.get(Math.max(0, spPlace.getSelectedItemPosition()));

        double total = 0;
        int plans = 0;
        Set<String> farmers = new HashSet<>();
        Map<String, Group> groups = new LinkedHashMap<>();

        for (PlanReportRepo.Entry e : data.entries) {
            if (cropId != null && !cropId.equals(e.cropId)) continue;
            if (placeId != null && !placeId.equals(e.originId)) continue;
            total += e.acres;
            plans++;
            if (e.farmerId != null) farmers.add(e.farmerId);

            String key = e.cropId + "|" + e.originId + "|" + e.year + "|" + e.month;
            Group g = groups.get(key);
            if (g == null) {
                g = new Group();
                g.cropName = e.cropName;
                g.originName = e.originName;
                g.year = e.year;
                g.month = e.month;
                groups.put(key, g);
            }
            g.acres += e.acres;
            if (e.farmerId != null) g.farmers.add(e.farmerId);
        }

        tvTotal.setText(Fmt.money(total));
        tvPlans.setText(String.valueOf(plans));
        tvFarmers.setText(String.valueOf(farmers.size()));

        List<Group> list = new ArrayList<>(groups.values());
        Collections.sort(list, (a, b) -> {
            int da = a.year * 12 + a.month;
            int db = b.year * 12 + b.month;
            if (da != db) return Integer.compare(da, db);
            return Double.compare(b.acres, a.acres);
        });

        rows.removeAllViews();
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText("No planting plans yet for upcoming harvests. Farmers can add them in More > Plan my planting, or by USSD.");

        double maxAcres = 1;
        for (Group g : list) maxAcres = Math.max(maxAcres, g.acres);

        StringBuilder share = new StringBuilder("Bei Yetu planting report (upcoming harvests)\n");
        share.append("Total: ").append(Fmt.money(total)).append(" acres from ").append(plans)
                .append(" plans by ").append(farmers.size()).append(" farmers\n");

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int lastKey = -1;
        for (Group g : list) {
            boolean risk = g.acres >= data.threshold;
            View item = inflater.inflate(R.layout.item_plan_row, rows, false);
            ((TextView) item.findViewById(R.id.title)).setText(g.cropName + " · " + g.originName);
            ((TextView) item.findViewById(R.id.acres)).setText(Fmt.money(g.acres) + " acres");
            ((TextView) item.findViewById(R.id.subtitle)).setText("Harvest: " + MONTHS[g.month - 1] + " " + g.year
                    + " · " + g.farmers.size() + (g.farmers.size() == 1 ? " farmer" : " farmers"));
            item.findViewById(R.id.risk).setVisibility(risk ? View.VISIBLE : View.GONE);
            LinearProgressIndicator bar = item.findViewById(R.id.bar);
            bar.setIndicatorColor(ContextCompat.getColor(requireContext(), risk ? R.color.red : R.color.bar_color));
            bar.setProgressCompat((int) Math.round(g.acres / maxAcres * 100), false);
            rows.addView(item);

            int key = g.year * 12 + g.month;
            if (key != lastKey) {
                share.append("\n").append(MONTHS[g.month - 1]).append(" ").append(g.year).append("\n");
                lastKey = key;
            }
            share.append("- ").append(g.cropName).append(", ").append(g.originName).append(": ")
                    .append(Fmt.money(g.acres)).append(" acres (").append(g.farmers.size()).append(" farmers)")
                    .append(risk ? "  [GLUT RISK]" : "").append("\n");
        }
        shareText = share.toString();
    }

    private void share() {
        if (shareText.isEmpty()) {
            Toast.makeText(requireContext(), "Nothing to share yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Bei Yetu planting report");
        i.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(i, "Share report"));
    }
}