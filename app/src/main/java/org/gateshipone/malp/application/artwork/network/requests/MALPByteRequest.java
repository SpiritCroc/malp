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

package org.gateshipone.malp.application.artwork.network.requests;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;

import org.gateshipone.malp.application.artwork.network.ArtworkRequestModel;
import org.gateshipone.malp.application.artwork.network.responses.ImageResponse;

import androidx.annotation.Nullable;

public class MALPByteRequest extends MALPRequest<ImageResponse> {

    private final Response.Listener<ImageResponse> mListener;

    private final ArtworkRequestModel mModel;

    public MALPByteRequest(ArtworkRequestModel model, String url, Response.Listener<ImageResponse> listener, @Nullable Response.ErrorListener errorListener) {
        super(Method.GET, url, errorListener);

        mModel = model;
        mListener = listener;
    }

    @Override
    protected Response<ImageResponse> parseNetworkResponse(NetworkResponse response) {
        ImageResponse imageResponse = new ImageResponse();
        imageResponse.model = mModel;
        imageResponse.url = getUrl();
        imageResponse.image = response.data;
        return Response.success(imageResponse, null);
    }

    @Override
    protected void deliverResponse(ImageResponse response) {
        mListener.onResponse(response);
    }
}
