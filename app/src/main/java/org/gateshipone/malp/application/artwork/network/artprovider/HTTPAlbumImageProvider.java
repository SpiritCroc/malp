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

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;

import org.gateshipone.malp.BuildConfig;
import org.gateshipone.malp.application.artwork.network.ArtworkRequestModel;
import org.gateshipone.malp.application.artwork.network.requests.MALPByteRequest;
import org.gateshipone.malp.application.artwork.network.responses.ImageResponse;
import org.gateshipone.malp.application.utils.FormatHelper;

public class HTTPAlbumImageProvider extends ArtProvider {
    private static final String TAG = HTTPAlbumImageProvider.class.getSimpleName();

    /**
     * Filename combinations used if only a directory is specified
     */
    private static final String[] COVER_FILENAMES = {"cover", "folder", "Cover", "Folder"};

    /**
     * File extensions tried for all filenames
     */
    private static final String[] COVER_FILEEXTENSIIONS = {"png", "jpg", "jpeg", "PNG", "JPG", "JPEG"};

    /**
     * Singleton instance
     */
    private static HTTPAlbumImageProvider mInstance;

    /**
     * Regex used for downloading
     */
    private static String mRegex;

    /**
     * {@link RequestQueue} used for downloading images. Separate queue for this provider
     * because no request limitations are necessary
     */
    private final RequestQueue mRequestQueue;

    private HTTPAlbumImageProvider(final Context context) {
        // Don't use MALPRequestQueue because we do not need to limit the load on the local server
        Network network = new BasicNetwork(new HurlStack());
        // 10MB disk cache
        Cache cache = new DiskBasedCache(context.getApplicationContext().getCacheDir(), 1024 * 1024 * 10);

        mRequestQueue = new RequestQueue(cache, network);
        mRequestQueue.start();
    }

    public static synchronized HTTPAlbumImageProvider getInstance(final Context context) {
        if (mInstance == null) {
            mInstance = new HTTPAlbumImageProvider(context);
        }

        return mInstance;
    }

    @Override
    public void fetchImage(ArtworkRequestModel model, Response.Listener<ImageResponse> listener, ArtFetchError errorListener) {
        switch (model.getType()) {
            case ALBUM:
            case ARTIST:
                // not used for this provider
                break;
            case TRACK:
                final String url = resolveRegex(model.getPath());

                // Check if URL ends with a file or directory
                if (url.endsWith("/")) {
                    final HTTPMultiRequest multiRequest = new HTTPMultiRequest(model, errorListener);
                    // Directory check all pre-defined files
                    for (String filename : COVER_FILENAMES) {
                        for (String fileextension : COVER_FILEEXTENSIIONS) {
                            String fileURL = url + filename + '.' + fileextension;
                            getAlbumImage(fileURL, model, listener, multiRequest::increaseFailure);
                        }
                    }
                } else {
                    // File, just check the file
                    getAlbumImage(url, model, listener, error -> errorListener.fetchVolleyError(model, error));
                }
                break;
        }
    }

    public void setRegex(String regex) {
        mRegex = regex;

        if (mRegex == null) {
            mRegex = "";
        }
        // Add a trailing / to signal it is a directory
        if (mRegex.endsWith("%d")) {
            mRegex += '/';
        }
    }

    public String getRegex() {
        return mRegex;
    }

    public boolean getActive() {
        return mRegex != null && !mRegex.isEmpty();
    }

    private String resolveRegex(String path) {
        String result;

        result = mRegex.replaceAll("%d", Uri.encode(FormatHelper.getDirectoryFromPath(path)));

        return result;
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

    private static class HTTPMultiRequest {

        private int mFailureCount;

        private final ArtFetchError mErrorListener;

        private final ArtworkRequestModel mModel;

        HTTPMultiRequest(final ArtworkRequestModel track, final ArtFetchError errorListener) {
            mModel = track;
            mErrorListener = errorListener;
        }

        synchronized void increaseFailure(final VolleyError error) {
            mFailureCount++;
            if (mFailureCount == COVER_FILENAMES.length * COVER_FILEEXTENSIIONS.length) {
                mErrorListener.fetchVolleyError(mModel, error);
            }
        }
    }
}
