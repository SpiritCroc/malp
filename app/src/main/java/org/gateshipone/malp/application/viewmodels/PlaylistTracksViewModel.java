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

import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseFileList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;

import java.lang.ref.WeakReference;
import java.util.List;

public class PlaylistTracksViewModel extends GenericViewModel<MPDFileEntry> {

    private final MPDResponseFileList mTrackResponseHandler;

    private final String mPlaylistPath;

    private PlaylistTracksViewModel(@NonNull final Application application, final String playlistPath) {
        super(application);

        mTrackResponseHandler = new TrackResponseHandler(this);

        mPlaylistPath = playlistPath;
    }

    @Override
    void loadData() {
        if ((null == mPlaylistPath) || mPlaylistPath.equals("")) {
            MPDQueryHandler.getCurrentPlaylist(mTrackResponseHandler);
        } else {
            MPDQueryHandler.getSavedPlaylist(mTrackResponseHandler, mPlaylistPath);
        }
    }

    private static class TrackResponseHandler extends MPDResponseFileList {
        private final WeakReference<PlaylistTracksViewModel> mPlaylistTracksViewModel;

        private TrackResponseHandler(final PlaylistTracksViewModel playlistTracksViewModel) {
            mPlaylistTracksViewModel = new WeakReference<>(playlistTracksViewModel);
        }

        @Override
        public void handleTracks(final List<MPDFileEntry> trackList, final int start, final int end) {
            PlaylistTracksViewModel model = mPlaylistTracksViewModel.get();

            if (model != null) {
                model.setData(trackList);
            }
        }
    }

    public static class PlaylistTracksModelFactory implements ViewModelProvider.Factory {

        private final Application mApplication;

        private final String mPlaylistPath;

        public PlaylistTracksModelFactory(final Application application, final String playlistPath) {
            mApplication = application;
            mPlaylistPath = playlistPath;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new PlaylistTracksViewModel(mApplication, mPlaylistPath);
        }
    }
}
