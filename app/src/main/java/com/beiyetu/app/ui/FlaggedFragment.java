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
import com.beiyetu.app.data.AdminRepo;
import com.beiyetu.app.data.AdminRepo.FlaggedItem;
import com.beiyetu.app.data.FirebaseRepo;
import com.beiyetu.app.data.Fmt;

import java.util.ArrayList;
import java.util.List;

/** Admin: prices that looked unusual and are waiting for approval. */
public class FlaggedFragment extends Fragment {

    private final AdminRepo admin = AdminRepo.get();
    private Adapter adapter;
    private TextView empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_flagged, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        empty = v.findViewById(R.id.empty);
        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new Adapter();
        list.setAdapter(adapter);
        v.findViewById(R.id.btnRefresh).setOnClickListener(x -> load());
        load();
    }

    private void load() {
        admin.listFlagged(new FirebaseRepo.Result<List<FlaggedItem>>() {
            @Override
            public void onSuccess(List<FlaggedItem> list) {
                if (getView() == null) return;
                adapter.submit(list);
                empty.setText("Nothing to review.");
                empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                if (getView() == null) return;
                empty.setText(message);
                empty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void act(FlaggedItem f, boolean approve, View button) {
        button.setEnabled(false);
        admin.review(f, approve, new FirebaseRepo.Result<String>() {
            @Override
            public void onSuccess(String message) {
                toast(message);
                if (getView() != null) load();
            }

            @Override
            public void onError(String message) {
                toast(message);
                button.setEnabled(true);
            }
        });
    }

    private void toast(String m) {
        if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_LONG).show();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<FlaggedItem> items = new ArrayList<>();

        void submit(List<FlaggedItem> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_flagged, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            FlaggedItem f = items.get(position);
            h.title.setText(f.marketName + " · " + f.cropName);
            h.price.setText("KES " + Fmt.money(f.price));
            h.reason.setText((f.reason == null ? "" : f.reason + ". ") + "Source: " + (f.source == null ? "unknown" : f.source));
            h.approve.setEnabled(true);
            h.reject.setEnabled(true);
            h.approve.setOnClickListener(x -> act(f, true, x));
            h.reject.setOnClickListener(x -> act(f, false, x));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, price, reason;
            final View approve, reject;

            VH(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.title);
                price = v.findViewById(R.id.price);
                reason = v.findViewById(R.id.reason);
                approve = v.findViewById(R.id.btnApprove);
                reject = v.findViewById(R.id.btnReject);
            }
        }
    }
}