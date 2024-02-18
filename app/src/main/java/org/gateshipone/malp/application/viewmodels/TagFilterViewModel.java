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
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFilterObject;

import java.lang.ref.WeakReference;
import java.util.List;

public class TagFilterViewModel extends GenericViewModel<MPDFilterObject> {

    private final GenericHandler mResponseHandler;

    private String mTagName;

    private TagFilterViewModel(@NonNull final Application application) {
        super(application);

        mResponseHandler = new GenericHandler(this);
    }

    public void setTagName(String tagName) {
        mTagName = tagName;
    }
    @Override
    void loadData() {
        if (mTagName != null) {
            MPDQueryHandler.getTagFilterEntries(mTagName, mResponseHandler);
        }
    }

    private static class GenericHandler extends MPDResponseGenericList<MPDFilterObject> {

        private final WeakReference<TagFilterViewModel> mFilterObjectsViewModel;

        GenericHandler(final TagFilterViewModel outputsViewModel) {
            mFilterObjectsViewModel = new WeakReference<>(outputsViewModel);
        }

        @Override
        public void handleList(List<MPDFilterObject> itemList) {
            final TagFilterViewModel viewModel = mFilterObjectsViewModel.get();

            if (viewModel != null) {
                viewModel.setData(itemList);
            }
        }
    }

    public static class TagFilterViewModelFactory implements ViewModelProvider.Factory {

        private final Application mApplication;

        public TagFilterViewModelFactory(final Application application) {
            mApplication = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new TagFilterViewModel(mApplication);
        }
    }
}
