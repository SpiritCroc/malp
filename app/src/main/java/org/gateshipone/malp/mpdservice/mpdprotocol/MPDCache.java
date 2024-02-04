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

import androidx.collection.LruCache;

import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;

import java.util.List;

public class MPDCache {

    public enum CACHE_TYPE {
        CACHE_TYPE_ARTISTS,
        CACHE_TYPE_ALBUMS,
        CACHE_TYPE_ARTIST_ALBUMS,
    }

    public abstract class CacheRequest<T> {
        protected CACHE_TYPE mCacheType;

        private List<T> mObjects;

        public int getCount() {
            return mObjects.size();
        }

        protected void setObjects(List<T> objects) {
            mObjects = objects;
        }

        public List<T> getObjects() {
            return mObjects;
        }
    }

    private class MPDAlbumsRequest extends CacheRequest<MPDAlbum> {
        public MPDAlbumsRequest(List<MPDAlbum> albums) {
            mCacheType = CACHE_TYPE.CACHE_TYPE_ALBUMS;

            setObjects(albums);
        }
    }

    private class MPDArtistAlbumsRequest extends CacheRequest<MPDAlbum> {
        private MPDArtist.MPD_ALBUM_ARTIST_SELECTOR mAlbumArtistSelector;

        private MPDArtist.MPD_ARTIST_SORT_SELECTOR mArtistSortSelector;

        public MPDArtist.MPD_ALBUM_ARTIST_SELECTOR getAlbumArtistSelector() {
            return mAlbumArtistSelector;
        }

        public MPDArtist.MPD_ARTIST_SORT_SELECTOR getArtistSortSelector() {
            return mArtistSortSelector;
        }

        private MPDAlbum.MPD_ALBUM_SORT_ORDER mSortOrder;

        public MPDAlbum.MPD_ALBUM_SORT_ORDER getSortOrder() {
            return mSortOrder;
        }

        public MPDArtistAlbumsRequest(List<MPDAlbum> albums, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR sortSelector, MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder) {
            mCacheType = CACHE_TYPE.CACHE_TYPE_ARTIST_ALBUMS;

            mAlbumArtistSelector = albumArtistSelector;
            mArtistSortSelector = sortSelector;
            mSortOrder = sortOrder;
            setObjects(albums);
        }
    }

    private class MPDArtistRequest extends CacheRequest<MPDArtist> {
        private MPDArtist.MPD_ALBUM_ARTIST_SELECTOR mAlbumArtistSelector;

        private MPDArtist.MPD_ARTIST_SORT_SELECTOR mArtistSortSelector;

        public MPDArtist.MPD_ALBUM_ARTIST_SELECTOR getAlbumArtistSelector() {
            return mAlbumArtistSelector;
        }

        public MPDArtist.MPD_ARTIST_SORT_SELECTOR getArtistSortSelector() {
            return mArtistSortSelector;
        }

        public MPDArtistRequest(List<MPDArtist> artists, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR sortSelector) {
            mCacheType = CACHE_TYPE.CACHE_TYPE_ALBUMS;

            mAlbumArtistSelector = albumArtistSelector;
            mArtistSortSelector = sortSelector;
            setObjects(artists);
        }
    }

    private final static int ARTIST_ALBUM_MAX_SIZE = 20;
    private final long mVersion;
    private CacheRequest<MPDAlbum> mAlbumsRequest;
    private CacheRequest<MPDArtist> mArtistsRequest;
    private final LruCache<String, MPDArtistAlbumsRequest> mArtistAlbumCache;

    public MPDCache(long version) {
        mVersion = version;
        mArtistAlbumCache = new LruCache<>(ARTIST_ALBUM_MAX_SIZE);
    }

    public long getVersion() {
        return mVersion;
    }

    public void cacheAlbums(List<MPDAlbum> albums) {
        mAlbumsRequest = new MPDAlbumsRequest(albums);
    }

    public List<MPDAlbum> getCachedAlbums() {
        if (mAlbumsRequest != null) {
            return mAlbumsRequest.getObjects();
        } else {
            return null;
        }
    }

    public void cacheArtistAlbumsRequests(String artist, List<MPDAlbum> albums, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR sortSelector, MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder) {
        mArtistAlbumCache.put(artist, new MPDArtistAlbumsRequest(albums, albumArtistSelector, sortSelector, sortOrder));
    }

    public List<MPDAlbum> getCachedArtistAlbumsRequest(String artist, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR sortSelector, MPDAlbum.MPD_ALBUM_SORT_ORDER sortOrder) {
        MPDArtistAlbumsRequest cachedEntry = mArtistAlbumCache.get(artist);
        if (cachedEntry == null) {
            return null;
        }

        if (cachedEntry.getAlbumArtistSelector() == albumArtistSelector && cachedEntry.getArtistSortSelector() == sortSelector && cachedEntry.getSortOrder() == sortOrder) {
            return cachedEntry.getObjects();
        } else {
            // Remove artist as it seem the settings for the parameters changed
            mArtistAlbumCache.remove(artist);
        }

        return null;
    }

    public void cacheArtistsRequests(List<MPDArtist> artists, MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR sortSelector) {
        mArtistsRequest = new MPDArtistRequest(artists, albumArtistSelector, sortSelector);
    }

    public List<MPDArtist> getCachedArtistsRequest(MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector, MPDArtist.MPD_ARTIST_SORT_SELECTOR sortSelector) {
        if (mArtistsRequest != null) {
            MPDArtistRequest artistsRequest = (MPDArtistRequest) mArtistsRequest;
            if (artistsRequest.getAlbumArtistSelector() != albumArtistSelector || artistsRequest.getArtistSortSelector() != sortSelector) {
                return null;
            } else {
                return mArtistsRequest.getObjects();
            }
        }
        return null;
    }


}
