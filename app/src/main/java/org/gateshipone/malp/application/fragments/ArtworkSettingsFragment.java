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
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.MalpApplication;
import org.gateshipone.malp.application.activities.MainActivity;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.artwork.BulkDownloadService;
import org.gateshipone.malp.application.artwork.network.artprovider.HTTPAlbumImageProvider;
import org.gateshipone.malp.application.artwork.network.artprovider.MPDAlbumImageProvider;
import org.gateshipone.malp.application.artwork.storage.ArtworkDatabaseManager;
import org.gateshipone.malp.application.callbacks.FABFragmentCallback;
import org.gateshipone.malp.application.utils.PermissionHelper;
import org.gateshipone.malp.application.utils.ThemeUtils;


public class ArtworkSettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private FABFragmentCallback mFABCallback = null;

    public static ArtworkSettingsFragment newInstance() {
        return new ArtworkSettingsFragment();
    }

    /**
     * Called to do initial creation of a fragment.
     * <p>
     * This method will setup a listener to start the system audio equalizer.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // add listener to clear album data
        Preference clearAlbums = findPreference(getString(R.string.pref_clear_album_key));
        clearAlbums.setOnPreferenceClickListener(preference -> {
            final Context context = getContext();
            ArtworkDatabaseManager.getInstance(context).clearAlbumImages();
            return true;
        });

        // add listener to clear artist data
        Preference clearArtist = findPreference(getString(R.string.pref_clear_artist_key));
        clearArtist.setOnPreferenceClickListener(preference -> {
            final Context context = getContext();
            ArtworkDatabaseManager.getInstance(context).clearArtistImages();
            return true;
        });

        Preference clearBlockedAlbums = findPreference(getString(R.string.pref_clear_blocked_album_key));
        clearBlockedAlbums.setOnPreferenceClickListener(preference -> {
            ArtworkDatabaseManager.getInstance(getContext()).clearBlockedAlbumImages();
            return true;
        });

        Preference clearBlockedArtists = findPreference(getString(R.string.pref_clear_blocked_artist_key));
        clearBlockedArtists.setOnPreferenceClickListener(preference -> {
            ArtworkDatabaseManager.getInstance(getContext()).clearBlockedArtistImages();
            return true;
        });

        Preference bulkLoad = findPreference(getString(R.string.pref_bulk_load_key));
        bulkLoad.setOnPreferenceClickListener(preference -> {
            if (PermissionHelper.areNotificationsAllowed(requireActivity())) {
                startBulkdownload();
            } else {
                ((MainActivity) requireActivity()).requestPermissionShowNotifications();
            }
            return true;
        });
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        // we have to set the background color at this point otherwise we loose the ripple effect
        view.setBackgroundColor(ThemeUtils.getThemeColor(requireContext(), R.attr.malp_color_background));

        return view;
    }


    /**
     * Called when the fragment resumes.
     * <p/>
     * Register listener and setup the toolbar.
     */
    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        // set toolbar behaviour and title

        if (null != mFABCallback) {
            mFABCallback.setupFAB(false, null);
            mFABCallback.setupToolbar(getString(R.string.artwork_settings), false, false, false);
        }
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
    }


    /**
     * Called when the Fragment is no longer resumed.
     * <p/>
     * Unregister listener.
     */
    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Create the preferences from an xml resource file
     */
    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.artwork_settings);
        PreferenceManager.setDefaultValues(getActivity(), R.xml.artwork_settings, false);
    }

    /**
     * Called when a shared preference is changed, added, or removed.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        String albumProviderKey = getString(R.string.pref_album_provider_key);
        String artistProviderKey = getString(R.string.pref_artist_provider_key);
        String downloadWifiOnlyKey = getString(R.string.pref_download_wifi_only_key);

        if (key.equals(albumProviderKey) || key.equals(artistProviderKey) || key.equals(downloadWifiOnlyKey)) {
            Intent nextIntent = new Intent(BulkDownloadService.ACTION_CANCEL);
            requireActivity().getApplicationContext().sendBroadcast(nextIntent);

            ArtworkManager artworkManager = ArtworkManager.getInstance(getContext());

            artworkManager.cancelAllRequests();

            if (key.equals(albumProviderKey)) {
                artworkManager.setAlbumProvider(sharedPreferences.getString(albumProviderKey, getString(R.string.pref_artwork_provider_album_default)));
            } else if (key.equals(artistProviderKey)) {
                artworkManager.setArtistProvider(sharedPreferences.getString(artistProviderKey, getString(R.string.pref_artwork_provider_artist_default)));
            } else if (key.equals(downloadWifiOnlyKey)) {
                artworkManager.setWifiOnly(sharedPreferences.getBoolean(downloadWifiOnlyKey, getResources().getBoolean(R.bool.pref_download_wifi_default)));
            }
        }
    }

    /**
     * Method to start the Bulkdownloader dialog which will then start the Bulkdownloader.
     */
    public void startBulkdownload() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(getResources().getString(R.string.bulk_download_notice_title));
        builder.setMessage(getResources().getString(R.string.bulk_download_notice_text));


        builder.setPositiveButton(R.string.dialog_action_ok, (dialog, id) -> {
            Intent serviceIntent = new Intent(getActivity(), BulkDownloadService.class);
            serviceIntent.setAction(BulkDownloadService.ACTION_START_BULKDOWNLOAD);
            SharedPreferences sharedPref = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());
            serviceIntent.putExtra(BulkDownloadService.BUNDLE_KEY_ARTIST_PROVIDER, sharedPref.getString(getString(R.string.pref_artist_provider_key),
                    getString(R.string.pref_artwork_provider_artist_default)));
            serviceIntent.putExtra(BulkDownloadService.BUNDLE_KEY_ALBUM_PROVIDER, sharedPref.getString(getString(R.string.pref_album_provider_key),
                    getString(R.string.pref_artwork_provider_album_default)));
            serviceIntent.putExtra(BulkDownloadService.BUNDLE_KEY_WIFI_ONLY, sharedPref.getBoolean(getString(R.string.pref_download_wifi_only_key),
                    getResources().getBoolean(R.bool.pref_download_wifi_default)));
            serviceIntent.putExtra(BulkDownloadService.BUNDLE_KEY_HTTP_COVER_REGEX, HTTPAlbumImageProvider.getInstance(getContext()).getRegex());
            serviceIntent.putExtra(BulkDownloadService.BUNDLE_KEY_MPD_COVER_ENABLED, MPDAlbumImageProvider.getInstance().getActive());
            requireActivity().startService(serviceIntent);
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

}
