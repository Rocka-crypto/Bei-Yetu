package com.beiyetu.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.beiyetu.app.R;

/** Explains how to use Bei Yetu from any phone with USSD, and opens the dialer with the code. */
public class UssdHelpFragment extends Fragment {

    /** Replace with the real code you get from Africa's Talking / Safaricom. */
    public static final String USSD_CODE = "*384*55#";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_ussd_help, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        ((TextView) v.findViewById(R.id.tvCode)).setText(USSD_CODE);
        v.findViewById(R.id.btnDial).setOnClickListener(x -> {
            // opens the phone's dialer with the code filled in; the user presses call
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(USSD_CODE)));
            startActivity(i);
        });
    }
}