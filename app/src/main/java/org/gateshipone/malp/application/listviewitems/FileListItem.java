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

package org.gateshipone.malp.application.listviewitems;


import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.ScrollSpeedAdapter;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.utils.FormatHelper;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDDirectory;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPlaylist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;


/**
 * Class that can be used for all track type items (albumtracks, playlist tracks, playlists, directories, etc)
 */
public class FileListItem extends AbsImageListViewItem {
    private static final String TAG = FileListItem.class.getSimpleName();

    private final boolean mIsSectionHeader;

    protected TextView mTitleView;
    protected TextView mSeparator;
    protected TextView mAdditionalInfoView;
    protected TextView mNumberView;
    protected TextView mDurationView;
    protected TextView mSectionHeader;
    protected LinearLayout mSectionHeaderLayout;
    private final LinearLayout mTextLayout;

    private final ImageView mItemIcon;

    private final boolean mShowIcon;

    /**
     * Base constructor to create a not section-type element
     *
     * @param context  Context used for creation of View
     * @param showIcon If left file/dir icon should be shown. It is not changeable after creation.
     */
    public FileListItem(final Context context, final boolean showIcon, @Nullable final ScrollSpeedAdapter adapter) {
        super(context, R.layout.listview_item_file, R.id.item_image, R.id.item_image_viewswitcher, adapter);

        mTitleView = findViewById(R.id.track_title);
        mAdditionalInfoView = findViewById(R.id.track_additional_information);
        mSeparator = findViewById(R.id.track_separator);
        mDurationView = findViewById(R.id.track_duration);
        mNumberView = findViewById(R.id.track_number);

        mItemIcon = findViewById(R.id.item_image_placeholder);
        mTextLayout = findViewById(R.id.item_track_text_layout);
        mIsSectionHeader = false;

        mShowIcon = showIcon;
        ViewSwitcher viewSwitcher = findViewById(R.id.item_image_viewswitcher);
        if (showIcon) {
            viewSwitcher.setVisibility(VISIBLE);
            //mTextLayout.setPadding(0, mTextLayout.getPaddingTop(), mTextLayout.getPaddingRight(), mTextLayout.getBottom());
        } else {
            viewSwitcher.setVisibility(GONE);
        }

        /* Show loading text */
        mSeparator.setVisibility(GONE);
        mTitleView.setText(getResources().getText(R.string.track_item_loading));
    }

    /**
     * Base constructor to create a section-type element
     *
     * @param context  Context used for creation of View
     * @param showIcon If left file/dir icon should be shown. It is not changeable after creation.
     */
    public FileListItem(Context context, String sectionTitle, boolean showIcon, ScrollSpeedAdapter adapter) {
        super(context, R.layout.listview_item_section_track,
                R.id.section_header_image,
                R.id.section_header_image_switcher,
                adapter);
        mIsSectionHeader = true;

        // Inflate the view with the given layout
        mSectionHeader = findViewById(R.id.section_header_text);
        mSectionHeaderLayout = findViewById(R.id.section_header);
        setSectionHeader(sectionTitle);


        mTitleView = findViewById(R.id.track_title);
        mAdditionalInfoView = findViewById(R.id.track_additional_information);
        mSeparator = findViewById(R.id.track_separator);
        mDurationView = findViewById(R.id.track_duration);
        mNumberView = findViewById(R.id.track_number);

        mItemIcon = findViewById(R.id.item_image_placeholder);
        mTextLayout = findViewById(R.id.item_track_text_layout);
        mShowIcon = showIcon;
        ViewSwitcher viewSwitcher = findViewById(R.id.item_image_viewswitcher);
        if (showIcon) {
            viewSwitcher.setVisibility(VISIBLE);
            //mTextLayout.setPadding(0, mTextLayout.getPaddingTop(), mTextLayout.getPaddingRight(), mTextLayout.getBottom());
        } else {
            viewSwitcher.setVisibility(GONE);
        }

        /* Show loading text */
        mSeparator.setVisibility(GONE);
        mTitleView.setText(getResources().getText(R.string.track_item_loading));
    }

    /**
     * Simple setter for the title (top line)
     *
     * @param title Title to use
     */
    public void setTitle(String title) {
        mTitleView.setText(title);
    }

    /**
     * Sets the duration of a pre-formatted string (right side)
     *
     * @param duration String of the length
     */
    public void setDuration(String duration) {
        mDurationView.setText(duration);
    }

    /**
     * Sets the track number of this item. (left side)
     *
     * @param number Number of this track
     */
    public void setTrackNumber(String number) {
        mNumberView.setText(number);
    }

    public void showTrackNumber(boolean enable) {
        if (enable) {
            mNumberView.setVisibility(VISIBLE);
        } else {
            mNumberView.setVisibility(GONE);
        }
    }

