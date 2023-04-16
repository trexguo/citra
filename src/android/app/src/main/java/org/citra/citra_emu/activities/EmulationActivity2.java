package org.citra.citra_emu.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import org.citra.citra_emu.R;
import org.citra.citra_emu.ui.main.MainActivity;

public class EmulationActivity2 extends AppCompatActivity {
    /** A {@link BroadcastReceiver} to receive action for finish from MainActivity. */
    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg, Intent intent) {
            String action = intent.getAction();
            if (MainActivity.ACTION_FINISH_MAIN2ACTIVITY.equals(action)) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emulation2);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mBroadcastReceiver, new IntentFilter(MainActivity.ACTION_FINISH_MAIN2ACTIVITY));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mBroadcastReceiver);
    }
}