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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.beiyetu.app.R;
import com.beiyetu.app.data.FirebaseRepo;
import com.beiyetu.app.data.ImportRepo;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Admin: add many rows at once from a CSV file or pasted text. */
public class ImportFragment extends Fragment {

    private static final String[] TYPES = {"prices", "markets", "origins", "crops"};
    private static final String[] LABELS = {"Prices", "Markets", "Farm locations", "Crops"};
    private static final String[] EXAMPLES = {
            "market,crop,price\nWakulima Nairobi,Tomatoes,85\nKibuye Kisumu,Tomatoes,98",
            "name,county,latitude,longitude\nNyeri Main Market,Nyeri,-0.4201,36.9476\nThika Town Market,Kiambu,-1.0332,37.0693",
            "name,county,latitude,longitude\nNaivasha,Nakuru,-0.7172,36.4310\nMeru,Meru,0.0470,37.6559",
            "name,nameSw,emoji\nOnions,Vitunguu,🧅\nMaize,Mahindi,🌽"
    };
    private static final String[] HELP = {
            "Columns: market, crop, price. Market and crop can be the id or the name as shown in the app. Prices are trusted.",
            "Columns: name, county, latitude, longitude (optional: id). Coordinates must be inside Kenya.",
            "Columns: name, county, latitude, longitude (optional: id).",
            "Columns: name, nameSw (Kiswahili), emoji (optional: id)."
    };

    private final ImportRepo importer = new ImportRepo();
    private Spinner spType;
    private EditText etCsv;
    private TextView tvHelp, tvResult;
    private Button btnImport;

    private final ActivityResultLauncher<String> picker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || getView() == null) return;
                try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append('\n');
                    etCsv.setText(sb.toString());
                } catch (Exception e) {
                    toast("Could not read the file: " + e.getMessage());
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_import, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        spType = v.findViewById(R.id.spType);
        etCsv = v.findViewById(R.id.etCsv);
        tvHelp = v.findViewById(R.id.tvHelp);
        tvResult = v.findViewById(R.id.tvResult);
        btnImport = v.findViewById(R.id.btnImport);

        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item,
                Arrays.asList(LABELS));
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(a);
        spType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                tvHelp.setText(HELP[position]);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        tvHelp.setText(HELP[0]);

        v.findViewById(R.id.btnPick).setOnClickListener(x -> picker.launch("*/*"));
        v.findViewById(R.id.btnExample).setOnClickListener(x -> etCsv.setText(EXAMPLES[spType.getSelectedItemPosition()]));
        btnImport.setOnClickListener(x -> run());
    }

    private void run() {
        String text = etCsv.getText().toString();
        if (text.trim().isEmpty()) {
            toast("Choose a file or paste some CSV first.");
            return;
        }
        btnImport.setEnabled(false);
        btnImport.setText("Importing...");
        importer.importCsv(TYPES[spType.getSelectedItemPosition()], text, new FirebaseRepo.Result<ImportRepo.ImportResult>() {
            @Override
            public void onSuccess(ImportRepo.ImportResult r) {
                if (getView() == null) return;
                done();
                StringBuilder sb = new StringBuilder();
                sb.append(r.imported > 0 ? "✅ Saved " + r.imported : "⚠️ Nothing saved");
                if (r.skipped > 0) sb.append(" · skipped ").append(r.skipped);
                List<String> errs = r.errors;
                for (int i = 0; i < Math.min(10, errs.size()); i++) sb.append("\n• ").append(errs.get(i));
                if (errs.size() > 10) sb.append("\n• and ").append(errs.size() - 10).append(" more");
                boolean clean = r.skipped == 0 && r.imported > 0;
                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText(sb.toString());
                tvResult.setBackgroundResource(clean ? R.drawable.bg_chip_ok : R.drawable.bg_chip_warn);
                tvResult.setTextColor(ContextCompat.getColor(requireContext(), clean ? R.color.brand_text : R.color.red));
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
        btnImport.setEnabled(true);
        btnImport.setText("Import");
    }

    private void toast(String m) {
        if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_LONG).show();
    }
}