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

package org.gateshipone.malp.mpdservice.handlers.serverhandler;


import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;

import org.gateshipone.malp.application.utils.FormatHelper;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseFileList;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseHandler;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseGenericList;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResonseGenericObject;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDCapabilities;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDCommands;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDException;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFilterObject;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDOutput;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPartition;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPlaytime;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDStatistics;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This handler is used for all long running queries to the mpd server. This includes:
 * database requests, playlists, outputs, current playlist, searches, file listings.
 * <p/>
 * To request certain items the caller needs to provide an instance of another Handler, called ResponseHandlers,
 * that ensure that the return of the requested values is also done asynchronously.
 * <p/>
 * Requests should look like this:
 * <p/>
 * UI-Thread --> QueryHandler |(send message to another thread)-->    {@link MPDInterface}
 * UI-Thread<--(send message to another thread)<--ResponseHandler <-- {@link MPDInterface}
 */
public class MPDQueryHandler extends MPDGenericHandler {
    private static final String TAG = "MPDQueryHandler";
    /**
     * Name of the thread created for the Looper.
     */
    private static final String THREAD_NAME = "AndroMPD-QueryHandler";


    /**
     * HandlerThread that is used by the looper. This ensures that all requests to this handler
     * are done multi-threaded and do not block the UI.
     */
    private static HandlerThread mHandlerThread = null;
    private static MPDQueryHandler mHandlerSingleton = null;

    /**
     * Private constructor for use in singleton. Called by the static singleton retrieval method.
     *
     * @param looper Looper of a HandlerThread (that is NOT the UI thread)
     */
    protected MPDQueryHandler(Looper looper) {
        super(looper);
    }

    /**
     * Private method to ensure that the singleton runs in a separate thread.
     * Otherwise android will deny network access because of UI blocks.
     *
     * @return Singleton instance
     */
    public synchronized static MPDQueryHandler getHandler() {
        // Check if handler was accessed before. If not create the singleton object for the first
        // time.
        if (null == mHandlerSingleton) {
            // Create a new thread used as a looper for this handler.
            // This is the thread in which all messages sent to this handler are handled.
            mHandlerThread = new HandlerThread(THREAD_NAME);
            // It is important to start the thread before using it as a thread for the Handler.
            // Otherwise the handler will cause a crash.
            mHandlerThread.start();
            // Create the actual singleton instance.
            mHandlerSingleton = new MPDQueryHandler(mHandlerThread.getLooper());
        }
        return mHandlerSingleton;
    }


    /**
     * This is the main entry point of messages.
     * Here all possible messages types need to be handled with the MPDConnection.
     * Have a look into the baseclass MPDGenericHandler for more information about the handling.
     *
     * @param msg Message to process.
     */
    @Override
    public void handleMessage(Message msg) {
        // Call the baseclass handleMessage method here to ensure that the messages handled
        // by the baseclass are handled in subclasses as well.
        super.handleMessage(msg);

        // Type checking
        if (!(msg.obj instanceof MPDHandlerAction)) {
            /* Check if the message object is of correct type. Otherwise just abort here. */
            return;
        }

        MPDHandlerAction mpdAction = (MPDHandlerAction) msg.obj;

        // ResponseHandler used to return the requested items to the caller
        MPDResponseHandler responseHandler;

        /*
         * All messages are handled the same way:
         *  * Check which action was requested
         *  * Check if a ResponseHandler is necessary and also provided. (If not just abort here)
         *  * Request the list of data objects from the MPDConnection (and therefor from the server)
         *  * Pack the response in a Message requested from the given ResponseHandler.
         *  * Send the message to the ResponseHandler
         */
        MPDHandlerAction.NET_HANDLER_ACTION action = mpdAction.getAction();
        try {
            if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ALBUMS) {
                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseGenericList)) {
                    return;
                }

