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

import android.os.Looper;

import com.android.volley.Response;

import org.gateshipone.malp.application.artwork.network.ArtworkRequestModel;
import org.gateshipone.malp.application.artwork.network.responses.ImageResponse;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseAlbumArt;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDArtworkHandler;

public class MPDAlbumImageProvider extends ArtProvider {

    /**
     * Singleton instance
     */
    private static MPDAlbumImageProvider mInstance;

    private boolean mActive;

    private Looper mResponseLooper;

    public static synchronized MPDAlbumImageProvider getInstance() {
        if (mInstance == null) {
            mInstance = new MPDAlbumImageProvider();
        }
        return mInstance;
    }

    @Override
    public void fetchImage(final ArtworkRequestModel model, final Response.Listener<ImageResponse> listener, final ArtFetchError errorListener) {
        if (mResponseLooper == null) {
            errorListener.fetchVolleyError(model, null);
            return;
        }
        switch (model.getType()) {
            case ALBUM: {
                MPDArtworkHandler.getAlbumArtworkForAlbum(model.getAlbumName(), model.getMBID(), new AlbumArtResponseListener(mResponseLooper, model, listener, errorListener));
                break;
            }
            case ARTIST:
                // not used for this provider
                break;
            case TRACK:
                MPDArtworkHandler.getAlbumArtworkForTrack(model.getPath(), new AlbumArtResponseListener(mResponseLooper, model, listener, errorListener));
                break;
        }
    }

    public void setActive(final boolean active) {
        mActive = active;
    }

    public boolean getActive() {
        return mActive;
    }

    public void setResponseLooper(final Looper looper) {
        mResponseLooper = looper;
    }

    private static class AlbumArtResponseListener extends MPDResponseAlbumArt {

        private final ArtworkRequestModel mModel;

        private final Response.Listener<ImageResponse> mListener;

        private final ArtFetchError mErrorListener;

        AlbumArtResponseListener(final Looper looper, final ArtworkRequestModel model,
                                 final Response.Listener<ImageResponse> listener, final ArtFetchError errorListener) {
            super(looper);
            mModel = model;
            mListener = listener;
            mErrorListener = errorListener;
        }

        @Override
        public void handleAlbumArt(byte[] artworkData, String url) {
            if (artworkData != null) {
                ImageResponse response = new ImageResponse();
                response.model = mModel;
                response.image = artworkData;
                response.url = url;
                mListener.onResponse(response);
            } else {
                mErrorListener.fetchLocalFailed(mModel);
            }
        }
    }
}
