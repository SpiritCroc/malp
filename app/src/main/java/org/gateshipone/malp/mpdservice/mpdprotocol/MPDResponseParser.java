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

import org.gateshipone.malp.mpdservice.handlers.MPDIdleChangeHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDDirectory;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFilterObject;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDOutput;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPartition;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPlaylist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDStatistics;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class MPDResponseParser {
    private static final String TAG = MPDResponseParser.class.getSimpleName();

    /**
     * Parses the return of MPD when a list of albums was requested.
     *
     * @return List of MPDAlbum objects
     * @throws MPDException if an error from MPD was received during reading
     */
    static ArrayList<MPDAlbum> parseMPDAlbums(final MPDConnection connection) throws MPDException {
        ArrayList<MPDAlbum> albumList = new ArrayList<>();
        if (!connection.isConnected()) {
            return albumList;
        }


        /* Parse the MPD response and create a list of MPD albums (pre 0.21.11), broken grouping */
        if (!connection.getServerCapabilities().hasListGroupingFixed()) {
            MPDAlbum tempAlbum = null;

            MPDResponses.MPD_RESPONSE_KEY key = null;
            key = connection.readKey();

            String value = "";
            while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
                try {
                    value = connection.readValue();
                } catch (MPDSocketInterface.NoKeyReadException e) {
                    e.printStackTrace();
                }
                switch (key) {
                    case RESPONSE_ALBUM:
                        /* We found an album, add it to the list. */
                        if (null != tempAlbum) {
                            albumList.add(tempAlbum);
                        }
                        tempAlbum = new MPDAlbum(value);
                        break;
                    case RESPONSE_ALBUM_MBID:
                        tempAlbum.setMBID(value);
                        break;
                    case RESPONSE_ALBUMARTIST:
                        tempAlbum.setArtistName(value);
                        break;
                    case RESPONSE_ALBUMARTISTSORT:
                        tempAlbum.setArtistSortName(value);
                        break;
                    case RESPONSE_DATE: {
                        // Try to parse Date
                        SimpleDateFormat format = new SimpleDateFormat("yyyy");
                        try {
                            tempAlbum.setDate(format.parse(value));
                        } catch (ParseException e) {
                            Log.w(TAG, "Error parsing date: " + value);
                        }
                    }
                    break;
                    default:
                        break;
                }

                key = connection.readKey();
            }

        /* Because of the loop structure the last album has to be added because no
        "ALBUM:" is sent anymore.
         */
            if (null != tempAlbum) {
                albumList.add(tempAlbum);
            }
        } else {
            // New parser protocol path (0.21.11 and above) with correct list grouping
            String albumMBID = "";
            String albumDate = "";
            String albumArtist = "";
            String albumArtistSort = "";

            MPDResponses.MPD_RESPONSE_KEY key = null;

            key = connection.readKey();

            String value = "";
            while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
                try {
                    value = connection.readValue();
                } catch (MPDSocketInterface.NoKeyReadException e) {
                    e.printStackTrace();
                }

                switch (key) {
                    case RESPONSE_ALBUM: {
                        MPDAlbum tempAlbum = new MPDAlbum(value);

                        if (!albumArtist.isEmpty()) {
                            tempAlbum.setArtistName(albumArtist);
                        }
                        if (!albumArtistSort.isEmpty()) {
                            tempAlbum.setArtistSortName(albumArtistSort);
                        }
                        if (!albumMBID.isEmpty()) {
                            tempAlbum.setMBID(albumMBID);
                        }
                        if (!albumDate.isEmpty()) {
                            SimpleDateFormat format = new SimpleDateFormat("yyyy");
                            try {
                                tempAlbum.setDate(format.parse(albumDate));
                            } catch (ParseException e) {
                                Log.w(TAG, "Error parsing date: " + albumDate);
                            }
                        }

                        albumList.add(tempAlbum);
                    }
                    break;
                    case RESPONSE_ALBUM_MBID:
                        albumMBID = value;
                        break;
                    case RESPONSE_ALBUMARTIST:
                        albumArtist = value;
                        break;
                    case RESPONSE_ALBUMARTISTSORT:
                        albumArtistSort = value;
                        break;
                    case RESPONSE_DATE:
                        albumDate = value;
                        break;
                    default:
                        break;
                }
                key = connection.readKey();
            }
        }

        // Sort the albums for later sectioning.
        Collections.sort(albumList);
        return albumList;
    }

    /**
     * Parses the return stream of MPD when a list of artists was requested.
     *
     * @return List of MPDArtists objects
     * @throws MPDException if an error from MPD was received during reading
     */
    static ArrayList<MPDArtist> parseMPDArtists(final MPDConnection connection, final boolean hasMusicBrainz, final boolean hasListGroup) throws MPDException {
        ArrayList<MPDArtist> artistList = new ArrayList<>();
        if (!connection.isConnected()) {
            return artistList;
        }

        MPDResponses.MPD_RESPONSE_KEY key = null;
        String value = "";
        /* Parse the MPD response and create a list of MPD albums (pre 0.21.11), broken grouping */
        if (!connection.getServerCapabilities().hasListGroupingFixed()) {
            /* Artist properties */
            String artistName;
            String artistMBID;

            MPDArtist tempArtist = null;

            key = connection.readKey();

            while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
                try {
                    value = connection.readValue();
                } catch (MPDSocketInterface.NoKeyReadException e) {
                    e.printStackTrace();
                }
                switch (key) {
                    case RESPONSE_ARTIST:
                    case RESPONSE_ALBUMARTIST:
                    case RESPONSE_ARTISTSORT:
                    case RESPONSE_ALBUMARTISTSORT: {
                        if (null != tempArtist) {
                            artistList.add(tempArtist);
                        }
                        artistName = value;
                        tempArtist = new MPDArtist(artistName);
                    }
                    break;
                    case RESPONSE_ARTIST_MBID: {
                        if (tempArtist != null) {
                            artistMBID = value;
                            tempArtist.addMBID(artistMBID);
                        }

                    }
                    break;
                    default:
                        break;
                }

                key = connection.readKey();
            }


            // Add last artist
            if (null != tempArtist) {
                artistList.add(tempArtist);
            }
        } else {
            // New parser protocol path (0.21.11 and above) with correct list grouping
            /* Artist properties */
            ArrayList<String> mbids = new ArrayList<>();

            key = connection.readKey();

            while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
                try {
                    value = connection.readValue();
                } catch (MPDSocketInterface.NoKeyReadException e) {
                    e.printStackTrace();
                }
                switch (key) {
                    case RESPONSE_ARTIST:
                    case RESPONSE_ALBUMARTIST:
                    case RESPONSE_ARTISTSORT:
                    case RESPONSE_ALBUMARTISTSORT: {
                        MPDArtist tempArtist = new MPDArtist(value);
                        tempArtist.setMBIDs(mbids);
                        artistList.add(tempArtist);
                        break;
                    }
                    case RESPONSE_ARTIST_MBID: {
                        mbids.clear();
                        if (!value.isEmpty()) {
                            String[] mbidsSplit = value.split("/");
                            mbids.addAll(Arrays.asList(mbidsSplit));
                        }
                    }
                }

                key = connection.readKey();
            }
        }

        // Sort the artists for later sectioning.
        Collections.sort(artistList);

        // If we used MBID filtering, it could happen that a user has an artist in the list multiple times,
        // once with and once without MBID. Try to filter this by sorting the list first by name and mbid count
        // and then remove duplicates.
        if (hasMusicBrainz && hasListGroup) {
            ArrayList<MPDArtist> clearedList = new ArrayList<>();

            // Remove multiple entries when one artist is in list with and without MBID
            int artistListSize = artistList.size();
            for (int i = 0; i < artistListSize; i++) {
                MPDArtist artist = artistList.get(i);
                if (i + 1 != artistListSize) {
                    MPDArtist nextArtist = artistList.get(i + 1);
                    // Next artist is different, add this one (the one with most MBIDs)
                    if (!artist.getArtistName().equals(nextArtist.getArtistName())) {
                        clearedList.add(artist);
                    }
                } else {
                    // Last artist in list -> add
                    clearedList.add(artist);
                }
            }

            return clearedList;
        } else {
            return artistList;
        }
    }

    /**
     * Parses the response of mpd on requests that return track items. This is also used
     * for MPD file, directory and playlist responses. This allows the GUI to develop
     * one adapter for all three types. Also MPD mixes them when requesting directory listings.
     * <p/>
     * It will return a list of MPDFileEntry objects which is a parent class for (MPDTrack, MPDPlaylist,
     * MPDDirectory) you can use instanceof to check which type you got.
     *
     * @return List of MPDFileEntry objects
     * @throws MPDException if an error from MPD was received during reading
     */
    static ArrayList<MPDFileEntry> parseMPDTracks(final MPDConnection connection) throws MPDException {
        ArrayList<MPDFileEntry> trackList = new ArrayList<>();
        if (!connection.isConnected()) {
            return trackList;
        }

        /* Temporary file entry (added to list later) */
        MPDFileEntry tempFileEntry = null;

        MPDResponses.MPD_RESPONSE_KEY key = null;

        key = connection.readKey();

        String value = "";
        while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
            try {
                value = connection.readValue();
            } catch (MPDSocketInterface.NoKeyReadException e) {
                e.printStackTrace();
            }

            /* This if block will just check all the different response possible by MPDs file/dir/playlist response */
            switch (key) {
                case RESPONSE_FILE:
                    if (null != tempFileEntry) {
                        trackList.add(tempFileEntry);
                    }
                    tempFileEntry = new MPDTrack(value);
                    break;
                case RESPONSE_PLAYLIST:
                    if (null != tempFileEntry) {
                        trackList.add(tempFileEntry);
                    }
                    tempFileEntry = new MPDPlaylist(value);
                    break;
                case RESPONSE_DIRECTORY:
                    if (null != tempFileEntry) {
                        trackList.add(tempFileEntry);
                    }
                    tempFileEntry = new MPDDirectory(value);
                    break;
                default:
                    break;
            }


            // Currently parsing a file (check its properties)
            if (tempFileEntry instanceof MPDTrack) {
                switch (key) {
                    case RESPONSE_TITLE:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.TITLE, value);
                        break;
                    case RESPONSE_ARTIST:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.ARTIST, value);
                        break;
                    case RESPONSE_ARTISTSORT:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.ARTISTSORT, value);
                        break;
                    case RESPONSE_NAME:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.NAME, value);
                        break;
                    case RESPONSE_ALBUMARTIST:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.ALBUMARTIST, value);
                        break;
                    case RESPONSE_ALBUMARTISTSORT:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.ALBUMARTISTSORT, value);
                        break;
                    case RESPONSE_ALBUM:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.ALBUM, value);
                        break;
                    case RESPONSE_DATE:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.DATE, value);
                        break;
                    case RESPONSE_ALBUM_MBID:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.ALBUM_MBID, value);
                        break;
                    case RESPONSE_ARTIST_MBID:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.ARTIST_MBID, value);
                        break;
                    case RESPONSE_ALBUMARTIST_MBID:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.ALBUMARTIST_MBID, value);
                        break;
                    case RESPONSE_TRACK_MBID:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.TRACK_MBID, value);
                        break;
                    case RESPONSE_PERFORMER:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.PERFORMER, value);
                        break;
                    case RESPONSE_CONDUCTOR:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.CONDUCTOR, value);
                        break;
                    case RESPONSE_COMPOSER:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.COMPOSER, value);
                        break;
                    case RESPONSE_WORK:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.WORK, value);
                        break;
                    case RESPONSE_WORK_MBID:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.WORK_MBID, value);
                        break;
                    case RESPONSE_GENRE:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.GENRE, value);
                        break;
                    case RESPONSE_LABEL:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.LABEL, value);
                        break;
                    case RESPONSE_COMMENT:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.COMMENT, value);
                        break;
                    case RESPONSE_FORMAT:
                        ((MPDTrack) tempFileEntry).setStringTag(MPDTrack.StringTagTypes.FILE_FORMAT, value);
                        break;
                    case RESPONSE_TIME:
                        try {
                            ((MPDTrack) tempFileEntry).setLength(Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    case RESPONSE_SONG_ID:
                        try {
                            ((MPDTrack) tempFileEntry).setSongID(Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    case RESPONSE_POS:
                        try {
                            ((MPDTrack) tempFileEntry).setSongPosition(Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    case RESPONSE_DISC: {
                        /*
                         * Check if MPD returned a discnumber like: "1" or "1/3" and set disc count accordingly.
                         */
                        String discNumber = value;
                        discNumber = discNumber.replaceAll(" ", "");
                        String[] discNumberSep = discNumber.split("/");
                        if (discNumberSep.length > 0) {
                            try {
                                ((MPDTrack) tempFileEntry).setDiscNumber(Integer.parseInt(discNumberSep[0]));
                            } catch (NumberFormatException ignored) {
                            }

                            if (discNumberSep.length > 1) {
                                try {
                                    ((MPDTrack) tempFileEntry).psetAlbumDiscCount(Integer.parseInt(discNumberSep[1]));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        } else {
                            try {
                                ((MPDTrack) tempFileEntry).setDiscNumber(Integer.parseInt(discNumber));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    break;
                    case RESPONSE_TRACK: {
                        /*
                         * Check if MPD returned a tracknumber like: "12" or "12/42" and set albumtrack count accordingly.
                         */
                        String trackNumber = value;
                        trackNumber = trackNumber.replaceAll(" ", "");
                        String[] trackNumbersSep = trackNumber.split("/");
                        if (trackNumbersSep.length > 0) {
                            try {
                                ((MPDTrack) tempFileEntry).setTrackNumber(Integer.parseInt(trackNumbersSep[0]));
                            } catch (NumberFormatException ignored) {
                            }
                            if (trackNumbersSep.length > 1) {
                                try {
                                    ((MPDTrack) tempFileEntry).setAlbumTrackCount(Integer.parseInt(trackNumbersSep[1]));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        } else {
                            try {
                                ((MPDTrack) tempFileEntry).setTrackNumber(Integer.parseInt(trackNumber));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    break;
                    case RESPONSE_LAST_MODIFIED:
                        tempFileEntry.setLastModified(value);
                        break;
                    default:
                        break;
                }

            } else if (tempFileEntry != null) {
                if (key == MPDResponses.MPD_RESPONSE_KEY.RESPONSE_LAST_MODIFIED) {
                    tempFileEntry.setLastModified(value);
                }
            }

            // Move to the next key.
            key = connection.readKey();

        }

        /* Add last remaining track to list. */
        if (null != tempFileEntry) {
            trackList.add(tempFileEntry);
        }
        return trackList;
    }

    static MPDCurrentStatus parseMPDCurrentStatus(final MPDConnection connection) throws MPDException {
        MPDCurrentStatus status = new MPDCurrentStatus();
        if (!connection.isConnected()) {
            return status;
        }

        MPDResponses.MPD_RESPONSE_KEY key = null;

        key = connection.readKey();

        String value = "";
        while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
            try {
                value = connection.readValue();
            } catch (MPDSocketInterface.NoKeyReadException e) {
                e.printStackTrace();
            }

            try {
                switch (key) {
                    case RESPONSE_VOLUME:
                        status.setVolume(Integer.parseInt(value));
                        break;
                    case RESPONSE_REPEAT:
                        status.setRepeat(Integer.parseInt(value));
                        break;
                    case RESPONSE_RANDOM:
                        status.setRandom(Integer.parseInt(value));
                        break;
                    case RESPONSE_SINGLE:
                        status.setSinglePlayback(Integer.parseInt(value));
                        break;
                    case RESPONSE_CONSUME:
                        status.setConsume(Integer.parseInt(value));
                        break;
                    case RESPONSE_PLAYLIST:
                        status.setPlaylistVersion(Integer.parseInt(value));
                        break;
                    case RESPONSE_PLAYLISTLENGTH:
                        status.setPlaylistLength(Integer.parseInt(value));
                        break;
                    case RESPONSE_STATE:
                        switch (value) {
                            case MPDResponses.MPD_PLAYBACK_STATE_RESPONSE_PLAY:
                                status.setPlaybackState(MPDCurrentStatus.MPD_PLAYBACK_STATE.MPD_PLAYING);
                                break;
                            case MPDResponses.MPD_PLAYBACK_STATE_RESPONSE_PAUSE:
                                status.setPlaybackState(MPDCurrentStatus.MPD_PLAYBACK_STATE.MPD_PAUSING);
                                break;
                            case MPDResponses.MPD_PLAYBACK_STATE_RESPONSE_STOP:
                                status.setPlaybackState(MPDCurrentStatus.MPD_PLAYBACK_STATE.MPD_STOPPED);
                                break;
                        }
                        break;
                    case RESPONSE_SONG:
                        status.setCurrentSongIndex(Integer.parseInt(value));
                        break;
                    case RESPONSE_NEXT_SONG:
                        status.setNextSongIndex(Integer.parseInt(value));
                        break;
                    case RESPONSE_TIME_OLD: {
                        String[] timeInfoSep = value.split(":");
                        if (timeInfoSep.length == 2) {
                            try {
                                status.setElapsedTime(Integer.parseInt(timeInfoSep[0]));
                                status.setTrackLength(Integer.parseInt(timeInfoSep[1]));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    break;
                    case RESPONSE_DURATION:
                        status.setTrackLength(Float.parseFloat(value));
                        break;
                    case RESPONSE_ELAPSED:
                        status.setElapsedTime(Float.parseFloat(value));
                        break;
                    case RESPONSE_BITRATE:
                        status.setBitrate(Integer.parseInt(value));
                        break;
                    case RESPONSE_AUDIO: {
                        String[] audioInfoSep = value.split(":");
                        if (audioInfoSep.length == 3) {
                            /* Extract the separate pieces */
                            try {
                                /* First is sampleRate */
                                status.setSamplerate(Integer.parseInt(audioInfoSep[0]));
                                /* Second is bitresolution */
                                status.setBitDepth(audioInfoSep[1]);
                                /* Third is channel count */
                                status.setChannelCount(Integer.parseInt(audioInfoSep[2]));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    break;
                    case RESPONSE_UPDATING_DB:
                        status.setUpdateDBJob(Integer.parseInt(value));
                        break;
                    case RESPONSE_PARTITION:
                        status.setPartition(value);
                        break;
                    default:
                        break;
                }
            } catch (NumberFormatException error) {
                Log.e(TAG, "Error parsing number: " + error.getMessage() + " for key: " + key);
            }

            key = connection.readKey();
        }
        return status;
    }

    /**
     * Parses the MPD response to a statistics request
     *
     * @param connection {@link MPDConnection} to use
     * @return Statistic object just parsed
     * @throws MPDException Thrown if MPD throws an error
     */
    static MPDStatistics parseMPDStatistic(final MPDConnection connection) throws MPDException {
        MPDStatistics stats = new MPDStatistics();

        MPDResponses.MPD_RESPONSE_KEY key;
        String value;

        /* Read key from MPD */
        try {
            key = connection.readKey();
            while (key != null && (key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK)) {
                value = connection.readValue();
                switch (key) {
                    case RESPONSE_UPTIME:
                        try {
                            stats.setServerUptime(Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    case RESPONSE_PLAYTIME:
                        try {
                            stats.setPlayDuration(Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    case RESPONSE_ARTISTS:
                        try {
                            stats.setArtistsCount(Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    case RESPONSE_ALBUMS:
                        try {
                            stats.setAlbumCount(Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    case RESPONSE_SONGS:
                        try {
                            stats.setSongCount(Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {
                        }
                    case RESPONSE_DB_PLAYTIME:
                        try {
                            stats.setAllSongDuration(Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    case RESPONSE_DB_UPDATE:
                        try {
                            stats.setLastDBUpdate(Long.parseLong(value));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    default:
                        break;
                }

                key = connection.readKey();
            }
        } catch (MPDSocketInterface.NoKeyReadException e) {
            e.printStackTrace();
            throw new MPDException.MPDConnectionException("Read value before key");
        }
        return stats;
    }

    /**
     * Private parsing method for MPDs command list
     *
     * @return A list of Strings of commands that are allowed on the server
     * @throws IOException  If an IO error occurs during read
     * @throws MPDException if an error from MPD was received during reading
     */
    static List<String> parseMPDCommands(final MPDConnection connection) throws IOException, MPDException {
        ArrayList<String> commandList = new ArrayList<>();

        MPDResponses.MPD_RESPONSE_KEY key = null;

        key = connection.readKey();

        String value = "";
        while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
            try {
                value = connection.readValue();
            } catch (MPDSocketInterface.NoKeyReadException e) {
                e.printStackTrace();
            }
            if (key == MPDResponses.MPD_RESPONSE_KEY.RESPONSE_COMMAND) {
                commandList.add(value);
            }

            key = connection.readKey();
        }
        return commandList;

    }

    /**
     * Parses the response of MPDs supported tag types
     *
     * @return List of tags supported by the connected MPD host
     * @throws IOException  If an IO error occurs during read
     * @throws MPDException if an error from MPD was received during reading
     */
    static List<String> parseMPDTagTypes(final MPDConnection connection) throws MPDException {
        ArrayList<String> tagList = new ArrayList<>();

        MPDResponses.MPD_RESPONSE_KEY key = null;

        key = connection.readKey();

        String value = "";
        while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
            try {
                value = connection.readValue();
            } catch (MPDSocketInterface.NoKeyReadException e) {
                e.printStackTrace();
            }
            if (key == MPDResponses.MPD_RESPONSE_KEY.RESPONSE_TAGTYPE) {
                tagList.add(value);
            }

            key = connection.readKey();
        }

        return tagList;
    }

    /**
     * Private parsing method for MPDs output lists.
     *
     * @return A list of MPDOutput objects with name,active,id values if successful. Otherwise empty list.
     * @throws MPDException if an error from MPD was received during reading
     */
    static List<MPDOutput> parseMPDOutputs(final MPDConnection connection) throws MPDException {
        ArrayList<MPDOutput> outputList = new ArrayList<>();
        // Parse outputs
        String outputName = null;
        String outputPlugin = null;
        boolean outputActive = false;
        int outputId = -1;

        MPDResponses.MPD_RESPONSE_KEY key = null;

        key = connection.readKey();

        String value = "";
        while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
            try {
                value = connection.readValue();
            } catch (MPDSocketInterface.NoKeyReadException e) {
                e.printStackTrace();
            }
            switch (key) {
                case RESPONSE_OUTPUT_ID:
                    if (null != outputName) {
                        if (outputPlugin == null || !outputPlugin.equals("dummy")) {
                            MPDOutput tempOutput = new MPDOutput(outputName, outputActive, outputId, outputPlugin);
                            outputList.add(tempOutput);
                        }
                    }
                    outputId = Integer.parseInt(value);
                    break;
                case RESPONSE_OUTPUT_NAME:
                    outputName = value;
                    break;
                case RESPONSE_OUTPUT_PLUGIN:
                    outputPlugin = value;
                    break;
                case RESPONSE_OUTPUT_ENABLED:
                    outputActive = value.equals("1");
                default:
                    break;
            }

            key = connection.readKey();
        }

        // Add remaining output to list
        if (null != outputName) {
            if (outputPlugin == null || !outputPlugin.equals("dummy")) {
                MPDOutput tempOutput = new MPDOutput(outputName, outputActive, outputId, outputPlugin);
                outputList.add(tempOutput);
            }
        }

        return outputList;
    }

    /**
     * Private parsing method for MPDs output lists.
     *
     * @return A list of MPDOutput objects with name,active,id values if successful. Otherwise empty list.
     * @throws MPDException if an error from MPD was received during reading
     */
    static List<MPDPartition> parseMPDPartitions(final MPDConnection connection) throws MPDException {
        ArrayList<MPDPartition> partitionList = new ArrayList<>();
        // Parse partitions
        String partitionName = null;
        boolean partitionActive = false;

        MPDResponses.MPD_RESPONSE_KEY key = null;

        key = connection.readKey();

        String value = "";
        while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
            try {
                value = connection.readValue();
            } catch (MPDSocketInterface.NoKeyReadException e) {
                e.printStackTrace();
            }
            if (key.equals(MPDResponses.MPD_RESPONSE_KEY.RESPONSE_PARTITION)) {
                partitionName = value;
                MPDPartition partition = new MPDPartition(partitionName, partitionActive);
                partitionList.add(partition);
            }

            key = connection.readKey();
        }

        return partitionList;
    }

    static MPDIdleChangeHandler.MPDChangedSubsystemsResponse parseMPDIdleResponse(final MPDConnection connection) throws MPDException {
        MPDIdleChangeHandler.MPDChangedSubsystemsResponse response = new MPDIdleChangeHandler.MPDChangedSubsystemsResponse();

        MPDResponses.MPD_RESPONSE_KEY key = null;

        key = connection.readKey();
        String value = "";
        MPDIdleChangeHandler.CHANGED_SUBSYSTEM responseValue;
        while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
            try {
                value = connection.readValue();
            } catch (MPDSocketInterface.NoKeyReadException e) {
                e.printStackTrace();
            }
            responseValue = MPDResponses.IDLE_RESPONSE_VALUES.get(value);
            if (key.equals(MPDResponses.MPD_RESPONSE_KEY.RESPONSE_CHANGED) && responseValue != null) {
                    response.setSubsystemChanged(responseValue, true);
            }

            key = connection.readKey();
        }

        return response;
    }

    static void parseMPDPlaylistLength(final MPDConnection connection, MPDPlaylist playlist) throws MPDException {
        MPDResponses.MPD_RESPONSE_KEY key = null;

        key = connection.readKey();
        String value = "";
        while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
            try {
                value = connection.readValue();
            } catch (MPDSocketInterface.NoKeyReadException e) {
                e.printStackTrace();
            }
            if (key.equals(MPDResponses.MPD_RESPONSE_KEY.RESPONSE_SONGS)) {
                try {
                    playlist.setTitleCount(Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                }
            } else if (key.equals(MPDResponses.MPD_RESPONSE_KEY.RESPONSE_PLAYTIME)) {
                try {
                    playlist.setLength(Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                }
            }

            key = connection.readKey();
        }
    }

    static List<MPDFilterObject> parseTagEntryList(final MPDConnection connection) throws MPDException {
        ArrayList<MPDFilterObject> entryList = new ArrayList<>();
        // Parse entry
        String entryName = null;

        MPDResponses.MPD_RESPONSE_KEY key = null;

        key = connection.readKey();

        String value = "";
        while (key != null && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK && key != MPDResponses.MPD_RESPONSE_KEY.RESPONSE_ACK) {
            try {
                value = connection.readValue();
            } catch (MPDSocketInterface.NoKeyReadException e) {
                e.printStackTrace();
            }
            entryList.add(new MPDFilterObject(value));


            key = connection.readKey();
        }

        Collections.sort(entryList);
        return entryList;
    }
}
