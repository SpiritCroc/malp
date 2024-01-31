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


import java.util.HashMap;
import java.util.Map;

public class MPDResponses {
    public static final String MPD_RESPONSE_SIZE = "size: ";
    public static final String MPD_RESPONSE_BINARY_SIZE = "binary: ";


    public static final String MPD_RESPONSE_CHANGED = "changed: ";

    public static final String MPD_PLAYBACK_STATE_RESPONSE_PLAY = "play";
    public static final String MPD_PLAYBACK_STATE_RESPONSE_PAUSE = "pause";
    public static final String MPD_PLAYBACK_STATE_RESPONSE_STOP = "stop";

    public static final String MPD_PARSE_ARGS_LIST_ERROR = "not able to parse args";
    public static final String MPD_UNKNOWN_FILTER_TYPE_ERROR = "Unknown filter type";

    public enum MPD_RESPONSE_KEY {
        RESPONSE_OK,
        RESPONSE_ACK,
        RESPONSE_ALBUM,
        RESPONSE_ALBUM_MBID,
        RESPONSE_ARTIST,
        RESPONSE_ARTISTSORT,
        RESPONSE_ALBUMARTIST,
        RESPONSE_ALBUMARTISTSORT,
        RESPONSE_FILE,
        RESPONSE_DIRECTORY,
        RESPONSE_TITLE,
        RESPONSE_TIME,
        RESPONSE_DATE,
        RESPONSE_NAME,
        RESPONSE_TRACK_MBID,
        RESPONSE_ALBUMARTIST_MBID,
        RESPONSE_ARTIST_MBID,
        RESPONSE_TRACK,
        RESPONSE_DISC,
        RESPONSE_POS,
        RESPONSE_ID,
        RESPONSE_PLAYLIST,
        RESPONSE_LAST_MODIFIED,
        RESPONSE_SIZE,
        RESPONSE_BINARY,
        RESPONSE_VOLUME,
        RESPONSE_REPEAT,
        RESPONSE_RANDOM,
        RESPONSE_SINGLE,
        RESPONSE_CONSUME,
        RESPONSE_PLAYLISTLENGTH,
        RESPONSE_SONG,
        RESPONSE_SONG_ID,
        RESPONSE_NEXT_SONG,
        RESPONSE_NEXT_SONG_ID,
        RESPONSE_TIME_OLD,
        RESPONSE_ELAPSED,
        RESPONSE_DURATION,
        RESPONSE_BITRATE,
        RESPONSE_AUDIO,
        RESPONSE_UPDATING_DB,
        RESPONSE_ERROR,
        RESPONSE_CHANGED,
        RESPONSE_STATE,
        RESPONSE_OUTPUT_ID,
        RESPONSE_OUTPUT_NAME,
        RESPONSE_OUTPUT_ENABLED,
        RESPONSE_OUTPUT_PLUGIN,
        RESPONSE_UPTIME,
        RESPONSE_PLAYTIME,
        RESPONSE_ARTISTS,
        RESPONSE_ALBUMS,
        RESPONSE_SONGS,
        RESPONSE_DB_PLAYTIME,
        RESPONSE_DB_UPDATE,
        RESPONSE_COMMAND,
        RESPONSE_TAGTYPE,
        RESPONSE_COMPOSER,
        RESPONSE_CONDUCTOR,
        RESPONSE_PERFORMER,
        RESPONSE_WORK,
        RESPONSE_WORK_MBID,
        RESPONSE_GENRE,
        RESPONSE_COMMENT,
        RESPONSE_LABEL,
        RESPONSE_UNKNOWN,
        RESPONSE_PARTITION,
    }

