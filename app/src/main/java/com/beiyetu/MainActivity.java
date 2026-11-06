package com.beiyetu.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.beiyetu.app.data.FirebaseRepo;
import com.beiyetu.app.ui.ActivityFragment;
import com.beiyetu.app.ui.MoreFragment;
import com.beiyetu.app.ui.PricesFragment;
import com.beiyetu.app.ui.ReportFragment;
import com.beiyetu.app.ui.SellFragment;
import com.beiyetu.app.ui.ThemeHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);   // System, Light or Dark, as chosen in More > Appearance
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseRepo.get().start();   // sign in anonymously + seed demo data on first run

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f;
            if (id == R.id.nav_prices) f = new PricesFragment();
            else if (id == R.id.nav_sell) f = new SellFragment();
            else if (id == R.id.nav_report) f = new ReportFragment();
            else if (id == R.id.nav_more) f = new MoreFragment();
            else f = new ActivityFragment();

            FragmentManager fm = getSupportFragmentManager();
            fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);   // leave any sub-screen of "More"
            fm.beginTransaction().replace(R.id.container, f).commit();
            return true;
        });

        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_prices);
        }
    }
}