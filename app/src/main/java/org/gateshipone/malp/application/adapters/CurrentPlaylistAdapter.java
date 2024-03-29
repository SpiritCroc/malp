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
import android.content.SharedPreferences;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import org.gateshipone.malp.BuildConfig;
import org.gateshipone.malp.R;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.listviewitems.FileListItem;
import org.gateshipone.malp.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.gateshipone.malp.mpdservice.handlers.MPDStatusChangeHandler;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseFileList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDStateMonitoringHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDCapabilities;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class is used to show ListItems that represent the songs in MPDs current playlist.
 * This adapter features two modes. One is the traditional fetch all songs and use the local data
 * set to create views. This is very memory inefficient for long lists.
 * <p/>
 * The second mode fetches only a comparable small block of songs and get a new block of songs if needed.
 * This decreases the memory footprint because the adapter is able to clear unneeded list blocks when
 * not longer needed (e.g. the user scrolled away)
 */
public class CurrentPlaylistAdapter extends BaseAdapter implements ArtworkManager.onNewAlbumImageListener, ScrollSpeedAdapter, SharedPreferences.OnSharedPreferenceChangeListener {
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

    private static final String TAG = CurrentPlaylistAdapter.class.getSimpleName();

    /**
     * Size of the list blocks. Should not be to small to reduce network stress but not to big because
     * they have to stay in the memory (at least 1)
     */
    private static final int WINDOW_SIZE = 500;

    /**
     * Context used for this adapter.
     */
    private final Context mContext;

    /**
     * List of songs that is used if the traditional fetch all mode is active
     */
    private List<MPDFileEntry> mPlaylist = null;

    /**
     * Array of smaller lists that is used if the ranged mode is active
     */
    private List<MPDFileEntry>[] mWindowedPlaylists;

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
     * The last status that was sent by the MPDStateMonitoringHandler. This is used to check
     * if a new playlist is ready or if the highlighted index needs changing.
     */
    private MPDCurrentStatus mLastStatus = null;

    /**
     * ResponseHandler that receives the song list from the MPDQueryHandler because we need an
     * asynchronous reply.
     */
    private final PlaylistFetchResponseHandler mTrackResponseHandler;

    /**
     * This handler receives status updates from the MPDStateMonitoringHandler asychronously.
     */
    private final PlaylistStateListener mStateListener;

    /**
     * This handler reacts on server connects/disconnects.
     */
    private final MPDConnectionStateChangeHandler mConnectionListener;

    /**
     * Listview that is used for showing the songs to the user. Used here to move the list to
     * the currently played song(if changed)
     */
    private ListView mListView;

    /**
     * Configuration variable if the server is new enough to feature the ranged playlist
     * feature. (MPD starting from v. 0.15). This is checked after the onConnect was called.
     */
    private boolean mWindowEnabled = true;

    private boolean mSectionsEnabled = true;

    private final ArtworkManager mArtworkManager;


    /**
     * Public constructor for this adapter
     *
     * @param context  Context for use in this adapter
     * @param listView ListView that will be connected with this adapter.
     */
    public CurrentPlaylistAdapter(Context context, ListView listView) {
        super();
        mContext = context;

        mTrackResponseHandler = new PlaylistFetchResponseHandler(this);
        mStateListener = new PlaylistStateListener(this);
        mConnectionListener = new ConnectionStateChangeListener(this, context.getMainLooper());

        if (null != listView) {
            listView.setAdapter(this);
            mListView = listView;
            mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }
        mListsLock = new ReentrantReadWriteLock();
        mClearTimer = null;

        mArtworkManager = ArtworkManager.getInstance(context.getApplicationContext());

        // Check if sections should be shown
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        mSectionsEnabled = sharedPref.getBoolean(mContext.getString(R.string.pref_show_playlist_sections_key), mContext.getResources().getBoolean(R.bool.pref_show_playlist_sections_default));
    }

