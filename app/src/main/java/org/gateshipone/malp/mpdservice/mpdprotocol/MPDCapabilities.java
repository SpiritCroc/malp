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

import java.util.List;

public class MPDCapabilities {
    private static final String TAG = MPDCapabilities.class.getSimpleName();

    private static final String MPD_TAG_TYPE_MUSICBRAINZ = "musicbrainz";
    private static final String MPD_TAG_TYPE_ALBUMARTIST = "albumartist";
    private static final String MPD_TAG_TYPE_ARTISTSORT = "artistsort";
    private static final String MPD_TAG_TYPE_ALBUMARTISTSORT = "albumartistsort";
    private static final String MPD_TAG_TYPE_DATE = "date";

    private int mMajorVersion;
    private int mMinorVersion;
    private int mPatchVersion;

    private boolean mHasIdle;
    private final boolean mHasRangedCurrentPlaylist;
    private boolean mHasSearchAdd;

    private boolean mHasMusicBrainzTags;
    private boolean mHasListGroup;

    private boolean mHasListFiltering;

    private boolean mHasCurrentPlaylistRemoveRange;

    private boolean mHasToggleOutput;

    private boolean mMopidyDetected;

    private boolean mMPDBug408Active;
    private boolean mMultipleListGroupFixed;

    private boolean mTagAlbumArtist;
    private boolean mTagArtistSort;
    private boolean mTagAlbumArtistSort;
    private boolean mTagDate;

    private boolean mHasPlaylistFind;

    private boolean mHasSeekCurrent;

    private boolean mHasAlbumArt;
    private boolean mHasReadPicture;

    public MPDCapabilities(String version, List<String> commands, List<String> tags) {
        String[] versions = version.split("\\.");
        if (versions.length == 3) {
            mMajorVersion = Integer.parseInt(versions[0]);
            mMinorVersion = Integer.parseInt(versions[1]);
            mPatchVersion = Integer.parseInt(versions[2]);
        }

        // Only MPD servers greater version 0.14 have ranged playlist fetching, this allows fallback
        if (mMinorVersion > 14 || mMajorVersion > 0) {
            mHasRangedCurrentPlaylist = true;
        } else {
            mHasRangedCurrentPlaylist = false;
        }

        if (mMinorVersion >= 16 || mMajorVersion > 0) {
            mHasCurrentPlaylistRemoveRange = true;
        }

        if (mMinorVersion >= 17 || mMajorVersion > 0) {
            mHasSeekCurrent = true;
        }


        if (mMinorVersion >= 18 || mMajorVersion > 0) {
            mHasToggleOutput = true;
        }

        if (mMinorVersion >= 19 && mMajorVersion == 0 && mMinorVersion <= 20) {
            // MPD 0.19 - 0.20 (only buggy for last MPD 0.20.x release, can be detected by mopidy workaround)
            mHasListGroup = true;
            mHasListFiltering = true;
        } else if (mMinorVersion == 21 && mMajorVersion == 0 && mPatchVersion < 11) {
            // Buggy for all MPD version from 0.21.0 to 0.21.10
            mMPDBug408Active = true;
            mHasListGroup = false;
            mHasListFiltering = false;
        } else if ((mMinorVersion >= 21 && mMajorVersion == 0 && mPatchVersion >= 11) || mMajorVersion > 0 || (mMajorVersion == 0 && mMinorVersion > 21)) {
            // Fixed versions >= MPD 0.21.11
            mMultipleListGroupFixed = true;
            mHasListGroup = true;
            mHasListFiltering = true;
        }

        if (mMinorVersion >= 21 || mMajorVersion > 0) {
            mHasAlbumArt = true;
        }


        if (null != commands) {
            mHasIdle = commands.contains(MPDCommands.MPD_COMMAND_START_IDLE);
            mHasSearchAdd = commands.contains(MPDCommands.MPD_COMMAND_ADD_SEARCH_FILES_CMD_NAME);
            mHasPlaylistFind = commands.contains(MPDCommands.MPD_COMMAND_PLAYLIST_FIND);
            mHasReadPicture = commands.contains(MPDCommands.MPD_COMMAND_READ_PICTURE);
        }


        if (null != tags) {
            for (String tag : tags) {
                String tagLC = tag.toLowerCase();
                if (tagLC.contains(MPD_TAG_TYPE_MUSICBRAINZ)) {
                    mHasMusicBrainzTags = true;
                    break;
                } else if (tagLC.equals(MPD_TAG_TYPE_ALBUMARTIST)) {
                    mTagAlbumArtist = true;
                } else if (tagLC.equals(MPD_TAG_TYPE_DATE)) {
                    mTagDate = true;
                } else if (tagLC.equals(MPD_TAG_TYPE_ARTISTSORT)) {
                    mTagArtistSort = true;
                } else if (tagLC.equals(MPD_TAG_TYPE_ALBUMARTISTSORT)) {
                    mTagAlbumArtistSort = true;
                }
            }
        }
    }

