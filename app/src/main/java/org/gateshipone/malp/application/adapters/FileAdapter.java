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

package org.gateshipone.malp.application.adapters;


import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.listviewitems.FileListItem;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDDirectory;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPlaylist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

/**
 * Adapter class that creates all the listitems for an album track view
 */
public class FileAdapter extends GenericSectionAdapter<MPDFileEntry> implements ArtworkManager.onNewDirectoryImageListener {
    private static final String TAG = FileAdapter.class.getSimpleName();
    private final Context mContext;

    /**
     * Defines if items show an icon for their corresponding type
     */
    private final boolean mShowIcons;

    /**
     * Defines if tracks show their tracknumber or their position in the list
     */
    private final boolean mShowTrackNumbers;

    private final boolean mShowSectionItems;

    private final boolean mUseTags;

    private int mListItemHeight;

    private ArtworkManager mArtworkManager;

    private enum VIEW_TYPES {
        TYPE_FILE_ITEM,
        TYPE_SECTION_FILE_ITEM,
        TYPE_COUNT
    }

    /**
     * Standard constructor
     *
     * @param context          Context used for creating listview items
     * @param showIcons        If icons should be shown in view (e.g. for FileExplorer)
     * @param showTrackNumbers If track numbers should be used for index or the position (Albums: tracknumbers, playlists: indices)
     */
    public FileAdapter(Context context, boolean showIcons, boolean showTrackNumbers) {
        this(context, showIcons, showTrackNumbers, false, true);
    }

    /**
     * Standard constructor
     *
     * @param context          Context used for creating listview items
     * @param showIcons        If icons should be shown in view (e.g. for FileExplorer)
     * @param showTrackNumbers If track numbers should be used for index or the position (Albums: tracknumbers, playlists: indices)
     */
    public FileAdapter(Context context, boolean showIcons, boolean showTrackNumbers, boolean showSectionItems, boolean useTags) {
        super();

        mShowIcons = showIcons;
        mContext = context;
        mShowTrackNumbers = showTrackNumbers;

        mShowSectionItems = showSectionItems;

        mUseTags = useTags;

        mListItemHeight = (int)context.getResources().getDimension(R.dimen.material_list_item_height);

        mArtworkManager = ArtworkManager.getInstance(context.getApplicationContext());

        // Disable sections as they cause troubles for directories,files,playlists order and repeating starting letters
        enableSections(false);
    }

    /**
     * Returns the type (section track or normal track) of the item at the given position.
     * If preceding {@link MPDFileEntry} is not a {@link MPDTrack} it will generate a secion entry.
     * Else it will check if the preceding {@link MPDTrack} is another album.
     *
     * @param position Position of the item in question
     * @return the int value of the enum {@link CurrentPlaylistAdapter.VIEW_TYPES}
     */
    @Override
    public int getItemViewType(int position) {
        // Get MPDTrack at the given index used for this item.
        MPDFileEntry file = (MPDFileEntry) getItem(position);
        if (file instanceof MPDTrack) {
            boolean newAlbum = false;
            MPDTrack track = (MPDTrack) file;
            MPDFileEntry previousFile;

            if (position > 0) {
                previousFile = (MPDFileEntry) getItem(position - 1);
                if (previousFile != null) {
                    if (previousFile instanceof MPDTrack) {
                        MPDTrack previousTrack = (MPDTrack) previousFile;
                        newAlbum = !previousTrack.equalsStringTag(MPDTrack.StringTagTypes.ALBUM, track);
                    }
                }
            } else {
                return VIEW_TYPES.TYPE_SECTION_FILE_ITEM.ordinal();
            }

            return newAlbum ? VIEW_TYPES.TYPE_SECTION_FILE_ITEM.ordinal() : VIEW_TYPES.TYPE_FILE_ITEM.ordinal();
        } else {
            return VIEW_TYPES.TYPE_FILE_ITEM.ordinal();
        }
    }

    /**
     * @return The count of values in the enum {@link CurrentPlaylistAdapter.VIEW_TYPES}.
     */
    @Override
    public int getViewTypeCount() {
        return VIEW_TYPES.TYPE_COUNT.ordinal();
    }

