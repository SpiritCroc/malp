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

public class MPDCommands {

    public static final String MPD_COMMAND_CLOSE = "close";

    public static String MPD_COMMAND_PASSWORD(String password) {
        return "password \"" + escapeString(password) + "\"";
    }

    private static String createAlbumGroupString(MPDCapabilities caps) {
        String groups = "";
        if (!caps.hasListGroup()) {
            return groups;
        }
        if (caps.hasTagAlbumArtist()) {
            groups += " group albumartist";
        }
        if (caps.hasTagAlbumArtistSort()) {
            groups += " group albumartistsort";
        }
        if (caps.hasMusicBrainzTags()) {
            groups += " group musicbrainz_albumid";
        }
        if (caps.hasTagDate()) {
            groups += " group date";
        }
        return groups;
    }

    /* Database request commands */
    public static String MPD_COMMAND_REQUEST_ALBUMS(MPDCapabilities caps) {
        if (caps.hasListGroup()) {
            return "list album" + createAlbumGroupString(caps);
        } else {
            return "list album";
        }
    }

    public static String  MPD_COMMAND_REQUEST_ARTIST_ALBUMS(String artistName, MPDCapabilities caps) {
        if (caps.hasListGroup()) {
            return "list album artist \"" + escapeString(artistName) + "\"" + createAlbumGroupString(caps);
        } else {
            return "list album \"" + escapeString(artistName) + "\"";
        }
    }


    public static String MPD_COMMAND_REQUEST_ARTISTSORT_ALBUMS(String artistName, MPDCapabilities caps) {
        if (caps.hasTagArtistSort()) {
            return "list album artistsort \"" + escapeString(artistName) + "\"" + createAlbumGroupString(caps);
        } else {
            return MPD_COMMAND_REQUEST_ARTIST_ALBUMS(artistName, caps);
        }
    }

    public static String MPD_COMMAND_REQUEST_ALBUMS_FOR_PATH(String path, MPDCapabilities caps) {
        if (caps.hasListGroup()) {
            return "list album base \"" + escapeString(path) + "\"" + createAlbumGroupString(caps);
        } else {
            // FIXME check if correct. Possible fallback for group missing -> base command also missing.
            return MPD_COMMAND_REQUEST_ALBUMS(caps);
        }
    }

    public static String MPD_COMMAND_REQUEST_ALBUMARTIST_ALBUMS(String artistName, MPDCapabilities caps) {
        if (caps.hasTagAlbumArtist()) {
            return "list album albumartist \"" + escapeString(artistName) + "\"" + createAlbumGroupString(caps);
        } else {
            return MPD_COMMAND_REQUEST_ARTIST_ALBUMS(artistName, caps);
        }
    }

    public static String MPD_COMMAND_REQUEST_ALBUMARTISTSORT_ALBUMS(String artistName, MPDCapabilities caps) {
        if (caps.hasTagAlbumArtistSort()) {
            return "list album albumartistsort \"" + escapeString(artistName) + "\"" + createAlbumGroupString(caps);
        } else {
            return MPD_COMMAND_REQUEST_ARTIST_ALBUMS(artistName, caps);
        }
    }


    public static String MPD_COMMAND_REQUEST_ALBUM_TRACKS(String albumName) {
        return "find album \"" + escapeString(albumName) + "\"";
    }

    public static String MPD_COMMAND_REQUEST_ARTISTS(MPDCapabilities capabilities) {
        if (!(capabilities.hasListGroup() && capabilities.hasMusicBrainzTags())) {
            return "list artist";
        } else {
            return "list artist group MUSICBRAINZ_ARTISTID";
        }
    }

    public static String MPD_COMMAND_REQUEST_ALBUMARTISTS(MPDCapabilities capabilities) {
        if (capabilities.hasTagAlbumArtist()) {
            if (!(capabilities.hasListGroup() && capabilities.hasMusicBrainzTags())) {
                return "list albumartist";
            } else {
                return "list albumartist group MUSICBRAINZ_ARTISTID";
            }
        } else {
            return MPD_COMMAND_REQUEST_ARTISTS(capabilities);
        }
    }

    public static String MPD_COMMAND_REQUEST_ARTISTS_SORT(MPDCapabilities capabilities) {
        if (capabilities.hasTagArtistSort()) {
            if (!(capabilities.hasListGroup() && capabilities.hasMusicBrainzTags())) {
                return "list artistsort";
            } else {
                return "list artistsort group MUSICBRAINZ_ARTISTID";
            }
        } else {
            return MPD_COMMAND_REQUEST_ARTISTS(capabilities);
        }
    }

    public static String MPD_COMMAND_REQUEST_ALBUMARTISTS_SORT(MPDCapabilities capabilities) {
        if (capabilities.hasTagAlbumArtistSort()) {
            if (!(capabilities.hasListGroup() && capabilities.hasMusicBrainzTags())) {
                return "list albumartistsort";
            } else {
                return "list albumartistsort group MUSICBRAINZ_ARTISTID";
            }
        } else {
            return MPD_COMMAND_REQUEST_ARTISTS(capabilities);
        }
    }

