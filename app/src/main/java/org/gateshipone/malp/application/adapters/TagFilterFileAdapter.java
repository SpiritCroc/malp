/*
 *  Copyright (C) 2024 Team Gateship-One
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

package org.gateshipone.malp.application.adapters;

import android.content.Context;
import android.util.Pair;

import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResonseGenericObject;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseFileList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPlaytime;

import java.lang.ref.WeakReference;

public class TagFilterFileAdapter extends WindowedFileAdapter {
    private int mCount;

    private Pair<String, String> mTagFilter;

    public TagFilterFileAdapter(Context context) {
        super(context);
    }

    private void setCount(int count) {
        mCount = count;
        updateFileList();
    }

    @Override
    public int getCount() {
        return mCount;
    }

    @Override
    protected boolean isWindowed() {
        return true;
    }

    @Override
    protected void fetchDataWindowed(MPDResponseFileList responseHandler, int start, int end) {
        if (mTagFilter != null) {
            MPDQueryHandler.getTagFilteredSongs(responseHandler, mTagFilter, start, end);
        }
    }

    @Override
    protected void fetchDataNonWindowed(MPDResponseFileList responseHandler) {

    }

    public void setTagFilter(Pair<String, String> tagFilter) {
        mTagFilter = tagFilter;
        mCount = 0;
        updateFileList();

        // Query actual song count
        MPDQueryHandler.getTagFilteredSongCount(new TrackCountResponse(this), mTagFilter);
    }

    private static class TrackCountResponse extends MPDResonseGenericObject {

        WeakReference<TagFilterFileAdapter> mAdapter;

        public TrackCountResponse(TagFilterFileAdapter adapter) {
            mAdapter = new WeakReference<>(adapter);
        }

        @Override
        public void handleObject(Object object) {
            TagFilterFileAdapter adapter = mAdapter.get();
            if (adapter == null) {
                return;
            }
            adapter.setCount(((MPDPlaytime)object).getSongCount());
        }
    }
}
