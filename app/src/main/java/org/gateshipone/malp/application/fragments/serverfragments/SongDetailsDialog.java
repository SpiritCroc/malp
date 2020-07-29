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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.fragments.ErrorDialog;
import org.gateshipone.malp.application.utils.FormatHelper;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

public class SongDetailsDialog extends DialogFragment {

    private static final String EXTRA_FILE = "file";

    private MPDTrack mFile;

    public static SongDetailsDialog newInstance(final MPDTrack file) {
        final Bundle args = new Bundle();
        args.putParcelable(EXTRA_FILE, file);

        final SongDetailsDialog fragment = new SongDetailsDialog();
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        /* Check if an artistname/albumame was given in the extras */
        Bundle args = getArguments();
        if (null != args) {
            mFile = args.getParcelable(EXTRA_FILE);
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final LayoutInflater inflater = getActivity().getLayoutInflater();
        final View rootView = inflater.inflate(R.layout.fragment_song_details, null);

        TextView mTrackTitle = rootView.findViewById(R.id.now_playing_text_track_title);
        TextView mTrackAlbum = rootView.findViewById(R.id.now_playing_text_track_album);
        TextView mTrackArtist = rootView.findViewById(R.id.now_playing_text_track_artist);
        TextView mTrackAlbumArtist = rootView.findViewById(R.id.now_playing_text_album_artist);

        TextView mTrackNo = rootView.findViewById(R.id.now_playing_text_track_no);
        TextView mTrackDisc = rootView.findViewById(R.id.now_playing_text_disc_no);
        TextView mTrackDate = rootView.findViewById(R.id.now_playing_text_date);
        TextView mTrackDuration = rootView.findViewById(R.id.now_playing_text_song_duration);

        TextView mTrackTitleMBID = rootView.findViewById(R.id.now_playing_text_track_mbid);
        TextView mTrackAlbumMBID = rootView.findViewById(R.id.now_playing_text_album_mbid);
        TextView mTrackArtistMBID = rootView.findViewById(R.id.now_playing_text_artist_mbid);
        TextView mTrackAlbumArtistMBID = rootView.findViewById(R.id.now_playing_text_album_artist_mbid);

        TextView mTrackURI = rootView.findViewById(R.id.now_playing_text_track_uri);

        TextView artistSort = rootView.findViewById(R.id.now_playing_text_track_artist_sort);
        TextView albumArtistSort = rootView.findViewById(R.id.now_playing_text_album_artist_sort);

        if (null != mFile) {
            mTrackTitle.setText(mFile.getTrackTitle());
            mTrackAlbum.setText(mFile.getTrackAlbum());
            mTrackArtist.setText(mFile.getTrackArtist());
            artistSort.setText(mFile.getTrackArtistSort());
            mTrackAlbumArtist.setText(mFile.getTrackAlbumArtist());
            albumArtistSort.setText(mFile.getTrackAlbumArtistSort());

            if (mFile.getAlbumTrackCount() != 0) {
                mTrackNo.setText(String.valueOf(mFile.getTrackNumber()) + '/' + mFile.getAlbumTrackCount());
            } else {
                mTrackNo.setText(String.valueOf(mFile.getTrackNumber()));
            }

            if (mFile.getAlbumDiscCount() != 0) {
                mTrackDisc.setText(String.valueOf(mFile.getDiscNumber()) + '/' + mFile.getAlbumDiscCount());
            } else {
                mTrackDisc.setText(String.valueOf(mFile.getDiscNumber()));
            }
            mTrackDate.setText(mFile.getDate());
            mTrackDuration.setText(FormatHelper.formatTracktimeFromS(mFile.getLength()));

            mTrackTitleMBID.setText(mFile.getTrackMBID());
            mTrackAlbumMBID.setText(mFile.getTrackAlbumMBID());
            mTrackArtistMBID.setText(mFile.getTrackArtistMBID());
            mTrackAlbumArtistMBID.setText(mFile.getTrackAlbumArtistMBID());

            mTrackURI.setText(mFile.getPath());

            mTrackTitleMBID.setOnClickListener(v -> {
                Intent urlIntent = new Intent(Intent.ACTION_VIEW);
                urlIntent.setData(Uri.parse("https://www.musicbrainz.org/recording/" + mFile.getTrackMBID()));

                try {
                    startActivity(urlIntent);
                } catch (ActivityNotFoundException e) {
                    final ErrorDialog noBrowserFoundDlg = ErrorDialog.newInstance(R.string.dialog_no_browser_found_title, R.string.dialog_no_browser_found_message);
                    noBrowserFoundDlg.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "BrowserNotFoundDlg");
                }
            });

            mTrackAlbumMBID.setOnClickListener(v -> {
                Intent urlIntent = new Intent(Intent.ACTION_VIEW);
                urlIntent.setData(Uri.parse("https://www.musicbrainz.org/release/" + mFile.getTrackAlbumMBID()));

                try {
                    startActivity(urlIntent);
                } catch (ActivityNotFoundException e) {
                    final ErrorDialog noBrowserFoundDlg = ErrorDialog.newInstance(R.string.dialog_no_browser_found_title, R.string.dialog_no_browser_found_message);
                    noBrowserFoundDlg.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "BrowserNotFoundDlg");
                }
            });

            mTrackArtistMBID.setOnClickListener(v -> {
                Intent urlIntent = new Intent(Intent.ACTION_VIEW);
                urlIntent.setData(Uri.parse("https://www.musicbrainz.org/artist/" + mFile.getTrackArtistMBID()));

                try {
                    startActivity(urlIntent);
                } catch (ActivityNotFoundException e) {
                    final ErrorDialog noBrowserFoundDlg = ErrorDialog.newInstance(R.string.dialog_no_browser_found_title, R.string.dialog_no_browser_found_message);
                    noBrowserFoundDlg.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "BrowserNotFoundDlg");
                }
            });

            mTrackAlbumArtistMBID.setOnClickListener(v -> {
                Intent urlIntent = new Intent(Intent.ACTION_VIEW);
                urlIntent.setData(Uri.parse("https://www.musicbrainz.org/artist/" + mFile.getTrackAlbumArtistMBID()));

                try {
                    startActivity(urlIntent);
                } catch (ActivityNotFoundException e) {
                    final ErrorDialog noBrowserFoundDlg = ErrorDialog.newInstance(R.string.dialog_no_browser_found_title, R.string.dialog_no_browser_found_message);
                    noBrowserFoundDlg.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "BrowserNotFoundDlg");
                }
            });
        }

        builder.setView(rootView);

        builder.setPositiveButton(R.string.action_add, (dialog, which) -> {
            if (null != mFile) {
                MPDQueryHandler.addPath(mFile.getPath());
            }
            dismiss();
        });
        builder.setNegativeButton(R.string.dialog_action_cancel, (dialog, which) -> dismiss());

        return builder.create();
    }
}
