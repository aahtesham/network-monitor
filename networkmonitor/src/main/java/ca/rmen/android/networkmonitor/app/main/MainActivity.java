/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2013 Benoit 'BoD' Lubek (BoD@JRAF.org)
 * Copyright (C) 2013-2015 Carmen Alvarez (c@rmen.ca)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.rmen.android.networkmonitor.app.main;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.SwitchPreferenceCompat;

import com.squareup.otto.Subscribe;

import ca.rmen.android.networkmonitor.Constants;
import ca.rmen.android.networkmonitor.R;
import ca.rmen.android.networkmonitor.app.bus.NetMonBus;
import ca.rmen.android.networkmonitor.app.dbops.backend.DBOpIntentService;
import ca.rmen.android.networkmonitor.app.dbops.ui.Share;
import ca.rmen.android.networkmonitor.app.dialog.ChoiceDialogFragment;
import ca.rmen.android.networkmonitor.app.dialog.ConfirmDialogFragment;
import ca.rmen.android.networkmonitor.app.dialog.DialogFragmentFactory;
import ca.rmen.android.networkmonitor.app.dialog.PreferenceDialog;
import ca.rmen.android.networkmonitor.app.prefs.NetMonPreferenceFragmentCompat;
import ca.rmen.android.networkmonitor.app.prefs.NetMonPreferences;
import ca.rmen.android.networkmonitor.app.service.NetMonService;
import ca.rmen.android.networkmonitor.app.speedtest.SpeedTestPreferences;
import ca.rmen.android.networkmonitor.util.Log;


public class MainActivity extends AppCompatActivity implements ConfirmDialogFragment.DialogButtonListener, ChoiceDialogFragment.DialogItemListener{
    private static final String TAG = Constants.TAG + MainActivity.class.getSimpleName();
    private GPSVerifier mGPSVerifier;
    private NetMonPreferenceFragmentCompat mPreferenceFragment;
    private static final String PREF_SHARE = "PREF_SHARE";
    private static final String PREF_CLEAR_LOG_FILE = "PREF_CLEAR_LOG_FILE";
    private static final int ID_ACTION_SHARE = 1;
    private static final int ID_ACTION_CLEAR = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreferenceFragment = NetMonPreferenceFragmentCompat.newInstance(R.xml.preferences);
        getSupportFragmentManager().
                beginTransaction().
                replace(android.R.id.content, mPreferenceFragment).
                commit();
        getSupportFragmentManager().executePendingTransactions();
        mGPSVerifier = new GPSVerifier(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setIcon(R.drawable.ic_launcher);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        if (NetMonPreferences.getInstance(this).isServiceEnabled()) startService(new Intent(MainActivity.this, NetMonService.class));
        // Use strict mode for monkey tests. We can't enable strict mode for normal use
        // because, when sharing (exporting), the mail app may read the attachment in
        // the main thread.
        if (ActivityManager.isUserAMonkey())
            StrictMode.setThreadPolicy(new ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyLog().penaltyDeath().build());

        mPreferenceFragment.findPreference(PREF_SHARE).setOnPreferenceClickListener(mOnPreferenceClickListener);
        mPreferenceFragment.findPreference(PREF_CLEAR_LOG_FILE).setOnPreferenceClickListener(mOnPreferenceClickListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
        NetMonBus.getBus().register(this);
    }

    @Override
    protected void onStop() {
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
        NetMonBus.getBus().unregister(this);
        super.onStop();
        mGPSVerifier.dismissGPSDialog();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            // Refresh the 'enabled' preference view
            boolean enabled = NetMonPreferences.getInstance(this).isServiceEnabled();
            ((SwitchPreferenceCompat) mPreferenceFragment.findPreference(NetMonPreferences.PREF_SERVICE_ENABLED)).setChecked(enabled);
        }
    }

