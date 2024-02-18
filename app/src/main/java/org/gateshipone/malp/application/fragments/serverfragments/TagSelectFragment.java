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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.FileAdapter;
import org.gateshipone.malp.application.adapters.FilterItemAdapter;
import org.gateshipone.malp.application.callbacks.AddPathToPlaylist;
import org.gateshipone.malp.application.utils.PreferenceHelper;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.application.viewmodels.SearchResultViewModel;
import org.gateshipone.malp.application.viewmodels.TagFilterViewModel;
import org.gateshipone.malp.application.views.NowPlayingView;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDCommands;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFilterObject;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.util.List;

public class TagSelectFragment extends GenericMPDFragment<MPDFilterObject> implements AdapterView.OnItemClickListener{
    public static final String TAG = TagSelectFragment.class.getSimpleName();

    /**
     * Main ListView of this fragment
     */
    private ListView mListView;

    private Spinner mSelectSpinner;

    private String mSearchText = "";

    private MPDCommands.MPD_SEARCH_TYPE mSearchType;

    private MPDAlbum.MPD_ALBUM_SORT_ORDER mAlbumSortOrder;
    private MPDArtist.MPD_ALBUM_ARTIST_SELECTOR mAlbumArtistSelector;

    private MPDArtist.MPD_ARTIST_SORT_SELECTOR mArtistSortSelector;

    private PreferenceHelper.LIBRARY_TRACK_CLICK_ACTION mClickAction;

    private ArrayAdapter<String> mSpinnerAdapter;

    private String mTagName;

    /**
     * Hack variable to save the position of a opened context menu because menu info is null for
     * submenus.
     */
    private int mContextMenuPosition;

    public static TagSelectFragment newInstance() {
        return new TagSelectFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_server_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get the main ListView of this fragment
        mListView = view.findViewById(R.id.main_listview);

        // Create the needed adapter for the ListView
        mAdapter = new FilterItemAdapter(getActivity());

        // Combine the two to a happy couple
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        registerForContextMenu(mListView);

        mSelectSpinner = view.findViewById(R.id.search_criteria);

        // Create an ArrayAdapter using the string array and a default spinner layout
        mSpinnerAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item);

        mSpinnerAdapter.addAll(MPDInterface.getGenericInstance().getServerCapabilities().getTags());

        // Specify the layout to use when the list of choices appears
        mSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mSelectSpinner.setAdapter(mSpinnerAdapter);
        mSelectSpinner.setOnItemSelectedListener(new SpinnerSelectListener());

        SearchView mSearchView = view.findViewById(R.id.search_text);
        mSearchView.setVisibility(View.GONE);

        // get swipe layout
        mSwipeRefreshLayout = view.findViewById(R.id.refresh_layout);
        // set swipe colors
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getThemeColor(requireContext(), R.attr.colorAccent),
                ThemeUtils.getThemeColor(requireContext(), R.attr.colorPrimary));
        // set swipe refresh listener
        mSwipeRefreshLayout.setOnRefreshListener(this::refreshContent);

        setHasOptionsMenu(true);

        // Get album sort order
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mAlbumSortOrder = PreferenceHelper.getMPDAlbumSortOrder(sharedPref, requireContext());

        mAlbumArtistSelector = PreferenceHelper.getAlbumArtistSelector(sharedPref, requireContext());
        mArtistSortSelector = PreferenceHelper.getArtistSortSelector(sharedPref, requireContext());

        mClickAction = PreferenceHelper.getClickAction(sharedPref, requireContext());

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);

    }

    @Override
    GenericViewModel<MPDFilterObject> getViewModel() {
        return new ViewModelProvider(this, new TagFilterViewModel.TagFilterViewModelFactory(requireActivity().getApplication())).get(TagFilterViewModel.class);
    }

    @Override
    protected void onDataReady(List<MPDFilterObject> model) {
        super.onDataReady(model);

        if (null != model && !model.isEmpty()) {
            showFAB(true);
        } else {
            showFAB(false);
        }
    }

    /**
     * Starts the loader to make sure the data is up-to-date after resuming the fragment (from background)
     */
    @Override
    public void onResume() {
        super.onResume();

        if (null != mFABCallback) {
            mFABCallback.setupFAB(true, new FABOnClickListener());
            mFABCallback.setupToolbar(getResources().getString(R.string.action_search), false, true, false);
        }

        mTagName = mSpinnerAdapter.getItem(0);
        ((TagFilterViewModel)getViewModel()).setTagName(mTagName);
        mSelectSpinner.setSelection(0);
    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = requireActivity().getMenuInflater();
        inflater.inflate(R.menu.context_menu_search_track, menu);
    }

    @Override
    public void onPause() {
        super.onPause();
    }



    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MPDFilterObject object = mAdapter.getItem(position);
        ((TagSelectCallback)getActivity()).onTagSelected(mTagName ,object.getName());
    }

    private void showFAB(boolean active) {
        if (null != mFABCallback) {
            mFABCallback.setupFAB(active, active ? new FABOnClickListener() : null);
        }
    }

    private class FABOnClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            MPDQueryHandler.searchPlayFiles(mSearchText, mSearchType);
        }
    }

    private class SpinnerSelectListener implements AdapterView.OnItemSelectedListener {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            mTagName = mSpinnerAdapter.getItem(position);
            ((TagFilterViewModel)getViewModel()).setTagName(mTagName);
            refreshContent();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    }

    public interface TagSelectCallback {
        void onTagSelected(String tagName, String tagValue);
    }
}
