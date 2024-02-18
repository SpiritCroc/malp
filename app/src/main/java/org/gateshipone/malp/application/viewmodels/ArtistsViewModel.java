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

    private MPDArtist.MPD_ALBUM_ARTIST_SELECTOR mAlbumArtistSelector;
    private MPDArtist.MPD_ARTIST_SORT_SELECTOR mArtistSortSelector;


    private ArtistsViewModel(final Application application, final MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector,
                             final MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector) {
        super(application);

        mArtistResponseHandler = new ArtistResponseHandler(this);

        mAlbumArtistSelector = albumArtistSelector;
        mArtistSortSelector = artistSortSelector;
    }

    @Override
    void loadData() {
        MPDQueryHandler.getArtists(mArtistResponseHandler, mAlbumArtistSelector, mArtistSortSelector);
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

        private MPDArtist.MPD_ALBUM_ARTIST_SELECTOR mAlbumArtistSelector;
        private MPDArtist.MPD_ARTIST_SORT_SELECTOR mArtistSortSelector;

        public ArtistViewModelFactory(final Application application, final MPDArtist.MPD_ALBUM_ARTIST_SELECTOR albumArtistSelector,
                                      final MPDArtist.MPD_ARTIST_SORT_SELECTOR artistSortSelector) {
            mApplication = application;
            mAlbumArtistSelector = albumArtistSelector;
            mArtistSortSelector = artistSortSelector;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new ArtistsViewModel(mApplication, mAlbumArtistSelector, mArtistSortSelector);
        }
    }
}