    @Override
    public void onBackPressed() {
        Log.v(TAG, "onBackPressed");
        // Prevent the monkey from exiting the app, to maximize the time the monkey spends testing the app.
        if (ActivityManager.isUserAMonkey()) {
            Log.v(TAG, "Sorry, monkeys must stay in the cage");
            return;
        }
        super.onBackPressed();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onDBOperationStarted(NetMonBus.DBOperationStarted event) {
        Log.d(TAG, "onDBOperationStarted() called with " + "event = [" + event + "]");
        mPreferenceFragment.findPreference(PREF_SHARE).setEnabled(false);
        mPreferenceFragment.findPreference(PREF_SHARE).setSummary(event.name);
        mPreferenceFragment.findPreference(PREF_CLEAR_LOG_FILE).setEnabled(false);
        mPreferenceFragment.findPreference(PREF_CLEAR_LOG_FILE).setSummary(event.name);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onDBOperationEnded(NetMonBus.DBOperationEnded event) {
        Log.d(TAG, "onDBOperationEnded() called with " + "event = [" + event + "]");
        mPreferenceFragment.findPreference(PREF_SHARE).setEnabled(true);
        mPreferenceFragment.findPreference(PREF_SHARE).setSummary("");
        mPreferenceFragment.findPreference(PREF_CLEAR_LOG_FILE).setEnabled(true);
        mPreferenceFragment.findPreference(PREF_CLEAR_LOG_FILE).setSummary(null);
    }

    private final OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            NetMonPreferences prefs = NetMonPreferences.getInstance(MainActivity.this);
            if (NetMonPreferences.PREF_SERVICE_ENABLED.equals(key)) {
                if (sharedPreferences.getBoolean(NetMonPreferences.PREF_SERVICE_ENABLED, NetMonPreferences.PREF_SERVICE_ENABLED_DEFAULT)) {
                    mGPSVerifier.verifyGPS();
                    startService(new Intent(MainActivity.this, NetMonService.class));
                }
            } else if (NetMonPreferences.PREF_UPDATE_INTERVAL.equals(key)) {
                if (prefs.isFastPollingEnabled()) {
                    prefs.setConnectionTestEnabled(false);
                    if (prefs.getDBRecordCount() < 0) prefs.setDBRecordCount(10000);
                    SpeedTestPreferences.getInstance(MainActivity.this).setEnabled(false);
                    DialogFragmentFactory.showWarningDialog(MainActivity.this, getString(R.string.warning_fast_polling_title),
                            getString(R.string.warning_fast_polling_message));
                }
            }
        }
    };


    @Override
    public void onOkClicked(int actionId, Bundle extras) {
        if (actionId == ID_ACTION_CLEAR) {
            Log.v(TAG, "Clicked ok to clear log");
            DBOpIntentService.startActionPurge(this, 0);
        }
    }

    @Override
    public void onCancelClicked(int actionId, Bundle extras) {

    }

    @Override
    public void onItemSelected(int actionId, CharSequence[] choices, int which) {
        // The user picked a file format to export.
        if (actionId == ID_ACTION_SHARE) {
            String[] exportChoices = getResources().getStringArray(R.array.export_choices);
            String selectedShareFormat = exportChoices[which];
            Share.share(this, selectedShareFormat);
        }

    }

    private final Preference.OnPreferenceClickListener mOnPreferenceClickListener = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (PREF_SHARE.equals(preference.getKey())) {
                DialogFragmentFactory.showChoiceDialog(MainActivity.this, getString(R.string.export_choice_title), getResources().getStringArray(R.array.export_choices), -1,
                        ID_ACTION_SHARE);
            } else if (PREF_CLEAR_LOG_FILE.equals(preference.getKey())) {
                DialogFragmentFactory.showConfirmDialog(MainActivity.this, getString(R.string.action_clear), getString(R.string.confirm_logs_clear), ID_ACTION_CLEAR, null);
            }
            return false;
        }
    };
}
