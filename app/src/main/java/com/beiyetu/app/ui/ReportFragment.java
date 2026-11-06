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
import com.beiyetu.app.model.Crop;
import com.beiyetu.app.model.Market;

import java.util.ArrayList;
import java.util.List;

/** Lets a user report the price they see at a market. */
public class ReportFragment extends Fragment {

    private final FirebaseRepo repo = FirebaseRepo.get();
    private final List<Crop> crops = new ArrayList<>();
    private final List<Market> markets = new ArrayList<>();
    private Spinner spCrop, spMarket;
    private EditText etPrice;
    private Button btn;
    private TextView resultMsg;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        spCrop = v.findViewById(R.id.spCrop);
        spMarket = v.findViewById(R.id.spMarket);
        etPrice = v.findViewById(R.id.etPrice);
        btn = v.findViewById(R.id.btnSubmit);
        resultMsg = v.findViewById(R.id.resultMsg);

        btn.setOnClickListener(x -> submit());

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
            repo.loadMarkets(new FirebaseRepo.Result<List<Market>>() {
                @Override
                public void onSuccess(List<Market> list) {
                    if (getView() == null) return;
                    markets.clear();
                    markets.addAll(list);
                    List<String> labels = new ArrayList<>();
                    for (Market m : list) labels.add(m.name + " (" + m.county + ")");
                    fill(spMarket, labels);
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

    private void submit() {
        if (crops.isEmpty() || markets.isEmpty()) {
            toast("Still loading. Please try again in a moment.");
            return;
        }
        double price;
        try {
            price = Double.parseDouble(etPrice.getText().toString().trim());
        } catch (NumberFormatException e) {
            price = -1;
        }
        if (price <= 0) {
            toast("Enter the price per kg in KES.");
            return;
        }
        Crop crop = crops.get(spCrop.getSelectedItemPosition());
        Market market = markets.get(spMarket.getSelectedItemPosition());

        btn.setEnabled(false);
        btn.setText("Submitting...");
        repo.submitReport(market, crop, price, new FirebaseRepo.Result<FirebaseRepo.ReportOutcome>() {
            @Override
            public void onSuccess(FirebaseRepo.ReportOutcome o) {
                if (getView() == null) return;
                done();
                resultMsg.setVisibility(View.VISIBLE);
                resultMsg.setText(o.message);
                resultMsg.setBackgroundResource(o.accepted ? R.drawable.bg_chip_ok : R.drawable.bg_chip_warn);
                resultMsg.setTextColor(ContextCompat.getColor(requireContext(),
                        o.accepted ? R.color.green_800 : R.color.red));
                etPrice.setText("");
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
        btn.setText("Submit price");
    }

    private void toast(String m) {
        if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_LONG).show();
    }
}