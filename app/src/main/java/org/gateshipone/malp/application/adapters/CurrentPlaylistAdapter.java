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
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.gateshipone.malp.BuildConfig;
import org.gateshipone.malp.application.listviewitems.FileListItem;
import org.gateshipone.malp.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.gateshipone.malp.mpdservice.handlers.MPDStatusChangeHandler;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseFileList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDStateMonitoringHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDCapabilities;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.lang.ref.WeakReference;

/**
 * This class is used to show ListItems that represent the songs in MPDs current playlist.
 * This adapter features two modes. One is the traditional fetch all songs and use the local data
 * set to create views. This is very memory inefficient for long lists.
 * <p/>
 * The second mode fetches only a comparable small block of songs and get a new block of songs if needed.
 * This decreases the memory footprint because the adapter is able to clear unneeded list blocks when
 * not longer needed (e.g. the user scrolled away)
 */
public class CurrentPlaylistAdapter extends WindowedFileAdapter {
    private static final String TAG = CurrentPlaylistAdapter.class.getSimpleName();

    /**
     * The last status that was sent by the MPDStateMonitoringHandler. This is used to check
     * if a new playlist is ready or if the highlighted index needs changing.
     */
    private MPDCurrentStatus mLastStatus = null;

    /**
     * This handler receives status updates from the MPDStateMonitoringHandler asynchronously.
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

    /**
     * Public constructor for this adapter
     *
     * @param context  Context for use in this adapter
     * @param listView ListView that will be connected with this adapter.
     */
    public CurrentPlaylistAdapter(Context context, ListView listView) {
        super(context);

        mStateListener = new PlaylistStateListener(this);
        mConnectionListener = new ConnectionStateChangeListener(this, context.getMainLooper());

        if (null != listView) {
            listView.setAdapter(this);
            mListView = listView;
            mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }
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

    @Override
    protected boolean isWindowed() {
        return mWindowEnabled;
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
        View view = super.getView(position, convertView, parent);
        if (null != mLastStatus && mLastStatus.getCurrentSongIndex() == position) {
            ((FileListItem) view).setPlaying(true);
        } else {
            ((FileListItem) view).setPlaying(false);
        }

        return view;
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
            updateFileList();
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
            mAdapter.get().updateFileList();
        }

        /**
         * Called when disconnected from the server. This will clear the list.
         */
        @Override
        public void onDisconnected() {
            mAdapter.get().mLastStatus = null;
            mAdapter.get().updateFileList();
            mAdapter.get().notifyDataSetChanged();
        }
    }

    /**
     * Called from the fragment, that the listview of the adapter is part of.
     * This ensures that the list is refreshed when the user changes into the application.
     */
    public void onResume() {
        super.onResume();
        // Register to the MPDStateNotifyHandler singleton
        MPDStateMonitoringHandler.getHandler().registerStatusListener(mStateListener);
        MPDInterface.getGenericInstance().addMPDConnectionStateChangeListener(mConnectionListener);

        mWindowEnabled = MPDInterface.getGenericInstance().getServerCapabilities().hasRangedCurrentPlaylist();

        // Reset old states because it is not ensured that it has any meaning.
        mLastStatus = null;
    }

    /**
     * This will unregister the listeners and clear the playlist.
     */
    public void onPause() {
        super.onPause();
        // Unregister to the MPDStateNotifyHandler singleton
        MPDStateMonitoringHandler.getHandler().unregisterStatusListener(mStateListener);
        MPDInterface.getGenericInstance().removeMPDConnectionStateChangeListener(mConnectionListener);
    }


    protected void fetchDataWindowed(MPDResponseFileList responseHandler, int start, int end) {
        if (MPDInterface.getGenericInstance().isConnected()) {
            MPDQueryHandler.getCurrentPlaylist(responseHandler, start, end);
        }
    }

    protected void fetchDataNonWindowed(MPDResponseFileList responseHandler) {
        if (MPDInterface.getGenericInstance().isConnected()) {
            MPDQueryHandler.getCurrentPlaylist(responseHandler);
        }
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
}
