package com.beiyetu.app.data;

import com.beiyetu.app.data.FirebaseRepo.Result;
import com.beiyetu.app.model.Crop;
import com.beiyetu.app.model.Market;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bulk import from CSV text: markets, farm locations, crops and prices. */
public class ImportRepo {

    public static class ImportResult {
        public final int imported;
        public final int skipped;
        public final List<String> errors;

        ImportResult(int imported, int skipped, List<String> errors) {
            this.imported = imported;
            this.skipped = skipped;
            this.errors = errors;
        }
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseRepo repo = FirebaseRepo.get();

    /** type = "prices", "markets", "origins" or "crops". */
    public void importCsv(String type, String text, Result<ImportResult> cb) {
        List<String> errors = new ArrayList<>();
        List<Map<String, String>> rows = Csv.parse(text, errors);
        if (rows.isEmpty()) {
            if (errors.isEmpty()) errors.add("No rows found. The first line must be the column names.");
            cb.onSuccess(new ImportResult(0, 0, errors));
            return;
        }
        int limit = "prices".equals(type) ? 200 : 400;
        if (rows.size() > limit) {
            cb.onError("Too many rows (" + rows.size() + "). Import at most " + limit + " at a time.");
            return;
        }
        switch (type) {
            case "markets":
            case "origins":
                importPlaces(type, rows, errors, cb);
                break;
            case "crops":
                importCrops(rows, errors, cb);
                break;
            case "prices":
                importPrices(rows, errors, cb);
                break;
            default:
                cb.onError("Unknown type.");
        }
    }

    // ------------------------------------------------------------------ markets and farm locations
    private void importPlaces(String collection, List<Map<String, String>> rows, List<String> errors,
                              Result<ImportResult> cb) {
        WriteBatch batch = db.batch();
        int ok = 0;
        for (Map<String, String> r : rows) {
            String line = r.getOrDefault("_line", "?");
            String name = r.getOrDefault("name", "");
            if (name.isEmpty()) { errors.add("Row " + line + ": name is missing"); continue; }
            Double lat = number(r.get("latitude"));
            Double lon = number(r.get("longitude"));
            if (lat == null || lon == null) { errors.add("Row " + line + ": latitude/longitude missing or not a number"); continue; }
            if (lat < -5.5 || lat > 5.5 || lon < 33 || lon > 42.5) {
                errors.add("Row " + line + ": coordinates are outside Kenya (are latitude and longitude swapped?)");
                continue;
            }
            String idCell = r.getOrDefault("id", "");
            String id = idCell.isEmpty() ? AdminRepo.slug(name) : AdminRepo.slug(idCell);
            Map<String, Object> m = new HashMap<>();
            m.put("name", name);
            m.put("county", r.getOrDefault("county", ""));
            m.put("location", new GeoPoint(lat, lon));
            m.put("active", true);
            batch.set(db.collection(collection).document(id), m, SetOptions.merge());
            ok++;
        }
        final int imported = ok;
        if (imported == 0) {
            cb.onSuccess(new ImportResult(0, rows.size(), errors));
            return;
        }
        batch.commit()
                .addOnSuccessListener(v -> cb.onSuccess(new ImportResult(imported, rows.size() - imported, errors)))
                .addOnFailureListener(e -> cb.onError(msg(e)));
    }

