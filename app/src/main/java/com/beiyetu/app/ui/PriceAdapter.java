package com.beiyetu.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.beiyetu.app.R;
import com.beiyetu.app.data.Fmt;
import com.beiyetu.app.model.CurrentPrice;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class PriceAdapter extends RecyclerView.Adapter<PriceAdapter.VH> {

    private final List<CurrentPrice> items = new ArrayList<>();
    private double max = 1;

    public void submit(List<CurrentPrice> list) {
        items.clear();
        items.addAll(list);
        max = list.isEmpty() ? 1 : Math.max(1, list.get(0).pricePerKg);   // list is sorted high to low
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_price, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CurrentPrice p = items.get(position);
        boolean best = position == 0;
        h.name.setText(p.marketName);
        h.county.setText(p.county + " · " + p.reportCount + " reports");
        h.value.setText("KES " + Fmt.money(p.pricePerKg));
        h.badge.setVisibility(best ? View.VISIBLE : View.GONE);
        h.bar.setIndicatorColor(ContextCompat.getColor(h.itemView.getContext(),
                best ? R.color.gold : R.color.green_700));
        h.bar.setProgressCompat((int) Math.round(p.pricePerKg / max * 100), false);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, county, value, badge;
        final LinearProgressIndicator bar;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.name);
            county = v.findViewById(R.id.county);
            value = v.findViewById(R.id.value);
            badge = v.findViewById(R.id.badge);
            bar = v.findViewById(R.id.bar);
        }
    }
}