    /**
     * Create the actual listview items if no reusable object is provided.
     *
     * @param position    Index of the item to create.
     * @param convertView If != null this view can be reused to optimize performance.
     * @param parent      Parent of the view
     * @return
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get MPDTrack at the given index used for this item.
        MPDFileEntry file = (MPDFileEntry) getItem(position);

        /*
         * Check which type of {@link MPDFileEntry} is necessary to draw ({@link MPDTrack}, {@link MPDDirectory}, {@link MPDPlaylist})
         *
         * For {@link MPDTrack} objects it optionally checks if a new album is started and shows the album cover and name.
         */

        if (file instanceof MPDTrack) {
            MPDTrack track = (MPDTrack) file;
            // Normal file entry
            if (!mShowSectionItems || (VIEW_TYPES.values()[getItemViewType(position)] == VIEW_TYPES.TYPE_FILE_ITEM)) {
                if (convertView == null) {
                    convertView = new FileListItem(mContext, mShowIcons, this);
                }
                ((FileListItem) convertView).setTrack(track, mUseTags);
                if (!mShowTrackNumbers) {
                    ((FileListItem) convertView).setTrackNumber(String.valueOf(position + 1));
                }

                // This will prepare the view for fetching the image from the internet if not already saved in local database.
                // Dummy MPDAlbum
                MPDAlbum tmpAlbum = track.getAlbum();
                ((FileListItem) convertView).prepareArtworkFetching(ArtworkManager.getInstance(mContext.getApplicationContext()), tmpAlbum);

                // Start async image loading if not scrolling at the moment. Otherwise the ScrollSpeedListener
                // starts the loading.
                if (mScrollSpeed == 0) {
                    ((FileListItem) convertView).startCoverImageTask();
                }

                return convertView;
            } else {
                // Section items
                if (convertView == null) {
                    // If not create a new Listitem
                    convertView = new FileListItem(mContext, track.getStringTag(MPDTrack.StringTagTypes.ALBUM), mShowIcons, this);
                }

                FileListItem tracksListViewItem = (FileListItem) convertView;
                tracksListViewItem.setSectionHeader(track.getStringTag(MPDTrack.StringTagTypes.ALBUM));
                tracksListViewItem.setTrack(track, mUseTags);
                if (!mShowTrackNumbers) {
                    tracksListViewItem.setTrackNumber(String.valueOf(position + 1));
                }

                // This will prepare the view for fetching the image from the internet if not already saved in local database.
                // Dummy MPDAlbum
                MPDAlbum tmpAlbum = track.getAlbum();
                ((FileListItem) convertView).prepareArtworkFetching(ArtworkManager.getInstance(mContext.getApplicationContext()), tmpAlbum);

                // Start async image loading if not scrolling at the moment. Otherwise the ScrollSpeedListener
                // starts the loading.
                if (mScrollSpeed == 0) {
                    ((FileListItem) convertView).startCoverImageTask();
                }
                return convertView;
            }
        } else if (file instanceof MPDDirectory) {
            FileListItem listItem;
            if (convertView == null) {
                listItem = new FileListItem(mContext, mShowIcons, this);
            } else {
                listItem = (FileListItem) convertView;
            }

            listItem.setDirectory((MPDDirectory) file);

            // This will prepare the view for fetching the image from the internet if not already saved in local database.
            listItem.prepareArtworkFetching(mArtworkManager, file);

            // Check if the scroll speed currently is already 0, then start the image task right away.
            if (mScrollSpeed == 0) {
                listItem.setImageDimension(mListItemHeight, mListItemHeight);
                listItem.startCoverImageTask();
            }
            return listItem;
        } else if (file instanceof MPDPlaylist) {
            if (convertView == null) {
                convertView = new FileListItem(mContext, mShowIcons, this);
            }

            ((FileListItem) convertView).setPlaylist((MPDPlaylist) file);
            return convertView;
        }
        return new FileListItem(mContext, mShowIcons, this);
    }

    @Override
    public void newDirectoryImage(MPDDirectory directory) {
        notifyDataSetChanged();
    }
}
