package com.beiyetu.app.ui;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.beiyetu.app.R;
import com.beiyetu.app.data.Fmt;
import com.beiyetu.app.model.PriceReport;

import java.util.ArrayList;
import java.util.List;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.VH> {

    private final List<PriceReport> items = new ArrayList<>();

    public void submit(List<PriceReport> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PriceReport r = items.get(position);
        String who;
        if ("seed".equals(r.source)) who = "Community";
        else if ("ussd".equals(r.source)) who = "USSD user";
        else if ("agent".equals(r.source)) who = "Market agent";
        else if ("web".equals(r.source)) who = "Web visitor";
        else who = "App user";
        String when = r.reportedAt == null ? "just now"
                : DateUtils.getRelativeTimeSpanString(r.reportedAt.getTime(),
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();

        h.title.setText(r.marketName);
        h.subtitle.setText(r.cropName + " · " + who + " · " + when);
        h.price.setText("KES " + Fmt.money(r.pricePerKg));
        h.status.setText(r.flagged ? "Held for review" : "Verified");
        h.status.setBackgroundResource(r.flagged ? R.drawable.bg_chip_warn : R.drawable.bg_chip_ok);
        h.status.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                r.flagged ? R.color.red : R.color.green_800));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, subtitle, price, status;

        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            price = v.findViewById(R.id.price);
            status = v.findViewById(R.id.status);
        }
    }
}