    public static final String MPD_COMMAND_REQUEST_ALL_FILES = "listall";

    /* Control commands */
    public static String MPD_COMMAND_PAUSE(boolean pause) {
        return "pause " + (pause ? "1" : "0");
    }

    public static final String MPD_COMMAND_NEXT = "next";
    public static final String MPD_COMMAND_PREVIOUS = "previous";
    public static final String MPD_COMMAND_STOP = "stop";

    public static final String MPD_COMMAND_GET_CURRENT_STATUS = "status";
    public static final String MPD_COMMAND_GET_STATISTICS = "stats";

    public static final String MPD_COMMAND_GET_SAVED_PLAYLISTS = "listplaylists";

    public static final String MPD_COMMAND_GET_CURRENT_PLAYLIST = "playlistinfo";
    public static final String MPD_COMMAND_GET_PLAYLIST_LENGTH = "playlistlength";

    public static String MPD_COMMAND_GET_CURRENT_PLAYLIST_WINDOW(int start, int end) {
        return "playlistinfo " + start + ':' + end;
    }

    public static String MPD_COMMAND_GET_SAVED_PLAYLIST(String playlistName) {
        return "listplaylistinfo \"" + escapeString(playlistName) + "\"";
    }

    public static String MPD_COMMAND_GET_FILES_INFO(String path) {
        return "lsinfo \"" + escapeString(path) + "\"";
    }

    public static String MPD_COMMAND_SAVE_PLAYLIST(String playlistName) {
        return "save \"" + escapeString(playlistName) + "\"";
    }

    public static String MPD_COMMAND_REMOVE_PLAYLIST(String playlistName) {
        return "rm \"" + escapeString(playlistName) + "\"";
    }

    public static String MPD_COMMAND_LOAD_PLAYLIST(String playlistName) {
        return "load \"" + escapeString(playlistName) + "\"";
    }

    public static String MPD_COMMAND_ADD_TRACK_TO_PLAYLIST(String playlistName, String url) {
        return "playlistadd \"" + escapeString(playlistName) + "\" \"" + url + '\"';
    }

    public static String MPD_COMMAND_REMOVE_TRACK_FROM_PLAYLIST(String playlistName, int position) {
        return "playlistdelete \"" + escapeString(playlistName) + "\" " + position;
    }

    public static String MPD_COMMAND_GET_PLAYLIST_LENGTH(String playlistName) {
        return MPD_COMMAND_GET_PLAYLIST_LENGTH + " \"" + escapeString(playlistName) + "\" ";
    }

    public static final String MPD_COMMAND_GET_CURRENT_SONG = "currentsong";

    public static final String MPD_COMMAND_START_IDLE = "idle";
    public static final String MPD_COMMAND_STOP_IDLE = "noidle";

    public static final String MPD_START_COMMAND_LIST = "command_list_begin";
    public static final String MPD_END_COMMAND_LIST = "command_list_end";

    public static String MPD_COMMAND_ADD_FILE(String url) {
        return "add \"" + escapeString(url) + "\"";
    }

    public static String MPD_COMMAND_ADD_FILE_AT_INDEX(String url, int index) {
        return "addid \"" + escapeString(url) + "\"  " + index;
    }

    public static String MPD_COMMAND_REMOVE_SONG_FROM_CURRENT_PLAYLIST(int index) {
        return "delete " + index;
    }

    public static String MPD_COMMAND_REMOVE_RANGE_FROM_CURRENT_PLAYLIST(int start, int end) {
        return "delete " + start + ':' + end;
    }

    public static String MPD_COMMAND_MOVE_SONG_FROM_INDEX_TO_INDEX(int from, int to) {
        return "move " + from + ' ' + to;
    }

    public static final String MPD_COMMAND_CLEAR_PLAYLIST = "clear";

    public static String MPD_COMMAND_SET_RANDOM(boolean random) {
        return "random " + (random ? "1" : "0");
    }

    public static String MPD_COMMAND_SET_REPEAT(boolean repeat) {
        return "repeat " + (repeat ? "1" : "0");
    }

    public static String MPD_COMMAND_SET_SINGLE(boolean single) {
        return "single " + (single ? "1" : "0");
    }

    public static String MPD_COMMAND_SET_CONSUME(boolean consume) {
        return "consume " + (consume ? "1" : "0");
    }


    public static String MPD_COMMAND_PLAY_SONG_INDEX(int index) {
        return "play " + index;
    }

    public static String MPD_COMMAND_SEEK_SECONDS(int index, int seconds) {
        return "seek " + index + ' ' + seconds;
    }

    public static String MPD_COMMAND_SEEK_CURRENT_SECONDS(int seconds) {
        return "seekcur " + seconds;
    }