    // ------------------------------------------------------------------ crops
    private void importCrops(List<Map<String, String>> rows, List<String> errors, Result<ImportResult> cb) {
        db.collection("crops").get().addOnSuccessListener(snap -> {
            Set<String> existing = new HashSet<>();
            for (DocumentSnapshot d : snap.getDocuments()) existing.add(d.getId());
            int nextOrder = existing.size();

            WriteBatch batch = db.batch();
            int ok = 0;
            for (Map<String, String> r : rows) {
                String line = r.getOrDefault("_line", "?");
                String name = r.getOrDefault("name", "");
                if (name.isEmpty()) { errors.add("Row " + line + ": name is missing"); continue; }
                String idCell = r.getOrDefault("id", "");
                String id = idCell.isEmpty() ? AdminRepo.slug(name) : AdminRepo.slug(idCell);
                Map<String, Object> m = new HashMap<>();
                m.put("name", name);
                m.put("nameSw", r.getOrDefault("namesw", ""));
                String emoji = r.getOrDefault("emoji", "");
                m.put("emoji", emoji.isEmpty() ? "🌾" : emoji);
                m.put("active", true);
                if (!existing.contains(id)) {
                    m.put("sortOrder", nextOrder++);
                    existing.add(id);
                }
                batch.set(db.collection("crops").document(id), m, SetOptions.merge());
                ok++;
            }
            final int imported = ok;
            if (imported == 0) {
                cb.onSuccess(new ImportResult(0, rows.size(), errors));
                return;
            }
            batch.commit()
                    .addOnSuccessListener(v -> cb.onSuccess(new ImportResult(imported, rows.size() - imported, errors)))
                    .addOnFailureListener(e -> cb.onError(msg(e)));
        }).addOnFailureListener(e -> cb.onError(msg(e)));
    }

    // ------------------------------------------------------------------ prices (trusted, one after another)
    private void importPrices(List<Map<String, String>> rows, List<String> errors, Result<ImportResult> cb) {
        repo.loadMarkets(new Result<List<Market>>() {
            @Override
            public void onSuccess(List<Market> markets) {
                repo.loadCrops(new Result<List<Crop>>() {
                    @Override
                    public void onSuccess(List<Crop> crops) {
                        next(rows, 0, 0, markets, crops, errors, cb);
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    private void next(List<Map<String, String>> rows, int i, int ok, List<Market> markets, List<Crop> crops,
                      List<String> errors, Result<ImportResult> cb) {
        if (i >= rows.size()) {
            cb.onSuccess(new ImportResult(ok, rows.size() - ok, errors));
            return;
        }
        Map<String, String> r = rows.get(i);
        String line = r.getOrDefault("_line", "?");
        Market m = findMarket(markets, r.getOrDefault("market", ""));
        if (m == null) {
            errors.add("Row " + line + ": unknown market '" + r.getOrDefault("market", "") + "'");
            next(rows, i + 1, ok, markets, crops, errors, cb);
            return;
        }
        Crop c = findCrop(crops, r.getOrDefault("crop", ""));
        if (c == null) {
            errors.add("Row " + line + ": unknown crop '" + r.getOrDefault("crop", "") + "'");
            next(rows, i + 1, ok, markets, crops, errors, cb);
            return;
        }
        Double price = number(r.get("price"));
        if (price == null || price <= 0 || price > 100000) {
            errors.add("Row " + line + ": price must be a number above 0");
            next(rows, i + 1, ok, markets, crops, errors, cb);
            return;
        }
        repo.submitReport(m, c, price, true, new Result<FirebaseRepo.ReportOutcome>() {
            @Override
            public void onSuccess(FirebaseRepo.ReportOutcome o) {
                next(rows, i + 1, ok + 1, markets, crops, errors, cb);
            }

            @Override
            public void onError(String message) {
                errors.add("Row " + line + ": " + message);
                next(rows, i + 1, ok, markets, crops, errors, cb);
            }
        });
    }

    private static Market findMarket(List<Market> list, String key) {
        String k = key.trim();
        for (Market m : list) {
            if ((m.id != null && m.id.equalsIgnoreCase(k)) || (m.name != null && m.name.equalsIgnoreCase(k))) return m;
        }
        return null;
    }

    private static Crop findCrop(List<Crop> list, String key) {
        String k = key.trim();
        for (Crop c : list) {
            if ((c.id != null && c.id.equalsIgnoreCase(k)) || (c.name != null && c.name.equalsIgnoreCase(k))
                    || (c.nameSw != null && c.nameSw.equalsIgnoreCase(k))) return c;
        }
        return null;
    }

    private static Double number(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String msg(Exception e) {
        String m = e.getMessage() == null ? "Unknown error" : e.getMessage();
        if (m.toUpperCase(Locale.ROOT).contains("PERMISSION_DENIED")) {
            return "Only admins can import. Check you are signed in as an admin and that the latest rules are published.";
        }
        return m;
    }
}