                Pair<String,String> tagPair = null;
                String tagName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_NAME);
                String tagValue = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_VALUE);
                if (tagName != null && tagValue != null && !tagName.isEmpty() && !tagValue.isEmpty()) {
                    tagPair = new Pair<>(tagName, tagValue);
                }


                List<MPDAlbum> albumList = MPDInterface.getGenericInstance().getAlbums(tagPair);

                ((MPDResponseGenericList<MPDAlbum>) responseHandler).sendList(albumList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ALBUMS_IN_PATH) {
                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseGenericList)) {
                    return;
                }
                String path = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH);
                List<MPDAlbum> albumList = MPDInterface.getGenericInstance().getAlbumsInPath(path);

                ((MPDResponseGenericList<MPDAlbum>) responseHandler).sendList(albumList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_ALBUMS_IN_PATH) {
                String path = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH);
                List<MPDAlbum> albumList = MPDInterface.getGenericInstance().getAlbumsInPath(path);

                MPDInterface.getGenericInstance().clearPlaylist();
                for (MPDAlbum album : albumList) {
                    MPDInterface.getGenericInstance().addAlbumTracks(album, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR.MPD_ALBUM_ARTIST_SELECTOR_ARTIST, MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTIST);
                }
                MPDInterface.getGenericInstance().playSongIndex(0);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ARTIST_ALBUMS) {
                String artistName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_ARTIST_NAME);
                MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector = mpdAction.getAlbumArtistSelector();
                MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector = mpdAction.getArtistSortSelector();
                MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder = mpdAction.getAlbumSortOrder();
                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseGenericList) || (null == artistName)) {
                    return;
                }

                List<MPDAlbum> albumList = MPDInterface.getGenericInstance().getArtistAlbums(artistName, albumArtistSelector, artistSortSelector, sortOrder);
                ((MPDResponseGenericList<MPDAlbum>) responseHandler).sendList(albumList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ARTISTS) {
                responseHandler = mpdAction.getResponseHandler();
                MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector = mpdAction.getAlbumArtistSelector();
                MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector = mpdAction.getArtistSortSelector();

                if (!(responseHandler instanceof MPDResponseGenericList)) {
                    return;
                }

                Pair<String,String> tagPair = null;
                String tagName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_NAME);
                String tagValue = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_VALUE);
                if (tagName != null && tagValue != null && !tagName.isEmpty() && !tagValue.isEmpty()) {
                    tagPair = new Pair<>(tagName, tagValue);
                }

                List<MPDArtist> artistList = MPDInterface.getGenericInstance().getArtists(albumArtistSelector, artistSortSelector, tagPair);

                ((MPDResponseGenericList<MPDArtist>) responseHandler).sendList(artistList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ALBUM_TRACKS) {
                MPDAlbum album = mpdAction.getMPDAlbum();
                responseHandler = mpdAction.getResponseHandler();
                MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector = mpdAction.getAlbumArtistSelector();
                MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector = mpdAction.getArtistSortSelector();

                if (!(responseHandler instanceof MPDResponseFileList) || (null == album)) {
                    return;
                }

                List<MPDFileEntry> trackList = MPDInterface.getGenericInstance().getAlbumTracks(album, albumArtistSelector, artistSortSelector);
                ((MPDResponseFileList) responseHandler).sendFileList(trackList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ARTWORK_TRACKS) {
                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseFileList)) {
                    return;
                }

                List<MPDFileEntry> trackList = MPDInterface.getGenericInstance().getAllTracks();

                // Get the set of directories
                final HashMap<String, MPDTrack> albumPaths = new HashMap<>();

                // Get a list of unique album folders
                for (MPDFileEntry track : trackList) {
                    if (track instanceof MPDTrack) {
                        String dirPath = FormatHelper.getDirectoryFromPath(track.getPath());
                        if (!albumPaths.containsKey(dirPath)) {
                            albumPaths.put(FormatHelper.getDirectoryFromPath(track.getPath()), (MPDTrack) track);
                        }
                    }
                }
                trackList.clear();

                // Get tags for all tracks
                for (MPDTrack track : albumPaths.values()) {
                    List<MPDFileEntry> tempFiles = MPDInterface.getGenericInstance().getFiles(track.getPath());
                    if (tempFiles.size() == 1) {
                        MPDFileEntry file = tempFiles.get(0);
                        if (file instanceof MPDTrack) {
                            trackList.add(file);
                        }
                    }
                }

                ((MPDResponseFileList) responseHandler).sendFileList(trackList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ARTIST_ALBUM_TRACKS) {
                MPDAlbum album = mpdAction.getMPDAlbum();
                MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector = mpdAction.getAlbumArtistSelector();
                MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector = mpdAction.getArtistSortSelector();

                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseFileList) || (null == album)) {
                    return;
                }

                List<MPDFileEntry> trackList;

                trackList = MPDInterface.getGenericInstance().getAlbumTracks(album, albumArtistSelector, artistSortSelector);

                ((MPDResponseFileList) responseHandler).sendFileList(trackList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_CURRENT_PLAYLIST) {
                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseFileList)) {
                    return;
                }

                List<MPDFileEntry> trackList = MPDInterface.getGenericInstance().getCurrentPlaylist();

                ((MPDResponseFileList) responseHandler).sendFileList(trackList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_CURRENT_PLAYLIST_WINDOW) {
                int start = mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_START);
                int end = mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_END);
                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseFileList)) {
                    return;
                }

                List<MPDFileEntry> trackList = MPDInterface.getGenericInstance().getCurrentPlaylistWindow(start, end);
                ((MPDResponseFileList) responseHandler).sendFileList(trackList, start, end);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_TAG_FILTERED_SONGS_WINDOWED) {
                int start = mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_START);
                int end = mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_END);
                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseFileList)) {
                    return;
                }

                List<MPDFileEntry> list;

                Pair<String,String> tagPair = null;
                String tagName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_NAME);
                String tagValue = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_VALUE);
                if (tagName != null && tagValue != null && !tagName.isEmpty() && !tagValue.isEmpty()) {
                    tagPair = new Pair<>(tagName, tagValue);
                    list = MPDInterface.getGenericInstance().getTagFilteredSongs(tagPair, start, end);
                } else {
                    list = new ArrayList<>();
                }

                ((MPDResponseFileList) responseHandler).sendFileList(list, start, end);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_SAVED_PLAYLIST) {
                String playlistName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME);
                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseFileList)) {
                    return;
                }

                List<MPDFileEntry> trackList = MPDInterface.getGenericInstance().getSavedPlaylist(playlistName);

                ((MPDResponseFileList) responseHandler).sendFileList(trackList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_SAVED_PLAYLISTS) {
                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseFileList)) {
                    return;
                }

                List<MPDFileEntry> playlistList = MPDInterface.getGenericInstance().getPlaylists();

                ((MPDResponseFileList) responseHandler).sendFileList(playlistList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_SAVE_PLAYLIST) {
                String playlistName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME);

                MPDInterface.getGenericInstance().savePlaylist(playlistName);

            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_SONG_TO_PLAYLIST) {
                String playlistName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME);
                String path = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH);

                MPDInterface.getGenericInstance().addSongToPlaylist(playlistName, path);

            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_REMOVE_SONG_FROM_PLAYLIST) {
                String playlistName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME);
                int position = mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SONG_INDEX);

                MPDInterface.getGenericInstance().removeSongFromPlaylist(playlistName, position);

            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_REMOVE_PLAYLIST) {
                String playlistName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME);

                MPDInterface.getGenericInstance().removePlaylist(playlistName);

            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_LOAD_PLAYLIST) {
                String playlistName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME);


                MPDInterface.getGenericInstance().loadPlaylist(playlistName);

            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_PLAYLIST) {
                String playlistName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME);

                MPDInterface.getGenericInstance().clearPlaylist();
                MPDInterface.getGenericInstance().loadPlaylist(playlistName);
                MPDInterface.getGenericInstance().playSongIndex(0);

            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_NEW_PARTITION) {
                String partitionName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PARTITION_NAME);

                MPDInterface.getGenericInstance().newPartition(partitionName);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_DELETE_PARTITION) {
                String partitionName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PARTITION_NAME);

                MPDInterface.getGenericInstance().deletePartition(partitionName);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_SWITCH_PARTITION) {
                String partitionName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PARTITION_NAME);

                MPDInterface.getGenericInstance().switchPartition(partitionName, true);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_ARTIST_ALBUM) {
                MPDAlbum album = mpdAction.getMPDAlbum();
                MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector = mpdAction.getAlbumArtistSelector();
                MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector = mpdAction.getArtistSortSelector();

                MPDInterface.getGenericInstance().addAlbumTracks(album, albumArtistSelector, artistSortSelector);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_ARTIST_ALBUM) {
                MPDAlbum album = mpdAction.getMPDAlbum();
                MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector = mpdAction.getAlbumArtistSelector();
                MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector = mpdAction.getArtistSortSelector();

                MPDInterface.getGenericInstance().clearPlaylist();
                MPDInterface.getGenericInstance().addAlbumTracks(album, albumArtistSelector, artistSortSelector);
                MPDInterface.getGenericInstance().playSongIndex(0);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_ARTIST) {
                String artistname = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_ARTIST_NAME);
                MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder = MPDAlbum.MPD_ALBUM_SORT_ORDER.values()[mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SORT_ORDER)];
                MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector = mpdAction.getAlbumArtistSelector();
                MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector = mpdAction.getArtistSortSelector();

                MPDInterface.getGenericInstance().addArtist(artistname, sortOrder, albumArtistSelector, artistSortSelector);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_ARTIST) {
                String artistname = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_ARTIST_NAME);
                MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder = MPDAlbum.MPD_ALBUM_SORT_ORDER.values()[mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SORT_ORDER)];
                MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector = mpdAction.getAlbumArtistSelector();
                MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector = mpdAction.getArtistSortSelector();

                MPDInterface.getGenericInstance().clearPlaylist();
                MPDInterface.getGenericInstance().addArtist(artistname, sortOrder, albumArtistSelector, artistSortSelector);
                MPDInterface.getGenericInstance().playSongIndex(0);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_PATH) {
                String url = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SONG_URL);

                MPDInterface.getGenericInstance().addSong(url);

            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_PATH_AT_INDEX) {
                String url = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SONG_URL);
                Integer index = mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SONG_INDEX_DESTINATION);

                MPDInterface.getGenericInstance().addSongatIndex(url, index);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_SONG_NEXT) {
                String url = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SONG_URL);


                MPDCurrentStatus status = MPDInterface.getGenericInstance().getCurrentServerStatus();
                MPDInterface.getGenericInstance().addSongatIndex(url, status.getCurrentSongIndex() + 1);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_SONG) {
                String url = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SONG_URL);
                MPDCapabilities caps = MPDInterface.getGenericInstance().getServerCapabilities();
                /*
                 * Check if song is already enqueued in the current playlist. If it is get the position
                 * and just jump to the song position.
                 *
                 * Otherwise add it to the last playlist position and jump there.
                 */
                List<MPDFileEntry> playlistFindTracks = null;
                if (caps != null && caps.hasPlaylistFind()) {
                    playlistFindTracks = MPDInterface.getGenericInstance().getPlaylistFindTrack(url);
                }
                if (playlistFindTracks != null && playlistFindTracks.size() > 0) {
                    // Song already found in the playlist. Jump there.
                    MPDInterface.getGenericInstance().playSongIndex(((MPDTrack) playlistFindTracks.get(0)).getSongPosition());
                } else {
                    // Not part of the current playlist. Add it at the end of the playlist and play it from there.
                    MPDInterface.getGenericInstance().addSong(url);
                    MPDCurrentStatus status = MPDInterface.getGenericInstance().getCurrentServerStatus();
                    MPDInterface.getGenericInstance().playSongIndex(status.getPlaylistLength() - 1);
                }
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_CLEAR_CURRENT_PLAYLIST) {
                MPDInterface.getGenericInstance().clearPlaylist();
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_SHUFFLE_CURRENT_PLAYLIST) {
                MPDInterface.getGenericInstance().shufflePlaylist();
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_MOVE_SONG_AFTER_CURRENT) {

                MPDCurrentStatus status = MPDInterface.getGenericInstance().getCurrentServerStatus();
                int index = mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SONG_INDEX);
                if (index < status.getCurrentSongIndex()) {
                    MPDInterface.getGenericInstance().moveSongFromTo(index, status.getCurrentSongIndex());
                } else if (index > status.getCurrentSongIndex()) {
                    MPDInterface.getGenericInstance().moveSongFromTo(index, status.getCurrentSongIndex() + 1);
                }
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_REMOVE_SONG_FROM_CURRENT_PLAYLIST) {
                int index = mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SONG_INDEX);
                MPDInterface.getGenericInstance().removeIndex(index);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_REMOVE_RANGE_FROM_CURRENT_PLAYLIST) {
                int start = mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_START);
                int end = mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_END);

                MPDInterface.getGenericInstance().removeRange(start, end);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_FILES) {
                String path = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH);

                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseFileList)) {
                    return;
                }

                List<MPDFileEntry> fileList = MPDInterface.getGenericInstance().getFiles(path);

                ((MPDResponseFileList) responseHandler).sendFileList(fileList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_DIRECTORY) {
                String path = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH);

                MPDInterface.getGenericInstance().clearPlaylist();
                MPDInterface.getGenericInstance().addSong(path);
                MPDInterface.getGenericInstance().playSongIndex(0);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_OUTPUTS) {
                responseHandler = mpdAction.getResponseHandler();

                List<MPDOutput> outputList = MPDInterface.getGenericInstance().getOutputs();

                ((MPDResponseGenericList<MPDOutput>) responseHandler).sendList(outputList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_OUTPUTS_ALL_PARTITIONS) {
                responseHandler = mpdAction.getResponseHandler();

                List<MPDOutput> outputList = MPDInterface.getGenericInstance().getAllPartitionOutputs();

                ((MPDResponseGenericList<MPDOutput>) responseHandler).sendList(outputList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_PARTITIONS) {
                responseHandler = mpdAction.getResponseHandler();

                List<MPDPartition> partitionList = MPDInterface.getGenericInstance().getPartitions();

                ((MPDResponseGenericList) responseHandler).sendList(partitionList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_TAG_FILTER_ENTRIES) {
                responseHandler = mpdAction.getResponseHandler();
                String tagName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_NAME);

                List<MPDFilterObject> filterList = MPDInterface.getGenericInstance().getTagEntries(tagName);

                ((MPDResponseGenericList) responseHandler).sendList(filterList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_MOVE_OUTPUT_TO_PARTITION) {
                String output = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_OUTPUT_NAME);
                String partition = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PARTITION_NAME);

                MPDInterface.getGenericInstance().moveOutputToPartition(output, partition);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_TOGGLE_PARTITION_OUTPUT) {
                String partition = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PARTITION_NAME);
                int outputId = mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_OUTPUT_ID);

                MPDInterface.getGenericInstance().toggleOutputPartition(outputId, partition);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_SERVER_STATISTICS) {
                responseHandler = mpdAction.getResponseHandler();

                MPDStatistics stats;
                stats = MPDInterface.getGenericInstance().getServerStatistics();
                ((MPDResonseGenericObject) responseHandler).sendObject(stats);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_TAG_FILTERED_SONG_COUNT) {
                responseHandler = mpdAction.getResponseHandler();

                Pair<String,String> tagPair = null;
                String tagName = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_NAME);
                String tagValue = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_VALUE);
                MPDPlaytime playtime;
                if (tagName != null && tagValue != null && !tagName.isEmpty() && !tagValue.isEmpty()) {
                    tagPair = new Pair<>(tagName, tagValue);
                    playtime = MPDInterface.getGenericInstance().getTagFilterSongCount(tagPair);
                } else {
                    playtime = new MPDPlaytime();
                }

                ((MPDResonseGenericObject) responseHandler).sendObject(playtime);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_UPDATE_DATABASE) {

                String updatePath = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH);

                MPDInterface.getGenericInstance().updateDatabase(updatePath);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_SEARCH_FILES) {
                String term = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SEARCH_TERM);
                MPDCommands.MPD_SEARCH_TYPE type = MPDCommands.MPD_SEARCH_TYPE.values()[mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SEARCH_TYPE)];

                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseFileList)) {
                    return;
                }

                List<MPDFileEntry> fileList = MPDInterface.getGenericInstance().getSearchedFiles(term, type);

                ((MPDResponseFileList) responseHandler).sendFileList(fileList);
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_SEARCH_FILES) {
                String term = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SEARCH_TERM);
                MPDCommands.MPD_SEARCH_TYPE type = MPDCommands.MPD_SEARCH_TYPE.values()[mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SEARCH_TYPE)];

                MPDCapabilities caps = MPDInterface.getGenericInstance().getServerCapabilities();

                // Check if server has the add search result capability
                if (null != caps && caps.hasSearchAdd()) {
                    MPDInterface.getGenericInstance().addSearchedFiles(term, type);
                } else {
                    // Fetch search results and add them
                    List<MPDFileEntry> searchResults = MPDInterface.getGenericInstance().getSearchedFiles(term, type);
                    MPDInterface.getGenericInstance().addTrackList(searchResults);
                }
            } else if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_SEARCH_FILES) {
                String term = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SEARCH_TERM);
                MPDCommands.MPD_SEARCH_TYPE type = MPDCommands.MPD_SEARCH_TYPE.values()[mpdAction.getIntExtra(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SEARCH_TYPE)];

                MPDInterface.getGenericInstance().clearPlaylist();

                // Check if server has the add search result capability
                if (MPDInterface.getGenericInstance().getServerCapabilities().hasSearchAdd()) {
                    MPDInterface.getGenericInstance().addSearchedFiles(term, type);
                } else {
                    // Fetch search results and add them
                    List<MPDFileEntry> searchResults = MPDInterface.getGenericInstance().getSearchedFiles(term, type);
                    MPDInterface.getGenericInstance().addTrackList(searchResults);
                }

                MPDInterface.getGenericInstance().playSongIndex(0);
            }
        } catch (MPDException e) {
            handleMPDError(e);
        }
    }


    /*
     * These static methods provide the only interface to outside classes.
     * They should not be allowed to interact with the instance itself.
     *
     * All of these methods work with the same principle. They all create an handler message
     * that will contain a MPDHandlerAction as a payload that contains all the information
     * for the requested action with extras.
     */


    /**
     * Serializes an action into a message and sends it.
     *
     * @param action to be sent out.
     */
    private static void sendMsg(MPDHandlerAction action) {
        Message msg = Message.obtain();
        if (null == msg) {
            return;
        }

        msg.obj = action;

        MPDQueryHandler.getHandler().sendMessage(msg);
    }

    private static void genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION action,
                                            MPDHandlerAction.NET_HANDLER_EXTRA_STRING actionString, String url) {
        MPDHandlerAction handlerAction = new MPDHandlerAction(action);

        handlerAction.setStringExtra(actionString, url);

        sendMsg(handlerAction);
    }

    private static void genericEmptyAction(MPDHandlerAction.NET_HANDLER_ACTION action) {
        MPDHandlerAction handlerAction = new MPDHandlerAction(action);

        sendMsg(handlerAction);
    }

    private static void genericIntAction(MPDHandlerAction.NET_HANDLER_ACTION action,
                                         MPDHandlerAction.NET_HANDLER_EXTRA_INT intAction, int index) {
        MPDHandlerAction handlerAction = new MPDHandlerAction(action);

        handlerAction.setIntExtras(intAction, index);

        sendMsg(handlerAction);
    }

    private static void genericStringIntAction(MPDHandlerAction.NET_HANDLER_ACTION action,
                                               MPDHandlerAction.NET_HANDLER_EXTRA_STRING actionString, String str,
                                               MPDHandlerAction.NET_HANDLER_EXTRA_INT actionInt, int index) {
        MPDHandlerAction handlerAction = new MPDHandlerAction(action);

        handlerAction.setStringExtra(actionString, str);
        handlerAction.setIntExtras(actionInt, index);

        sendMsg(handlerAction);
    }

    private static void genericStringResponseAction(MPDHandlerAction.NET_HANDLER_ACTION action,
                                                    MPDHandlerAction.NET_HANDLER_EXTRA_STRING actionString, String path,
                                                    MPDResponseGenericList responseHandler) {
        MPDHandlerAction handlerAction = new MPDHandlerAction(action);

        handlerAction.setStringExtra(actionString, path);
        handlerAction.setResponseHandler(responseHandler);

        sendMsg(handlerAction);
    }

    private static void genericResponseStringAction(MPDHandlerAction.NET_HANDLER_ACTION action,
                                                    MPDResponseFileList responseHandler,
                                                    MPDHandlerAction.NET_HANDLER_EXTRA_STRING actionString, String path) {
        MPDHandlerAction handlerAction = new MPDHandlerAction(action);

        handlerAction.setResponseHandler(responseHandler);
        handlerAction.setStringExtra(actionString, path);

        sendMsg(handlerAction);
    }

    /**
     * Method to retrieve a list of all albums available on the currently connected MPD server.
     *
     * @param responseHandler The Handler that is used for asynchronous callback calls when the result
     *                        of the MPD server is ready and parsed.
     */
    public static void getAlbums(MPDResponseGenericList<MPDAlbum> responseHandler, Pair<String, String> tagFilter) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ALBUMS);

        if (tagFilter != null) {
            action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_NAME, tagFilter.first);
            action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_VALUE, tagFilter.second);
        }

        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }

    /**
     * Method to retrieve a list of all albums available on the currently connected MPD server.
     * This only shows album that lay in the given path. This feature is only available for servers
     * >= 0.19.
     *
     * @param path            Path to list albums for
     * @param responseHandler The Handler that is used for asynchronous callback calls when the result
     *                        of the MPD server is ready and parsed.
     */
    public static void getAlbumsInPath(String path, MPDResponseGenericList<MPDAlbum> responseHandler) {
        genericStringResponseAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ALBUMS_IN_PATH,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH, path,
                responseHandler);
    }

    /**
     * Method to retrieve a list of all albums available on the currently connected MPD server.
     * This only shows album that lay in the given path. This feature is only available for servers
     * >= 0.19.
     *
     * @param path Path to list albums for
     */
    public static void playAlbumsInPath(String path) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_ALBUMS_IN_PATH,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH, path);
    }

    /**
     * Requests a list of albums of an artist.
     *
     * @param responseHandler The handler used to send the requested data
     * @param artist          Artist to get a list of albums from.
     */
    public static void getArtistAlbums(MPDResponseGenericList<MPDAlbum> responseHandler, String artist, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector, MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ARTIST_ALBUMS);
        action.setAlbumArtistSelector(albumArtistSelector);
        action.setArtistSortSelector(artistSortSelector);
        action.setAlbumSortOrder(sortOrder);
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_ARTIST_NAME, artist);

        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }

    /**
     * Requests a list of all the artists available on this server
     *
     * @param responseHandler The handler used to send the requested data
     */
    public static void getArtists(MPDResponseHandler responseHandler, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector, Pair<String, String> tagFilter) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ARTISTS);
        action.setAlbumArtistSelector(albumArtistSelector);
        action.setArtistSortSelector(artistSortSelector);

        if (tagFilter != null) {
            action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_NAME, tagFilter.first);
            action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_VALUE, tagFilter.second);
        }

        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }

    /**
     * Requests a list of tracks (MPDFileEntry objects).
     *
     * @param responseHandler The handler used to send the requested data
     */
    public static void getAllTracks(MPDResponseFileList responseHandler) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ARTWORK_TRACKS);

        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }

    /**
     * Requests a list of tracks (MPDFileEntry) on an album. This method will also filter the results
     * with a given artistname
     *
     * @param responseHandler The handler used to send the requested data
     * @param album {@link MPDAlbum} to fetch tracks for
     */
    public static void getArtistAlbumTracks(MPDResponseFileList responseHandler, MPDAlbum album, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ARTIST_ALBUM_TRACKS);

        action.setResponseHandler(responseHandler);
        action.setMPDAlbum(album);
        action.setAlbumArtistSelector(albumArtistSelector);
        action.setArtistSortSelector(artistSortSelector);

        sendMsg(action);
    }

    /**
     * Requests a list of all tracks enlisted in the current playlist.
     *
     * @param responseHandler The handler used to send the requested data
     */
    public static void getCurrentPlaylist(MPDResponseFileList responseHandler) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_CURRENT_PLAYLIST);

        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }

    /**
     * Requests a list of tracks enlisted in the current playlist.
     * This method is able to request a partial list to speed up the query and lower the network
     * usage.
     *
     * @param responseHandler The handler used to send the requested data
     */
    public static void getCurrentPlaylist(MPDResponseFileList responseHandler, int start, int end) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_CURRENT_PLAYLIST_WINDOW);

        action.setResponseHandler(responseHandler);
        action.setIntExtras(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_START, start);
        action.setIntExtras(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_END, end);

        sendMsg(action);
    }

    /**
     * Requests a list of songs filtered by a tag name/value pair.
     *
     * @param responseHandler The handler used to send the requested data
     */
    public static void getTagFilteredSongs(MPDResponseFileList responseHandler,Pair<String, String> tagFilter, int start, int end) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_TAG_FILTERED_SONGS_WINDOWED);

        action.setResponseHandler(responseHandler);
        action.setIntExtras(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_START, start);
        action.setIntExtras(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_END, end);
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_NAME, tagFilter.first);
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_VALUE, tagFilter.second);

        sendMsg(action);
    }

    /**
     * Requests a list of playlists saved on the server.
     *
     * @param responseHandler The handler used to send the requested data
     */
    public static void getSavedPlaylists(MPDResponseFileList responseHandler) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_SAVED_PLAYLISTS);

        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }

    /**
     * Returns a list of tracks listed in a saved playlist.
     *
     * @param responseHandler The handler used to send the requested data
     * @param playlistName    Name of the playlist to get the tracks from.
     */
    public static void getSavedPlaylist(MPDResponseFileList responseHandler, String playlistName) {
        genericResponseStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_SAVED_PLAYLIST,
                responseHandler,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME, playlistName);
    }

    /**
     * Requests a list of files for a specified path. If no path is given the database root is used.
     *
     * @param responseHandler The handler used to send the requested data
     * @param path            Path to get the files/directory/playlist from
     */
    public static void getFiles(MPDResponseFileList responseHandler, String path) {
        genericResponseStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_FILES,
                responseHandler,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH, path);
    }

    /**
     * Requests a list of available outputs configured on the MPD server.
     *
     * @param responseHandler The handler used to send the requested data.
     */
    public static void getOutputs(MPDResponseGenericList<MPDOutput> responseHandler) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_OUTPUTS);

        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }

    /**
     * Requests a list of available outputs for all partitions configured on the MPD server.
     *
     * @param responseHandler The handler used to send the requested data.
     */
    public static void getOutputsAllPartitions(MPDResponseGenericList<MPDOutput> responseHandler) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_OUTPUTS_ALL_PARTITIONS);

        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }

    /**
     * Requests a list of available partitions configured on the MPD server.
     *
     * @param responseHandler The handler used to send the requested data.
     */
    public static void getPartitions(MPDResponseGenericList responseHandler) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_PARTITIONS);

        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }

    /**
     * Requests a statistics object for the connected mpd server.
     *
     * @param responseHandler The handler used to send the requested data.
     */
    public static void getStatistics(MPDResonseGenericObject responseHandler) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_SERVER_STATISTICS);

        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }

    /**
     * Requests the number of songs for a given pair of tag name/value
     *
     * @param responseHandler The handler used to send the requested data.
     */
    public static void getTagFilteredSongCount(MPDResonseGenericObject responseHandler, Pair<String,String> tagFilter) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_TAG_FILTERED_SONG_COUNT);
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_NAME, tagFilter.first);
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_VALUE, tagFilter.second);

        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }

    /**
     * Adds all tracks from an album (filtered with an artist name) to the current playlist.
     *
     * @param album {@link MPDAlbum} to fetch tracks for
     */
    public static void addArtistAlbum(MPDAlbum album, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_ARTIST_ALBUM);
        action.setAlbumArtistSelector(albumArtistSelector);
        action.setArtistSortSelector(artistSortSelector);
        action.setMPDAlbum(album);

        sendMsg(action);
    }


    /**
     * Adds an album to the current playlist and start playing it
     *
     * @param album {@link MPDAlbum} to play tracks of
     */
    public static void playArtistAlbum(MPDAlbum album, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_ARTIST_ALBUM);
        action.setAlbumArtistSelector(albumArtistSelector);
        action.setArtistSortSelector(artistSortSelector);
        action.setMPDAlbum(album);

        sendMsg(action);
    }

    /**
     * Adds all albums from an artist to the current playlist.
     *
     * @param artistname Name of the artist to add to the current playlist.
     */
    public static void addArtist(String artistname, MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_ARTIST);
        action.setAlbumArtistSelector(albumArtistSelector);
        action.setArtistSortSelector(artistSortSelector);
        action.setIntExtras(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SORT_ORDER, sortOrder.ordinal());
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_ARTIST_NAME, artistname);

        sendMsg(action);
    }

    /**
     * Adds all albums from an artist to the current playlist and starts playing them.
     *
     * @param artistname Name of the artist to play its albums
     */
    public static void playArtist(String artistname, MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_ARTIST);
        action.setAlbumArtistSelector(albumArtistSelector);
        action.setArtistSortSelector(artistSortSelector);
        action.setIntExtras(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SORT_ORDER, sortOrder.ordinal());
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_ARTIST_NAME, artistname);

        sendMsg(action);
    }

    /**
     * Adds a path to the current playlist. Can be a file or directory
     *
     * @param url URL of the path to add.
     */
    public static void addPath(String url) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_PATH,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SONG_URL, url);
    }

    /**
     * Prepends a path to the current playlist. Can be a file or directory
     *
     * @param url URL of the path to add.
     */
    public static void addPathAtStart(String url) {
        addPathAtIndex(url, 0);
    }

    /**
     * Adds a path to the current playlist at specific index. Can be a file or directory
     *
     * @param url URL of the path to add.
     */
    public static void addPathAtIndex(String url, Integer index) {
        genericStringIntAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_PATH_AT_INDEX,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SONG_URL, url,
                MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SONG_INDEX_DESTINATION, index);
    }

    public static void playDirectory(String url) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_DIRECTORY,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH, url);
    }


    public static void playSong(String url) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_SONG,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SONG_URL, url);
    }

    public static void playSongNext(String url) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_SONG_NEXT,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SONG_URL, url);
    }

    public static void clearPlaylist() {
        genericEmptyAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_CLEAR_CURRENT_PLAYLIST);
    }

    public static void shufflePlaylist() {
        genericEmptyAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_SHUFFLE_CURRENT_PLAYLIST);
    }


    public static void removeSongFromCurrentPlaylist(int index) {
        genericIntAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_REMOVE_SONG_FROM_CURRENT_PLAYLIST,
                MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SONG_INDEX, index);
    }

    public static void removeSongRangeFromCurrentPlaylist(int start, int end) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_REMOVE_RANGE_FROM_CURRENT_PLAYLIST);

        action.setIntExtras(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_START, start);
        action.setIntExtras(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_WINDOW_END, end);

        sendMsg(action);
    }

    public static void playIndexAsNext(int index) {
        genericIntAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_MOVE_SONG_AFTER_CURRENT,
                MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SONG_INDEX, index);
    }

    public static void savePlaylist(String name) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_SAVE_PLAYLIST,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME, name);
    }

    public static void addURLToSavedPlaylist(String playlistName, String url) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_SONG_TO_PLAYLIST);

        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME, playlistName);
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH, url);

        sendMsg(action);
    }

    public static void removeSongFromSavedPlaylist(String playlistName, int position) {
        genericStringIntAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_REMOVE_SONG_FROM_PLAYLIST,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME, playlistName,
                MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SONG_INDEX, position);
    }

    public static void removePlaylist(String name) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_REMOVE_PLAYLIST,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME, name);
    }

    public static void loadPlaylist(String name) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_LOAD_PLAYLIST,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME, name);
    }

    public static void playPlaylist(String name) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_PLAYLIST,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PLAYLIST_NAME, name);
    }

    public static void newPartition(String name) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_NEW_PARTITION,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PARTITION_NAME, name);
    }

    public static void deletePartition(String name) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_DELETE_PARTITION,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PARTITION_NAME, name);
    }

    public static void switchPartition(String name) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_SWITCH_PARTITION,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PARTITION_NAME, name);
    }

    public static void moveOutputToPartition(MPDOutput output, MPDPartition partition) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_MOVE_OUTPUT_TO_PARTITION);

        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_OUTPUT_NAME, output.getOutputName());
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PARTITION_NAME, partition.getPartitionName());

        sendMsg(action);
    }

    public static void toggleOutputPartition(MPDOutput output) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_TOGGLE_PARTITION_OUTPUT);

        action.setIntExtras(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_OUTPUT_ID, output.getID());
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PARTITION_NAME, output.getPartitionName());

        sendMsg(action);
    }

    public static void updateDatabase(String path) {
        genericStringAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_UPDATE_DATABASE,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH, path);
    }

    /**
     * Requests a list of files matching the search term and type
     *
     * @param term            The string to search for
     * @param type            The type of items to search for
     * @param responseHandler The handler used to send the requested data.
     */
    public static void searchFiles(String term, MPDCommands.MPD_SEARCH_TYPE type, MPDResponseFileList responseHandler) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_SEARCH_FILES);

        action.setResponseHandler(responseHandler);
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SEARCH_TERM, term);
        action.setIntExtras(MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SEARCH_TYPE, type.ordinal());

        sendMsg(action);
    }

    /**
     * Requests to add a search request
     *
     * @param term The string to search for
     * @param type The type of items to search for
     */
    public static void searchAddFiles(String term, MPDCommands.MPD_SEARCH_TYPE type) {
        genericStringIntAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_ADD_SEARCH_FILES,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SEARCH_TERM, term,
                MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SEARCH_TYPE, type.ordinal());
    }

    /**
     * Requests to play a search result
     *
     * @param term The string to search for
     * @param type The type of items to search for
     */
    public static void searchPlayFiles(String term, MPDCommands.MPD_SEARCH_TYPE type) {
        genericStringIntAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_PLAY_SEARCH_FILES,
                MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_SEARCH_TERM, term,
                MPDHandlerAction.NET_HANDLER_EXTRA_INT.EXTRA_SEARCH_TYPE, type.ordinal());
    }

    public static void getTagFilterEntries(String tagName, MPDResponseGenericList<MPDFilterObject> responseHandler) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_TAG_FILTER_ENTRIES);
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_TAG_NAME, tagName);
        action.setResponseHandler(responseHandler);

        sendMsg(action);
    }
}