    /**
     * @return The number of tracks in the servers playlist. If no status is available 0.
     */
    @Override
    public int getCount() {
        if (null != mLastStatus) {
            return mLastStatus.getPlaylistLength();
        } else {
            return 0;
        }
    }

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
                    convertView = new FileListItem(mContext, false, this);
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


            if (null != mLastStatus && mLastStatus.getCurrentSongIndex() == position) {
                ((FileListItem) convertView).setPlaying(true);
            } else {
                ((FileListItem) convertView).setPlaying(false);
            }

        } else {
            // If the element is not yet received we will show an empty view, that notifies the user about
            // the running fetch.
            if (convertView == null) {
                // If not create a new Listitem
                convertView = new FileListItem(mContext, false, this);
            } else {
                FileListItem tracksListViewItem = (FileListItem) convertView;
                tracksListViewItem.setTrack(null, true);
            }
        }

        // The view that is used for the position in the list
        return convertView;
    }

    /**
     * Sets the highlighted song to the given index. It does not need to changed the data set, because
     * the getView method will check with the last status if the rendering view is active.
     * <p/>
     * This will also move the connected list view to the new position. This ensures that the user will
     * stay in sync with the current song index.
     *
     * @param index Position of the song that is currently played.
     */
    private void setCurrentIndex(int index) {
        if ((index >= 0) && (index < getCount())) {
            notifyDataSetChanged();
            mListView.setSelection(index);
        }
    }

    /**
     * Private class used to receive status updates from MPDStateMonitoringHandler
     */
    private static class PlaylistStateListener extends MPDStatusChangeHandler {

        private final WeakReference<CurrentPlaylistAdapter> mCurrentPlaylistAdapter;

        PlaylistStateListener(final CurrentPlaylistAdapter currentPlaylistAdapter) {
            mCurrentPlaylistAdapter = new WeakReference<>(currentPlaylistAdapter);
        }

        /**
         * Will be called from the MPDStateMonitoringHandler if a new MPDCurrentStatus is ready.
         *
         * @param status
         */
        protected void onNewStatusReady(MPDCurrentStatus status) {
            final CurrentPlaylistAdapter currentPlaylistAdapter = mCurrentPlaylistAdapter.get();

            if (currentPlaylistAdapter != null) {
                currentPlaylistAdapter.updateStatus(status);
            }
        }

        /**
         * Callback not used for this adapter.
         *
         * @param track
         */
        protected void onNewTrackReady(MPDTrack track) {
            /* not needed here */
        }
    }

    private void updateStatus(final MPDCurrentStatus status) {
        boolean newPl = false;
        // Check if the playlist changed or this is called the first time.
        if ((null == mLastStatus) || (mLastStatus.getPlaylistVersion() != status.getPlaylistVersion())) {
            newPl = true;
        }

        // If it is the first status update set the highlighted song to the index of the status.
        if (null == mLastStatus) {
            // The current song index has changed. Set the old one to false and the new one to true.
            int index = status.getCurrentSongIndex();

            if (index < getCount()) {
                setCurrentIndex(index);
            }
        } else {
            // If it is not the first status check if the song index changed and only then move the view.
            if (mLastStatus.getCurrentSongIndex() != status.getCurrentSongIndex()) {
                // The current song index has changed. Set the old one to false and the new one to true.
                int index = status.getCurrentSongIndex();

                setCurrentIndex(index);
            }
        }

        // Save the status for use in other methods of this adapter
        mLastStatus = status;

        // If the playlist changed on the server side, update the internal list state of this adapter.
        if (newPl) {
            updatePlaylist();
        }
    }

    /**
     * Private class to handle asynchronous track responses from MPDQueryHandler. This is used
     * to handle the requested song list.
     */
    private static class PlaylistFetchResponseHandler extends MPDResponseFileList {

        private final WeakReference<CurrentPlaylistAdapter> mCurrentPlaylistAdapter;

        PlaylistFetchResponseHandler(final CurrentPlaylistAdapter currentPlaylistAdapter) {
            mCurrentPlaylistAdapter = new WeakReference<>(currentPlaylistAdapter);
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
            final CurrentPlaylistAdapter currentPlaylistAdapter = mCurrentPlaylistAdapter.get();

            if (currentPlaylistAdapter != null) {
                currentPlaylistAdapter.updatePlaylist(trackList, start);
            }
        }
    }

    private void updatePlaylist(final List<MPDFileEntry> trackList, final int start) {
        // If the ranged playlist feature is disabled
        if (!mWindowEnabled) {
            // Save the new playlist
            mPlaylist = trackList;

            // Set the index active for the currently playing/paused song (if any)
            if (null != mLastStatus) {
                int index = mLastStatus.getCurrentSongIndex();
                if ((null != mPlaylist) && (index < mPlaylist.size())) {
                    setCurrentIndex(index);
                }
            }
            // Notify the listener for this adapter
            notifyDataSetChanged();
        } else {
            // Get the lock to prevent race-conditions.
            mListsLock.writeLock().lock();

            if (mWindowedPlaylists.length <= start / WINDOW_SIZE) {
                // Obviously we received old data here. Abort handling.
                // Crash reported via Google Play (07.11.2016)
                mListsLock.writeLock().unlock();
                return;
            }

            // If a ranged playlist is used, then the list block is saved into the array of list blocks at
            // the position depending on the start position and the WINDOW_SIZE
            mWindowedPlaylists[start / WINDOW_SIZE] = trackList;

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
     * Handler used to react on connects/disconnects from the MPD server.
     */
    private static class ConnectionStateChangeListener extends MPDConnectionStateChangeHandler {
        private final WeakReference<CurrentPlaylistAdapter> mAdapter;

        private ConnectionStateChangeListener(CurrentPlaylistAdapter adapter, Looper looper) {
            super(looper);
            mAdapter = new WeakReference<>(adapter);
        }

        /**
         * Called when the connection to the MPD server is established successfully. This will
         * check if the server supports ranged playlists.
         * <p>
         * After this it will update the playlist to the initial state.
         */
        @Override
        public void onConnected() {
            // Check if connected server version is recent enough
            MPDCapabilities capabilities = MPDInterface.getGenericInstance().getServerCapabilities();
            mAdapter.get().mWindowEnabled = capabilities != null && capabilities.hasRangedCurrentPlaylist();

            mAdapter.get().updatePlaylist();
        }

        /**
         * Called when disconnected from the server. This will clear the list.
         */
        @Override
        public void onDisconnected() {
            mAdapter.get().mPlaylist = null;
            mAdapter.get().mLastStatus = null;
            mAdapter.get().updatePlaylist();
            mAdapter.get().notifyDataSetChanged();
        }
    }

    /**
     * Called from the fragment, that the listview of the adapter is part of.
     * This ensures that the list is refreshed when the user changes into the application.
     */
    public void onResume() {
        // Register to the MPDStateNotifyHandler singleton
        MPDStateMonitoringHandler.getHandler().registerStatusListener(mStateListener);
        MPDInterface.getGenericInstance().addMPDConnectionStateChangeListener(mConnectionListener);


        // Reset old states because it is not ensured that it has any meaning.
        mLastStatus = null;
        updatePlaylist();
        mStateListener.onNewStatusReady(MPDStateMonitoringHandler.getHandler().getLastStatus());
        ArtworkManager.getInstance(mContext.getApplicationContext()).registerOnNewAlbumImageListener(this);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * This will unregister the listeners and clear the playlist.
     */
    public void onPause() {
        // Unregister to the MPDStateNotifyHandler singleton
        MPDStateMonitoringHandler.getHandler().unregisterStatusListener(mStateListener);
        MPDInterface.getGenericInstance().removeMPDConnectionStateChangeListener(mConnectionListener);

        mPlaylist = null;
        ArtworkManager.getInstance(mContext.getApplicationContext()).unregisterOnNewAlbumImageListener(this);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * This methods updates the internal playlist state when the server-side playlist changed.
     */
    private void updatePlaylist() {
        // If ranged playlist is not available just request the complete list.
        if (!mWindowEnabled) {
            // The playlist has changed and we need to fetch a new one.
            MPDQueryHandler.getCurrentPlaylist(mTrackResponseHandler);
        } else {
            // If ranged playlists are available check if we know how many tracks are in the server side list.
            // This determines how many list blocks we need locally.
            if (null != mLastStatus) {
                // Lock list structures
                mListsLock.writeLock().lock();
                // Calculate the number of needed list blocks
                int listCount = (mLastStatus.getPlaylistLength() / WINDOW_SIZE) + 1;
                // Create the array that later contains the list blocks
                mWindowedPlaylists = (List<MPDFileEntry>[]) new List[listCount];

                // Create the state array for the list blocks
                mWindowedListStates = new LIST_STATE[listCount];

                // Reset the last accessed block because it is now invalid.
                mLastAccessedList = 0;

                // Initialize the state and list arrays with a clean state.
                for (int i = 0; i < listCount; i++) {
                    mWindowedPlaylists[i] = null;
                    mWindowedListStates[i] = LIST_STATE.LIST_EMPTY;

                }

                // Release the list lock
                mListsLock.writeLock().unlock();
            }

        }
        notifyDataSetChanged();
    }

    /**
     * Requests the list block for a given list index. This maps the index to the list block index.
     *
     * @param index Index to fetch the block for.
     */
    private void fetchWindow(int index) {
        int tableIndex = index / WINDOW_SIZE;

        int start = tableIndex * WINDOW_SIZE;
        int end = start + WINDOW_SIZE;
        if (end > mLastStatus.getPlaylistLength()) {
            end = mLastStatus.getPlaylistLength();
        }
        MPDQueryHandler.getCurrentPlaylist(mTrackResponseHandler, start, end);
    }

    /**
     * This will return the MPDTrack entry for a given position. This could be null (e.g. block is still fetching).
     *
     * @param position Position of the track to get
     * @return the MPDTrack at position or null if not ready.
     */
    private MPDTrack getTrack(int position) {
        if (!mWindowEnabled) {
            // Check if list is long enough, can be that the new list is not ready yet.
            if (null != mPlaylist && mPlaylist.size() > position) {
                return (MPDTrack) mPlaylist.get(position);
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
                if (listPosition < mWindowedPlaylists[listIndex].size()) {
                    // Return the MPDTrack from the list block.
                    mListsLock.readLock().unlock();
                    return (MPDTrack) mWindowedPlaylists[listIndex].get(position % WINDOW_SIZE);
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

    public void removeAlbumFrom(int position) {
        int rangeEnd = position;

        while (rangeEnd < getCount() && getItemViewType(rangeEnd + 1) != VIEW_TYPES.TYPE_SECTION_TRACK_ITEM.ordinal()) {
            rangeEnd++;
        }

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Remove range from: " + position + " to: " + rangeEnd);
        }

        MPDQueryHandler.removeSongRangeFromCurrentPlaylist(position, rangeEnd);
    }

    /**
     * Moves the listview to the current position. Can be used from outside to position the view
     * when the user changes to the list view
     */
    public void jumpToCurrent() {
        if (null != mLastStatus) {
            setCurrentIndex(mLastStatus.getCurrentSongIndex());
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
            for (int i = 0; i < mWindowedPlaylists.length; i++) {
                if (i != mLastAccessedList) {
                    mWindowedPlaylists[i] = null;
                    mWindowedListStates[i] = LIST_STATE.LIST_EMPTY;
                }
            }
            // Cleanup the timer field
            mClearTimer = null;
            // Release the list lock
            mListsLock.writeLock().unlock();
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
}
