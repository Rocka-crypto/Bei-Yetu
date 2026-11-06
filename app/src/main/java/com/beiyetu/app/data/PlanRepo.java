package com.beiyetu.app.data;

import com.beiyetu.app.data.FirebaseRepo.Result;
import com.beiyetu.app.model.Crop;
import com.beiyetu.app.model.Origin;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/** Planting plans: what farmers intend to plant. Used for the glut (oversupply) warning. */
public class PlanRepo {

    public static class PlanOutcome {
        public final double totalAcres;
        public final double threshold;
        public final boolean glut;

        PlanOutcome(double totalAcres, double threshold) {
            this.totalAcres = totalAcres;
            this.threshold = threshold;
            this.glut = totalAcres >= threshold;
        }
    }

    private static final double DEFAULT_THRESHOLD = 50;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    /** Saves the plan, then adds up all plans for the same crop, place and harvest month. */
    public void savePlan(Crop crop, Origin origin, double acres, int month, Result<PlanOutcome> cb) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            cb.onError("Not signed in yet. Check your internet connection and try again.");
            return;
        }
        if (acres <= 0 || acres > 1000) {
            cb.onError("Enter acres between 1 and 1,000.");
            return;
        }

        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi"));
        int year = month >= now.get(Calendar.MONTH) + 1 ? now.get(Calendar.YEAR) : now.get(Calendar.YEAR) + 1;

        Map<String, Object> plan = new HashMap<>();
        plan.put("farmerId", "app:" + user.getUid());
        plan.put("cropId", crop.id);
        plan.put("cropName", crop.name);
        plan.put("originId", origin.id);
        plan.put("originName", origin.name);
        plan.put("harvestMonth", month);
        plan.put("harvestYear", year);
        plan.put("acres", acres);
        plan.put("createdAt", FieldValue.serverTimestamp());
        plan.put("source", "app");

        db.collection("plantingIntentions").add(plan)
                .addOnSuccessListener(ref -> total(crop.id, origin.id, year, month, cb))
                .addOnFailureListener(e -> cb.onError(msg(e)));
    }

    private void total(String cropId, String originId, int year, int month, Result<PlanOutcome> cb) {
        db.collection("plantingIntentions")
                .whereEqualTo("cropId", cropId)
                .whereEqualTo("originId", originId)
                .whereEqualTo("harvestYear", year)
                .whereEqualTo("harvestMonth", month)
                .get()
                .addOnSuccessListener(snap -> {
                    double sum = 0;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Double a = d.getDouble("acres");
                        if (a != null) sum += a;
                    }
                    final double totalAcres = sum;
                    db.collection("settings").document("config").get()
                            .addOnSuccessListener(cfg -> {
                                Double t = cfg.exists() ? cfg.getDouble("glutAcresThreshold") : null;
                                cb.onSuccess(new PlanOutcome(totalAcres, t == null ? DEFAULT_THRESHOLD : t));
                            })
                            .addOnFailureListener(e -> cb.onSuccess(new PlanOutcome(totalAcres, DEFAULT_THRESHOLD)));
                })
                .addOnFailureListener(e -> cb.onError(msg(e)));
    }

    private static String msg(Exception e) {
        String m = e.getMessage() == null ? "Unknown error" : e.getMessage();
        if (m.contains("PERMISSION_DENIED")) {
            return "Firestore rules blocked this. Publish the latest firestore.rules in the Firebase console.";
        }
        return m;
    }
}