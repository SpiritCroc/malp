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

package org.gateshipone.malp.mpdservice.mpdprotocol;


import android.util.Log;

import org.gateshipone.malp.BuildConfig;
import org.gateshipone.malp.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.gateshipone.malp.mpdservice.handlers.MPDIdleChangeHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDOutput;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPartition;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDStatistics;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public class MPDInterface {
    private static final String TAG = MPDInterface.class.getSimpleName();

    private final MPDConnection mConnection;

    private static String mHostname;
    private static int mPort;
    private static String mPassword;

    private static String mPartition;

    private MPDCache mCache;

    private static final long MAX_IMAGE_SIZE = 50 * 1024 * 1024; // 50 MB

    private MPDInterface(boolean autoDisconnect) {
        mConnection = new MPDConnection(autoDisconnect);
        mCache = new MPDCache(0);
    }

    public static synchronized MPDInterface getGenericInstance() {
        if (mGenericInterface == null) {
            mGenericInterface = new MPDInterface(false);
            mGenericInterface.setInstanceServerParameters(mHostname, mPassword, mPort);
        }

        return mGenericInterface;
    }

    public static synchronized MPDInterface getArtworkInstance() {
        if (mArtworkInterface == null) {
            mArtworkInterface = new MPDInterface(true);
            mArtworkInterface.setInstanceServerParameters(mHostname, mPassword, mPort);
        }

        return mArtworkInterface;
    }

    public static synchronized void memoryPressure() {
        if (mArtworkInterface != null) {
            mArtworkInterface.invalidateCache();
        }

        if (mGenericInterface != null) {
            mGenericInterface.invalidateCache();
        }
    }

    // Connection methods

    private static MPDInterface mArtworkInterface;
    private static MPDInterface mGenericInterface;

    public void setServerParameters(String hostname, String password, int port, String partition) {
        mHostname = hostname;
        mPassword = password;
        mPort = port;

        mPartition = partition;

        if (mGenericInterface != null) {
            mGenericInterface.setInstanceServerParameters(hostname, password, port);
        }
        if (mArtworkInterface != null) {
            mArtworkInterface.setInstanceServerParameters(hostname, password, port);
        }
    }

    private void setInstanceServerParameters(String hostname, String password, int port) {
        mConnection.setServerParameters(hostname, password, port);
    }

    public synchronized void connect() throws MPDException {
        mConnection.connectToServer();

        if (mPartition != null && !mPartition.equals("") && !mPartition.equals("default")) {
            try {
                switchPartition(mPartition, false);
            } catch (MPDException e) {
                Log.w(TAG, "Invalid partition (" + mPartition + "), fail silently");
            }
        }
        invalidateCache();
    }

    public synchronized void disconnect() {
        mConnection.disconnectFromServer();
    }

    public boolean isConnected() {
        return mConnection.isConnected();
    }

    // Observer methods
    public void addMPDConnectionStateChangeListener(MPDConnectionStateChangeHandler listener) {
        mConnection.addConnectionStateChangeHandler(listener);
    }

    public void removeMPDConnectionStateChangeListener(MPDConnectionStateChangeHandler listener) {
        mConnection.removeConnectionStateChangeHandler(listener);
    }

    public void addMPDIdleChangeHandler(MPDIdleChangeHandler listener) {
        mConnection.setIdleListener(listener);
    }


    /*
     * **********************
     * * Request functions  *
     * **********************
     */
    public MPDCapabilities getServerCapabilities() {
        return mConnection.getServerCapabilities();
    }

    /**
     * Get a list of all albums available in the database.
     *
     * @return List of MPDAlbum
     */
    public List<MPDAlbum> getAlbums() throws MPDException {
        List<MPDAlbum> albums;
        checkCacheState();

        synchronized (mCache) {
            albums = mCache.getCachedAlbums();
        }
        if (albums != null) {
            return albums;
        }

        synchronized (this) {
            // Get a list of albums. Check if server is new enough for MB and AlbumArtist filtering
            mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ALBUMS(mConnection.getServerCapabilities()));

            // Remove empty albums at beginning of the list
            albums = MPDResponseParser.parseMPDAlbums(mConnection);
        }
        ListIterator<MPDAlbum> albumIterator = albums.listIterator();
        while (albumIterator.hasNext()) {
            MPDAlbum album = albumIterator.next();
            if (album.getName().isEmpty()) {
                albumIterator.remove();
            } else {
                break;
            }
        }

        synchronized (mCache) {
            mCache.cacheAlbums(albums);
        }
        return albums;
    }

    /**
     * Get a list of all albums available in the database.
     *
     * @return List of MPDAlbum
     */
    public List<MPDAlbum> getAlbumsInPath(String path) throws MPDException {
        List<MPDAlbum> albums;

        synchronized (this) {
            // Get a list of albums. Check if server is new enough for MB and AlbumArtist filtering
            mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ALBUMS_FOR_PATH(path, mConnection.getServerCapabilities()));

            // Remove empty albums at beginning of the list
            albums = MPDResponseParser.parseMPDAlbums(mConnection);
        }
        ListIterator<MPDAlbum> albumIterator = albums.listIterator();
        while (albumIterator.hasNext()) {
            MPDAlbum album = albumIterator.next();
            if (album.getName().isEmpty()) {
                albumIterator.remove();
            } else {
                break;
            }
        }
        return albums;
    }


    /**
     * Get a list of all albums by an artist where artist is part of or artist is the AlbumArtist (tag)
     *
     * @param artistName Artist to filter album list with.
     * @return List of MPDAlbum objects
     */
    public List<MPDAlbum> getArtistAlbums(String artistName, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector, MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder) throws MPDException {
        MPDCapabilities capabilities;
        synchronized (this) {
            capabilities = mConnection.getServerCapabilities();
        }

        List<MPDAlbum> result;
        synchronized (mCache) {
            result = mCache.getCachedArtistAlbumsRequest(artistName, albumArtistSelector, artistSortSelector, sortOrder);
        }

        if (result != null) {
            return result;
        }

        synchronized (this) {
            if (albumArtistSelector == MPDArtist.MPD_ALBUM_ARTIST_SELECTOR.MPD_ALBUM_ARTIST_SELECTOR_ARTIST && artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTIST) {
                mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ARTIST_ALBUMS(artistName, capabilities));
            } else if (albumArtistSelector == MPDArtist.MPD_ALBUM_ARTIST_SELECTOR.MPD_ALBUM_ARTIST_SELECTOR_ALBUMARTIST && artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTIST) {
                mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ALBUMARTIST_ALBUMS(artistName, capabilities));
            } else if (albumArtistSelector == MPDArtist.MPD_ALBUM_ARTIST_SELECTOR.MPD_ALBUM_ARTIST_SELECTOR_ARTIST && artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTISTSORT) {
                mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ARTISTSORT_ALBUMS(artistName, capabilities));
            } else if (albumArtistSelector == MPDArtist.MPD_ALBUM_ARTIST_SELECTOR.MPD_ALBUM_ARTIST_SELECTOR_ALBUMARTIST && artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTISTSORT) {
                mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ALBUMARTISTSORT_ALBUMS(artistName, capabilities));
            }
            result = MPDResponseParser.parseMPDAlbums(mConnection);
        }

        if (!capabilities.hasListGroup()) {
            // Hack for old MPD versions that do not support group command to add the artist property to MPDAlbum objects
            for (MPDAlbum album : result) {
                album.setArtistName(artistName);
            }
        }

        // Sort the created list
        if (sortOrder == MPDAlbum.MPD_ALBUM_SORT_ORDER.DATE) {
            Collections.sort(result, new MPDAlbum.MPDAlbumDateComparator());
        } else {
            Collections.sort(result);
        }

        synchronized (mCache) {
            mCache.cacheArtistAlbumsRequests(artistName, result, albumArtistSelector, artistSortSelector, sortOrder);
        }
        return result;
    }


    /**
     * Get a list of all album artists available in MPDs database
     *
     * @return List of MPDArtist objects
     */
    public List<MPDArtist> getArtists(MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector) throws MPDException {
        checkCacheState();
        // Get list of artists for MBID correction
        List<MPDArtist> normalArtists;
        synchronized (mCache) {
            normalArtists = mCache.getCachedArtistsRequest(albumArtistSelector, artistSortSelector);
        }

        if (normalArtists != null) {
            return normalArtists;
        }
        MPDCapabilities capabilities;
        synchronized (this) {
            capabilities = mConnection.getServerCapabilities();
        }



        List<MPDArtist> artists;
        synchronized (this) {
            if (albumArtistSelector == MPDArtist.MPD_ALBUM_ARTIST_SELECTOR.MPD_ALBUM_ARTIST_SELECTOR_ARTIST && artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTIST) {
                mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ARTISTS(capabilities));
            } else if (albumArtistSelector == MPDArtist.MPD_ALBUM_ARTIST_SELECTOR.MPD_ALBUM_ARTIST_SELECTOR_ALBUMARTIST && artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTIST) {
                mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ALBUMARTISTS(capabilities));
            } else if (albumArtistSelector == MPDArtist.MPD_ALBUM_ARTIST_SELECTOR.MPD_ALBUM_ARTIST_SELECTOR_ARTIST && artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTISTSORT) {
                mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ARTISTS_SORT(capabilities));
            } else if (albumArtistSelector == MPDArtist.MPD_ALBUM_ARTIST_SELECTOR.MPD_ALBUM_ARTIST_SELECTOR_ALBUMARTIST && artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTISTSORT) {
                mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ALBUMARTISTS_SORT(capabilities));
            }

            artists = MPDResponseParser.parseMPDArtists(mConnection, capabilities.hasMusicBrainzTags(), capabilities.hasListGroup());
        }

        // If MusicBrainz support is present, try to correct the MBIDs
        if (capabilities.hasMusicBrainzTags()) {
            /*
             * Get all artists ("Artist" and "ArtistSort" tags) for MBID correction, otherwise the grouping for
             * "AlbumArtist" and "AlbumArtistSort" returns wrong MBIDs
             */
            if (albumArtistSelector == MPDArtist.MPD_ALBUM_ARTIST_SELECTOR.MPD_ALBUM_ARTIST_SELECTOR_ALBUMARTIST) {
                if (artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTIST) {
                    mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ARTISTS(capabilities));
                } else {
                    mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ARTISTS_SORT(capabilities));
                }
                normalArtists = MPDResponseParser.parseMPDArtists(mConnection, capabilities.hasMusicBrainzTags(), capabilities.hasListGroup());

                // Merge normalArtists MBIDs with album artists MBIDs
                HashMap<String, MPDArtist> normalArtistsHashed = new HashMap<>();
                for (MPDArtist artist : normalArtists) {
                    normalArtistsHashed.put(artist.getArtistName(), artist);
                }

                // For every "AlbumArtist"/"AlbumArtistSort" try to get normal artistMBID
                for (MPDArtist artist : artists) {
                    MPDArtist hashedArtist = normalArtistsHashed.get(artist.getArtistName());
                    if (hashedArtist != null && hashedArtist.getMBIDCount() > 0) {
                        artist.setMBID(hashedArtist.getMBID(0));
                    }
                }
            }
        }

        // Remove first empty artist if present.
        if (artists.size() > 0 && artists.get(0).getArtistName().isEmpty()) {
            artists.remove(0);
        }

        synchronized (mCache) {
            mCache.cacheArtistsRequests(artists, albumArtistSelector, artistSortSelector);
        }
        return artists;
    }

    /**
     * Get a list of all playlists available in MPDs database
     *
     * @return List of MPDArtist objects
     */
    public List<MPDFileEntry> getPlaylists() throws MPDException {
        List<MPDFileEntry> playlists;

        synchronized (this) {
            mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_SAVED_PLAYLISTS);
            playlists = MPDResponseParser.parseMPDTracks(mConnection);
        }
        Collections.sort(playlists);
        return playlists;
    }

    /**
     * Gets all tracks from MPD server. This could take a long time to process. Be warned.
     *
     * @return A list of all tracks in MPDTrack objects
     */
    public synchronized List<MPDFileEntry> getAllTracks() throws MPDException {
        mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ALL_FILES, 120L * 1000L * 1000L * 1000L);

        return MPDResponseParser.parseMPDTracks(mConnection);
    }

    public List<MPDFileEntry> getAlbumTracks(MPDAlbum album, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector) throws MPDException {
        List<MPDFileEntry> result;
        String albumName = album.getName();
        String artistName = null;
        String mbid = album.getMBID();

        if (artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTIST) {
            artistName = album.getArtistName();
        } else {
            artistName = album.getArtistSortName();
        }

        synchronized (this) {
            mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_REQUEST_ALBUM_TRACKS(albumName));

            // Filter tracks with artistName
            result = MPDResponseParser.parseMPDTracks(mConnection);
            // Filter if one of the arguments is non-empty
            if (!mbid.isEmpty() || !artistName.isEmpty()) {
                if (artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTIST) {
                    MPDFileListFilter.filterAlbumMBIDandAlbumArtist(result, mbid, artistName);
                } else if (artistSortSelector == MPDArtist.MPD_ARTIST_SORT_SELECTOR.MPD_ARTIST_SORT_SELECTOR_ARTISTSORT) {
                    MPDFileListFilter.filterAlbumMBIDandAlbumArtistSort(result, mbid, artistName);
                }
            }
        }
        // Sort with disc & track number
        MPDSortHelper.sortFileListNumeric(result);
        return result;
    }

    /**
     * Requests the current playlist of the server
     *
     * @return List of MPDTrack items with all tracks of the current playlist
     */
    public synchronized List<MPDFileEntry> getCurrentPlaylist() throws MPDException {
        mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_CURRENT_PLAYLIST);

        /* Parse the return */
        return MPDResponseParser.parseMPDTracks(mConnection);
    }

    /**
     * Requests the current playlist of the server with a window
     *
     * @return List of MPDTrack items with all tracks of the current playlist
     */
    public synchronized List<MPDFileEntry> getCurrentPlaylistWindow(int start, int end) throws MPDException {
        mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_CURRENT_PLAYLIST_WINDOW(start, end));

        /* Parse the return */
        return MPDResponseParser.parseMPDTracks(mConnection);
    }

    /**
     * Requests the current playlist of the server
     *
     * @return List of MPDTrack items with all tracks of the current playlist
     */
    public synchronized List<MPDFileEntry> getSavedPlaylist(String playlistName) throws MPDException {
        mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_SAVED_PLAYLIST(playlistName));

        /* Parse the return */
        return MPDResponseParser.parseMPDTracks(mConnection);
    }

    /**
     * Requests the files for a specific path with info
     *
     * @return List of MPDTrack items with all tracks of the current playlist
     */
    public List<MPDFileEntry> getFiles(String path) throws MPDException {
        List<MPDFileEntry> retList;
        synchronized (this) {
            mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_FILES_INFO(path));

            // Parse the return
            retList = MPDResponseParser.parseMPDTracks(mConnection);
        }
        Collections.sort(retList);
        return retList;
    }

    /**
     * Requests the files for a specific search term and type
     *
     * @param term The search term to use
     * @param type The type of items to search
     * @return List of MPDTrack items with all tracks matching the search
     */
    public synchronized List<MPDFileEntry> getSearchedFiles(String term, MPDCommands.MPD_SEARCH_TYPE type) throws MPDException {
        mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_SEARCH_FILES(term, type));

        /* Parse the return */
        return MPDResponseParser.parseMPDTracks(mConnection);
    }

    /**
     * Searches a URL in the current playlist. If available the track is part of the returned list.
     *
     * @param url URL to search in the current playlist.
     * @return List with one entry or none.
     */
    public synchronized List<MPDFileEntry> getPlaylistFindTrack(String url) throws MPDException {
        mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_PLAYLIST_FIND_URI(url));

        /* Parse the return */
        return MPDResponseParser.parseMPDTracks(mConnection);
    }

    /**
     * Requests the currentstatus package from the mpd server.
     *
     * @return The CurrentStatus object with all gathered information.
     */
    public synchronized MPDCurrentStatus getCurrentServerStatus() throws MPDException {
        /* Request status */
        mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_CURRENT_STATUS);
        return MPDResponseParser.parseMPDCurrentStatus(mConnection);
    }

    /**
     * Requests the server statistics package from the mpd server.
     *
     * @return The CurrentStatus object with all gathered information.
     */
    public synchronized MPDStatistics getServerStatistics() throws MPDException {
        /* Request status */
        mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_STATISTICS);

        return MPDResponseParser.parseMPDStatistic(mConnection);
    }

    /**
     * This will query the current song playing on the mpd server.
     *
     * @return MPDTrack entry for the song playing.
     */
    public synchronized MPDTrack getCurrentSong() throws MPDException {
        mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_CURRENT_SONG);

        // Reuse the parsing function for tracks here.
        List<MPDFileEntry> retList;

        retList = MPDResponseParser.parseMPDTracks(mConnection);

        if (retList.size() == 1) {
            MPDFileEntry tmpFileEntry = retList.get(0);
            if (null != tmpFileEntry && tmpFileEntry instanceof MPDTrack) {
                return (MPDTrack) tmpFileEntry;
            }
            return null;
        } else {
            return null;
        }
    }


    /*
     ***********************
     *    Control commands *
     ***********************
     */

    /**
     * Sends the pause commando to MPD.
     *
     * @param pause 1 if playback should be paused, 0 if resumed
     */
    public synchronized void pause(boolean pause) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_PAUSE(pause));
    }

    /**
     * Jumps to the next song
     */
    public synchronized void nextSong() throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_NEXT);
    }

    /**
     * Jumps to the previous song
     */
    public synchronized void previousSong() throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_PREVIOUS);
    }

    /**
     * Stops playback
     */
    public synchronized void stopPlayback() throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_STOP);
    }

    /**
     * Sets random to true or false
     *
     * @param random If random should be set (true) or not (false)
     */
    public synchronized void setRandom(boolean random) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_SET_RANDOM(random));
    }

    /**
     * Sets repeat to true or false
     *
     * @param repeat If repeat should be set (true) or not (false)
     */
    public synchronized void setRepeat(boolean repeat) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_SET_REPEAT(repeat));
    }

    /**
     * Sets single playback to enable (true) or disabled (false)
     *
     * @param single if single playback should be enabled or not.
     */
    public void setSingle(boolean single) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_SET_SINGLE(single));
    }

    /**
     * Sets if files should be removed after playback (consumed)
     *
     * @param consume True if yes and false if not.
     */
    public synchronized void setConsume(boolean consume) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_SET_CONSUME(consume));
    }

    /**
     * Plays the song with the index in the current playlist.
     *
     * @param index Index of the song that should be played.
     */
    public synchronized void playSongIndex(int index) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_PLAY_SONG_INDEX(index));
    }

    /**
     * Seeks the currently playing song to a certain position
     *
     * @param seconds Position in seconds to which a seek is requested to.
     */
    public synchronized void seekSeconds(int seconds) throws MPDException {
        if (mConnection.getServerCapabilities().hasSeekCurrent()) {
            mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_SEEK_CURRENT_SECONDS(seconds));
        } else {
            MPDCurrentStatus status;

            status = getCurrentServerStatus();

            mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_SEEK_SECONDS(status.getCurrentSongIndex(), seconds));
        }
    }

    /**
     * Sets the volume of the mpd servers output. It is an absolute value between (0-100).
     *
     * @param volume Volume to set to the server.
     */
    public synchronized void setVolume(int volume) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_SET_VOLUME(volume));
    }

    /*
     ***********************
     *    Queue commands   *
     ***********************
     */

    /**
     * This method adds songs in a bulk command list. Should be reasonably in performance this way.
     *
     * @param tracks List of MPDFileEntry objects to add to the current playlist.
     */
    public synchronized void addTrackList(List<MPDFileEntry> tracks) throws MPDException {
        if (null == tracks) {
            return;
        }
        mConnection.startCommandList();

        for (MPDFileEntry track : tracks) {
            if (track instanceof MPDTrack) {
                mConnection.sendMPDRAWCommand(MPDCommands.MPD_COMMAND_ADD_FILE(track.getPath()));
            }
        }
        mConnection.endCommandList();
    }

    /**
     * Adds all tracks from a certain album from artistname to the current playlist.
     *
     * @param album The album object to get tracks for
     */
    public synchronized void addAlbumTracks(MPDAlbum album, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector) throws MPDException {
        List<MPDFileEntry> tracks = getAlbumTracks(album, albumArtistSelector, artistSortSelector);
        addTrackList(tracks);
    }

    /**
     * Adds all albums of an artist to the current playlist. Will first get a list of albums for the
     * artist and then call addAlbumTracks for every album on this result.
     *
     * @param artistname Name of the artist to enqueue the albums from.
     */
    public synchronized void addArtist(String artistname, MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder,
                                       MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector,
                                       MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector) throws MPDException {
        List<MPDAlbum> albums = getArtistAlbums(artistname, albumArtistSelector, artistSortSelector, sortOrder);
        if (null == albums) {
            return;
        }

        for (MPDAlbum album : albums) {
            // This will add all tracks from album where artistname is either the artist or
            // the album artist.
            addAlbumTracks(album, albumArtistSelector, artistSortSelector);
        }
    }

    /**
     * Adds a single File/Directory to the current playlist.
     *
     * @param url URL of the file or directory! to add to the current playlist.
     */
    public synchronized void addSong(String url) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_ADD_FILE(url));
    }

    /**
     * This method adds a song to a specified positiion in the current playlist.
     * This allows GUI developers to implement a method like "add after current".
     *
     * @param url   URL to add to the playlist.
     * @param index Index at which the item should be added.
     */
    public synchronized void addSongatIndex(String url, int index) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_ADD_FILE_AT_INDEX(url, index));
    }

    /**
     * Adds files to the playlist with a search term for a specific type
     *
     * @param term The search term to use
     * @param type The type of items to search
     */
    public synchronized void addSearchedFiles(String term, MPDCommands.MPD_SEARCH_TYPE type) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_ADD_SEARCH_FILES(term, type));
    }

    /**
     * Instructs the mpd server to clear its current playlist.
     */
    public synchronized void clearPlaylist() throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_CLEAR_PLAYLIST);
    }

    /**
     * Instructs the mpd server to shuffle its current playlist.
     */
    public synchronized void shufflePlaylist() throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_SHUFFLE_PLAYLIST);
    }

    /**
     * Instructs the mpd server to remove one item from the current playlist at index.
     *
     * @param index Position of the item to remove from current playlist.
     */
    public synchronized void removeIndex(int index) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_REMOVE_SONG_FROM_CURRENT_PLAYLIST(index));
    }

    /**
     * Instructs the mpd server to remove an range of songs from current playlist.
     *
     * @param start Start of songs to remoge
     * @param end   End of the range
     */
    public synchronized void removeRange(int start, int end) throws MPDException {
        // Check capabilities if removal with one command is possible
        if (mConnection.getServerCapabilities().hasCurrentPlaylistRemoveRange()) {
            mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_REMOVE_RANGE_FROM_CURRENT_PLAYLIST(start, end + 1));
        } else {
            // Create commandlist instead
            mConnection.startCommandList();
            for (int i = start; i <= end; i++) {
                mConnection.sendMPDRAWCommand(MPDCommands.MPD_COMMAND_REMOVE_SONG_FROM_CURRENT_PLAYLIST(start));
            }
            mConnection.endCommandList();
        }
    }

    /**
     * Moves one item from an index in the current playlist to an new index. This allows to move
     * tracks for example after the current to priotize songs.
     *
     * @param from Item to move from.
     * @param to   Position to enter item
     */
    public synchronized void moveSongFromTo(int from, int to) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_MOVE_SONG_FROM_INDEX_TO_INDEX(from, to));
    }

    /**
     * Saves the current playlist as a new playlist with a name.
     *
     * @param name Name of the playlist to save to.
     */
    public synchronized void savePlaylist(String name) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_SAVE_PLAYLIST(name));
    }

    /**
     * Adds a song to the saved playlist
     *
     * @param playlistName Name of the playlist to add the url to.
     * @param url          URL to add to the saved playlist
     */
    public synchronized void addSongToPlaylist(String playlistName, String url) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_ADD_TRACK_TO_PLAYLIST(playlistName, url));
    }

    /**
     * Removes a song from a saved playlist
     *
     * @param playlistName Name of the playlist of which the song should be removed from
     * @param position     Index of the song to remove from the lits
     */
    public synchronized void removeSongFromPlaylist(String playlistName, int position) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_REMOVE_TRACK_FROM_PLAYLIST(playlistName, position));
    }

    /**
     * Removes a saved playlist from the servers database.
     *
     * @param name Name of the playlist to remove.
     */
    public synchronized void removePlaylist(String name) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_REMOVE_PLAYLIST(name));
    }

    /**
     * Loads a saved playlist (added after the last song) to the current playlist.
     *
     * @param name Of the playlist to add to.
     */
    public synchronized void loadPlaylist(String name) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_LOAD_PLAYLIST(name));
    }


    /**
     * Returns the list of MPDOutputs to the outside callers.
     *
     * @return List of MPDOutput objects or null in case of error.
     */
    public synchronized List<MPDOutput> getOutputs() throws MPDException {
        mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_OUTPUTS);

        return MPDResponseParser.parseMPDOutputs(mConnection);
    }

    /**
     * Returns the list of MPDOutputs of all partitions to the outside callers.
     *
     * @return List of MPDOutput objects or null in case of error.
     */
    public synchronized List<MPDOutput> getAllPartitionOutputs() throws MPDException {
        List<MPDPartition> partitions = getPartitions();

        List<MPDOutput> outputs = new ArrayList<>();
        if (!getServerCapabilities().hasPartitions()) {
            return getOutputs();
        }

        String currentPartition = getCurrentServerStatus().getPartition();

        for (MPDPartition partition : partitions) {
            String partitionName = partition.getPartitionName();
            switchPartition(partitionName, false);
            List<MPDOutput> partitionOutputs = getOutputs();
            for (MPDOutput output : partitionOutputs) {
                output.setPartitionName(partitionName);
            }
            outputs.addAll(partitionOutputs);
        }

        switchPartition(currentPartition, false);

        // Consume all the changes because we have to change partitions to get a list of all outputs and their partitions
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_START_IDLE);

        return outputs;
    }

    /**
     * Returns the list of MPDPartition to the outside callers.
     *
     * @return List of MPDPartition objects or null in case of error.
     */
    public synchronized List<MPDPartition> getPartitions() throws MPDException {
        if (!getServerCapabilities().hasPartitions()) {
            return null;
        }
        mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_PARTITIONS);
        List<MPDPartition> partitions = MPDResponseParser.parseMPDPartitions(mConnection);

        MPDCurrentStatus status = getCurrentServerStatus();

        for (MPDPartition partition : partitions) {
            if (partition.getPartitionName().equals(status.getPartition())) {
                partition.setPartitionState(true);
                break;
            }
        }

        return partitions;
    }

    /**
     * Creates a new partition
     * @param name of the new partition
     * @throws MPDException
     */
    public synchronized void newPartition(String name) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_NEW_PARTITION(name));
    }

    /**
     * Deletes a partition
     * @param name of partition to delete
     * @throws MPDException
     */
    public synchronized void deletePartition(String name) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_DELETE_PARTITION(name));
    }

    /**
     * Deletes a partition
     * @param name of partition to delete
     * @throws MPDException
     */
    public synchronized void switchPartition(String name, boolean invalidate) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_SWITCH_PARTITION(name));

        if (invalidate) {
            checkCacheState();
        }

        mPartition = name;
    }

    /**
     * Moves an output to the current partition
     * @param name of partition to delete
     * @throws MPDException
     */
    public synchronized void moveOutputToCurrentPartiton(String name) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_MOVE_OUTPUT(name));
    }

    public synchronized void moveOutputToPartition(String output, String partition) throws MPDException {
        String currentPartition = getCurrentServerStatus().getPartition();

        if (!partition.equals(currentPartition)) {
            switchPartition(partition, false);
        }

        moveOutputToCurrentPartiton(output);

        if (!partition.equals(currentPartition)) {
            switchPartition(currentPartition, false);
        }
    }

    /**
     * Toggles the state of the output with the id.
     *
     * @param id Id of the output to toggle (active/deactive)
     */
    public synchronized void toggleOutput(int id) throws MPDException {
        if (mConnection.getServerCapabilities().hasToggleOutput()) {
            mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_TOGGLE_OUTPUT(id));
        } else {
            // Implement functionality with enable/disable
            List<MPDOutput> outputs = getOutputs();
            if (id < outputs.size()) {
                if (outputs.get(id).getOutputState()) {
                    disableOutput(id);
                } else {
                    enableOutput(id);
                }
            }
        }
    }

    public synchronized void toggleOutputPartition(int id, String partition) throws MPDException {
        String currentPartition = getCurrentServerStatus().getPartition();

        if (!partition.equals(currentPartition)) {
            switchPartition(partition, false);
        }

        toggleOutput(id);

        if (!partition.equals(currentPartition)) {
            switchPartition(currentPartition, false);
        }
    }

    /**
     * Enable the output with the id.
     *
     * @param id Id of the output to enable (active/deactive)
     */
    public synchronized void enableOutput(int id) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_ENABLE_OUTPUT(id));
    }

    /**
     * Disable the output with the id.
     *
     * @param id Id of the output to disable (active/deactive)
     */
    public synchronized void disableOutput(int id) throws MPDException {
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_DISABLE_OUTPUT(id));
    }

    /**
     * Instructs to update the database of the MPD server.
     *
     * @param path Path to update
     */
    public synchronized void updateDatabase(String path) throws MPDException {
        // Update root directory
        mConnection.sendSimpleMPDCommand(MPDCommands.MPD_COMMAND_UPDATE_DATABASE(path));
    }

    public byte[] getAlbumArt(String path, boolean readPicture) throws MPDException {
        // Check if server supports either of the two artwork commands
        if ((!readPicture && !mConnection.getServerCapabilities().hasAlbumArt()) || (readPicture && !mConnection.getServerCapabilities().hasReadPicture())) {
            return null;
        }

        // Size of the complete image
        long imageSize = 0;
        // Remaining data to read
        int dataToRead = 0;
        // Size of the current chunk (usually 8KiB or less)
        int chunkSize = 0;

        // Image return value. Accumulates all chunks to final image
        byte[] imageData = null;

        // Used to check if the initial image allocation happened
        boolean firstRun = true;

        // Signalizes if an error happened during reading and that image data must be discarded
        boolean abort = false;
        String line;
        while (dataToRead != 0 || firstRun) {
            // Request the image
            if (!readPicture) {
                mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_ALBUMART(path, ((int)imageSize - dataToRead)));
            } else {
                mConnection.sendMPDCommand(MPDCommands.MPD_COMMAND_GET_READPICTURE(path, ((int) imageSize - dataToRead)));
            }
            try {
                line = mConnection.readLine();
            } catch (MPDException e) {
                return null;
            }
            if (firstRun && (line == null || line.startsWith("OK"))) {
                // No image found
                return null;
            }

            while (line != null && !line.startsWith("OK")) {
                if (line.startsWith("size")) {
                    if (firstRun) {
                        try {
                            imageSize = Long.parseLong(line.substring(MPDResponses.MPD_RESPONSE_SIZE.length()));
                            if(imageSize > MAX_IMAGE_SIZE) {
                                Log.e(TAG, "Size=" + imageSize + " unsupported for path=" + path + " - Aborting download with " + (readPicture ? "readPicture" : "albumArt"));
                                // Ensure no more data after the initial chunk is read
                                imageSize = 0;
                                abort = true;
                            } else {
                                // Allocate complete image data
                                imageData = new byte[(int)imageSize];
                                dataToRead = (int)imageSize;
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Can't understand MPD anymore (imageSize): " + path + " - " + e.getMessage());
                            // Ensure no more data after the initial chunk is read
                            imageSize = 0;
                            abort = true;
                        }
                        firstRun = false;
                    }
                } else if (line.startsWith("binary")) {
                    // This means that after this line a binary chunk is incoming
                    try {
                        chunkSize = Integer.parseInt(line.substring(MPDResponses.MPD_RESPONSE_BINARY_SIZE.length()));
                    } catch (NumberFormatException e) {
                        // We currently can not recover from this error case because the binary reader
                        // does not know how much data is available in the socket until the "OK"  is reached.
                        // TODO: In the future this could be fixed by reading until the socket is exhausted.
                        Log.e(TAG, "Can't understand MPD anymore (chunkSize): " + path + " - " + e.getMessage());
                        Log.e(TAG, "Can't recover because binary read length is undefined");
                        throw new MPDException.MPDConnectionException("Cover error:" + e.getMessage());
                    }

                    byte[] readData;
                    try {
                        // Do the actual binary read from the socket. The precise length must be known a priori.
                        readData = mConnection.readBinary(chunkSize);
                    } catch (MPDException e) {
                        return null;
                    }

                    // Only use the chunk if no error condition triggered
                    if (!abort) {
                        if (((imageSize - dataToRead) + chunkSize) > imageSize) {
                            Log.e(TAG, "imageSize=" + imageSize + " dataToRead=" + dataToRead + " chunkSize=" + chunkSize);
                            Log.e(TAG, "Abort processing the image=" + path + " because MPD provides more data than announced");
                            // Ensure no more data is going to be read
                            dataToRead = 0;
                            abort = true;
                        } else {
                            // Copy chunk to final output array
                            if (readData != null) {
                                // Spurious crash happened here with src being null
                                System.arraycopy(readData, 0, imageData, ((int) imageSize - dataToRead), chunkSize);
                            }
                            dataToRead -= chunkSize;
                        }
                    }
                }

                try {
                    line = mConnection.readLine();
                } catch (MPDException e) {
                    return null;
                }
            }
        }
        if (!abort) {
            return imageData;
        } else {
            // Discard potential broken image data
            return null;
        }
    }

    private void checkCacheState() throws MPDException {
        long version = getServerStatistics().getLastDBUpdate();
        synchronized (mCache) {
            if (mCache.getVersion() != getServerStatistics().getLastDBUpdate()) {
                invalidateCache();
                mCache.setVersion(version);
            }
        }
    }

    private void invalidateCache() {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "MPD cache invalidate");
        }
        synchronized (mCache) {
            mCache.invalidate();
        }
    }
}
