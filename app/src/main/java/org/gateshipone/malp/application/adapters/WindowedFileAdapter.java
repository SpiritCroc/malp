/*
 *  Copyright (C) 2024 Team Gateship-One
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
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.preference.PreferenceManager;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.listviewitems.FileListItem;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseFileList;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class WindowedFileAdapter extends BaseAdapter implements ArtworkManager.onNewAlbumImageListener, ScrollSpeedAdapter, SharedPreferences.OnSharedPreferenceChangeListener {
    private final static String TAG = WindowedFileAdapter.class.getSimpleName();
    /**
     * States of list blocks.
     */
    private enum LIST_STATE {
        // List is not available and not fetching
        LIST_EMPTY,
        // List is currently enqueued for fetching from the server
        LIST_LOADING,
        // List is ready to be used for creating views
        LIST_READY
    }

    public enum VIEW_TYPES {
        TYPE_TRACK_ITEM,
        TYPE_SECTION_TRACK_ITEM,
        TYPE_COUNT
    }

    /**
     * Variable to store the current scroll speed. Used for image view optimizations
     */
    private int mScrollSpeed;

    /**
     * Determines how the new time value affects the average (0.0(new value has no effect) - 1.0(average is only the new value, no smoothing)
     */
    private static final float mSmoothingFactor = 0.3f;

    /**
     * Smoothed average(exponential smoothing) value
     */
    private long mAvgImageTime;

    /**
     * Time to wait until old list blocks are removed from the memory. (30s)
     */
    private static final int CLEANUP_TIMEOUT = 30 * 1000;


    /**
     * Size of the list blocks. Should not be to small to reduce network stress but not to big because
     * they have to stay in the memory (at least 1)
     */
    private static final int WINDOW_SIZE = 500;

    /**
     * Context used for this adapter.
     */
    private final Context mContext;

    private final ArtworkManager mArtworkManager;

    /**
     * List of songs that is used if the traditional fetch all mode is active
     */
    private List<MPDFileEntry> mNonWindowedFileList = null;

    /**
     * Array of smaller lists that is used if the ranged mode is active
     */
    private List<MPDFileEntry>[] mWindowedFileLists;

    /**
     * Array that represents the state of the list blocks necessary for the amount of tracks
     * in MPDs playlist.
     */
    private LIST_STATE[] mWindowedListStates;

    /**
     * This holds the index of the last accessed list block. This ensures that at least the currently
     * viewed items stay in memory.
     */
    private int mLastAccessedList;

    /**
     * Semaphore to synchronize list access from cleanup task and the UI thread.
     */
    private final ReadWriteLock mListsLock;

    /**
     * Timer that is used for triggering the clean up of unneeded list blocks.
     */
    private Timer mClearTimer;

    /**
     * ResponseHandler that receives the song list from the MPDQueryHandler because we need an
     * asynchronous reply.
     */
    private final FileListFetchResponseHandler mTrackResponseHandler;

    private boolean mSectionsEnabled = true;

    public WindowedFileAdapter(Context context) {
        mContext = context;

        mListsLock = new ReentrantReadWriteLock();
        mTrackResponseHandler = new FileListFetchResponseHandler(this);
        mClearTimer = null;

        // Check if sections should be shown
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        mSectionsEnabled = sharedPref.getBoolean(mContext.getString(R.string.pref_show_playlist_sections_key), mContext.getResources().getBoolean(R.bool.pref_show_playlist_sections_default));

        mArtworkManager = ArtworkManager.getInstance(context.getApplicationContext());
    }



    public abstract int getCount();

    /**
     * Returns an Object that is part of the data set of this adapter
     *
     * @param position Position of the object.
     * @return The requested object itself or null if not available.
     */
    @Override
    public Object getItem(int position) {
        return getTrack(position);

    }

    /**
     * Returns the type (section track or normal track) of the item at the given position
     *
     * @param position Position of the item in question
     * @return the int value of the enum {@link VIEW_TYPES}
     */
    @Override
    public int getItemViewType(int position) {
        // If playlist sections are disabled, just return track type here
        if (!mSectionsEnabled) {
            return VIEW_TYPES.TYPE_TRACK_ITEM.ordinal();
        }
        // Get MPDTrack at the given index used for this item.
        MPDTrack track = getTrack(position);
        boolean newAlbum = false;

        // Check if the track was available in local data set already (or is currently fetching)
        if (track != null) {
            MPDTrack previousTrack;
            if (position > 0) {
                previousTrack = getTrack(position - 1);
                if (previousTrack != null) {
                    newAlbum = !track.equalsStringTag(MPDTrack.StringTagTypes.ALBUM, previousTrack);
                }
            } else {
                return VIEW_TYPES.TYPE_SECTION_TRACK_ITEM.ordinal();
            }
        }
        return newAlbum ? VIEW_TYPES.TYPE_SECTION_TRACK_ITEM.ordinal() : VIEW_TYPES.TYPE_TRACK_ITEM.ordinal();
    }

    /**
     * @return The count of values in the enum {@link VIEW_TYPES}.
     */
    @Override
    public int getViewTypeCount() {
        return VIEW_TYPES.TYPE_COUNT.ordinal();
    }

    /**
     * Returns an id for an position. Currently it is just the position itself.
     *
     * @param position Position to get the id for.
     * @return The id of the position.
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    private void receiveFileList(final List<MPDFileEntry> trackList, final int start) {
        // If the ranged playlist feature is disabled
        if (!isWindowed()) {

            // Save the new playlist
            mNonWindowedFileList = trackList;

            // Notify the listener for this adapter
            notifyDataSetChanged();
        } else {
            // Get the lock to prevent race-conditions.
            mListsLock.writeLock().lock();

            if (mWindowedFileLists == null || mWindowedFileLists.length <= start / WINDOW_SIZE) {
                // Obviously we received old data here. Abort handling.
                // Crash reported via Google Play (07.11.2016)
                mListsLock.writeLock().unlock();
                return;
            }

            // If a ranged playlist is used, then the list block is saved into the array of list blocks at
            // the position depending on the start position and the WINDOW_SIZE
            mWindowedFileLists[start / WINDOW_SIZE] = trackList;

            // Set the list state to ready.
            mWindowedListStates[start / WINDOW_SIZE] = LIST_STATE.LIST_READY;

            // Check if a clean up timer is already running and cancel it in case.
            if (null != mClearTimer) {
                mClearTimer.cancel();
            }
            // Start a new cleanup task to cleanup old mess
            mClearTimer = new Timer();
            mClearTimer.schedule(new ListCleanUp(), CLEANUP_TIMEOUT);

            // Relinquish the lock again
            mListsLock.writeLock().unlock();

            // Notify the system that the internal data changed. This will change "loading" track
            // views to the finished ones.
            notifyDataSetChanged();
        }
    }

    /**
     * This methods updates the internal playlist state when the server-side playlist changed.
     */
    protected void updateFileList() {
        // If ranged playlist is not available just request the complete list.
        if (!isWindowed()) {
            // Remove windowed list if used
            mWindowedFileLists = null;
            mWindowedListStates = null;
            // The playlist has changed and we need to fetch a new one.
            fetchDataNonWindowed(mTrackResponseHandler);
        } else {
            // Remove unwindowed tracklist if used
            mNonWindowedFileList = null;
            // If ranged playlists are available check if we know how many tracks are in the server side list.
            // This determines how many list blocks we need locally.
            // Lock list structures
            mListsLock.writeLock().lock();
            // Calculate the number of needed list blocks
            int listCount = (getCount() / WINDOW_SIZE) + 1;
            // Create the array that later contains the list blocks
            mWindowedFileLists = (List<MPDFileEntry>[]) new List[listCount];

            // Create the state array for the list blocks
            mWindowedListStates = new LIST_STATE[listCount];

            // Reset the last accessed block because it is now invalid.
            mLastAccessedList = 0;

            // Initialize the state and list arrays with a clean state.
            for (int i = 0; i < listCount; i++) {
                mWindowedFileLists[i] = null;
                mWindowedListStates[i] = LIST_STATE.LIST_EMPTY;
            }

            // Release the list lock
            mListsLock.writeLock().unlock();
        }
        notifyDataSetChanged();
    }

    protected abstract boolean isWindowed();
    protected abstract void fetchDataWindowed(MPDResponseFileList responseHandler, int start, int end);
    protected abstract void fetchDataNonWindowed(MPDResponseFileList responseHandler);

    /**
     * Requests the list block for a given list index. This maps the index to the list block index.
     *
     * @param index Index to fetch the block for.
     */
    private void fetchWindow(int index) {
        int tableIndex = index / WINDOW_SIZE;

        int start = tableIndex * WINDOW_SIZE;
        int end = start + WINDOW_SIZE;
        if (end > getCount()) {
            end = getCount();
        }
        fetchDataWindowed(mTrackResponseHandler, start, end);
    }

    /**
     * This will return the MPDTrack entry for a given position. This could be null (e.g. block is still fetching).
     *
     * @param position Position of the track to get
     * @return the MPDTrack at position or null if not ready.
     */
    protected MPDTrack getTrack(int position) {
        if (!isWindowed()) {
            // Check if list is long enough, can be that the new list is not ready yet.
            if (null != mNonWindowedFileList && mNonWindowedFileList.size() > position) {
                return (MPDTrack) mNonWindowedFileList.get(position);
            } else {
                return null;
            }
        } else {
            // If ranged playlist is activated calculate the index of the requested list block.
            int listIndex = position / WINDOW_SIZE;

            // Block the list
            mListsLock.readLock().lock();

            // Check if this list block is already available.
            if (mWindowedListStates[listIndex] == LIST_STATE.LIST_READY) {
                // Save that the list index was the last accessed one
                mLastAccessedList = listIndex;

                // Calculate the position of the MPDTrack within the list block
                int listPosition = position % WINDOW_SIZE;
                if (listPosition < mWindowedFileLists[listIndex].size()) {
                    // Return the MPDTrack from the list block.
                    mListsLock.readLock().unlock();
                    return (MPDTrack) mWindowedFileLists[listIndex].get(position % WINDOW_SIZE);
                }
                mListsLock.readLock().unlock();
            } else if (mWindowedListStates[position / WINDOW_SIZE] == LIST_STATE.LIST_EMPTY) {
                // If the list is not yet available, request it with the method fetchWindow and set the state
                // to LIST_LOADING.
                mWindowedListStates[position / WINDOW_SIZE] = LIST_STATE.LIST_LOADING;
                mListsLock.readLock().unlock();
                fetchWindow(position);
            } else {
                mListsLock.readLock().unlock();
            }


        }
        return null;
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
        MPDTrack track = getTrack(position);

        // Check if the track was available in local data set already (or is currently fetching)
        if (track != null) {
            String trackAlbum = track.getStringTag(MPDTrack.StringTagTypes.ALBUM);

            VIEW_TYPES type = VIEW_TYPES.values()[getItemViewType(position)];
            // Normal track item type
            if (type == VIEW_TYPES.TYPE_TRACK_ITEM) {
                if (convertView == null) {
                    // If not create a new Listitem
                    convertView = new FileListItem(mContext, false, null);
                }
                FileListItem tracksListViewItem = (FileListItem) convertView;
                tracksListViewItem.setTrack(track, true);
                tracksListViewItem.setTrackNumber(String.valueOf(position + 1));
            } else if (type == VIEW_TYPES.TYPE_SECTION_TRACK_ITEM) { // Section track type.
                if (convertView == null) {
                    // If not create a new Listitem
                    convertView = new FileListItem(mContext, trackAlbum, false, this);
                }
                FileListItem tracksListViewItem = (FileListItem) convertView;
                tracksListViewItem.setSectionHeader(trackAlbum);
                tracksListViewItem.setTrack(track, true);
                tracksListViewItem.setTrackNumber(String.valueOf(position + 1));

                // This will prepare the view for fetching the image from the internet if not already saved in local database.
                ((FileListItem) convertView).prepareArtworkFetching(mArtworkManager, track);

                // Start async image loading if not scrolling at the moment. Otherwise the ScrollSpeedListener
                // starts the loading.
                if (mScrollSpeed == 0) {
                    ((FileListItem) convertView).startCoverImageTask();
                }
            }
        } else {
            // If the element is not yet received we will show an empty view, that notifies the user about
            // the running fetch.
            if (convertView == null) {
                // If not create a new Listitem
                convertView = new FileListItem(mContext, false, null);
            } else {
                FileListItem tracksListViewItem = (FileListItem) convertView;
                tracksListViewItem.setTrack(null, true);
            }
        }

        // The view that is used for the position in the list
        return convertView;
    }

    /**
     * Called from the fragment, that the listview of the adapter is part of.
     * This ensures that the list is refreshed when the user changes into the application.
     */
    public void onResume() {
        ArtworkManager.getInstance(mContext.getApplicationContext()).registerOnNewAlbumImageListener(this);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
    }

    public void onPause() {
        ArtworkManager.getInstance(mContext.getApplicationContext()).unregisterOnNewAlbumImageListener(this);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Sets the scrollspeed in items per second.
     *
     * @param speed Items per seconds as Integer.
     */
    public void setScrollSpeed(int speed) {
        mScrollSpeed = speed;
    }

    /**
     * Returns the smoothed average loading time of images.
     * This value is used by the scrollspeed listener to determine if
     * the scrolling is slow enough to render images (artist, album images)
     *
     * @return Average time to load an image in ms
     */
    public long getAverageImageLoadTime() {
        return mAvgImageTime == 0 ? 1 : mAvgImageTime;
    }

    /**
     * This method adds new loading times to the smoothed average.
     * Should only be called from the async cover loader.
     *
     * @param time Time in ms to load a image
     */
    public void addImageLoadTime(long time) {
        // Implement exponential smoothing here
        if (mAvgImageTime == 0) {
            mAvgImageTime = time;
        } else {
            mAvgImageTime = (long) (((1 - mSmoothingFactor) * mAvgImageTime) + (mSmoothingFactor * time));
        }
    }

    @Override
    public void newAlbumImage(MPDAlbum album) {
        notifyDataSetChanged();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(mContext.getString(R.string.pref_show_playlist_sections_key))) {
            mSectionsEnabled = sharedPreferences.getBoolean(mContext.getString(R.string.pref_show_playlist_sections_key), mContext.getResources().getBoolean(R.bool.pref_show_playlist_sections_default));
            notifyDataSetInvalidated();
        }
    }

    /**
     * Task used for cleaning unnecessary list blocks.
     */
    private class ListCleanUp extends TimerTask {
        @Override
        public void run() {
            // Lock the list structures
            mListsLock.writeLock().lock();


            // Cleanup all but the currently active list block.
            for (int i = 0; i < mWindowedFileLists.length; i++) {
                if (i != mLastAccessedList) {
                    mWindowedFileLists[i] = null;
                    mWindowedListStates[i] = LIST_STATE.LIST_EMPTY;
                }
            }
            // Cleanup the timer field
            mClearTimer = null;
            // Release the list lock
            mListsLock.writeLock().unlock();
        }
    }

    /**
     * Private class to handle asynchronous track responses from MPDQueryHandler. This is used
     * to handle the requested song list.
     */
    private static class FileListFetchResponseHandler extends MPDResponseFileList {

        private final WeakReference<WindowedFileAdapter> mWindowedFileAdapter;

        FileListFetchResponseHandler(final WindowedFileAdapter windowedFileAdapter) {
            mWindowedFileAdapter = new WeakReference<>(windowedFileAdapter);
        }

        /**
         * Called when a list of songs is ready.
         *
         * @param trackList List of MPDTrack objects containing a list of mpds tracks.
         * @param start     If a range was given to the request initially this contains the start of the window
         * @param end       If a range was given to the request initially this contains the end of the window
         */
        @Override
        public void handleTracks(List<MPDFileEntry> trackList, int start, int end) {
            final WindowedFileAdapter windowedFileAdapter = mWindowedFileAdapter.get();

            if (windowedFileAdapter != null) {
                windowedFileAdapter.receiveFileList(trackList, start);
            }
        }
    }
}
