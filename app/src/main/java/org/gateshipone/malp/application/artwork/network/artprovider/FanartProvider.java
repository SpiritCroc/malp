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

import org.gateshipone.malp.application.artwork.network.responses.FanartResponse;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.util.List;

/**
 * Interface to extend an {@link ArtProvider} instance to be used in the {@link org.gateshipone.malp.application.artwork.FanartManager}.
 */
public interface FanartProvider {

    interface FanartFetchError {
        void imageListFetchError();

        void fanartFetchError(MPDTrack track);
    }

    void getTrackArtistMBID(final MPDTrack track, final Response.Listener<String> listener, final FanartFetchError errorListener);

    void getArtistFanartURLs(final String mbid, final Response.Listener<List<String>> listener, final FanartFetchError errorListener);

    void getFanartImage(final MPDTrack track, final String url, final Response.Listener<FanartResponse> listener, final Response.ErrorListener errorListener);
}
