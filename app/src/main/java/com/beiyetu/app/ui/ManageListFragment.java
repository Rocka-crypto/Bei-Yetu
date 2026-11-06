package com.beiyetu.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.beiyetu.app.R;
import com.beiyetu.app.data.AdminRepo;
import com.beiyetu.app.data.FirebaseRepo;
import com.beiyetu.app.model.AdminItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

/** Add, edit, hide or delete markets, farm locations or crops. type = "markets", "origins" or "crops". */
public class ManageListFragment extends Fragment {

    private static final String ARG_TYPE = "type";

    public static ManageListFragment newInstance(String type) {
        ManageListFragment f = new ManageListFragment();
        Bundle b = new Bundle();
        b.putString(ARG_TYPE, type);
        f.setArguments(b);
        return f;
    }

    private final AdminRepo admin = AdminRepo.get();
    private final List<AdminItem> current = new ArrayList<>();
    private String type = "markets";
    private AdminItemAdapter adapter;
    private TextView empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_manage_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        Bundle args = getArguments();
        if (args != null) type = args.getString(ARG_TYPE, "markets");

        TextView title = v.findViewById(R.id.tvTitle);
        TextView sub = v.findViewById(R.id.tvSub);
        switch (type) {
            case "crops":
                title.setText("🌱 Crops");
                sub.setText("Tap a crop to edit it. Switch off \"Show in the app\" to hide it without losing its prices.");
                break;
            case "origins":
                title.setText("🚜 Farm locations");
                sub.setText("Places where farmers grow crops. Used to work out transport costs. Tap one to edit it.");
                break;
            default:
                title.setText("🏪 Markets");
                sub.setText("Tap a market to edit it. Switch off \"Show in the app\" to hide it without losing its prices.");
                break;
        }
        empty = v.findViewById(R.id.empty);

        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminItemAdapter(type, this::showDialog);
        list.setAdapter(adapter);

        v.findViewById(R.id.btnAdd).setOnClickListener(x -> showDialog(null));
        load();
    }

    private void load() {
        admin.listItems(type, new FirebaseRepo.Result<List<AdminItem>>() {
            @Override
            public void onSuccess(List<AdminItem> list) {
                if (getView() == null) return;
                current.clear();
                current.addAll(list);
                adapter.submit(list);
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

    // ---------------------------------------------------------------- add / edit dialog
    private void showDialog(@Nullable AdminItem existing) {
        final boolean crop = "crops".equals(type);
        View form = LayoutInflater.from(requireContext())
                .inflate(crop ? R.layout.dialog_crop : R.layout.dialog_place, null);

        final EditText etName = form.findViewById(R.id.etName);
        final MaterialSwitch swActive = form.findViewById(R.id.swActive);
        final EditText etCounty = crop ? null : form.<EditText>findViewById(R.id.etCounty);
        final EditText etLat = crop ? null : form.<EditText>findViewById(R.id.etLat);
        final EditText etLon = crop ? null : form.<EditText>findViewById(R.id.etLon);
        final EditText etSw = crop ? form.<EditText>findViewById(R.id.etSw) : null;
        final EditText etEmoji = crop ? form.<EditText>findViewById(R.id.etEmoji) : null;
        final EditText etOrder = crop ? form.<EditText>findViewById(R.id.etOrder) : null;

        if ("origins".equals(type)) swActive.setVisibility(View.GONE);

        if (existing != null) {
            etName.setText(existing.name);
            swActive.setChecked(existing.active);
            if (crop) {
                etSw.setText(existing.nameSw);
                etEmoji.setText(existing.emoji);
                etOrder.setText(String.valueOf(existing.sortOrder));
            } else {
                etCounty.setText(existing.county);
                etLat.setText(String.valueOf(existing.latitude));
                etLon.setText(String.valueOf(existing.longitude));
            }
        } else {
            swActive.setChecked(true);
            if (crop) etOrder.setText(String.valueOf(current.size()));
        }

        String noun = crop ? "crop" : "origins".equals(type) ? "farm location" : "market";
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle((existing == null ? "Add a " : "Edit ") + noun)
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);
        if (existing != null) b.setNeutralButton("Delete", null);
        AlertDialog dlg = b.create();

        dlg.setOnShowListener(d -> {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
                FirebaseRepo.Result<String> cb = new FirebaseRepo.Result<String>() {
                    @Override
                    public void onSuccess(String message) {
                        toast(message);
                        dlg.dismiss();
                        if (getView() != null) load();
                    }

                    @Override
                    public void onError(String message) {
                        toast(message);
                    }
                };
                String id = existing == null ? null : existing.id;
                String name = etName.getText().toString().trim();
                if (crop) {
                    int order = (int) num(etOrder, current.size());
                    admin.saveCrop(id, name, etSw.getText().toString().trim(),
                            etEmoji.getText().toString().trim(), order, swActive.isChecked(), cb);
                } else {
                    double lat = num(etLat, Double.NaN);
                    double lon = num(etLon, Double.NaN);
                    if (Double.isNaN(lat) || Double.isNaN(lon)) {
                        toast("Enter latitude and longitude as numbers.");
                        return;
                    }
                    admin.savePlace(type, id, name, etCounty.getText().toString().trim(),
                            lat, lon, swActive.isChecked(), cb);
                }
            });
            if (existing != null) {
                dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x -> confirmDelete(existing, dlg));
            }
        });
        dlg.show();
    }

    private void confirmDelete(AdminItem item, AlertDialog parent) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete " + item.name + "?")
                .setMessage("This removes it for good. Hiding it (switch off \"Show in the app\") is safer because old prices keep working.")
                .setPositiveButton("Delete", (d, w) -> admin.deleteItem(type, item.id, new FirebaseRepo.Result<String>() {
                    @Override
                    public void onSuccess(String message) {
                        toast(message);
                        parent.dismiss();
                        if (getView() != null) load();
                    }

                    @Override
                    public void onError(String message) {
                        toast(message);
                    }
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static double num(EditText e, double fallback) {
        try {
            return Double.parseDouble(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void toast(String m) {
        if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_LONG).show();
    }
}