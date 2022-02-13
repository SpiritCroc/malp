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

import org.gateshipone.malp.R;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseFileList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPlaylist;

import java.lang.ref.WeakReference;
import java.util.List;

public class PlaylistsViewModel extends GenericViewModel<MPDFileEntry> {

    private final PlaylistResponseHandler mPlaylistResponseHandler;

    private final boolean mAddHeader;

    private PlaylistsViewModel(@NonNull final Application application, final boolean addHeader) {
        super(application);

        mPlaylistResponseHandler = new PlaylistResponseHandler(this);

        mAddHeader = addHeader;
    }

    @Override
    void loadData() {
        MPDQueryHandler.getSavedPlaylists(mPlaylistResponseHandler);
    }

    private static class PlaylistResponseHandler extends MPDResponseFileList {
        private final WeakReference<PlaylistsViewModel> mPlaylistViewModel;

        private PlaylistResponseHandler(final PlaylistsViewModel playlistsViewModel) {
            mPlaylistViewModel = new WeakReference<>(playlistsViewModel);
        }

        @Override
        public void handleTracks(final List<MPDFileEntry> fileList, final int start, final int end) {
            final PlaylistsViewModel playlistsViewModel = mPlaylistViewModel.get();

            if (playlistsViewModel != null) {
                if (playlistsViewModel.mAddHeader) {
                    fileList.add(0, new MPDPlaylist(playlistsViewModel.getApplication().getString(R.string.create_new_playlist)));
                }
                playlistsViewModel.setData(fileList);
            }
        }
    }

    public static class PlaylistsViewModelFactory implements ViewModelProvider.Factory {

        private final Application mApplication;

        private final boolean mAddHeader;

        public PlaylistsViewModelFactory(final Application application, final boolean addHeader) {
            mApplication = application;
            mAddHeader = addHeader;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new PlaylistsViewModel(mApplication, mAddHeader);
        }
    }
}
