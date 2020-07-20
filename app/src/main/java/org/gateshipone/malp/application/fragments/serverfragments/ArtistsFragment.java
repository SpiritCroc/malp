/*
 *  Copyright (C) 2020 Team Gateship-One
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

package org.gateshipone.malp.application.fragments.serverfragments;


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.ArtistsAdapter;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.listviewitems.AbsImageListViewItem;
import org.gateshipone.malp.application.utils.PreferenceHelper;
import org.gateshipone.malp.application.utils.ScrollSpeedListener;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.ArtistsViewModel;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;

public class ArtistsFragment extends GenericMPDFragment<MPDArtist> implements AdapterView.OnItemClickListener {
    public final static String TAG = ArtistsFragment.class.getSimpleName();

    /**
     * Save the root GridView for later usage.
     */
    private AbsListView mAdapterView;

    /**
     * Save the last position here. Gets reused when the user returns to this view after selecting sme
     * albums.
     */
    private int mLastPosition = -1;

    private ArtistSelectedCallback mSelectedCallback;

    private boolean mUseList = false;

    private MPDAlbum.MPD_ALBUM_SORT_ORDER mAlbumSortOrder;

    private boolean mUseAlbumArtists;

    private boolean mUseArtistSort;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        String libraryView = sharedPref.getString(getString(R.string.pref_library_view_key), getString(R.string.pref_library_view_default));

        if (libraryView.equals(getString(R.string.pref_library_view_list_key))) {
            mUseList = true;
        }

        mAlbumSortOrder = PreferenceHelper.getMPDAlbumSortOrder(sharedPref, getContext());
        mUseAlbumArtists = sharedPref.getBoolean(getString(R.string.pref_use_album_artists_key), getResources().getBoolean(R.bool.pref_use_album_artists_default));
        mUseArtistSort = sharedPref.getBoolean(getString(R.string.pref_use_artist_sort_key), getResources().getBoolean(R.bool.pref_use_artist_sort_default));

        View rootView;
        // get gridview
        if (mUseList) {
            rootView = inflater.inflate(R.layout.listview_layout_refreshable, container, false);
            mAdapterView = (ListView) rootView.findViewById(R.id.main_listview);
        } else {
            // Inflate the layout for this fragment
            rootView = inflater.inflate(R.layout.fragment_gridview, container, false);
            mAdapterView = (GridView) rootView.findViewById(R.id.grid_refresh_gridview);
        }

        mAdapter = new ArtistsAdapter(getActivity(), mUseList);

        mAdapterView.setAdapter(mAdapter);
        mAdapterView.setOnItemClickListener(this);

        mAdapterView.setOnScrollListener(new ScrollSpeedListener(mAdapter));

        // register for context menu
        registerForContextMenu(mAdapterView);


        // get swipe layout
        mSwipeRefreshLayout = rootView.findViewById(R.id.refresh_layout);
        // set swipe colors
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent),
                ThemeUtils.getThemeColor(getContext(), R.attr.colorPrimary));
        // set swipe refresh listener
        mSwipeRefreshLayout.setOnRefreshListener(this::refreshContent);

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);

        return rootView;
    }

    @Override
    GenericViewModel<MPDArtist> getViewModel() {
        return new ViewModelProvider(this, new ArtistsViewModel.ArtistViewModelFactory(getActivity().getApplication(), mUseAlbumArtists, mUseArtistSort)).get(ArtistsViewModel.class);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (null != mFABCallback) {
            mFABCallback.setupFAB(false, null);
            mFABCallback.setupToolbar(getString(R.string.app_name), true, true, false);
        }
        ArtworkManager.getInstance(getContext().getApplicationContext()).registerOnNewArtistImageListener((ArtistsAdapter) mAdapter);
    }

    @Override
    public void onPause() {
        super.onPause();

        ArtworkManager.getInstance(getContext().getApplicationContext()).unregisterOnNewArtistImageListener((ArtistsAdapter) mAdapter);
    }

    /**
     * Called when the fragment is first attached to its context.
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mSelectedCallback = (ArtistSelectedCallback) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnArtistSelectedListener");
        }
    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.context_menu_artist, menu);
    }

    /**
     * Hook called when an menu item in the context menu is selected.
     *
     * @param item The menu item that was selected.
     * @return True if the hook was consumed here.
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        if (info == null) {
            return super.onContextItemSelected(item);
        }

        switch (item.getItemId()) {
            case R.id.fragment_artist_action_enqueue:
                enqueueArtist(info.position);
                return true;
            case R.id.fragment_artist_action_play:
                playArtist(info.position);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mLastPosition = position;

        MPDArtist artist = (MPDArtist) mAdapter.getItem(position);

        Bitmap bitmap = null;

        // Check if correct view type, to be safe
        if (view instanceof AbsImageListViewItem) {
            bitmap = ((AbsImageListViewItem) view).getBitmap();
        }

        mSelectedCallback.onArtistSelected(artist, bitmap);
    }

    public interface ArtistSelectedCallback {
        void onArtistSelected(MPDArtist artistname, Bitmap bitmap);
    }


    private void enqueueArtist(int index) {
        MPDArtist artist = (MPDArtist) mAdapter.getItem(index);

        MPDQueryHandler.addArtist(artist.getArtistName(), mAlbumSortOrder);
    }

    private void playArtist(int index) {
        MPDArtist artist = (MPDArtist) mAdapter.getItem(index);

        MPDQueryHandler.playArtist(artist.getArtistName(), mAlbumSortOrder);
    }

    public void applyFilter(String name) {
        mAdapter.applyFilter(name);
    }

    public void removeFilter() {
        mAdapter.removeFilter();
    }

}
