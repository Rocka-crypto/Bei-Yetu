package com.beiyetu.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.beiyetu.app.R;
import com.beiyetu.app.data.Fmt;
import com.beiyetu.app.data.FirebaseRepo;
import com.beiyetu.app.model.Crop;
import com.beiyetu.app.model.CurrentPrice;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/** Live prices for the chosen crop. Updates by itself when anyone reports a new price. */
public class PricesFragment extends Fragment {

    private final FirebaseRepo repo = FirebaseRepo.get();
    private ListenerRegistration registration;
    private PriceAdapter adapter;
    private ChipGroup chips;
    private TextView empty, highValue, highWhere, avgValue, avgSub, lowValue, lowWhere;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_prices, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        chips = v.findViewById(R.id.chipCrops);
        empty = v.findViewById(R.id.empty);
        highValue = v.findViewById(R.id.statHighValue);
        highWhere = v.findViewById(R.id.statHighWhere);
        avgValue = v.findViewById(R.id.statAvgValue);
        avgSub = v.findViewById(R.id.statAvgSub);
        lowValue = v.findViewById(R.id.statLowValue);
        lowWhere = v.findViewById(R.id.statLowWhere);

        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PriceAdapter();
        list.setAdapter(adapter);

        empty.setVisibility(View.VISIBLE);
        repo.whenReady(() -> {
            if (getView() == null) return;
            if (repo.getStartupError() != null) {
                Toast.makeText(requireContext(), repo.getStartupError(), Toast.LENGTH_LONG).show();
            }
            repo.loadCrops(new FirebaseRepo.Result<List<Crop>>() {
                @Override
                public void onSuccess(List<Crop> crops) {
                    if (getView() == null) return;
                    buildChips(crops);
                }

                @Override
                public void onError(String message) {
                    if (getView() == null) return;
                    empty.setText(message);
                }
            });
        });
    }

    private void buildChips(List<Crop> crops) {
        chips.removeAllViews();
        if (crops.isEmpty()) {
            empty.setText("No crops found in the database yet.");
            return;
        }
        for (Crop c : crops) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(c.label());
            chip.setTextSize(16);
            chip.setCheckable(true);
            chip.setTag(c);
            chips.addView(chip);
        }
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = group.findViewById(ids.get(0));
            if (chip != null) select((Crop) chip.getTag());
        });
        ((Chip) chips.getChildAt(0)).setChecked(true);
    }

    private void select(Crop crop) {
        if (registration != null) registration.remove();
        empty.setText("Loading prices...");
        empty.setVisibility(View.VISIBLE);
        registration = repo.listenPrices(crop.id, new FirebaseRepo.Result<List<CurrentPrice>>() {
            @Override
            public void onSuccess(List<CurrentPrice> prices) {
                if (getView() == null) return;
                render(prices, crop);
            }

            @Override
            public void onError(String message) {
                if (getView() == null) return;
                empty.setText(message);
                empty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void render(List<CurrentPrice> prices, Crop crop) {
        adapter.submit(prices);
        if (prices.isEmpty()) {
            empty.setText("No prices for " + crop.name + " yet. Be the first to report one!");
            empty.setVisibility(View.VISIBLE);
            highValue.setText("-"); highWhere.setText("");
            avgValue.setText("-"); avgSub.setText("");
            lowValue.setText("-"); lowWhere.setText("");
            return;
        }
        empty.setVisibility(View.GONE);
        CurrentPrice best = prices.get(0);
        CurrentPrice low = prices.get(prices.size() - 1);
        double sum = 0;
        for (CurrentPrice p : prices) sum += p.pricePerKg;

        highValue.setText("KES " + Fmt.money(best.pricePerKg));
        highWhere.setText(best.marketName);
        avgValue.setText("KES " + Fmt.money(sum / prices.size()));
        avgSub.setText(prices.size() + " markets");
        lowValue.setText("KES " + Fmt.money(low.pricePerKg));
        lowWhere.setText(low.marketName);
    }

    @Override
    public void onDestroyView() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        super.onDestroyView();
    }
}