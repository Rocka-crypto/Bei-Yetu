package com.beiyetu.app.data;

import com.beiyetu.app.data.FirebaseRepo.Result;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Reads the planting plans (from the app and from USSD) for the planting report. */
public class PlanReportRepo {

    /** One planting plan. */
    public static class Entry {
        public String farmerId, cropId, cropName, originId, originName;
        public int month, year;
        public double acres;
    }

    public static class Data {
        public final List<Entry> entries;
        public final double threshold;

        Data(List<Entry> entries, double threshold) {
            this.entries = entries;
            this.threshold = threshold;
        }
    }

    private static final double DEFAULT_THRESHOLD = 50;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /** Loads plans whose harvest month has not passed yet, plus the glut-warning threshold. */
    public void load(Result<Data> cb) {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi"));
        final int thisYear = now.get(Calendar.YEAR);
        final int thisMonth = now.get(Calendar.MONTH) + 1;

        db.collection("plantingIntentions")
                .whereGreaterThanOrEqualTo("harvestYear", thisYear)
                .limit(2000)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Entry> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Long y = d.getLong("harvestYear");
                        Long m = d.getLong("harvestMonth");
                        Double a = d.getDouble("acres");
                        if (y == null || m == null || a == null) continue;
                        if (y == thisYear && m < thisMonth) continue;   // harvest month already passed
                        Entry e = new Entry();
                        e.farmerId = d.getString("farmerId");
                        e.cropId = d.getString("cropId");
                        e.cropName = d.getString("cropName");
                        e.originId = d.getString("originId");
                        e.originName = d.getString("originName");
                        e.year = y.intValue();
                        e.month = m.intValue();
                        e.acres = a;
                        list.add(e);
                    }
                    db.collection("settings").document("config").get()
                            .addOnSuccessListener(cfg -> {
                                Double t = cfg.exists() ? cfg.getDouble("glutAcresThreshold") : null;
                                cb.onSuccess(new Data(list, t == null ? DEFAULT_THRESHOLD : t));
                            })
                            .addOnFailureListener(e -> cb.onSuccess(new Data(list, DEFAULT_THRESHOLD)));
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage() == null ? "Could not load the plans." : e.getMessage()));
    }
}