    /**
     * Extracts the information from a MPDTrack.
     *
     * @param track Track to show the view for.
     */
    public void setTrack(MPDTrack track, boolean useTags) {
        final Context context = getContext();

        if (track != null) {
            String trackNumber;

            if (useTags) {
                if (track.getAlbumDiscCount() > 0) {
                    trackNumber = String.valueOf(track.getDiscNumber()) + '-' + track.getTrackNumber();
                } else {
                    trackNumber = String.valueOf(track.getTrackNumber());
                }

                // Extract the information from the track
                mNumberView.setText(trackNumber);

                int trackLength = track.getLength();
                if (trackLength > 0) {
                    // Get the preformatted duration of the track.
                    mDurationView.setText(FormatHelper.formatTracktimeFromS(track.getLength()));
                    mDurationView.setVisibility(VISIBLE);
                } else {
                    mDurationView.setVisibility(GONE);
                }
                // Get track title
                String trackTitle = track.getVisibleTitle();
                mTitleView.setText(trackTitle);

                // additional information (artist + album)
                String trackInformation = track.getSubLine(context);

                mAdditionalInfoView.setText(trackInformation);
                mSeparator.setVisibility(VISIBLE);
                mAdditionalInfoView.setVisibility(VISIBLE);
                mNumberView.setVisibility(VISIBLE);
            } else {
                mTitleView.setText(track.getFilename());
                mAdditionalInfoView.setText(track.getLastModifiedString());

                mSeparator.setVisibility(GONE);
                mNumberView.setVisibility(GONE);
                mDurationView.setVisibility(GONE);
            }
        } else {
            /* Show loading text */
            mSeparator.setVisibility(GONE);
            mTitleView.setText(getResources().getText(R.string.track_item_loading));
            mNumberView.setVisibility(GONE);
            mDurationView.setVisibility(GONE);
            mAdditionalInfoView.setVisibility(GONE);
        }

        if (mShowIcon) {
            Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_file_48dp);

            if (icon != null) {
                // get tint color
                int tintColor = ThemeUtils.getThemeColor(context, android.R.attr.textColor);
                // tint the icon
                DrawableCompat.setTint(icon, tintColor);
            }
            mItemIcon.setImageDrawable(icon);
        }


    }

    /**
     * Extracts the information from a MPDDirectory
     *
     * @param directory Directory to show the view for.
     */
    public void setDirectory(MPDDirectory directory) {
        final Context context = getContext();

        mTitleView.setText(directory.getSectionTitle());
        mAdditionalInfoView.setText(directory.getLastModifiedString());

        mSeparator.setVisibility(GONE);
        mNumberView.setVisibility(GONE);
        mDurationView.setVisibility(GONE);

        if (mShowIcon) {
            Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_folder_48dp);

            if (icon != null) {
                // get tint color
                int tintColor = ThemeUtils.getThemeColor(context, android.R.attr.textColor);
                // tint the icon
                DrawableCompat.setTint(icon, tintColor);
            }
            mItemIcon.setImageDrawable(icon);
        }
    }

    /**
     * Extracts the information from a MPDPlaylist
     *
     * @param playlist Playlist to show the view for.
     */
    public void setPlaylist(MPDPlaylist playlist) {
        final Context context = getContext();

        mTitleView.setText(playlist.getSectionTitle());
        mAdditionalInfoView.setText(playlist.getLastModifiedString());

        mSeparator.setVisibility(GONE);
        mNumberView.setVisibility(GONE);
        mDurationView.setVisibility(GONE);

        if (mShowIcon) {
            Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_queue_music_black_48dp);

            if (icon != null) {
                // get tint color
                int tintColor = ThemeUtils.getThemeColor(context, android.R.attr.textColor);
                // tint the icon
                DrawableCompat.setTint(icon, tintColor);
            }
            mItemIcon.setImageDrawable(icon);
        }
    }

    /**
     * Sets the header of the view (if one is available)
     *
     * @param header Text to show in the header.
     */
    public void setSectionHeader(String header) {
        if (mIsSectionHeader) {
            mSectionHeader.setText(header);
        }
    }


    public boolean isSectionView() {
        return mIsSectionHeader;
    }

    /**
     * Method that tint the title, number and separator view according to the state.
     *
     * @param state flag indicates if the representing track is currently marked as played by the playbackservice
     */
    public void setPlaying(boolean state) {
        if (state) {
            int color = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
            mTitleView.setTextColor(color);
            mNumberView.setTextColor(color);
            mSeparator.setTextColor(color);
        } else {
            int color = ThemeUtils.getThemeColor(getContext(), R.attr.malp_color_text_background_primary);
            mTitleView.setTextColor(color);
            mNumberView.setTextColor(color);
            mSeparator.setTextColor(color);
        }

    }
}
