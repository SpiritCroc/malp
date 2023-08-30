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

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gateshipone.malp.application.artwork.network.ArtworkRequestModel;
import org.gateshipone.malp.application.artwork.network.responses.ImageResponse;
import org.gateshipone.malp.application.utils.StringCompareUtils;
import org.json.JSONException;

public abstract class ArtProvider {

    public interface ArtFetchError {
        void fetchJSONException(final ArtworkRequestModel model, final JSONException exception);
        void fetchLocalFailed(final ArtworkRequestModel model);
        void fetchVolleyError(final ArtworkRequestModel model, final VolleyError error);
    }

    public abstract void fetchImage(final ArtworkRequestModel model, final Response.Listener<ImageResponse> listener, final ArtFetchError errorListener);

    boolean compareAlbumResponse(final String expectedAlbum, final String expectedArtist, final String retrievedAlbum, final String retrievedArtist) {
        return StringCompareUtils.compareStrings(expectedAlbum, retrievedAlbum) && StringCompareUtils.compareStrings(expectedArtist, retrievedArtist);
    }

    boolean compareArtistResponse(final String expectedArtist, final String retrievedArtist) {
        return StringCompareUtils.compareStrings(expectedArtist, retrievedArtist);
    }
}
