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

public class FilesViewModel extends GenericViewModel<MPDFileEntry> {

    private final FilesResponseHandler mFilesResponseHandler;

    private final String mPath;

    private FilesViewModel(@NonNull final Application application, final String path) {
        super(application);

        mFilesResponseHandler = new FilesResponseHandler(this);

        mPath = path;
    }

    @Override
    void loadData() {
        MPDQueryHandler.getFiles(mFilesResponseHandler, mPath);
    }

    private static class FilesResponseHandler extends MPDResponseFileList {

        private final WeakReference<FilesViewModel> mFilesViewModel;

        private FilesResponseHandler(FilesViewModel filesViewModel) {
            mFilesViewModel = new WeakReference<>(filesViewModel);
        }

        @Override
        public void handleTracks(final List<MPDFileEntry> fileList, final int start, final int end) {
            final FilesViewModel filesViewModel = mFilesViewModel.get();

            if (filesViewModel != null) {
                filesViewModel.setData(fileList);
            }
        }
    }

    public static class FilesViewModelFactory implements ViewModelProvider.Factory {

        private final Application mApplication;

        private final String mPath;

        public FilesViewModelFactory(final Application application, final String path) {
            mApplication = application;
            mPath = path;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new FilesViewModel(mApplication, mPath);
        }
    }
}
