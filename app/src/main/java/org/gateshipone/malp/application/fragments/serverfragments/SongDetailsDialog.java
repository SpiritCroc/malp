/*
 *  Copyright (C) 2020 Team Gateship-One
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
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.SongDetailsAdapter;
import org.gateshipone.malp.application.fragments.ErrorDialog;
import org.gateshipone.malp.application.utils.FormatHelper;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.util.ArrayList;
import java.util.List;

public class SongDetailsDialog extends DialogFragment {
    public static final String EXTRA_FILE = "file";
    public static final String EXTRA_HIDE_ADD = "hide_add";

    private MPDTrack mFile;

    private boolean mHideAdd;

    private SongDetailsAdapter mAdapter;

    public static SongDetailsDialog createDialog(@NonNull MPDTrack track, boolean hideAdd) {
        SongDetailsDialog dialog = new SongDetailsDialog();
        Bundle args = new Bundle();
        args.putParcelable(SongDetailsDialog.EXTRA_FILE, track);
        args.putBoolean(EXTRA_HIDE_ADD, hideAdd);

        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        /* Check if a file was passed in the extras */
        Bundle args = getArguments();
        if (null != args) {
            mFile = args.getParcelable(EXTRA_FILE);
            mHideAdd = args.getBoolean(EXTRA_HIDE_ADD);
        }

        final List<SongDetailsAdapter.SongDetailsItem> items = createItems();

        mAdapter = new SongDetailsAdapter(requireContext(), items);

        final AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());

        builder.setAdapter(mAdapter, (dialog, which) -> {
            final SongDetailsAdapter.SongDetailsItem item = mAdapter.getItem(which);

            final MPDTrack.StringTagTypes tag = item.getTagType();

            // add click listener only for MusicBrainz Links
            if (tag != null && tag.name().contains("MBID")) {
                String url = null;
                switch (tag) {
                    case TRACK_MBID:
                        url = "https://www.musicbrainz.org/recording/";
                        break;
                    case ALBUM_MBID:
                        url = "https://www.musicbrainz.org/release/";
                        break;
                    case WORK_MBID:
                        url = "https://www.musicbrainz.org/work/";
                        break;
                    case ARTIST_MBID:
                    case ALBUMARTIST_MBID:
                        url = "https://www.musicbrainz.org/artist/";
                        break;
                    default:
                        break;
                }

                if (url != null) {
                    final String mbidURL = url + item.getDisplayValue();

                    Intent urlIntent = new Intent(Intent.ACTION_VIEW);
                    urlIntent.setData(Uri.parse(mbidURL));

                    try {
                        startActivity(urlIntent);
                    } catch (ActivityNotFoundException e) {
                        final ErrorDialog noBrowserFoundDlg = ErrorDialog.newInstance(R.string.dialog_no_browser_found_title, R.string.dialog_no_browser_found_message);
                        noBrowserFoundDlg.show(requireActivity().getSupportFragmentManager(), "BrowserNotFoundDlg");
                    }
                }
            }
        });

        if (!mHideAdd) {
            builder.setPositiveButton(R.string.action_add, (dialog, which) -> {
                if (null != mFile) {
                    MPDQueryHandler.addPath(mFile.getPath());
                }
                dismiss();
            });
            builder.setNegativeButton(R.string.dialog_action_cancel, (dialog, which) -> dismiss());
        } else {
            builder.setPositiveButton(R.string.dialog_action_ok, (dialogInterface, i) -> dismiss());
        }

        return builder.create();
    }

    private List<SongDetailsAdapter.SongDetailsItem> createItems() {
        final List<SongDetailsAdapter.SongDetailsItem> items = new ArrayList<>();

        // create custom items
        String customKey;
        String customValue;

        // track number
        customKey = getString(R.string.song_track_no);

        if (mFile.getAlbumTrackCount() != 0) {
            customValue = getString(R.string.track_number_template, mFile.getTrackNumber(), mFile.getAlbumTrackCount());
        } else {
            customValue = String.valueOf(mFile.getTrackNumber());
        }

        items.add(new SongDetailsAdapter.SongDetailsItem(customKey, customValue));

        // tracks disc
        customKey = getString(R.string.song_disc);

        if (mFile.getAlbumTrackCount() != 0) {
            customValue = getString(R.string.track_number_template, mFile.getDiscNumber(), mFile.getAlbumDiscCount());
        } else {
            customValue = String.valueOf(mFile.getDiscNumber());
        }

        items.add(new SongDetailsAdapter.SongDetailsItem(customKey, customValue));

        // track duration
        customKey = getString(R.string.song_duration);
        customValue = FormatHelper.formatTracktimeFromS(mFile.getLength());

        items.add(new SongDetailsAdapter.SongDetailsItem(customKey, customValue));

        // track uri
        customKey = getString(R.string.song_uri);
        customValue = mFile.getPath();

        items.add(new SongDetailsAdapter.SongDetailsItem(customKey, customValue));

        // create default items
        for (MPDTrack.StringTagTypes tag : MPDTrack.StringTagTypes.values()) {
            final String tagValue = mFile.getStringTag(tag);

            if (!tagValue.isEmpty()) {
                items.add(new SongDetailsAdapter.SongDetailsItem(tag, getString(R.string.tag_header_template, tag.name()), tagValue));
            }
        }

        return items;
    }
}
