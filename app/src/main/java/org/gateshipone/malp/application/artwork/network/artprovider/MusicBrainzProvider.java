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

package org.gateshipone.malp.application.artwork.network.artprovider;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;

import org.gateshipone.malp.BuildConfig;
import org.gateshipone.malp.application.artwork.network.ArtworkRequestModel;
import org.gateshipone.malp.application.artwork.network.MALPRequestQueue;
import org.gateshipone.malp.application.artwork.network.requests.MALPByteRequest;
import org.gateshipone.malp.application.artwork.network.requests.MALPJsonObjectRequest;
import org.gateshipone.malp.application.artwork.network.responses.ImageResponse;
import org.gateshipone.malp.application.utils.FormatHelper;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MusicBrainzProvider extends ArtProvider {

    private static final String TAG = MusicBrainzProvider.class.getSimpleName();

    private static final String MUSICBRAINZ_API_URL = "https://musicbrainz.org/ws/2";

    private static final String COVERART_ARCHIVE_API_URL = "https://coverartarchive.org";

    private final RequestQueue mRequestQueue;

    private static MusicBrainzProvider mInstance;

    private static final String MUSICBRAINZ_FORMAT_JSON = "&fmt=json";

    private static final int MUSICBRAINZ_LIMIT_RESULT_COUNT = 10;

    private static final String MUSICBRAINZ_LIMIT_RESULT = "&limit=" + MUSICBRAINZ_LIMIT_RESULT_COUNT;

    private MusicBrainzProvider(Context context) {
        mRequestQueue = MALPRequestQueue.getInstance(context);
    }

    public static synchronized MusicBrainzProvider getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MusicBrainzProvider(context);
        }
        return mInstance;
    }

    @Override
    public void fetchImage(final ArtworkRequestModel model, final Response.Listener<ImageResponse> listener, final ArtFetchError errorListener) {
        switch (model.getType()) {
            case ALBUM:
                if (model.getMBID().isEmpty()) {
                    resolveAlbumMBID(model, listener, errorListener);
                } else {
                    String url = COVERART_ARCHIVE_API_URL + "/" + "release/" + model.getMBID() + "/front-500";
                    getAlbumImage(url, model, listener, error -> {
                        if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                            // Try without MBID from MPD
                            resolveAlbumMBID(model, listener, errorListener);
                        } else {
                            errorListener.fetchVolleyError(model, error);
                        }
                    });
                }
                break;
            case ARTIST:
            case TRACK:
                // not used for this provider
                break;
        }
    }

    /**
     * Wrapper to manually get an MBID for an {@link MPDAlbum} without an MBID already set
     *
     * @param album         Album to search
     * @param listener      Callback listener to handle the response
     * @param errorListener Callback to handle lookup errors
     */
    private void resolveAlbumMBID(final ArtworkRequestModel album, final Response.Listener<ImageResponse> listener, final ArtFetchError errorListener) {
        getAlbumMBID(album,
                response -> parseMusicBrainzReleaseJSON(album, 0, response, listener, errorListener),
                error -> errorListener.fetchVolleyError(album, error));
    }

    /**
     * Parses the JSON response and searches the image URL
     *
     * @param model         Album to check for an image
     * @param releaseIndex  Index of the requested release to check for an image
     * @param response      Response to check use to search for an image
     * @param listener      Callback to handle the response
     * @param errorListener Callback to handle errors
     */
    private void parseMusicBrainzReleaseJSON(final ArtworkRequestModel model, final int releaseIndex, final JSONObject response,
                                             final Response.Listener<ImageResponse> listener, final ArtFetchError errorListener) {
        if (releaseIndex >= MUSICBRAINZ_LIMIT_RESULT_COUNT) {
            errorListener.fetchVolleyError(model, null);
            return;
        }

        try {
            final JSONArray releases = response.getJSONArray("releases");
            if (releases.length() > releaseIndex) {
                final JSONObject baseObj = releases.getJSONObject(releaseIndex);

                // verify response
                final String album = baseObj.getString("title");
                final String artist = baseObj.getJSONArray("artist-credit").getJSONObject(0).getString("name");

                final boolean isMatching = compareAlbumResponse(model.getAlbumName(), model.getArtistName(), album, artist);

                if (isMatching) {
                    final String mbid = releases.getJSONObject(releaseIndex).getString("id");
                    final String url = COVERART_ARCHIVE_API_URL + "/" + "release/" + mbid + "/front-500";

                    getAlbumImage(url, model, listener, error -> {
                        if (BuildConfig.DEBUG) {
                            Log.v(TAG, "No image found for: " + model.getAlbumName() + " with release index: " + releaseIndex);
                        }

                        if (releaseIndex + 1 < releases.length()) {
                            parseMusicBrainzReleaseJSON(model, releaseIndex + 1, response, listener, errorListener);
                        } else {
                            errorListener.fetchVolleyError(model, error);
                        }
                    });
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.v(TAG, "Response ( " + album + "-" + artist + " )" + " doesn't match requested model: " +
                                "( " + model.getLoggingString() + " )");
                    }

                    if (releaseIndex + 1 < releases.length()) {
                        parseMusicBrainzReleaseJSON(model, releaseIndex + 1, response, listener, errorListener);
                    } else {
                        errorListener.fetchVolleyError(model, null);
                    }
                }
            } else {
                errorListener.fetchVolleyError(model, null);
            }
        } catch (JSONException e) {
            errorListener.fetchJSONException(model, e);
        }

    }

    /**
     * Wrapper to get an MBID out of an {@link ArtworkRequestModel}.
     *
     * @param model         Album to get the MBID for
     * @param listener      Response listener
     * @param errorListener Error listener
     */
    private void getAlbumMBID(final ArtworkRequestModel model, final Response.Listener<JSONObject> listener, final Response.ErrorListener errorListener) {
        final String albumName = FormatHelper.escapeSpecialCharsLucene(model.getLuceneEscapedEncodedAlbumName());
        final String artistName = FormatHelper.escapeSpecialCharsLucene(model.getLuceneEscapedEncodedArtistName());

        String url;
        if (!artistName.isEmpty()) {
            url = MUSICBRAINZ_API_URL + "/" + "release/?query=release:" + albumName + "%20AND%20artist:" + artistName + MUSICBRAINZ_LIMIT_RESULT + MUSICBRAINZ_FORMAT_JSON;
        } else {
            url = MUSICBRAINZ_API_URL + "/" + "release/?query=release:" + albumName + MUSICBRAINZ_LIMIT_RESULT + MUSICBRAINZ_FORMAT_JSON;
        }

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Requesting release mbid for: " + url);
        }

        MALPJsonObjectRequest jsonObjectRequest = new MALPJsonObjectRequest(url, null, listener, errorListener);

        mRequestQueue.add(jsonObjectRequest);
    }

    /**
     * Raw download for an image
     *
     * @param url           Final image URL to download
     * @param model         Album associated with the image to download
     * @param listener      Response listener to receive the image as a byte array
     * @param errorListener Error listener
     */
    private void getAlbumImage(final String url, final ArtworkRequestModel model,
                               final Response.Listener<ImageResponse> listener,
                               final Response.ErrorListener errorListener) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Get image: " + url);
        }

        Request<ImageResponse> byteResponse = new MALPByteRequest(model, url, listener, errorListener);
        mRequestQueue.add(byteResponse);
    }
}
