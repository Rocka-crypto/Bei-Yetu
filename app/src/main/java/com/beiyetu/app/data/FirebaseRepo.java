package com.beiyetu.app.data;

import android.util.Log;

import com.beiyetu.app.model.Advice;
import com.beiyetu.app.model.Config;
import com.beiyetu.app.model.Crop;
import com.beiyetu.app.model.CurrentPrice;
import com.beiyetu.app.model.Market;
import com.beiyetu.app.model.Origin;
import com.beiyetu.app.model.PriceReport;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Single place that talks to Firebase. Fragments never touch Firestore directly. */
public class FirebaseRepo {

    public interface Result<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    public static class ReportOutcome {
        public final boolean accepted;
        public final String message;

        ReportOutcome(boolean accepted, String message) {
            this.accepted = accepted;
            this.message = message;
        }
    }

    private static final String TAG = "BeiYetu";
    private static FirebaseRepo instance;

    public static synchronized FirebaseRepo get() {
        if (instance == null) instance = new FirebaseRepo();
        return instance;
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final List<Runnable> waiting = new ArrayList<>();
    private boolean started;
    private boolean ready;
    private String startupError;
    private Config config;

    private FirebaseRepo() { }

    // ------------------------------------------------------------------
    // Start-up: anonymous sign-in, then seed demo data if the DB is empty
    // ------------------------------------------------------------------
    public void start() {
        if (started) return;
        started = true;
        if (auth.getCurrentUser() != null) {
            afterSignIn();
        } else {
            auth.signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    afterSignIn();
                } else {
                    startupError = "Could not sign in. Check your internet connection and that Anonymous sign-in is enabled in Firebase.";
                    Log.e(TAG, "Sign-in failed", task.getException());
                    markReady();
                }
            });
        }
    }

    private void afterSignIn() {
        db.collection("settings").document("config").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && !task.getResult().exists()) {
                Seeder.seed(db, auth.getCurrentUser().getUid()).addOnCompleteListener(t -> {
                    if (!t.isSuccessful()) {
                        startupError = friendly(t.getException());
                    }
                    markReady();
                });
            } else {
                markReady();
            }
        });
    }

    private void markReady() {
        ready = true;
        List<Runnable> copy = new ArrayList<>(waiting);
        waiting.clear();
        for (Runnable r : copy) r.run();
    }

    /** Runs the task once start-up (sign-in and seeding) has finished. */
    public void whenReady(Runnable r) {
        if (ready) r.run(); else waiting.add(r);
    }

    public String getStartupError() {
        return startupError;
    }

    /** Forget the cached settings so the next report or calculation reads them again. */
    public void clearConfigCache() {
        config = null;
    }

    // ------------------------------------------------------------------
    // Reference data
    // ------------------------------------------------------------------
    public void loadCrops(Result<List<Crop>> cb) {
        db.collection("crops").orderBy("sortOrder").get()
                .addOnSuccessListener(snap -> {
                    List<Crop> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Crop c = d.toObject(Crop.class);
                        if (c != null && c.active) list.add(c);
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(e -> cb.onError(friendly(e)));
    }

    public void loadMarkets(Result<List<Market>> cb) {
        db.collection("markets").orderBy("name").get()
                .addOnSuccessListener(snap -> {
                    List<Market> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Market m = d.toObject(Market.class);
                        if (m != null && m.active) list.add(m);
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(e -> cb.onError(friendly(e)));
    }

    public void loadOrigins(Result<List<Origin>> cb) {
        db.collection("origins").orderBy("name").get()
                .addOnSuccessListener(snap -> {
                    List<Origin> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Origin o = d.toObject(Origin.class);
                        if (o != null) list.add(o);
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(e -> cb.onError(friendly(e)));
    }

    private void loadConfig(Result<Config> cb) {
        if (config != null) {
            cb.onSuccess(config);
            return;
        }
        db.collection("settings").document("config").get()
                .addOnSuccessListener(d -> {
                    Config c = d.exists() ? d.toObject(Config.class) : null;
                    config = (c != null) ? c : new Config();
                    cb.onSuccess(config);
                })
                .addOnFailureListener(e -> cb.onError(friendly(e)));
    }

    // ------------------------------------------------------------------
    // Live prices and activity feed (real-time listeners)
    // ------------------------------------------------------------------
    public ListenerRegistration listenPrices(String cropId, Result<List<CurrentPrice>> cb) {
        return db.collection("currentPrices").whereEqualTo("cropId", cropId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        cb.onError(friendly(e));
                        return;
                    }
                    List<CurrentPrice> list = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            CurrentPrice p = d.toObject(CurrentPrice.class,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
                            if (p != null) list.add(p);
                        }
                    }
                    Collections.sort(list, (a, b) -> Double.compare(b.pricePerKg, a.pricePerKg));
                    cb.onSuccess(list);
                });
    }

    public ListenerRegistration listenRecentReports(Result<List<PriceReport>> cb) {
        return db.collection("priceReports")
                .orderBy("reportedAt", Query.Direction.DESCENDING).limit(20)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        cb.onError(friendly(e));
                        return;
                    }
                    List<PriceReport> list = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            PriceReport r = d.toObject(PriceReport.class,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
                            if (r != null) list.add(r);
                        }
                    }
                    cb.onSuccess(list);
                });
    }

    // ------------------------------------------------------------------
    // Where should I sell?
    // ------------------------------------------------------------------
    public void loadAdvice(Crop crop, Origin origin, double qtyKg, Result<List<Advice>> cb) {
        loadConfig(new Result<Config>() {
            @Override
            public void onSuccess(Config cfg) {
                db.collection("currentPrices").whereEqualTo("cropId", crop.id).get()
                        .addOnSuccessListener(snap -> {
                            List<Advice> list = new ArrayList<>();
                            for (DocumentSnapshot d : snap.getDocuments()) {
                                CurrentPrice p = d.toObject(CurrentPrice.class);
                                if (p == null || p.location == null || origin.location == null) continue;
                                double km = Geo.haversineKm(origin.location.getLatitude(), origin.location.getLongitude(),
                                        p.location.getLatitude(), p.location.getLongitude());
                                double gross = p.pricePerKg * qtyKg;
                                double transport = km * cfg.transportCostPerKgKm * qtyKg;
                                list.add(new Advice(p.marketName, p.county, p.pricePerKg, km,
                                        transport, gross, gross - transport));
                            }
                            Collections.sort(list, (a, b) -> Double.compare(b.netIncome, a.netIncome));
                            cb.onSuccess(list);
                        })
                        .addOnFailureListener(e -> cb.onError(friendly(e)));
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    // ------------------------------------------------------------------
    // Report a price (verification + write in one atomic batch)
    // ------------------------------------------------------------------
    public void submitReport(Market market, Crop crop, double price, Result<ReportOutcome> cb) {
        submitReport(market, crop, price, false, cb);
    }

    /** trusted = true (market agents and admins) skips the outlier check and marks the report source as "agent". */
    public void submitReport(Market market, Crop crop, double price, boolean trusted, Result<ReportOutcome> cb) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            cb.onError("Not signed in yet. Check your internet connection and try again.");
            return;
        }
        if (price <= 0 || price > 100000) {
            cb.onError("Enter a price between 1 and 100,000.");
            return;
        }

        loadConfig(new Result<Config>() {
            @Override
            public void onSuccess(Config cfg) {
                String pair = market.id + "_" + crop.id;
                Date since = new Date(System.currentTimeMillis() - cfg.reportWindowHours * 3600_000L);

                db.collection("priceReports")
                        .whereEqualTo("pairKey", pair)
                        .whereGreaterThan("reportedAt", since)
                        .get()
                        .addOnSuccessListener(snap -> {
                            // verified prices from the last N hours
                            List<Double> prices = new ArrayList<>();
                            for (DocumentSnapshot d : snap.getDocuments()) {
                                Boolean flagged = d.getBoolean("flagged");
                                Double p = d.getDouble("pricePerKg");
                                if (p != null && (flagged == null || !flagged)) prices.add(p);
                            }

                            boolean flagged = false;
                            String reason = null;
                            if (!trusted && prices.size() >= cfg.minReportsForVerification) {
                                double med = Geo.median(prices);
                                if (price < med * cfg.outlierLow || price > med * cfg.outlierHigh) {
                                    flagged = true;
                                    reason = "Outside the normal range around the median of KES " + Fmt.money(med);
                                }
                            }

                            WriteBatch batch = db.batch();

                            // 1) the report itself (never deleted: it is the audit trail)
                            Map<String, Object> report = new HashMap<>();
                            report.put("marketId", market.id);
                            report.put("marketName", market.name);
                            report.put("cropId", crop.id);
                            report.put("cropName", crop.name);
                            report.put("pairKey", pair);
                            report.put("pricePerKg", price);
                            report.put("reporterUid", user.getUid());
                            report.put("reportedAt", FieldValue.serverTimestamp());
                            report.put("flagged", flagged);
                            if (reason != null) report.put("flagReason", reason);
                            report.put("source", trusted ? "agent" : "app");
                            batch.set(db.collection("priceReports").document(), report);

                            // 2) reporter statistics (trust score = accepted / total, computed when read)
                            Map<String, Object> rep = new HashMap<>();
                            rep.put("totalReports", FieldValue.increment(1));
                            rep.put("acceptedReports", FieldValue.increment(flagged ? 0 : 1));
                            rep.put("lastReportAt", FieldValue.serverTimestamp());
                            DocumentReference reporterRef = db.collection("reporters").document(user.getUid());
                            batch.set(reporterRef, rep, SetOptions.merge());

                            // 3) update the headline price only when the report is accepted
                            if (!flagged) {
                                List<Double> all = new ArrayList<>(prices);
                                all.add(price);
                                Map<String, Object> cp = new HashMap<>();
                                cp.put("marketId", market.id);
                                cp.put("cropId", crop.id);
                                cp.put("marketName", market.name);
                                cp.put("county", market.county);
                                GeoPoint loc = market.location;
                                if (loc != null) cp.put("location", loc);
                                cp.put("pricePerKg", Geo.median(all));
                                cp.put("reportCount", all.size());
                                cp.put("updatedAt", FieldValue.serverTimestamp());
                                batch.set(db.collection("currentPrices").document(pair), cp, SetOptions.merge());
                            }

                            final boolean wasFlagged = flagged;
                            batch.commit()
                                    .addOnSuccessListener(v -> cb.onSuccess(wasFlagged
                                            ? new ReportOutcome(false, "That price looks unusual, so it was held for review.")
                                            : new ReportOutcome(true, "Thank you! Your price has been recorded.")))
                                    .addOnFailureListener(e -> cb.onError(friendly(e)));
                        })
                        .addOnFailureListener(e -> cb.onError(friendly(e)));
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    private static String friendly(Exception e) {
        String m = (e == null || e.getMessage() == null) ? "Unknown error" : e.getMessage();
        Log.e(TAG, m, e);
        if (m.contains("requires an index")) {
            // the error text contains a ready-made link that creates the index: show it in Logcat (filter: BeiYetu)
            java.util.regex.Matcher url = java.util.regex.Pattern
                    .compile("https://console\\.firebase\\.google\\.com\\S+").matcher(m);
            if (url.find()) {
                Log.e(TAG, "CREATE THIS INDEX (open the link, then press Create): " + url.group());
            }
            return "The database is still being set up (an index is missing or still building). Please try again in a few minutes.";
        }
        if (m.contains("PERMISSION_DENIED")) {
            return "Firestore rules blocked this. Publish firebase/firestore.rules in the Firebase console.";
        }
        return m;
    }
}