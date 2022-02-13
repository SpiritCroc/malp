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

package org.gateshipone.malp.application.artwork.network;

import android.net.Uri;

import org.gateshipone.malp.application.utils.FormatHelper;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDGenericItem;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

public class ArtworkRequestModel {

    public enum ArtworkRequestType {
        ALBUM,
        ARTIST,
        TRACK
    }

    private final MPDGenericItem mModel;

    private final ArtworkRequestType mType;

    public ArtworkRequestModel(MPDArtist artistModel) {
        this(artistModel, ArtworkRequestType.ARTIST);
    }

    public ArtworkRequestModel(MPDAlbum albumModel) {
        this(albumModel, ArtworkRequestType.ALBUM);
    }

    public ArtworkRequestModel(MPDTrack trackModel) {
        this(trackModel, ArtworkRequestType.TRACK);
    }

    private ArtworkRequestModel(MPDGenericItem model, ArtworkRequestType type) {
        mModel = model;
        mType = type;
    }

    public ArtworkRequestType getType() {
        return mType;
    }

    public MPDGenericItem getGenericModel() {
        return mModel;
    }

    public String getMBID() {
        return getMBID(0);
    }

    public String getMBID(int position) {
        switch (mType) {
            case ALBUM:
                return ((MPDAlbum) mModel).getMBID();
            case ARTIST:
                final MPDArtist artist = (MPDArtist) mModel;
                if (artist.getMBIDCount() > position) {
                    return artist.getMBID(position);
                }
                break;
            case TRACK:
                break;
        }
        return null;
    }

    public String getPath() {
        switch (mType) {
            case ALBUM:
            case ARTIST:
                break;
            case TRACK:
                return ((MPDTrack) mModel).getPath();
        }
        return null;
    }

    public String getAlbumName() {
        String albumName = null;

        switch (mType) {
            case ALBUM:
                albumName = ((MPDAlbum) mModel).getName();
                break;
            case ARTIST:
            case TRACK:
                break;
        }

        return albumName;
    }

    public String getEncodedAlbumName() {
        String encodedAlbumName = null;

        switch (mType) {
            case ALBUM:
                encodedAlbumName = Uri.encode(((MPDAlbum) mModel).getName());
                break;
            case ARTIST:
            case TRACK:
                break;
        }

        return encodedAlbumName;
    }

    public String getLuceneEscapedEncodedAlbumName() {
        String escapedAlbumName = null;

        switch (mType) {
            case ALBUM:
                escapedAlbumName = FormatHelper.escapeSpecialCharsLucene(((MPDAlbum) mModel).getName());
                break;
            case ARTIST:
            case TRACK:
                break;
        }

        return Uri.encode(escapedAlbumName);
    }

    public String getArtistName() {
        String artistName = null;

        switch (mType) {
            case ALBUM:
                artistName = ((MPDAlbum) mModel).getArtistName();
                break;
            case ARTIST:
                artistName = ((MPDArtist) mModel).getArtistName();
                break;
            case TRACK:
                break;
        }

        return artistName;
    }

    public String getEncodedArtistName() {
        String encodedArtistName = null;

        switch (mType) {
            case ALBUM:
                encodedArtistName = Uri.encode(((MPDAlbum) mModel).getArtistName());
                break;
            case ARTIST:
                encodedArtistName = Uri.encode(((MPDArtist) mModel).getArtistName().replaceAll("/", " "));
                break;
            case TRACK:
                break;
        }

        return encodedArtistName;
    }

    public String getLuceneEscapedEncodedArtistName() {
        String escapedArtistName = null;

        switch (mType) {
            case ALBUM:
                escapedArtistName = FormatHelper.escapeSpecialCharsLucene(((MPDAlbum) mModel).getArtistName());
                break;
            case ARTIST:
                escapedArtistName = FormatHelper.escapeSpecialCharsLucene(((MPDArtist) mModel).getArtistName());
                break;
            case TRACK:
                break;
        }

        return Uri.encode(escapedArtistName);
    }


    public String getLoggingString() {
        String loggingString = "";

        switch (mType) {
            case ALBUM:
                loggingString = ((MPDAlbum) mModel).getName() + "-" + ((MPDAlbum) mModel).getArtistName();
                break;
            case ARTIST:
                loggingString = ((MPDArtist) mModel).getArtistName();
                break;
            case TRACK:
                loggingString = ((MPDTrack) mModel).getStringTag(MPDTrack.StringTagTypes.ALBUM);
                break;
        }

        return loggingString;
    }
}
