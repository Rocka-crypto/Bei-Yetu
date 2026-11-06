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
import androidx.fragment.app.Fragment;

import com.beiyetu.app.R;
import com.beiyetu.app.data.AdminRepo;
import com.beiyetu.app.data.FirebaseRepo;
import com.beiyetu.app.model.Crop;
import com.beiyetu.app.model.Market;

import java.util.ArrayList;
import java.util.List;

/** Admin home: sign in, add a trusted price, and open the data-management screens. */
public class AdminFragment extends Fragment {

    private final FirebaseRepo repo = FirebaseRepo.get();
    private final AdminRepo admin = AdminRepo.get();
    private final List<Crop> crops = new ArrayList<>();
    private final List<Market> markets = new ArrayList<>();

    private View signInCard, toolsBox;
    private TextView tvSignedIn;
    private EditText etEmail, etPassword, etPrice;
    private Spinner spCrop, spMarket;
    private Button btnSignIn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_admin, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        signInCard = v.findViewById(R.id.signInCard);
        toolsBox = v.findViewById(R.id.toolsBox);
        tvSignedIn = v.findViewById(R.id.tvSignedIn);
        etEmail = v.findViewById(R.id.etEmail);
        etPassword = v.findViewById(R.id.etPassword);
        etPrice = v.findViewById(R.id.etPrice);
        spCrop = v.findViewById(R.id.spCrop);
        spMarket = v.findViewById(R.id.spMarket);
        btnSignIn = v.findViewById(R.id.btnSignIn);

        btnSignIn.setOnClickListener(x -> signIn());
        v.findViewById(R.id.btnSignOut).setOnClickListener(x -> admin.signOut(() -> {
            if (getView() != null) showTools(false);
        }));
        v.findViewById(R.id.btnSavePrice).setOnClickListener(x -> savePrice());

        v.findViewById(R.id.btnMarkets).setOnClickListener(x -> Nav.open(this, ManageListFragment.newInstance("markets")));
        v.findViewById(R.id.btnOrigins).setOnClickListener(x -> Nav.open(this, ManageListFragment.newInstance("origins")));
        v.findViewById(R.id.btnCrops).setOnClickListener(x -> Nav.open(this, ManageListFragment.newInstance("crops")));
        v.findViewById(R.id.btnSettings).setOnClickListener(x -> Nav.open(this, new SettingsFragment()));
        v.findViewById(R.id.btnFlagged).setOnClickListener(x -> Nav.open(this, new FlaggedFragment()));
        v.findViewById(R.id.btnImport).setOnClickListener(x -> Nav.open(this, new ImportFragment()));

        repo.whenReady(() -> admin.checkAdmin(new FirebaseRepo.Result<Boolean>() {
            @Override
            public void onSuccess(Boolean isAdmin) {
                if (getView() != null) showTools(isAdmin);
            }

            @Override
            public void onError(String message) {
                if (getView() != null) showTools(false);
            }
        }));
    }

    private void showTools(boolean isAdmin) {
        signInCard.setVisibility(isAdmin ? View.GONE : View.VISIBLE);
        toolsBox.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        if (isAdmin) {
            tvSignedIn.setText("Signed in as " + admin.currentEmail());
            loadLists();
        }
    }

    private void signIn() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            toast("Enter your email and password.");
            return;
        }
        btnSignIn.setEnabled(false);
        admin.signIn(email, password, new FirebaseRepo.Result<Boolean>() {
            @Override
            public void onSuccess(Boolean ok) {
                if (getView() == null) return;
                btnSignIn.setEnabled(true);
                etPassword.setText("");
                showTools(true);
            }

            @Override
            public void onError(String message) {
                if (getView() == null) return;
                btnSignIn.setEnabled(true);
                toast(message);
            }
        });
    }

    // ---------------------------------------------------------------- quick trusted price
    private void loadLists() {
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
    }

    private void fill(Spinner sp, List<String> labels) {
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
    }

    private void savePrice() {
        if (crops.isEmpty() || markets.isEmpty()) {
            toast("Still loading, or there are no markets or crops yet. Add some first.");
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
        repo.submitReport(market, crop, price, true, new FirebaseRepo.Result<FirebaseRepo.ReportOutcome>() {
            @Override
            public void onSuccess(FirebaseRepo.ReportOutcome o) {
                if (getView() == null) return;
                etPrice.setText("");
                toast(o.message);
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void toast(String m) {
        if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_LONG).show();
    }
}