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
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDCommands;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class SearchResultViewModel extends GenericViewModel<MPDFileEntry> {

    private final MPDResponseFileList pTrackResponseHandler;

    private String mSearchString;

    private MPDCommands.MPD_SEARCH_TYPE mSearchType;

    public SearchResultViewModel(@NonNull final Application application) {
        super(application);

        pTrackResponseHandler = new TrackResponseHandler(this);
    }

    public void setSearchOptions(final String searchTerm, final MPDCommands.MPD_SEARCH_TYPE type) {
        mSearchString = searchTerm;
        mSearchType = type;
    }

    @Override
    void loadData() {
        if (mSearchString != null && !mSearchString.isEmpty() && mSearchType != null) {
            MPDQueryHandler.searchFiles(mSearchString, mSearchType, pTrackResponseHandler);
        } else {
            setData(new ArrayList<>());
        }
    }

    private static class TrackResponseHandler extends MPDResponseFileList {
        private final WeakReference<SearchResultViewModel> mSearchResultViewModel;

        private TrackResponseHandler(final SearchResultViewModel searchResultViewModel) {
            mSearchResultViewModel = new WeakReference<>(searchResultViewModel);
        }

        @Override
        public void handleTracks(final List<MPDFileEntry> trackList, final int start, final int end) {
            SearchResultViewModel searchResultViewModel = mSearchResultViewModel.get();

            if (searchResultViewModel != null) {
                searchResultViewModel.setData(trackList);
            }
        }
    }

    public static class SearchResultViewModelFactory implements ViewModelProvider.Factory {

        private final Application mApplication;

        public SearchResultViewModelFactory(final Application application) {
            mApplication = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new SearchResultViewModel(mApplication);
        }
    }
}
