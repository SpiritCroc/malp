/*
 *  Copyright (C) 2020 Team Gateship-One
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

package org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects;


import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.gateshipone.malp.R;

import java.util.HashMap;

/**
 * This class represents an MPDTrack. This is the same type for tracks and files.
 * This is used for tracks in playlist, album, search results,... and for music files when
 * retrieving an directory listing from the mpd server.
 */
public class MPDTrack extends MPDFileEntry implements MPDGenericItem, Parcelable {
    public enum StringTagTypes {
        ARTIST,
        ARTISTSORT,
        ALBUM,
        ALBUMSORT,
        ALBUMARTIST,
        ALBUMARTISTSORT,
        DATE,
        TITLE,
        NAME,
        GENRE,
        COMPOSER,
        PERFORMER,
        CONDUCTOR,
        WORK,
        COMMENT,
        LABEL,
        ARTIST_MBID,
        ALBUM_MBID,
        ALBUMARTIST_MBID,
        TRACK_MBID,
        RELEASETRACK_MBID,
        WORK_MBID
    }

    private HashMap<StringTagTypes, String> pStringTags;

    /**
     * Length in seconds
     */
    private int pLength;

    /**
     * Track number within the album of the song
     */
    private int pTrackNumber;

    /**
     * Count of songs on the album of the song. Can be 0
     */
    private int pAlbumTrackCount;

    /**
     * The number of the medium(of the songs album) the song is on
     */
    private int pDiscNumber;

    /**
     * The count of mediums of the album the track is on. Can be 0.
     */
    private int pAlbumDiscCount;

    /**
     * Available for tracks in the current playlist
     */
    private int pSongPosition;

    /**
     * Available for tracks in the current playlist
     */
    private int pSongID;

