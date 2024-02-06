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

package org.gateshipone.malp.application.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.activities.MainActivity;
import org.gateshipone.malp.application.callbacks.FABFragmentCallback;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.views.VolumeStepPreferenceDialog;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = SettingsFragment.class.getSimpleName();

    private FABFragmentCallback mFABCallback = null;
    private OnArtworkSettingsRequestedCallback mArtworkCallback;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // add listener to open artwork settings
        Preference openArtwork = findPreference(getString(R.string.pref_artwork_settings_key));
        openArtwork.setOnPreferenceClickListener(preference -> {
            mArtworkCallback.openArtworkSettings();
            return true;
        });

        Preference openVolumeStepDialog = findPreference(getString(R.string.pref_volume_steps_dialog_key));
        openVolumeStepDialog.setOnPreferenceClickListener(preference -> {
            VolumeStepPreferenceDialog dialog = new VolumeStepPreferenceDialog();
            dialog.show(requireActivity().getSupportFragmentManager(), "Volume steps");
            return true;
        });


        // Read theme preference
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this.getContext());
        boolean legacyTheme = sharedPref.getBoolean(getString(R.string.pref_legacy_theme_key), getResources().getBoolean(R.bool.pref_theme_legacy_default));
        String themePref = sharedPref.getString(getString(R.string.pref_legacy_theme_selector_key), getString(R.string.pref_oleddark_key));
        if (legacyTheme && themePref.equals(getString(R.string.pref_oleddark_key))) {
            findPreference(getString(R.string.pref_dark_theme_key)).setVisible(false);
        }

        themePref = sharedPref.getString(getString(R.string.pref_materialyou_theme_selector_key), getString(R.string.pref_oleddark_key) );
        if (!legacyTheme && themePref.equals(getString(R.string.pref_materialyou_auto_key))) {
            findPreference(getString(R.string.pref_dark_theme_key)).setVisible(false);
        }

        if (legacyTheme) {
            findPreference(getString(R.string.pref_materialyou_theme_selector_key)).setVisible(false);
            findPreference(getString(R.string.pref_legacy_theme_selector_key)).setVisible(true);
        } else {
            findPreference(getString(R.string.pref_materialyou_theme_selector_key)).setVisible(true);
            findPreference(getString(R.string.pref_legacy_theme_selector_key)).setVisible(false);
        }

    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        // we have to set the background color at this point otherwise we loose the ripple effect
        view.setBackgroundColor(ThemeUtils.getThemeColor(requireContext(), R.attr.app_color_content));

        return view;
    }

    /**
     * Called when the fragment is first attached to its context.
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mFABCallback = (FABFragmentCallback) context;
        } catch (ClassCastException e) {
            mFABCallback = null;
        }

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mArtworkCallback = (OnArtworkSettingsRequestedCallback) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement OnArtworkSettingsRequestedCallback");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);

        if (null != mFABCallback) {
            mFABCallback.setupFAB(false, null);
            mFABCallback.setupToolbar(getString(R.string.menu_settings), false, true, false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.main_settings);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_legacy_theme_selector_key)) || key.equals(getString(R.string.pref_dark_theme_key)) ||
                key.equals(getString(R.string.pref_legacy_theme_key)) || key.equals(getString(R.string.pref_materialyou_theme_selector_key))) {
            Intent intent = requireActivity().getIntent();
            intent.putExtra(MainActivity.MAINACTIVITY_INTENT_EXTRA_REQUESTEDVIEW, MainActivity.REQUESTEDVIEW.SETTINGS.ordinal());
            requireActivity().finish();
            startActivity(intent);
        }
    }

    public interface OnArtworkSettingsRequestedCallback {
        void openArtworkSettings();
    }
}
