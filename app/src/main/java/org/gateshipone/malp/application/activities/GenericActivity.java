/*
 *  Copyright (C) 2023 Team Gateship-One
 *  (Hendrik Borghorst & Frederik Luetkes)
 *
 *  The AUTHORS.md file contains a detailed contributors list:
 *  <https://gitlab.com/gateship-one/malp/blob/master/AUTHORS.md>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.gateshipone.malp.application.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.WindowManager;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.background.BackgroundService;
import org.gateshipone.malp.application.background.BackgroundServiceConnection;
import org.gateshipone.malp.application.utils.HardwareKeyHandler;
import org.gateshipone.malp.mpdservice.ConnectionManager;
import org.gateshipone.malp.mpdservice.handlers.MPDConnectionErrorHandler;
import org.gateshipone.malp.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDCommandHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDStateMonitoringHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDException;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;

import java.lang.ref.WeakReference;


public abstract class GenericActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = GenericActivity.class.getSimpleName();

    private boolean mHardwareControls;

    private boolean mKeepDisplayOn;

    private BackgroundServiceConnection mBackgroundServiceConnection;

    private BackgroundService.STREAMING_STATUS mStreamingStatus;

    private MPDConnectionStateCallbackHandler mConnectionCallback;

    private StreamingStatusReceiver mStreamingStatusReceiver;

    private MPDErrorListener mErrorListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Read theme preference
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String themePref = sharedPref.getString(getString(R.string.pref_legacy_theme_selector_key), getString(R.string.pref_theme_default));
        String themeMaterialPref = sharedPref.getString(getString(R.string.pref_materialyou_theme_selector_key), getString(R.string.pref_theme_materialyou_default));
        boolean darkTheme = sharedPref.getBoolean(getString(R.string.pref_dark_theme_key), getResources().getBoolean(R.bool.pref_theme_dark_default));
        boolean legacyTheme = sharedPref.getBoolean(getString(R.string.pref_legacy_theme_key), getResources().getBoolean(R.bool.pref_theme_legacy_default));
        if (darkTheme && legacyTheme) {
            if (themePref.equals(getString(R.string.pref_indigo_key))) {
                setTheme(R.style.AppTheme_indigo);
            } else if (themePref.equals(getString(R.string.pref_orange_key))) {
                setTheme(R.style.AppTheme_orange);
            } else if (themePref.equals(getString(R.string.pref_deeporange_key))) {
                setTheme(R.style.AppTheme_deepOrange);
            } else if (themePref.equals(getString(R.string.pref_blue_key))) {
                setTheme(R.style.AppTheme_blue);
            } else if (themePref.equals(getString(R.string.pref_darkgrey_key))) {
                setTheme(R.style.AppTheme_darkGrey);
            } else if (themePref.equals(getString(R.string.pref_brown_key))) {
                setTheme(R.style.AppTheme_brown);
            } else if (themePref.equals(getString(R.string.pref_lightgreen_key))) {
                setTheme(R.style.AppTheme_lightGreen);
            } else if (themePref.equals(getString(R.string.pref_red_key))) {
                setTheme(R.style.AppTheme_red);
            } else if (themePref.equals(getString(R.string.pref_oleddark_key))) {
                setTheme(R.style.AppTheme_oledDark);
            } else if (themePref.equals(getString(R.string.pref_materialyou_auto_key))) {
                setTheme(R.style.AppTheme_materialyou);
            } else if (themePref.equals(getString(R.string.pref_materialyou_key))) {
                setTheme(R.style.AppTheme_materialyou_dark);
            }
        } else if (!darkTheme && legacyTheme){
            if (themePref.equals(getString(R.string.pref_indigo_key))) {
                setTheme(R.style.AppTheme_light_indigo);
            } else if (themePref.equals(getString(R.string.pref_orange_key))) {
                setTheme(R.style.AppTheme_light_orange);
            } else if (themePref.equals(getString(R.string.pref_deeporange_key))) {
                setTheme(R.style.AppTheme_light_deepOrange);
            } else if (themePref.equals(getString(R.string.pref_blue_key))) {
                setTheme(R.style.AppTheme_light_blue);
            } else if (themePref.equals(getString(R.string.pref_darkgrey_key))) {
                setTheme(R.style.AppTheme_light_darkGrey);
            } else if (themePref.equals(getString(R.string.pref_brown_key))) {
                setTheme(R.style.AppTheme_light_brown);
            } else if (themePref.equals(getString(R.string.pref_lightgreen_key))) {
                setTheme(R.style.AppTheme_light_lightGreen);
            } else if (themePref.equals(getString(R.string.pref_red_key))) {
                setTheme(R.style.AppTheme_light_red);
            } else if (themePref.equals(getString(R.string.pref_oleddark_key))) {
                setTheme(R.style.AppTheme_oledDark);
            } else if (themePref.equals(getString(R.string.pref_materialyou_auto_key))) {
                setTheme(R.style.AppTheme_materialyou);
            } else if (themePref.equals(getString(R.string.pref_materialyou_key))) {
                setTheme(R.style.AppTheme_materialyou_light);
            }
        } else if (darkTheme && !legacyTheme) {
            if (themeMaterialPref.equals(getString(R.string.pref_indigo_key))) {
                setTheme(R.style.AppTheme_materialyou_indigo_dark);
            } else if (themeMaterialPref.equals(getString(R.string.pref_orange_key))) {
                setTheme(R.style.AppTheme_materialyou_orange_dark);
            } else if (themeMaterialPref.equals(getString(R.string.pref_blue_key))) {
                setTheme(R.style.AppTheme_materialyou_blue_dark);
            } else if (themeMaterialPref.equals(getString(R.string.pref_lightgreen_key))) {
                setTheme(R.style.AppTheme_materialyou_green_dark);
            } else if (themeMaterialPref.equals(getString(R.string.pref_red_key))) {
                setTheme(R.style.AppTheme_materialyou_red_dark);
            } else if (themeMaterialPref.equals(getString(R.string.pref_materialyou_auto_key))) {
                setTheme(R.style.AppTheme_materialyou);
            } else if (themeMaterialPref.equals(getString(R.string.pref_materialyou_key))) {
                setTheme(R.style.AppTheme_materialyou_dark);
            } else {
                setTheme(R.style.AppTheme_materialyou);
            }
        } else {
            if (themeMaterialPref.equals(getString(R.string.pref_indigo_key))) {
                setTheme(R.style.AppTheme_materialyou_indigo_light);
            } else if (themeMaterialPref.equals(getString(R.string.pref_orange_key)))  {
                setTheme(R.style.AppTheme_materialyou_orange_light);
            } else if (themeMaterialPref.equals(getString(R.string.pref_blue_key))) {
                setTheme(R.style.AppTheme_materialyou_blue_light);
            } else if (themeMaterialPref.equals(getString(R.string.pref_lightgreen_key))) {
                setTheme(R.style.AppTheme_materialyou_green_light);
            } else if (themeMaterialPref.equals(getString(R.string.pref_red_key))) {
                setTheme(R.style.AppTheme_materialyou_red_light);
            } else if (themeMaterialPref.equals(getString(R.string.pref_materialyou_auto_key))) {
                setTheme(R.style.AppTheme_materialyou);
            } else if (themeMaterialPref.equals(getString(R.string.pref_materialyou_key))) {
                setTheme(R.style.AppTheme_materialyou_light);
            } else {
                setTheme(R.style.AppTheme_materialyou);
            }
        }


        super.onCreate(savedInstanceState);

        mErrorListener = new MPDErrorListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Add error listener
        MPDStateMonitoringHandler.getHandler().addErrorListener(mErrorListener);
        MPDCommandHandler.getHandler().addErrorListener(mErrorListener);
        MPDQueryHandler.getHandler().addErrorListener(mErrorListener);

        mConnectionCallback = new MPDConnectionStateCallbackHandler(this, getMainLooper());
        MPDInterface.getGenericInstance().addMPDConnectionStateChangeListener(mConnectionCallback);

        ConnectionManager.getInstance(getApplicationContext()).registerMPDUse(getApplicationContext());

        if (null == mBackgroundServiceConnection) {
            mBackgroundServiceConnection = new BackgroundServiceConnection(getApplicationContext(), new BackgroundServiceConnectionStateListener());
        }
        mBackgroundServiceConnection.openConnection();

        if (mStreamingStatusReceiver == null) {
            mStreamingStatusReceiver = new StreamingStatusReceiver();
        }


        IntentFilter filter = new IntentFilter();
        filter.addAction(BackgroundService.ACTION_STREAMING_STATUS_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getApplicationContext().registerReceiver(mStreamingStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getApplicationContext().registerReceiver(mStreamingStatusReceiver, filter);
        }

        // Check if hardware key control is enabled by the user
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        mHardwareControls = sharedPref.getBoolean(getString(R.string.pref_hardware_controls_key), getResources().getBoolean(R.bool.pref_hardware_controls_default));
        HardwareKeyHandler.getInstance().setVolumeStepSize(sharedPref.getInt(getString(R.string.pref_volume_steps_key),getResources().getInteger(R.integer.pref_volume_steps_default)));
        mKeepDisplayOn = sharedPref.getBoolean(getString(R.string.pref_keep_display_on_key),getResources().getBoolean(R.bool.pref_keep_display_on_default));
        handleKeepDisplayOnSetting();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        // Disconnect from MPD server
        ConnectionManager.getInstance(getApplicationContext()).unregisterMPDUse(getApplicationContext());

        mBackgroundServiceConnection.closeConnection();

        sharedPref.unregisterOnSharedPreferenceChangeListener(this);

        MPDInterface.getGenericInstance().removeMPDConnectionStateChangeListener(mConnectionCallback);
        mConnectionCallback = null;

        getApplicationContext().unregisterReceiver(mStreamingStatusReceiver);

        // Unregister error listeners
        MPDStateMonitoringHandler.getHandler().removeErrorListener(mErrorListener);
        MPDCommandHandler.getHandler().removeErrorListener(mErrorListener);
        MPDQueryHandler.getHandler().removeErrorListener(mErrorListener);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_hardware_controls_key))) {
            mHardwareControls = sharedPreferences.getBoolean(getString(R.string.pref_hardware_controls_key), getResources().getBoolean(R.bool.pref_hardware_controls_default));
        } else if (key.equals(getString(R.string.pref_volume_steps_key))) {
            // Set the hardware key handler to the new value
            HardwareKeyHandler.getInstance().setVolumeStepSize(sharedPreferences.getInt(getString(R.string.pref_volume_steps_key),getResources().getInteger(R.integer.pref_volume_steps_default)));
        } else if (key.equals(getString(R.string.pref_keep_display_on_key))) {
            mKeepDisplayOn = sharedPreferences.getBoolean(getString(R.string.pref_keep_display_on_key),getResources().getBoolean(R.bool.pref_keep_display_on_default));
            handleKeepDisplayOnSetting();
        }
    }

    /**
     * Handles the volume keys of the device to control MPDs volume.
     *
     * @param event KeyEvent that was pressed by the user.
     * @return True if handled by MALP
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean streamingActive = !(mStreamingStatus == BackgroundService.STREAMING_STATUS.STOPPED);
        if (mHardwareControls) {
            return HardwareKeyHandler.getInstance().handleKeyEvent(event, !streamingActive) || super.dispatchKeyEvent(event);
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    private void handleKeepDisplayOnSetting() {
        if (mKeepDisplayOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    protected abstract void onConnected();

    protected abstract void onDisconnected();

    protected abstract void onMPDError(MPDException.MPDServerException e);

    protected abstract void onMPDConnectionError(MPDException.MPDConnectionException e);

    private static class MPDConnectionStateCallbackHandler extends MPDConnectionStateChangeHandler {
        private final WeakReference<GenericActivity> mActivity;

        MPDConnectionStateCallbackHandler(GenericActivity activity, Looper looper) {
            super(looper);
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void onConnected() {
            mActivity.get().onConnected();
        }

        @Override
        public void onDisconnected() {
            mActivity.get().onDisconnected();
        }
    }

    /**
     * Receives stream playback status updates. When stream playback is started the status
     * is necessary to show the right menu item.
     */
    private class StreamingStatusReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BackgroundService.ACTION_STREAMING_STATUS_CHANGED)) {
                mStreamingStatus = BackgroundService.STREAMING_STATUS.values()[intent.getIntExtra(BackgroundService.INTENT_EXTRA_STREAMING_STATUS, 0)];
            }
        }
    }


    /**
     * Private class to handle when a {@link android.content.ServiceConnection} to the {@link BackgroundService}
     * is established. When the connection is established, the stream playback status is retrieved.
     */
    private class BackgroundServiceConnectionStateListener implements BackgroundServiceConnection.OnConnectionStatusChangedListener {

        @Override
        public void onConnected() {
            try {
                mStreamingStatus = BackgroundService.STREAMING_STATUS.values()[mBackgroundServiceConnection.getService().getStreamingStatus()];
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected() {

        }
    }

    private static class MPDErrorListener extends MPDConnectionErrorHandler {
        private final WeakReference<GenericActivity> mActivity;

        public MPDErrorListener(GenericActivity activity) {
            mActivity = new WeakReference<>(activity);
        }


        @Override
        protected void onMPDError(MPDException.MPDServerException e) {
            mActivity.get().onMPDError(e);
        }

        @Override
        protected void onMPDConnectionError(MPDException.MPDConnectionException e) {
            mActivity.get().onMPDConnectionError(e);
        }
    }
}
