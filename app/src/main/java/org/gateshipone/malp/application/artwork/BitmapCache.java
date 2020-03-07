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

package org.gateshipone.malp.application.artwork;

import android.graphics.Bitmap;
import android.util.Log;

import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDDirectory;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import androidx.annotation.NonNull;
import androidx.collection.LruCache;

/**
 * Simple LRU-based caching for album & artist images. This could reduce CPU usage
 * for the cost of memory usage by caching decoded {@link Bitmap} objects in a {@link LruCache}.
 */
public class BitmapCache {
    private static final String TAG = BitmapCache.class.getSimpleName();

    private static final int mMaxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

    /**
     * Maximum size of the cache in kilobytes
     */
    private static final int mCacheSize = mMaxMemory / 4;

    /**
     * Hash prefix for album images
     */
    private static final String ALBUM_PREFIX = "A_";

    /**
     * Hash prefix for artist images
     */
    private static final String ARTIST_PREFIX = "B_";

    /**
     * Hash prefix for directory images
     */
    private static final String DIRECTORY_PREFIX = "C_";

    /**
     * Private cache instance
     */
    private final LruCache<String, Bitmap> mCache;

    /**
     * Singleton instance
     */
    private static BitmapCache mInstance;

    private BitmapCache() {
        mCache = new LruCache<String, Bitmap>(mCacheSize) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }

        };
    }

    public static synchronized BitmapCache getInstance() {
        if (mInstance == null) {
            mInstance = new BitmapCache();
        }
        return mInstance;
    }

    /**
     * Tries to get an album image from the cache
     *
     * @param album Album object to try
     * @return Bitmap if cache hit, null otherwise
     */
    public synchronized Bitmap requestAlbumBitmap(final MPDAlbum album) {
        return mCache.get(getAlbumHash(album));
    }

    /**
     * Tries to get an track image from the cache
     * Track images will be treated as album images.
     *
     * @param track Track object to use for cache key
     * @return Bitmap if cache hit, null otherwise
     */
    public synchronized Bitmap requestTrackBitmap(final MPDTrack track) {
        return mCache.get(getAlbumHash(track));
    }

    /**
     * Tries to get a directory image from the cache
     *
     * @param directory Directory object to use for cache key
     * @return Bitmap if cache hit, null otherwise
     */
    public synchronized Bitmap requestDirectoryBitmap(final MPDDirectory directory) {
        return mCache.get(getDirectoryHash(directory));
    }

    /**
     * Puts an album image to the cache
     *
     * @param album Album object to use for cache key
     * @param bm    Bitmap to store in cache
     */
    synchronized void putAlbumBitmap(final MPDAlbum album, final Bitmap bm) {
        if (bm != null) {
            mCache.put(getAlbumHash(album), bm);
        }
    }

    /**
     * Puts a track image to the cache.
     * Track images will be treated as album images.
     *
     * @param track Track object to use for cache key
     * @param bm    Bitmap to store in cache
     */
    public synchronized void putTrackBitmap(final MPDTrack track, final Bitmap bm) {
        if (bm != null) {
            mCache.put(getAlbumHash(track), bm);
        }
    }

    /**
     * Puts a directory image to the cache.
     *
     * @param directory Track object to use for cache key
     * @param bm        Bitmap to store in cache
     */
    public synchronized void putDirectoryBitmap(final MPDDirectory directory, final Bitmap bm) {
        if (bm != null) {
            mCache.put(getDirectoryHash(directory), bm);
        }
    }

    /**
     * Removes an album image from the cache
     *
     * @param album Album object to use for cache key
     */
    synchronized void removeAlbumBitmap(final MPDAlbum album) {
        mCache.remove(getAlbumHash(album));
    }

    /**
     * Private hash method for cache key
     *
     * @param album Album to calculate the key from
     * @return Hash string for cache key
     */
    private String getAlbumHash(final MPDAlbum album) {
        final String albumMBID = album.getMBID();

        if (!albumMBID.isEmpty()) {
            return getAlbumHashMBID(albumMBID);
        } else {
            return getAlbumHash(album.getName(), album.getArtistName());
        }
    }

    /**
     * Private hash method for cache key
     *
     * @param track Track to calculate the key from
     * @return Hash string for cache key
     */
    private String getAlbumHash(final MPDTrack track) {
        final String mbid = track.getStringTag(MPDTrack.StringTagTypes.ALBUM_MBID);
        final String albumName = track.getStringTag(MPDTrack.StringTagTypes.ALBUM);
        final String albumArtistName = track.getStringTag(MPDTrack.StringTagTypes.ALBUMARTIST);
        final String artistName = albumArtistName.isEmpty() ? track.getStringTag(MPDTrack.StringTagTypes.ARTIST) : albumArtistName;

        if (!mbid.isEmpty()) {
            return getAlbumHashMBID(mbid);
        } else {
            return getAlbumHash(albumName, artistName);
        }
    }

    /**
     * Private hash method for cache key
     *
     * @param albumName  Album name to calculate key from
     * @param artistName Album artist name to calculate key from
     * @return Hash string for cache key
     */
    private String getAlbumHash(final String albumName, final String artistName) {
        return ALBUM_PREFIX + artistName + '_' + albumName;
    }

    /**
     * Private hash method for cache key
     *
     * @param mbid MBID used as cache key
     * @return Hash string for cache key
     */
    private String getAlbumHashMBID(final String mbid) {
        return ALBUM_PREFIX + mbid;
    }

    /**
     * Private hash method for cache key
     *
     * @param directory Directory to calculate the key from
     * @return Hash string for cache key
     */
    private String getDirectoryHash(final MPDDirectory directory) {
        final String path = directory.getPath();
        return DIRECTORY_PREFIX + path;
    }

    /**
     * Tries to get an artist image from the cache
     *
     * @param artist Artist object to check in cache
     * @return Bitmap if cache hit, null otherwise
     */
    public synchronized Bitmap requestArtistImage(final MPDArtist artist) {
        return mCache.get(getArtistHash(artist));
    }

    /**
     * Puts an artist image to the cache
     *
     * @param artist Artist used as cache key
     * @param bm     Bitmap to store in cache
     */
    synchronized void putArtistImage(final MPDArtist artist, final Bitmap bm) {
        if (bm != null) {
            mCache.put(getArtistHash(artist), bm);
        }
    }

    /**
     * Removes an artist image from the cache
     *
     * @param artist Artist used as cache key
     */
    synchronized void removeArtistImage(final MPDArtist artist) {
        mCache.remove(getArtistHash(artist));
    }

    /**
     * Private hash method for cache key
     *
     * @param artist Artist used as cache key
     * @return Hash string for cache key
     */
    private String getArtistHash(final MPDArtist artist) {
        String hashString = ARTIST_PREFIX;
        if (artist.getMBIDCount() > 0) {
            hashString += artist.getMBID(0);
            return hashString;
        }

        hashString += artist.getArtistName();
        return hashString;
    }

    /**
     * Debug method to provide performance evaluation metrics
     */
    private void printUsage() {
        Log.v(TAG, "Cache usage: " + ((mCache.size() * 100) / mCache.maxSize()) + '%');
        int missCount = mCache.missCount();
        int hitCount = mCache.hitCount();
        if (missCount > 0) {
            Log.v(TAG, "Cache hit count: " + hitCount + " miss count: " + missCount + " Miss rate: " + ((hitCount * 100) / missCount) + '%');
        }
    }
}
