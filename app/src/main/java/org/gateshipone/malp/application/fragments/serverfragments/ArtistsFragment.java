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

package org.gateshipone.malp.application.fragments.serverfragments;


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.util.Pair;
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
import androidx.annotation.Nullable;
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
import org.gateshipone.malp.application.viewmodels.SearchViewModel;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;

public class ArtistsFragment extends GenericMPDFragment<MPDArtist> implements AdapterView.OnItemClickListener {
    public static final String TAG = ArtistsFragment.class.getSimpleName();

    private static final String REQUESTED_TAG_NAME = "ARG_TAG_NAME";
    private static final String REQUESTED_TAG_VALUE = "ARG_TAG_VALUE";

    private ArtistSelectedCallback mSelectedCallback;

    private MPDAlbum.MPD_ALBUM_SORT_ORDER mAlbumSortOrder;
    private MPDArtist.MPD_ALBUM_ARTIST_SELECTOR mAlbumArtistSelector;
    private MPDArtist.MPD_ARTIST_SORT_SELECTOR mArtistSortSelector;

    private String mTagName;
    private String mTagValue;


    public static ArtistsFragment newInstance() {
        return new ArtistsFragment();
    }

    public static ArtistsFragment newInstance(Pair<String, String> tagFilter) {
        final Bundle args = new Bundle();
        if (tagFilter != null) {
            args.putString(REQUESTED_TAG_NAME, tagFilter.first);
            args.putString(REQUESTED_TAG_VALUE, tagFilter.second);
        }
        ArtistsFragment fragment = new ArtistsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        final String viewAppearance = sharedPref.getString(getString(R.string.pref_library_view_key), getString(R.string.pref_library_view_default));

        final boolean useList = viewAppearance.equals(getString(R.string.pref_library_view_list_key));

        if (useList) {
            return inflater.inflate(R.layout.listview_layout_refreshable, container, false);
        } else {
            return inflater.inflate(R.layout.fragment_gridview, container, false);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        final String viewAppearance = sharedPref.getString(getString(R.string.pref_library_view_key), getString(R.string.pref_library_view_default));

        final boolean useList = viewAppearance.equals(getString(R.string.pref_library_view_list_key));

        final Bundle args = getArguments();

        if (args != null && savedInstanceState == null) {
            mTagName = args.getString(REQUESTED_TAG_NAME);
            mTagValue = args.getString(REQUESTED_TAG_VALUE);
        }

        Log.e(TAG, "Tag[" + mTagName + "]=" + mTagValue);

        mAlbumSortOrder = PreferenceHelper.getMPDAlbumSortOrder(sharedPref, requireContext());
        mAlbumArtistSelector = PreferenceHelper.getAlbumArtistSelector(sharedPref, requireContext());
        mArtistSortSelector = PreferenceHelper.getArtistSortSelector(sharedPref, requireContext());

        AbsListView adapterView;
        if (useList) {
            // get listview
            adapterView = (ListView) view.findViewById(R.id.main_listview);
        } else {
            // get gridview
            adapterView = (GridView) view.findViewById(R.id.grid_refresh_gridview);
        }

        mAdapter = new ArtistsAdapter(getActivity(), useList);

        adapterView.setAdapter(mAdapter);
        adapterView.setOnItemClickListener(this);

        adapterView.setOnScrollListener(new ScrollSpeedListener(mAdapter));

        // register for context menu
        registerForContextMenu(adapterView);


        // get swipe layout
        mSwipeRefreshLayout = view.findViewById(R.id.refresh_layout);
        // set swipe colors
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getThemeColor(requireContext(), R.attr.colorAccent),
                ThemeUtils.getThemeColor(requireContext(), R.attr.colorPrimary));
        // set swipe refresh listener
        mSwipeRefreshLayout.setOnRefreshListener(this::refreshContent);

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);

        final SearchViewModel searchViewModel = new ViewModelProvider(requireParentFragment()).get(SearchViewModel.class);
        searchViewModel.getSearchString().observe(getViewLifecycleOwner(), searchString -> {
            if (searchString != null) {
                applyFilter(searchString);
            } else {
                removeFilter();
            }
        });
    }

    @Override
    GenericViewModel<MPDArtist> getViewModel() {
        return new ViewModelProvider(this, new ArtistsViewModel.ArtistViewModelFactory(requireActivity().getApplication(), mAlbumArtistSelector, mArtistSortSelector, new Pair<>(mTagName, mTagValue))).get(ArtistsViewModel.class);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (null != mFABCallback) {
            mFABCallback.setupFAB(false, null);
            String title;
            boolean backbutton;
            if (mTagValue == null || mTagValue.isEmpty()) {
                title = getString(R.string.app_name);
                backbutton = false;
            } else {
                title = mTagValue;
                backbutton = true;
            }
            mFABCallback.setupToolbar(title, true, !backbutton, false);
        }
        ArtworkManager.getInstance(requireContext().getApplicationContext()).registerOnNewArtistImageListener((ArtistsAdapter) mAdapter);
    }

    @Override
    public void onPause() {
        super.onPause();

        ArtworkManager.getInstance(requireContext().getApplicationContext()).unregisterOnNewArtistImageListener((ArtistsAdapter) mAdapter);
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
            throw new ClassCastException(context + " must implement OnArtistSelectedListener");
        }
    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = requireActivity().getMenuInflater();
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

        final int itemId = item.getItemId();

        if (itemId == R.id.fragment_artist_action_enqueue) {
            enqueueArtist(info.position);
            return true;
        } else if (itemId == R.id.fragment_artist_action_play) {
            playArtist(info.position);
            return true;
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
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

        MPDQueryHandler.addArtist(artist.getArtistName(), mAlbumSortOrder, mAlbumArtistSelector, mArtistSortSelector);
    }

    private void playArtist(int index) {
        MPDArtist artist = (MPDArtist) mAdapter.getItem(index);

        MPDQueryHandler.playArtist(artist.getArtistName(), mAlbumSortOrder, mAlbumArtistSelector, mArtistSortSelector);
    }

    public void applyFilter(String name) {
        mAdapter.applyFilter(name);
    }

    public void removeFilter() {
        mAdapter.removeFilter();
    }

}
