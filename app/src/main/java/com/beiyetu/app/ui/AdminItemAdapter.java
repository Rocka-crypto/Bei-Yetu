package com.beiyetu.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.beiyetu.app.R;
import com.beiyetu.app.model.AdminItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** List rows for markets, farm locations and crops in the admin screens. */
public class AdminItemAdapter extends RecyclerView.Adapter<AdminItemAdapter.VH> {

    public interface Listener {
        void onClick(AdminItem item);
    }

    private final List<AdminItem> items = new ArrayList<>();
    private final String type;
    private final Listener listener;

    public AdminItemAdapter(String type, Listener listener) {
        this.type = type;
        this.listener = listener;
    }

    public void submit(List<AdminItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AdminItem it = items.get(position);
        boolean crop = "crops".equals(type);
        if (crop) {
            h.title.setText((it.emoji == null ? "" : it.emoji + " ") + it.name);
            h.subtitle.setText(it.nameSw == null || it.nameSw.isEmpty()
                    ? "No Kiswahili name" : "Kiswahili: " + it.nameSw);
        } else {
            h.title.setText(it.name);
            h.subtitle.setText(it.county + " · " + String.format(Locale.US, "%.4f, %.4f", it.latitude, it.longitude));
        }
        h.hidden.setVisibility(!it.active && !"origins".equals(type) ? View.VISIBLE : View.GONE);
        h.itemView.setOnClickListener(x -> listener.onClick(it));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, subtitle, hidden;

        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            hidden = v.findViewById(R.id.hidden);
        }
    }
}