    public static String MPD_COMMAND_SET_VOLUME(int volume) {
        if (volume > 100) {
            volume = 100;
        } else if (volume < 0) {
            volume = 0;
        }
        return "setvol " + volume;
    }

    public static final String MPD_COMMAND_GET_OUTPUTS = "outputs";

    public static String MPD_COMMAND_TOGGLE_OUTPUT(int id) {
        return "toggleoutput " + id;
    }

    public static String MPD_COMMAND_ENABLE_OUTPUT(int id) {
        return "enableoutput " + id;
    }

    public static String MPD_COMMAND_DISABLE_OUTPUT(int id) {
        return "disableoutput " + id;
    }

    public static String MPD_COMMAND_UPDATE_DATABASE(String path) {
        if (null != path && !path.isEmpty()) {
            return "update \"" + escapeString(path) + "\"";
        } else {
            return "update";
        }
    }

    public enum MPD_SEARCH_TYPE {
        MPD_SEARCH_TRACK,
        MPD_SEARCH_ALBUM,
        MPD_SEARCH_ARTIST,
        MPD_SEARCH_FILE,
        MPD_SEARCH_ANY,
    }

    public static String MPD_COMMAND_SEARCH_FILES(String searchTerm, MPD_SEARCH_TYPE type) {
        switch (type) {
            case MPD_SEARCH_TRACK:
                return "search title \"" + escapeString(searchTerm) + '\"';
            case MPD_SEARCH_ALBUM:
                return "search album \"" + escapeString(searchTerm) + '\"';
            case MPD_SEARCH_ARTIST:
                return "search artist \"" + escapeString(searchTerm) + '\"';
            case MPD_SEARCH_FILE:
                return "search file \"" + escapeString(searchTerm) + '\"';
            case MPD_SEARCH_ANY:
                return "search any \"" + escapeString(searchTerm) + '\"';
        }
        return "ping";
    }

    public static final String MPD_COMMAND_ADD_SEARCH_FILES_CMD_NAME = "searchadd";

    public static String MPD_COMMAND_ADD_SEARCH_FILES(String searchTerm, MPD_SEARCH_TYPE type) {
        switch (type) {
            case MPD_SEARCH_TRACK:
                return MPD_COMMAND_ADD_SEARCH_FILES_CMD_NAME + " title \"" + escapeString(searchTerm) + '\"';
            case MPD_SEARCH_ALBUM:
                return MPD_COMMAND_ADD_SEARCH_FILES_CMD_NAME + " album \"" + escapeString(searchTerm) + '\"';
            case MPD_SEARCH_ARTIST:
                return MPD_COMMAND_ADD_SEARCH_FILES_CMD_NAME + " artist \"" + escapeString(searchTerm) + '\"';
            case MPD_SEARCH_FILE:
                return MPD_COMMAND_ADD_SEARCH_FILES_CMD_NAME + " file \"" + escapeString(searchTerm) + '\"';
            case MPD_SEARCH_ANY:
                return MPD_COMMAND_ADD_SEARCH_FILES_CMD_NAME + " any \"" + escapeString(searchTerm) + '\"';
        }
        return "ping";
    }

    public static final String MPD_COMMAND_GET_COMMANDS = "commands";

    public static final String MPD_COMMAND_GET_TAGS = "tagtypes";

    public static final String MPD_COMMAND_PLAYLIST_FIND = "playlistfind";

    public static final String MPD_COMMAND_READ_PICTURE = "readpicture";

    /**
     * Searches the song of an given URL in the current playlist. MPD will respond by
     * returning a track object if found or nothing else.
     *
     * @param url URL to search for.
     * @return command string for MPD
     */
    public static String MPD_COMMAND_PLAYLIST_FIND_URI(String url) {
        return "playlistfind file \"" + escapeString(url) + "\"";
    }

    public static final String MPD_COMMAND_SHUFFLE_PLAYLIST = "shuffle";


    private static String escapeString(String input) {
        return input.replaceAll("\\\\","\\\\\\\\").replaceAll("\"", "\\\\\"");
    }

    public static String MPD_COMMAND_GET_ALBUMART(String url, int offset) {
        return "albumart \"" + escapeString(url) + "\" " + offset;
    }

    public static String MPD_COMMAND_GET_READPICTURE(String url, int offset) {
        return "readpicture \"" + escapeString(url) + "\" " + offset;
    }

    public static final String MPD_COMMAND_GET_PARTITIONS = "listpartitions";

    public static String MPD_COMMAND_NEW_PARTITION(String name) {
        return "newpartition \"" + escapeString(name) + "\"";
    }

    public static String MPD_COMMAND_DELETE_PARTITION(String name) {
        return "delpartition \"" + escapeString(name) + "\"";
    }

    public static String MPD_COMMAND_SWITCH_PARTITION(String name) {
        return "partition \"" + escapeString(name) + "\"";
    }

    public static String MPD_COMMAND_MOVE_OUTPUT(String name) {
        return "moveoutput \"" + escapeString(name) + "\"";
    }

}
