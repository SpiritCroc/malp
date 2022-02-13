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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.artwork.network.artprovider.FanartProvider;
import org.gateshipone.malp.application.artwork.network.artprovider.FanartTVProvider;
import org.gateshipone.malp.application.utils.NetworkUtils;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.io.File;

public class FanartManager implements FanartProvider.FanartFetchError {

    public interface OnFanartCacheChangeListener {
        void fanartInitialCacheCount(final int count);

        void fanartCacheCountChanged(final int count);
    }

    /**
     * Private static singleton instance that can be used by other classes via the
     * getInstance method.
     */
    private static FanartManager mInstance;

    /**
     * Private {@link Context} used for all kinds of things like Broadcasts.
     * It is using the ApplicationContext so it should be safe against
     * memory leaks.
     */
    private final Context mApplicationContext;

    /**
     * Flag if the artist provider is not disabled in the settings.
     */
    private final boolean mUseFanartProvider;

    /**
     * Flag if the usage is restricted to wifi only.
     */
    private final boolean mWifiOnly;

    /**
     * The cache for the fanart images.
     */
    private final FanartCache mFanartCache;

    private FanartManager(final Context context) {
        mApplicationContext = context.getApplicationContext();

        mFanartCache = FanartCache.getInstance(mApplicationContext);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mApplicationContext);
        final String artistProvider = sharedPref.getString(mApplicationContext.getString(R.string.pref_artist_provider_key), mApplicationContext.getString(R.string.pref_artwork_provider_artist_default));
        mUseFanartProvider = !artistProvider.equals(mApplicationContext.getString(R.string.provider_off));
        mWifiOnly = sharedPref.getBoolean(mApplicationContext.getString(R.string.pref_download_wifi_only_key), mApplicationContext.getResources().getBoolean(R.bool.pref_download_wifi_default));
    }

    public static synchronized FanartManager getInstance(final Context context) {
        if (null == mInstance) {
            mInstance = new FanartManager(context);
        }

        return mInstance;
    }

    /**
     * Returns fanart image number index from the fanart cache for the given MBID.
     *
     * @param mbid  The musicbrainz id for the fanart lookup.
     * @param index The index of the fanart image. If the index exceeds the stored images the image will be null.
     * @return A bitmap of the requested image or null if it was not found in the cache.
     */
    public Bitmap getFanartImage(final String mbid, final int index) {
        final File file = mFanartCache.getFanart(mbid, index);

        if (null == file) {
            return null;
        }
        return BitmapFactory.decodeFile(file.getPath());
    }

    /**
     * Returns the current count of stored fanart images in the cache for the given MBID.
     *
     * @param mbid The musicbrainz id for the fanart lookup.
     * @return The number of stored fanart images in the cache for the given MBID.
     */
    public int getFanartCount(final String mbid) {
        return mFanartCache.getFanartCount(mbid);
    }

    /**
     * This method will trigger the fanart sync for the given {@link MPDTrack}.
     * <p>
     * If the track has no valid artist musicbrainz id this will be resolved first.
     * The provider will be always the {@link FanartTVProvider} instance.
     *
     * @param track                     The current {@link MPDTrack} for which fanart should be provided.
     * @param fanartCacheChangeListener Callback if a new fanart was added to the cache.
     */
    public void syncFanart(final MPDTrack track, final OnFanartCacheChangeListener fanartCacheChangeListener) {
        if (!mUseFanartProvider && !NetworkUtils.isDownloadAllowed(mApplicationContext, mWifiOnly)) {
            return;
        }

        if (track.getStringTag(MPDTrack.StringTagTypes.ARTIST_MBID).isEmpty()) {
            // resolve mbid
            FanartTVProvider.getInstance(mApplicationContext).getTrackArtistMBID(track, trackMBID -> {
                track.setStringTag(MPDTrack.StringTagTypes.ARTIST_MBID, trackMBID);

                loadFanartImages(track, fanartCacheChangeListener);
            }, this);
        } else {
            loadFanartImages(track, fanartCacheChangeListener);
        }
    }

    /**
     * Callback if an image fetch error occured.
     */
    @Override
    public void imageListFetchError() {
        // for now error handling is not necessary
        // TODO maybe add logging
    }

    /**
     * Callback if an image fetch error occured.
     *
     * @param track The {@link MPDTrack} for which the error occured.
     */
    @Override
    public void fanartFetchError(MPDTrack track) {
        // for now error handling is not necessary
        // TODO maybe add logging
    }

    /**
     * This method will download all fanart images for the given {@link MPDTrack} that is not already cached.
     * <p>
     * The provider will be always the {@link FanartTVProvider} instance.
     *
     * @param track                     The current {@link MPDTrack} for which fanart should be provided.
     * @param fanartCacheChangeListener Callback if a new fanart was added to the cache.
     */
    private void loadFanartImages(final MPDTrack track, final OnFanartCacheChangeListener fanartCacheChangeListener) {
        fanartCacheChangeListener.fanartInitialCacheCount(mFanartCache.getFanartCount(track.getStringTag(MPDTrack.StringTagTypes.ARTIST_MBID)));


        FanartTVProvider.getInstance(mApplicationContext).getArtistFanartURLs(track.getStringTag(MPDTrack.StringTagTypes.ARTIST_MBID),
                artistURLs -> {
                    for (final String url : artistURLs) {
                        // Check if the given image is in the cache already.
                        if (mFanartCache.inCache(track.getStringTag(MPDTrack.StringTagTypes.ARTIST_MBID), String.valueOf(url.hashCode()))) {
                            continue;
                        }

                        loadSingleFanartImage(track, url, fanartCacheChangeListener);
                    }
                }, this);
    }

    /**
     * This method will trigger the actual download of a fanart image.
     * <p>
     * The provider will be always the {@link FanartTVProvider} instance.
     *
     * @param track                     The current {@link MPDTrack} for which fanart should be provided.
     * @param imageURL                  The current url for the fanart.
     * @param fanartCacheChangeListener Callback if a new fanart was added to the cache.
     */
    private void loadSingleFanartImage(final MPDTrack track, final String imageURL, final OnFanartCacheChangeListener fanartCacheChangeListener) {
        FanartTVProvider.getInstance(mApplicationContext).getFanartImage(track, imageURL,
                response -> {
                    mFanartCache.addFanart(track.getStringTag(MPDTrack.StringTagTypes.ARTIST_MBID), String.valueOf(response.hashCode()), response.image);

                    fanartCacheChangeListener.fanartCacheCountChanged(mFanartCache.getFanartCount(track.getStringTag(MPDTrack.StringTagTypes.ARTIST_MBID)));
                },
                error -> {
                    // for now error handling is not necessary
                    // TODO maybe add logging
                });
    }
}
