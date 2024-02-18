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

package org.gateshipone.malp.application.viewmodels;

import android.app.Application;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseGenericList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;

import java.lang.ref.WeakReference;
import java.util.List;

public class ArtistsViewModel extends GenericViewModel<MPDArtist> {

    private final MPDResponseGenericList<MPDArtist> mArtistResponseHandler;

    private final MPDArtist.MPD_ALBUM_ARTIST_SELECTOR mAlbumArtistSelector;
    private final MPDArtist.MPD_ARTIST_SORT_SELECTOR mArtistSortSelector;

    private final String mTagName;
    private final String mTagValue;

    private ArtistsViewModel(final Application application, final MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector,
                             final MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector, String tagName, String tagValue) {
        super(application);

        mArtistResponseHandler = new ArtistResponseHandler(this);

        mAlbumArtistSelector = albumArtistSelector;
        mArtistSortSelector = artistSortSelector;

        mTagName = tagName;
        mTagValue = tagValue;
    }

    @Override
    void loadData() {
        Log.e(ArtistsViewModel.class.getSimpleName(), "Tag[" + mTagName + "]=" + mTagValue);
        MPDQueryHandler.getArtists(mArtistResponseHandler, mAlbumArtistSelector, mArtistSortSelector, new Pair<>(mTagName, mTagValue));
    }

    private static class ArtistResponseHandler extends MPDResponseGenericList<MPDArtist> {
        private final WeakReference<ArtistsViewModel> mArtistViewModel;

        private ArtistResponseHandler(final ArtistsViewModel artistsViewModel) {
            mArtistViewModel = new WeakReference<>(artistsViewModel);
        }

        @Override
        public void handleList(final List<MPDArtist> artistList) {
            final ArtistsViewModel artistsViewModel = mArtistViewModel.get();

            if (artistsViewModel != null) {
                artistsViewModel.setData(artistList);
            }
        }
    }

    public static class ArtistViewModelFactory implements ViewModelProvider.Factory {

        private final Application mApplication;

        private final MPDArtist.MPD_ALBUM_ARTIST_SELECTOR mAlbumArtistSelector;
        private final MPDArtist.MPD_ARTIST_SORT_SELECTOR mArtistSortSelector;

        private final String mTagName;
        private final String mTagValue;

        public ArtistViewModelFactory(final Application application, final MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector,
                                      final MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector, Pair<String, String> tagFilter) {
            mApplication = application;
            mAlbumArtistSelector = albumArtistSelector;
            mArtistSortSelector = artistSortSelector;

            if (tagFilter != null) {
                mTagName = tagFilter.first;
                mTagValue = tagFilter.second;
            } else {
                mTagName = null;
                mTagValue = null;
            }
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new ArtistsViewModel(mApplication, mAlbumArtistSelector, mArtistSortSelector, mTagName, mTagValue);
        }
    }
}
