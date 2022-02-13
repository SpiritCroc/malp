/*
 *  Copyright (C) 2019 Team Gateship-One
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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.utils.PreferenceHelper;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseAlbumList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

public class AlbumsViewModel extends GenericViewModel<MPDAlbum> {

    private final MPDResponseAlbumList mAlbumsResponseHandler;

    private final String mArtistName;

    private final String mAlbumsPath;

    private final MPDAlbum.MPD_ALBUM_SORT_ORDER mSortOrder;

    private final boolean mUseArtistSort;

    private AlbumsViewModel(@NonNull final Application application, final String artistName, final String albumsPath) {
        super(application);

        mAlbumsResponseHandler = new AlbumResponseHandler(this);

        mArtistName = artistName;
        mAlbumsPath = albumsPath;

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(application);
        mSortOrder = PreferenceHelper.getMPDAlbumSortOrder(sharedPref, application);
        mUseArtistSort = sharedPref.getBoolean(application.getString(R.string.pref_use_artist_sort_key), application.getResources().getBoolean(R.bool.pref_use_artist_sort_default));
    }

    @Override
    void loadData() {
        if ((null == mArtistName) || mArtistName.isEmpty()) {
            if (null == mAlbumsPath || mAlbumsPath.isEmpty()) {
                MPDQueryHandler.getAlbums(mAlbumsResponseHandler);
            } else {
                MPDQueryHandler.getAlbumsInPath(mAlbumsPath, mAlbumsResponseHandler);
            }
        } else {
            if (!mUseArtistSort) {
                MPDQueryHandler.getArtistAlbums(mAlbumsResponseHandler, mArtistName);
            } else {
                MPDQueryHandler.getArtistSortAlbums(mAlbumsResponseHandler, mArtistName);
            }
        }
    }

    private static class AlbumResponseHandler extends MPDResponseAlbumList {

        private final WeakReference<AlbumsViewModel> mAlbumViewModel;

        private AlbumResponseHandler(final AlbumsViewModel albumsViewModel) {
            mAlbumViewModel = new WeakReference<>(albumsViewModel);
        }

        @Override
        public void handleAlbums(final List<MPDAlbum> albumList) {
            final AlbumsViewModel albumsViewModel = mAlbumViewModel.get();

            if (albumsViewModel != null) {
                // If artist albums and sort by year is active, resort the list
                if (albumsViewModel.mSortOrder == MPDAlbum.MPD_ALBUM_SORT_ORDER.DATE && !((null == albumsViewModel.mArtistName) || albumsViewModel.mArtistName.isEmpty())) {
                    Collections.sort(albumList, new MPDAlbum.MPDAlbumDateComparator());
                }
                albumsViewModel.setData(albumList);
            }
        }
    }

    public static class AlbumViewModelFactory implements ViewModelProvider.Factory {

        private final Application mApplication;

        private final String mArtistName;

        private final String mAlbumsPath;

        public AlbumViewModelFactory(final Application application, final String artistName, final String albumsPath) {
            mApplication = application;
            mArtistName = artistName;
            mAlbumsPath = albumsPath;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new AlbumsViewModel(mApplication, mArtistName, mAlbumsPath);
        }
    }
}
