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
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.fragments.ErrorDialog;
import org.gateshipone.malp.application.utils.FormatHelper;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

public class SongDetailsDialog extends DialogFragment {
    public static final String EXTRA_FILE = "file";
    public static final String EXTRA_HIDE_ADD = "hide_add";

    private MPDTrack mFile;
    private boolean mHideAdd;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        /* Check if a file was passed in the extras */
        Bundle args = getArguments();
        if (null != args) {
            mFile = args.getParcelable(EXTRA_FILE);
            mHideAdd = args.getBoolean(EXTRA_HIDE_ADD);
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());

        final LayoutInflater inflater = getLayoutInflater();
        final View rootView = inflater.inflate(R.layout.fragment_song_details, null);

        TextView mTrackNo = rootView.findViewById(R.id.song_detail_text_track_no);
        TextView mTrackDisc = rootView.findViewById(R.id.song_detail_text_disc_no);
        TextView mTrackDuration = rootView.findViewById(R.id.song_detail_text_song_duration);

        TextView mTrackURI = rootView.findViewById(R.id.song_detail_text_track_uri);


        if (null != mFile) {
            if (mFile.getAlbumTrackCount() != 0) {
                mTrackNo.setText(getResources().getString(R.string.track_number_template, mFile.getTrackNumber(), mFile.getAlbumTrackCount()));
            } else {
                mTrackNo.setText(String.valueOf(mFile.getTrackNumber()));
            }

            if (mFile.getAlbumDiscCount() != 0) {
                mTrackDisc.setText(getResources().getString(R.string.track_number_template, mFile.getDiscNumber(), mFile.getAlbumDiscCount()));
            } else {
                mTrackDisc.setText(String.valueOf(mFile.getDiscNumber()));
            }
            mTrackDuration.setText(FormatHelper.formatTracktimeFromS(mFile.getLength()));

            mTrackURI.setText(mFile.getPath());

            LinearLayout textViewLL = rootView.findViewById(R.id.tag_linear_layout);

            final float scale = getResources().getDisplayMetrics().density;
            final int topPadding = (int) (getResources().getDimension(R.dimen.material_content_spacing) *
                    scale + 0.5f);
            for (MPDTrack.StringTagTypes tag : MPDTrack.StringTagTypes.values()) {
                // Check all tags for values
                String tagValue = mFile.getStringTag(tag);
                if (!tagValue.isEmpty()) {
                    TextView tagHeader = new TextView(getContext());
                    tagHeader.setText(getResources().getString(R.string.tag_header_template, tag.name()));
                    tagHeader.setPadding(0, topPadding, 0, 0);
                    tagHeader.setTextColor(ThemeUtils.getThemeColor(requireContext(), R.attr.malp_color_text_background_secondary));
                    tagHeader.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.material_font_style_size_body_1));
                    textViewLL.addView(tagHeader);

                    TextView tagValueView = new TextView(getContext());
                    tagValueView.setText(tagValue);
                    tagValueView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.material_font_style_size_body_2));

                    // MusicBrainz linking
                    if (tag.name().contains("MBID")) {
                        String url = null;
                        switch (tag) {
                            case TRACK_MBID:
                                url = "https://www.musicbrainz.org/recording/" + mFile.getStringTag(MPDTrack.StringTagTypes.TRACK_MBID);
                                break;
                            case ALBUM_MBID:
                                url = "https://www.musicbrainz.org/release/" + mFile.getStringTag(MPDTrack.StringTagTypes.ALBUM_MBID);
                                break;
                            case WORK_MBID:
                                url = "https://www.musicbrainz.org/work/" + mFile.getStringTag(MPDTrack.StringTagTypes.WORK_MBID);
                                break;
                            case ARTIST_MBID:
                                url = "https://www.musicbrainz.org/artist/" + mFile.getStringTag(MPDTrack.StringTagTypes.ARTIST_MBID);
                                break;
                            case ALBUMARTIST_MBID:
                                url = "https://www.musicbrainz.org/artist/" + mFile.getStringTag(MPDTrack.StringTagTypes.ALBUMARTIST_MBID);
                                break;
                            default:
                                break;
                        }

                        if (url != null) {
                            final String mbidURL = url;
                            tagValueView.setOnClickListener(v -> {
                                Intent urlIntent = new Intent(Intent.ACTION_VIEW);
                                urlIntent.setData(Uri.parse(mbidURL));

                                try {
                                    startActivity(urlIntent);
                                } catch (ActivityNotFoundException e) {
                                    final ErrorDialog noBrowserFoundDlg = ErrorDialog.newInstance(R.string.dialog_no_browser_found_title, R.string.dialog_no_browser_found_message);
                                    noBrowserFoundDlg.show(((AppCompatActivity) requireActivity()).getSupportFragmentManager(), "BrowserNotFoundDlg");
                                }
                            });
                        }
                    }

                    textViewLL.addView(tagValueView);
                }
            }
        }

        builder.setView(rootView);
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

    public static SongDetailsDialog createDialog(@NonNull MPDTrack track, boolean hideAdd) {
        SongDetailsDialog dialog = new SongDetailsDialog();
        Bundle args = new Bundle();
        args.putParcelable(SongDetailsDialog.EXTRA_FILE, track);
        args.putBoolean(EXTRA_HIDE_ADD, hideAdd);

        dialog.setArguments(args);
        return dialog;
    }
}
