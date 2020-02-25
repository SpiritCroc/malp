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
package org.gateshipone.malp.application.artwork.network.artprovider;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;

import org.gateshipone.malp.application.artwork.network.ArtworkRequestModel;
import org.gateshipone.malp.application.artwork.network.MALPRequestQueue;
import org.gateshipone.malp.application.artwork.network.requests.FanartImageRequest;
import org.gateshipone.malp.application.artwork.network.requests.MALPByteRequest;
import org.gateshipone.malp.application.artwork.network.requests.MALPJsonObjectRequest;
import org.gateshipone.malp.application.artwork.network.responses.FanartResponse;
import org.gateshipone.malp.application.artwork.network.responses.ImageResponse;
import org.gateshipone.malp.application.utils.FormatHelper;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Artwork downloading class for http://fanart.tv. This class provides an interface
 * to download artist images and artist fanart images.
 */
public class FanartTVProvider extends ArtProvider implements FanartProvider {
    private static final String TAG = FanartTVProvider.class.getSimpleName();

    /**
     * API-URL for MusicBrainz database. Used to resolve artist names to MBIDs
     */
    private static final String MUSICBRAINZ_API_URL = "https://musicbrainz.org/ws/2";

    /**
     * API-URL for fanart.tv itself.
     */
    private static final String FANART_TV_API_URL = "https://webservice.fanart.tv/v3/music";

    /**
     * {@link RequestQueue} used to handle the requests of this class.
     */
    private RequestQueue mRequestQueue;

    /**
     * Singleton instance
     */
    private static FanartTVProvider mInstance;

    /**
     * constant API url part to instruct MB to return json format
     */
    private static final String MUSICBRAINZ_FORMAT_JSON = "&fmt=json";

    /**
     * Limit the number of results to one. Used for resolving artist names to MBIDs
     */
    private static final int MUSICBRAINZ_LIMIT_RESULT_COUNT = 1;

    /**
     * Constant URL format to limit results
     */
    private static final String MUSICBRAINZ_LIMIT_RESULT = "&limit=" + MUSICBRAINZ_LIMIT_RESULT_COUNT;

    /**
     * Maximum number of fanart images to return an URL for.
     */
    private static final int FANART_COUNT_LIMIT = 50;

    /**
     * API-Key for used for fanart.tv.
     * THIS KEY IS ONLY INTENDED FOR THE USE BY GATESHIP-ONE APPLICATIONS. PLEASE RESPECT THIS.
     */
    private static final String API_KEY = "c0cc5d1b6e807ce93e49d75e0e5d371b";

    private FanartTVProvider(Context context) {
        mRequestQueue = MALPRequestQueue.getInstance(context);
    }

