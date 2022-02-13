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

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseFileList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;

import java.lang.ref.WeakReference;
import java.util.List;

public class AlbumTracksViewModel extends GenericViewModel<MPDFileEntry> {

    private final MPDResponseFileList mTrackResponseHandler;

    private final String mArtistName;

    private final String mArtistSortName;

    private final String mAlbumName;

    private final String mAlbumMBID;

    private final boolean mUseArtistSort;

    private AlbumTracksViewModel(@NonNull final Application application, final MPDAlbum album, final boolean useArtistSort) {
        super(application);

        mTrackResponseHandler = new TrackResponseHandler(this);

        mArtistName = album.getArtistName();
        mArtistSortName = album.getArtistSortName();
        mAlbumName = album.getName();
        mAlbumMBID = album.getMBID();

        mUseArtistSort = useArtistSort;
    }

    @Override
    void loadData() {
        if (mArtistName.isEmpty()) {
            MPDQueryHandler.getAlbumTracks(mTrackResponseHandler, mAlbumName, mAlbumMBID);
        } else {
            if (mUseArtistSort && !mArtistSortName.isEmpty()) {
                MPDQueryHandler.getArtistSortAlbumTracks(mTrackResponseHandler, mAlbumName, mArtistSortName, mAlbumMBID);
            } else {
                MPDQueryHandler.getArtistAlbumTracks(mTrackResponseHandler, mAlbumName, mArtistName, mAlbumMBID);
            }
        }
    }

    private static class TrackResponseHandler extends MPDResponseFileList {
        private WeakReference<AlbumTracksViewModel> mAlbumTracksViewModel;

        private TrackResponseHandler(final AlbumTracksViewModel albumTracksViewModel) {
            mAlbumTracksViewModel = new WeakReference<>(albumTracksViewModel);
        }

        @Override
        public void handleTracks(final List<MPDFileEntry> trackList, final int start, final int end) {
            final AlbumTracksViewModel model = mAlbumTracksViewModel.get();

            if (model != null) {
                model.setData(trackList);
            }
        }
    }

    public static class AlbumTracksModelFactory implements ViewModelProvider.Factory {

        private final Application mApplication;

        private final MPDAlbum mAlbum;

        private final boolean mUseArtistSort;

        public AlbumTracksModelFactory(final Application application, final MPDAlbum album, final boolean useArtistSort) {
            mApplication = application;
            mAlbum = album;
            mUseArtistSort = useArtistSort;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new AlbumTracksViewModel(mApplication, mAlbum, mUseArtistSort);
        }
    }
}