    private static Map<String, MPD_RESPONSE_KEY> createResponseMap() {
        Map<String, MPD_RESPONSE_KEY> map = new HashMap<>(MPD_RESPONSE_KEY.values().length);

        // Create dumb mapping
        map.put("OK", MPD_RESPONSE_KEY.RESPONSE_OK);
        map.put("ACK", MPD_RESPONSE_KEY.RESPONSE_ACK);
        map.put("Album", MPD_RESPONSE_KEY.RESPONSE_ALBUM);
        map.put("MUSICBRAINZ_ALBUMID", MPD_RESPONSE_KEY.RESPONSE_ALBUM_MBID);
        map.put("Artist", MPD_RESPONSE_KEY.RESPONSE_ARTIST);
        map.put("ArtistSort", MPD_RESPONSE_KEY.RESPONSE_ARTISTSORT);
        map.put("AlbumArtist", MPD_RESPONSE_KEY.RESPONSE_ALBUMARTIST);
        map.put("AlbumArtistSort", MPD_RESPONSE_KEY.RESPONSE_ALBUMARTISTSORT);
        map.put("file", MPD_RESPONSE_KEY.RESPONSE_FILE);
        map.put("directory", MPD_RESPONSE_KEY.RESPONSE_DIRECTORY);
        map.put("Title", MPD_RESPONSE_KEY.RESPONSE_TITLE);
        map.put("Time", MPD_RESPONSE_KEY.RESPONSE_TIME);
        map.put("Date", MPD_RESPONSE_KEY.RESPONSE_DATE);
        map.put("Name", MPD_RESPONSE_KEY.RESPONSE_NAME);
        map.put("MUSICBRAINZ_TRACKID", MPD_RESPONSE_KEY.RESPONSE_TRACK_MBID);
        map.put("MUSICBRAINZ_ALBUMARTISTID", MPD_RESPONSE_KEY.RESPONSE_ALBUMARTIST_MBID);
        map.put("MUSICBRAINZ_ARTISTID", MPD_RESPONSE_KEY.RESPONSE_ARTIST_MBID);
        map.put("Track", MPD_RESPONSE_KEY.RESPONSE_TRACK);
        map.put("Disc", MPD_RESPONSE_KEY.RESPONSE_DISC);
        map.put("Pos", MPD_RESPONSE_KEY.RESPONSE_POS);
        map.put("Id", MPD_RESPONSE_KEY.RESPONSE_ID);
        map.put("playlist", MPD_RESPONSE_KEY.RESPONSE_PLAYLIST);
        map.put("Last-Modified", MPD_RESPONSE_KEY.RESPONSE_LAST_MODIFIED);
        map.put("size", MPD_RESPONSE_KEY.RESPONSE_SIZE);
        map.put("binary", MPD_RESPONSE_KEY.RESPONSE_BINARY);
        map.put("volume", MPD_RESPONSE_KEY.RESPONSE_VOLUME);
        map.put("repeat", MPD_RESPONSE_KEY.RESPONSE_REPEAT);
        map.put("random", MPD_RESPONSE_KEY.RESPONSE_RANDOM);
        map.put("single", MPD_RESPONSE_KEY.RESPONSE_SINGLE);
        map.put("consume", MPD_RESPONSE_KEY.RESPONSE_CONSUME);
        map.put("playlistlength", MPD_RESPONSE_KEY.RESPONSE_PLAYLISTLENGTH);
        map.put("song", MPD_RESPONSE_KEY.RESPONSE_SONG);
        map.put("songid", MPD_RESPONSE_KEY.RESPONSE_SONG_ID);
        map.put("nextsong", MPD_RESPONSE_KEY.RESPONSE_NEXT_SONG);
        map.put("nextsongid", MPD_RESPONSE_KEY.RESPONSE_NEXT_SONG_ID);
        map.put("time", MPD_RESPONSE_KEY.RESPONSE_TIME_OLD);
        map.put("elapsed", MPD_RESPONSE_KEY.RESPONSE_ELAPSED);
        map.put("duration", MPD_RESPONSE_KEY.RESPONSE_DURATION);
        map.put("bitrate", MPD_RESPONSE_KEY.RESPONSE_BITRATE);
        map.put("audio", MPD_RESPONSE_KEY.RESPONSE_AUDIO);
        map.put("updating_db", MPD_RESPONSE_KEY.RESPONSE_UPDATING_DB);
        map.put("error", MPD_RESPONSE_KEY.RESPONSE_ERROR);
        map.put("changed", MPD_RESPONSE_KEY.RESPONSE_CHANGED);
        map.put("state", MPD_RESPONSE_KEY.RESPONSE_STATE);
        map.put("outputid", MPD_RESPONSE_KEY.RESPONSE_OUTPUT_ID);
        map.put("outputname", MPD_RESPONSE_KEY.RESPONSE_OUTPUT_NAME);
        map.put("outputenabled", MPD_RESPONSE_KEY.RESPONSE_OUTPUT_ENABLED);
        map.put("plugin", MPD_RESPONSE_KEY.RESPONSE_OUTPUT_PLUGIN);
        map.put("uptime", MPD_RESPONSE_KEY.RESPONSE_UPTIME);
        map.put("playtime", MPD_RESPONSE_KEY.RESPONSE_PLAYTIME);
        map.put("artists", MPD_RESPONSE_KEY.RESPONSE_ARTISTS);
        map.put("albums", MPD_RESPONSE_KEY.RESPONSE_ALBUMS);
        map.put("songs", MPD_RESPONSE_KEY.RESPONSE_SONGS);
        map.put("db_playtime", MPD_RESPONSE_KEY.RESPONSE_DB_PLAYTIME);
        map.put("db_update", MPD_RESPONSE_KEY.RESPONSE_DB_UPDATE);
        map.put("command", MPD_RESPONSE_KEY.RESPONSE_COMMAND);
        map.put("tagtype", MPD_RESPONSE_KEY.RESPONSE_TAGTYPE);
        map.put("Composer", MPD_RESPONSE_KEY.RESPONSE_COMPOSER);
        map.put("Conductor", MPD_RESPONSE_KEY.RESPONSE_CONDUCTOR);
        map.put("Performer", MPD_RESPONSE_KEY.RESPONSE_PERFORMER);
        map.put("Work", MPD_RESPONSE_KEY.RESPONSE_WORK);
        map.put("MUSICBRAINZ_WORKID", MPD_RESPONSE_KEY.RESPONSE_WORK_MBID);
        map.put("Genre", MPD_RESPONSE_KEY.RESPONSE_GENRE);
        map.put("Comment", MPD_RESPONSE_KEY.RESPONSE_COMMENT);
        map.put("Label", MPD_RESPONSE_KEY.RESPONSE_LABEL);
        map.put("partition", MPD_RESPONSE_KEY.RESPONSE_PARTITION);

        return map;
    }

    public static final Map<String, MPD_RESPONSE_KEY> RESPONSE_KEYMAP = createResponseMap();

}