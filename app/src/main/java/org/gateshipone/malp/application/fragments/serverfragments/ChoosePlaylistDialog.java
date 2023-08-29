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

package org.gateshipone.malp.application.fragments.serverfragments;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.FileAdapter;
import org.gateshipone.malp.application.callbacks.OnSaveDialogListener;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.application.viewmodels.PlaylistsViewModel;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPlaylist;

public class ChoosePlaylistDialog extends GenericMPDFragment<MPDFileEntry> {

    private static final String EXTRA_SHOW_NEW_ENTRY = "show_newentry";

    /**
     * Listener to save the bookmark
     */
    private OnSaveDialogListener mSaveCallback;

    private boolean mShowNewEntry;

    public static ChoosePlaylistDialog newInstance(final boolean showNewEntry) {
        final Bundle args = new Bundle();
        args.putBoolean(EXTRA_SHOW_NEW_ENTRY, showNewEntry);

        final ChoosePlaylistDialog fragment = new ChoosePlaylistDialog();
        fragment.setArguments(args);
        return fragment;
    }

    public void setCallback(OnSaveDialogListener callback) {
        mSaveCallback = callback;
    }

    /**
     * Create the dialog to choose to override an existing bookmark or to create a new bookmark.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();

        if (null != args) {
            mShowNewEntry = args.getBoolean(EXTRA_SHOW_NEW_ENTRY);
        }

        // Use the Builder class for convenient dialog construction
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());

        mAdapter = new FileAdapter(requireActivity(), false, false);

        builder.setTitle(getString(R.string.dialog_choose_playlist)).setAdapter(mAdapter, (dialog, which) -> {
            if (null == mSaveCallback) {
                return;
            }
            if (which == 0) {
                // open save dialog to create a new playlist
                mSaveCallback.onCreateNewObject();
            } else {
                // override existing playlist
                MPDPlaylist playlist = (MPDPlaylist) mAdapter.getItem(which);
                String objectTitle = playlist.getPath();
                mSaveCallback.onSaveObject(objectTitle);
            }
        }).setNegativeButton(R.string.dialog_action_cancel, (dialog, id) -> {
            // User cancelled the dialog don't save object
            requireDialog().cancel();
        });

        getViewModel().getData().observe(this, this::onDataReady);

        // set divider
        AlertDialog dlg = builder.create();
        dlg.getListView().setDivider(new ColorDrawable(ThemeUtils.getThemeColor(requireContext(), R.attr.malp_color_divider)));
        dlg.getListView().setDividerHeight(getResources().getDimensionPixelSize(R.dimen.list_divider_size));

        return dlg;
    }

    @Override
    GenericViewModel<MPDFileEntry> getViewModel() {
        return new ViewModelProvider(this, new PlaylistsViewModel.PlaylistsViewModelFactory(requireActivity().getApplication(), mShowNewEntry)).get(PlaylistsViewModel.class);
    }
}
