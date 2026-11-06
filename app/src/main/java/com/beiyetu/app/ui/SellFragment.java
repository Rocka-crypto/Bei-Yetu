package com.beiyetu.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.beiyetu.app.R;
import com.beiyetu.app.data.Fmt;
import com.beiyetu.app.data.FirebaseRepo;
import com.beiyetu.app.model.Advice;
import com.beiyetu.app.model.Crop;
import com.beiyetu.app.model.Origin;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** "Where should I sell?": ranks markets by income after transport costs. */
public class SellFragment extends Fragment {

    private final FirebaseRepo repo = FirebaseRepo.get();
    private final List<Crop> crops = new ArrayList<>();
    private final List<Origin> origins = new ArrayList<>();
    private Spinner spCrop, spOrigin;
    private EditText etQty;
    private Button btn;
    private TextView summary;
    private LinearLayout results;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_sell, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        spCrop = v.findViewById(R.id.spCrop);
        spOrigin = v.findViewById(R.id.spOrigin);
        etQty = v.findViewById(R.id.etQty);
        btn = v.findViewById(R.id.btnCompare);
        summary = v.findViewById(R.id.summary);
        results = v.findViewById(R.id.results);

        btn.setOnClickListener(x -> compare());

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

    private void compare() {
        if (crops.isEmpty() || origins.isEmpty()) {
            toast("Still loading. Please try again in a moment.");
            return;
        }
        double qty;
        try {
            qty = Double.parseDouble(etQty.getText().toString().trim());
        } catch (NumberFormatException e) {
            qty = -1;
        }
        if (qty <= 0 || qty > 100000) {
            toast("Enter a quantity between 1 and 100,000 kg.");
            return;
        }
        final double quantity = qty;
        Crop crop = crops.get(spCrop.getSelectedItemPosition());
        Origin origin = origins.get(spOrigin.getSelectedItemPosition());

        btn.setEnabled(false);
        repo.loadAdvice(crop, origin, quantity, new FirebaseRepo.Result<List<Advice>>() {
            @Override
            public void onSuccess(List<Advice> list) {
                if (getView() == null) return;
                btn.setEnabled(true);
                show(list, crop, origin, quantity);
            }

            @Override
            public void onError(String message) {
                if (getView() == null) return;
                btn.setEnabled(true);
                toast(message);
            }
        });
    }

    private void show(List<Advice> list, Crop crop, Origin origin, double qty) {
        results.removeAllViews();
        if (list.isEmpty()) {
            summary.setVisibility(View.VISIBLE);
            summary.setText("No prices for " + crop.name + " yet, so we cannot compare markets.");
            return;
        }
        Advice best = list.get(0);
        Advice worst = list.get(list.size() - 1);
        String tip = list.size() > 1
                ? " Choosing " + best.marketName + " instead of " + worst.marketName
                  + " earns you about KES " + Fmt.money(best.netIncome - worst.netIncome) + " more."
                : "";
        summary.setVisibility(View.VISIBLE);
        summary.setText("Selling " + Fmt.money(qty) + " kg of " + crop.name + " from " + origin.name + "." + tip);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < list.size(); i++) {
            Advice a = list.get(i);
            boolean isBest = i == 0;
            View item = inflater.inflate(R.layout.item_advice, results, false);
            MaterialCardView card = item.findViewById(R.id.card);
            TextView rank = item.findViewById(R.id.rank);
            rank.setText(String.valueOf(i + 1));
            rank.setBackgroundResource(isBest ? R.drawable.bg_rank_best : R.drawable.bg_rank);
            rank.setTextColor(ContextCompat.getColor(requireContext(), isBest ? R.color.white : R.color.ink));
            ((TextView) item.findViewById(R.id.name)).setText(a.marketName);
            item.findViewById(R.id.badge).setVisibility(isBest ? View.VISIBLE : View.GONE);
            ((TextView) item.findViewById(R.id.meta)).setText(
                    "KES " + Fmt.money(a.pricePerKg) + "/kg · " + Fmt.money(a.distanceKm) + " km\n"
                            + "Transport KES " + Fmt.money(a.transportCost));
            ((TextView) item.findViewById(R.id.net)).setText("KES " + Fmt.money(a.netIncome));
            if (isBest) {
                card.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.green_500));
            }
            results.addView(item);
        }
    }

    private void toast(String m) {
        if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_LONG).show();
    }
}