    protected MPDTrack(Parcel in) {
        pLength = in.readInt();
        pTrackNumber = in.readInt();
        pAlbumTrackCount = in.readInt();
        pDiscNumber = in.readInt();
        pAlbumDiscCount = in.readInt();
        pSongPosition = in.readInt();
        pSongID = in.readInt();
        pChannelCount = in.readInt();
        pSampleRate = in.readInt();
        pBitDepth = in.readInt();
        pImageFetching = in.readByte() != 0;
        pStringTags = (HashMap<StringTagTypes, String>) in.readSerializable();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(pLength);
        dest.writeInt(pTrackNumber);
        dest.writeInt(pAlbumTrackCount);
        dest.writeInt(pDiscNumber);
        dest.writeInt(pAlbumDiscCount);
        dest.writeInt(pSongPosition);
        dest.writeInt(pSongID);
        dest.writeInt(pChannelCount);
        dest.writeInt(pSampleRate);
        dest.writeInt(pBitDepth);
        dest.writeByte((byte) (pImageFetching ? 1 : 0));
        dest.writeSerializable(pStringTags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MPDTrack> CREATOR = new Creator<MPDTrack>() {
        @Override
        public MPDTrack createFromParcel(Parcel in) {
            return new MPDTrack(in);
        }

        @Override
        public MPDTrack[] newArray(int size) {
            return new MPDTrack[size];
        }
    };

    public int getChannelCount() {
        return pChannelCount;
    }

    public void setChannelCount(int pChannelCount) {
        this.pChannelCount = pChannelCount;
    }

    public int getSampleRate() {
        return pSampleRate;
    }

    public void setSampleRate(int pSampleRate) {
        this.pSampleRate = pSampleRate;
    }

    public int getBitDepth() {
        return pBitDepth;
    }

    public void setBitDepth(int pBitDepth) {
        this.pBitDepth = pBitDepth;
    }

    private int pChannelCount;

    private int pSampleRate;

    private int pBitDepth;

    /**
     * Used for {@link org.gateshipone.malp.application.adapters.CurrentPlaylistAdapter} to save if an
     * image is already being fetchted from the internet for this item
     */
    private boolean pImageFetching;

    /**
     * Create empty MPDTrack (track). Fill it with setter methods during
     * parsing of mpds output.
     *
     * @param path The path of the file. This should never change.
     */
    public MPDTrack(@NonNull String path) {
        super(path);
        pLength = 0;

        pStringTags = new HashMap<>();

        pImageFetching = false;
    }


    public int getLength() {
        return pLength;
    }

    public void setLength(int pLength) {
        this.pLength = pLength;
    }

    public void setTrackNumber(int trackNumber) {
        pTrackNumber = trackNumber;
    }

    public int getTrackNumber() {
        return pTrackNumber;
    }

    public void setDiscNumber(int discNumber) {
        pDiscNumber = discNumber;
    }

    public int getDiscNumber() {
        return pDiscNumber;
    }

    public int getAlbumTrackCount() {
        return pAlbumTrackCount;
    }

    public void setAlbumTrackCount(int albumTrackCount) {
        pAlbumTrackCount = albumTrackCount;
    }

    public int getAlbumDiscCount() {
        return pAlbumDiscCount;
    }

    public void psetAlbumDiscCount(int discCount) {
        pAlbumDiscCount = discCount;
    }

    public int getSongPosition() {
        return pSongPosition;
    }

    public void setSongPosition(int position) {
        pSongPosition = position;
    }

    public int getSongID() {
        return pSongID;
    }

    public void setSongID(int id) {
        pSongID = id;
    }

    public boolean getFetching() {
        return pImageFetching;
    }

    public void setFetching(boolean fetching) {
        pImageFetching = fetching;
    }

    /**
     * Only used for recyclerview adapter as stable id.
     *
     * @return The return value of {@link #hashCode()} call.
     */
    public long getTrackId() {
        return hashCode();
    }

    @Override
    public int hashCode() {
        String title = getStringTag(StringTagTypes.TITLE);
        String album = getStringTag(StringTagTypes.ALBUM);
        String trackMBID = getStringTag(StringTagTypes.TRACK_MBID);
        return (title + album + trackMBID).hashCode();
    }

    @NonNull
    public String getStringTag(StringTagTypes tag) {
        String tagValue = pStringTags.get(tag);
        return tagValue == null ? "" : tagValue;
    }

    public void setStringTag(StringTagTypes tag, @NonNull String value) {
        pStringTags.put(tag, value);
    }

    /**
     * Returns either the track title, name or filename depending on which is set.
     */
    @NonNull
    public String getVisibleTitle() {
        String title = getStringTag(StringTagTypes.TITLE);
        String trackName = getStringTag(StringTagTypes.NAME);
        if (!title.isEmpty()) {
            return title;
        } else if (!trackName.isEmpty()) {
            return trackName;
        } else {
            return getFilename();
        }
    }

    /**
     * @return String that is used for section based scrolling
     */
    @Override
    @NonNull
    public String getSectionTitle() {
        String title = getStringTag(StringTagTypes.TITLE);
        return title.isEmpty() ? mPath : title;
    }

    public int indexCompare(MPDTrack compFile) {
        String albumMBID = getStringTag(StringTagTypes.ALBUM_MBID);
        String compAlbumMBID = compFile.getStringTag(StringTagTypes.ALBUM_MBID);

        if (!albumMBID.equals(compAlbumMBID)) {
            return albumMBID.compareTo(compAlbumMBID);
        }

        // Compare disc numbers first
        if (pDiscNumber > compFile.pDiscNumber) {
            return 1;
        } else if (pDiscNumber == compFile.pDiscNumber) {
            // Compare track number field
            if (pTrackNumber > compFile.pTrackNumber) {
                return 1;
            } else if (pTrackNumber == compFile.pTrackNumber) {
                return 0;
            } else {
                return -1;
            }
        } else {
            return -1;
        }
    }

    /**
     * Compares the file names of two tracks with each other. The prefix path is discarded before
     * comparing.
     *
     * @param another {@link MPDTrack} to compare
     * @return see super class
     */
    public int compareTo(@NonNull MPDTrack another) {
        String title = getFilename();
        String anotherTitle = another.getFilename();

        return title.toLowerCase().compareTo(anotherTitle.toLowerCase());
    }

    public MPDAlbum getAlbum() {
        MPDAlbum tmpAlbum = new MPDAlbum(getStringTag(MPDTrack.StringTagTypes.ALBUM));

        String albumArtist = getStringTag(StringTagTypes.ALBUMARTIST);
        String artist = getStringTag(StringTagTypes.ARTIST);

        // Set album artist
        if (!albumArtist.isEmpty()) {
            tmpAlbum.setArtistName(albumArtist);
        } else {
            tmpAlbum.setArtistName(artist);
        }

        String albumArtistSort = getStringTag(StringTagTypes.ALBUMARTISTSORT);
        String artistSort = getStringTag(StringTagTypes.ARTISTSORT);
        // Set albumartistsort
        if (!albumArtistSort.isEmpty()) {
            tmpAlbum.setArtistSortName(albumArtistSort);
        } else {
            tmpAlbum.setArtistSortName(artistSort);
        }

        tmpAlbum.setMBID(getStringTag(StringTagTypes.ALBUM_MBID));
        return tmpAlbum;
    }

    public String getSubLine(Context context) {
        String subLine;
        String trackArtist = getStringTag(MPDTrack.StringTagTypes.ARTIST);
        String trackAlbum = getStringTag(MPDTrack.StringTagTypes.ALBUM);
        if (!trackArtist.isEmpty() && !trackAlbum.isEmpty()) {
            subLine = trackArtist + context.getString(R.string.track_item_separator) + trackAlbum;
        } else if (trackArtist.isEmpty() && !trackAlbum.isEmpty()) {
            subLine = trackAlbum;
        } else if (!trackArtist.isEmpty()) {
            subLine = trackArtist;
        } else {
            subLine = getPath();
        }
        return subLine;
    }

    public boolean equalsStringTag(StringTagTypes tag, MPDTrack compTrack) {
        return getStringTag(tag).equals(compTrack.getStringTag(tag));
    }
}
