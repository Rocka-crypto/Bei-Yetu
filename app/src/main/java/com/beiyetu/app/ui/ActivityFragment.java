package com.beiyetu.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.beiyetu.app.R;
import com.beiyetu.app.data.FirebaseRepo;
import com.beiyetu.app.model.PriceReport;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/** Live feed of the latest reports, with their verification status. */
public class ActivityFragment extends Fragment {

    private final FirebaseRepo repo = FirebaseRepo.get();
    private ListenerRegistration registration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_activity, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        RecyclerView list = v.findViewById(R.id.list);
        TextView empty = v.findViewById(R.id.empty);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        ReportAdapter adapter = new ReportAdapter();
        list.setAdapter(adapter);

        repo.whenReady(() -> {
            if (getView() == null) return;
            registration = repo.listenRecentReports(new FirebaseRepo.Result<List<PriceReport>>() {
                @Override
                public void onSuccess(List<PriceReport> reports) {
                    if (getView() == null) return;
                    adapter.submit(reports);
                    empty.setVisibility(reports.isEmpty() ? View.VISIBLE : View.GONE);
                }

                @Override
                public void onError(String message) {
                    if (getView() == null) return;
                    empty.setText(message);
                    empty.setVisibility(View.VISIBLE);
                }
            });
        });
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