    public static synchronized FanartTVProvider getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new FanartTVProvider(context);
        }
        return mInstance;
    }

    @Override
    public void fetchImage(final ArtworkRequestModel model, final Context context,
                           final Response.Listener<ImageResponse> listener, final ArtFetchError errorListener) {
        switch (model.getType()) {
            case ALBUM:
                // not used for this provider
                break;
            case ARTIST:
                tryArtistMBID(0, model, context, listener, errorListener);
                break;
        }
    }

    /**
     * Recursive method to try all available MBIDs from an {@link MPDArtist}
     *
     * @param mbidIndex     Index of the available MBIDs for the given artists
     * @param model         {@link ArtworkRequestModel} to check for images
     * @param listener      Response listener called when an image is found
     * @param errorListener Error listener called when an error occurs during communication
     */
    private void tryArtistMBID(final int mbidIndex, final ArtworkRequestModel model, final Context context,
                               final Response.Listener<ImageResponse> listener, final ArtFetchError errorListener) {
        final String mbid = model.getMBID(mbidIndex);

        // Check if recursive call ends here.
        if (mbid != null) {
            // Query fanart.tv for this MBID
            getArtistImageURL(mbid, response -> {
                JSONArray thumbImages;
                try {
                    thumbImages = response.getJSONArray("artistthumb");

                    final JSONObject firstThumbImage = thumbImages.getJSONObject(0);

                    getArtistImage(firstThumbImage.getString("url"), model, listener, error -> {
                        // If we have multiple artist mbids try the next one
                        tryArtistMBID(mbidIndex + 1, model, context, listener, errorListener);
                    });

                } catch (JSONException e) {
                    // If we have multiple artist mbids try the next one
                    tryArtistMBID(mbidIndex + 1, model, context, listener, errorListener);
                }
            }, error -> errorListener.fetchVolleyError(model, context, error));
        } else {
            // If no MBID is set at this point try to resolve one with musicbrainz database.
            final String artistURLName = model.getLuceneEscapedEncodedArtistName();

            // Get the list of artists "matching" the name.
            getArtists(artistURLName, response -> {
                JSONArray artists;
                try {
                    artists = response.getJSONArray("artists");

                    // Only check the first matching artist
                    if (!artists.isNull(0)) {
                        final JSONObject artistObj = artists.getJSONObject(0);

                        // verify response
                        final String artist = artistObj.getString("name");

                        final boolean isMatching = compareArtistResponse(model.getArtistName(), artist);

                        if (isMatching) {

                            final String artistMBID = artistObj.getString("id");

                            // Try to get information for this artist from fanart.tv
                            getArtistImageURL(artistMBID, response1 -> {
                                JSONArray thumbImages;
                                try {
                                    thumbImages = response1.getJSONArray("artistthumb");

                                    final JSONObject firstThumbImage = thumbImages.getJSONObject(0);

                                    // Get the image for the artist.
                                    getArtistImage(firstThumbImage.getString("url"), model, listener, error -> errorListener.fetchVolleyError(model, context, error));

                                } catch (JSONException e) {
                                    errorListener.fetchJSONException(model, context, e);
                                }
                            }, error -> errorListener.fetchVolleyError(model, context, error));
                        } else {
                            Log.v(TAG, "Response ( " + artist + " )" + " doesn't match requested model: " +
                                    "( " + model.getLoggingString() + " )");

                            errorListener.fetchVolleyError(model, context, null);
                        }
                    }
                } catch (JSONException e) {
                    errorListener.fetchJSONException(model, context, e);
                }
            }, error -> errorListener.fetchVolleyError(model, context, error));
        }
    }

    /**
     * Gets a list of possible artists from Musicbrainz database.
     *
     * @param artistName    Name of the artist to search for
     * @param listener      Response listener to handle the artist list
     * @param errorListener Error listener
     */
    private void getArtists(String artistName, Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {

        Log.v(FanartTVProvider.class.getSimpleName(), artistName);

        String url = MUSICBRAINZ_API_URL + "/" + "artist/?query=artist:" + artistName + MUSICBRAINZ_LIMIT_RESULT + MUSICBRAINZ_FORMAT_JSON;

        MALPJsonObjectRequest jsonObjectRequest = new MALPJsonObjectRequest(url, null, listener, errorListener);

        mRequestQueue.add(jsonObjectRequest);
    }

    /**
     * Retrieves all available information (Artist image url, fanart url, ...) for an artist with an MBID of fanart.tv
     *
     * @param artistMBID    Artists MBID to query
     * @param listener      Response listener to handle the artists information from fanart.tv
     * @param errorListener Error listener
     */
    private void getArtistImageURL(final String artistMBID, final Response.Listener<JSONObject> listener, final Response.ErrorListener errorListener) {

        Log.v(FanartTVProvider.class.getSimpleName(), artistMBID);

        String url = FANART_TV_API_URL + "/" + artistMBID + "?api_key=" + API_KEY;

        MALPJsonObjectRequest jsonObjectRequest = new MALPJsonObjectRequest(url, null, listener, errorListener);

        mRequestQueue.add(jsonObjectRequest);
    }

    /**
     * Raw download for an image-
     *
     * @param url           Final image URL to download
     * @param model         Artist associated with the image to download
     * @param listener      Response listener to receive the image as a byte array
     * @param errorListener Error listener
     */
    private void getArtistImage(final String url, final ArtworkRequestModel model,
                                final Response.Listener<ImageResponse> listener, final Response.ErrorListener errorListener) {
        Log.v(FanartTVProvider.class.getSimpleName(), url);

        Request<ImageResponse> byteResponse = new MALPByteRequest(model, url, listener, errorListener);

        mRequestQueue.add(byteResponse);
    }

    /**
     * Wrapper to get an artist out of an {@link MPDTrack}.
     *
     * @param track         Track to get artist information for
     * @param listener      Response listener
     * @param errorListener Error listener
     */
    @Override
    public void getTrackArtistMBID(final MPDTrack track, final Response.Listener<String> listener, final FanartFetchError errorListener) {
        String artistName = track.getTrackAlbumArtist();
        if (artistName.isEmpty()) {
            artistName = track.getTrackArtist();
        }

        final String artistURLName = Uri.encode(FormatHelper.escapeSpecialCharsLucene(artistName));

        getArtists(artistURLName, response -> {
            JSONArray artists;
            try {
                artists = response.getJSONArray("artists");

                if (!artists.isNull(0)) {
                    JSONObject artistObj = artists.getJSONObject(0);
                    final String artistMBID = artistObj.getString("id");
                    listener.onResponse(artistMBID);
                }
            } catch (JSONException e) {
                errorListener.fanartFetchError(track);
            }
        }, error -> errorListener.fanartFetchError(track));

    }

    /**
     * Retrieves a list of fanart image urls for the given MBID.
     *
     * @param mbid          MBID to get fanart images for.
     * @param listener      Response listener to handle the URL list retrieved by this method
     * @param errorListener Error listener
     */
    @Override
    public void getArtistFanartURLs(String mbid, final Response.Listener<List<String>> listener, final FanartFetchError errorListener) {
        getArtistImageURL(mbid, response -> {
            JSONArray backgroundImages;
            try {
                backgroundImages = response.getJSONArray("artistbackground");
                if (backgroundImages.length() == 0) {
                    errorListener.imageListFetchError();
                } else {
                    ArrayList<String> urls = new ArrayList<>();
                    for (int i = 0; i < backgroundImages.length() && i < FANART_COUNT_LIMIT; i++) {
                        JSONObject image = backgroundImages.getJSONObject(i);
                        urls.add(image.getString("url"));
                    }
                    listener.onResponse(urls);
                }
            } catch (JSONException exception) {
                errorListener.imageListFetchError();
            }
        }, error -> errorListener.imageListFetchError());
    }

    /**
     * Raw image download to download fanart images
     *
     * @param track         Track for the associated image
     * @param url           URL to download
     * @param listener      Listener to handle the downloaded image as a byte response.
     * @param errorListener Error listener
     */
    @Override
    public void getFanartImage(MPDTrack track, String url, Response.Listener<FanartResponse> listener, Response.ErrorListener errorListener) {
        Request<FanartResponse> byteResponse = new FanartImageRequest(url, track, listener, errorListener);

        mRequestQueue.add(byteResponse);
    }
}