    public boolean hasIdling() {
        return mHasIdle;
    }

    public boolean hasRangedCurrentPlaylist() {
        return mHasRangedCurrentPlaylist;
    }

    public boolean hasSearchAdd() {
        return mHasSearchAdd;
    }

    public boolean hasListGroup() {
        return mHasListGroup;
    }

    public boolean hasListFiltering() {
        return mHasListFiltering;
    }

    public int getMajorVersion() {
        return mMajorVersion;
    }

    public int getMinorVersion() {
        return mMinorVersion;
    }

    public boolean hasMusicBrainzTags() {
        return mHasMusicBrainzTags;
    }

    public boolean hasCurrentPlaylistRemoveRange() {
        return mHasCurrentPlaylistRemoveRange;
    }

    public boolean hasTagAlbumArtist() {
        return mTagAlbumArtist;
    }

    public boolean hasTagArtistSort() {
        return mTagArtistSort;
    }

    public boolean hasTagAlbumArtistSort() {
        return mTagAlbumArtistSort;
    }

    public boolean hasTagDate() {
        return mTagDate;
    }

    public boolean hasToggleOutput() {
        return mHasToggleOutput;
    }

    public boolean hasPlaylistFind() {
        return mHasPlaylistFind;
    }

    public boolean hasSeekCurrent() {
        return mHasSeekCurrent;
    }

    public boolean hasAlbumArt() {
        return mHasAlbumArt;
    }

    public boolean hasReadPicture() {
        return mHasReadPicture;
    }

    public boolean hasListGroupingFixed() {
        return mMultipleListGroupFixed;
    }

    public String getServerFeatures() {
        return "MPD protocol version: " + mMajorVersion + '.' + mMinorVersion + '.' + mPatchVersion + '\n'
                + "TAGS:" + '\n'
                + "MUSICBRAINZ: " + mHasMusicBrainzTags + '\n'
                + "AlbumArtist: " + mTagAlbumArtist + '\n'
                + "Date: " + mTagDate + '\n'
                + "IDLE support: " + mHasIdle + '\n'
                + "Windowed playlist: " + mHasRangedCurrentPlaylist + '\n'
                + "Fast search add: " + mHasSearchAdd + '\n'
                + "List grouping: " + mHasListGroup + '\n'
                + "List filtering: " + mHasListFiltering + '\n'
                + "Fast ranged currentplaylist delete: " + mHasCurrentPlaylistRemoveRange + '\n'
                + "MPD based album artwork: " + mHasAlbumArt + '|' + mHasReadPicture + '\n'
                + (mMopidyDetected ? "Mopidy detected, consider using the real MPD server (www.musicpd.org)!\n" : "")
                + (mMPDBug408Active ? "Temporarily limited protocol usage active because of MPD bug #408 and arbitrary protocol changes\n" : "");
    }

    public void enableMopidyWorkaround() {
        Log.w(TAG, "Enabling workarounds for detected Mopidy server");
        mHasListGroup = false;
        mHasListFiltering = false;
        mMopidyDetected = true;

        // Command is listed in "commands" but mopidy returns "not implemented"
        mHasPlaylistFind = false;
    }
}
