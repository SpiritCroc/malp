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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.FileAdapter;
import org.gateshipone.malp.application.callbacks.AddPathToPlaylist;
import org.gateshipone.malp.application.utils.PreferenceHelper;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.application.viewmodels.SearchResultViewModel;
import org.gateshipone.malp.application.views.NowPlayingView;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDCommands;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.util.List;

public class SearchFragment extends GenericMPDFragment<MPDFileEntry> implements AdapterView.OnItemClickListener, View.OnFocusChangeListener {
    public static final String TAG = SearchFragment.class.getSimpleName();

    /**
     * Main ListView of this fragment
     */
    private ListView mListView;

    private Spinner mSelectSpinner;

    private SearchView mSearchView;

    private String mSearchText = "";

    private MPDCommands.MPD_SEARCH_TYPE mSearchType;

    private MPDAlbum.MPD_ALBUM_SORT_ORDER mAlbumSortOrder;

    private PreferenceHelper.LIBRARY_TRACK_CLICK_ACTION mClickAction;

    /**
     * Hack variable to save the position of a opened context menu because menu info is null for
     * submenus.
     */
    private int mContextMenuPosition;

    public static SearchFragment newInstance() {
        return new SearchFragment();
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
        mAdapter = new FileAdapter(getActivity(), false, true);

        // Combine the two to a happy couple
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        registerForContextMenu(mListView);

        mSelectSpinner = view.findViewById(R.id.search_criteria);

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.server_search_choices, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mSelectSpinner.setAdapter(adapter);
        mSelectSpinner.setOnItemSelectedListener(new SpinnerSelectListener());

        mSearchView = view.findViewById(R.id.search_text);
        mSearchView.setOnQueryTextListener(new SearchViewQueryListener());
        mSearchView.setOnFocusChangeListener(this);
        mSearchView.setIconified(false);

        // get swipe layout
        mSwipeRefreshLayout = view.findViewById(R.id.refresh_layout);
        // set swipe colors
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent),
                ThemeUtils.getThemeColor(getContext(), R.attr.colorPrimary));
        // set swipe refresh listener
        mSwipeRefreshLayout.setOnRefreshListener(this::refreshContent);

        setHasOptionsMenu(true);

        // Get album sort order
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        mAlbumSortOrder = PreferenceHelper.getMPDAlbumSortOrder(sharedPref, getContext());
        mClickAction = PreferenceHelper.getClickAction(sharedPref, getContext());

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);

    }

    @Override
    GenericViewModel<MPDFileEntry> getViewModel() {
        return new ViewModelProvider(this, new SearchResultViewModel.SearchResultViewModelFactory(getActivity().getApplication())).get(SearchResultViewModel.class);
    }

    @Override
    protected void onDataReady(List<MPDFileEntry> model) {
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

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        String searchTypeSetting = sharedPref.getString(getString(R.string.pref_search_type_key), getString(R.string.pref_search_type_default));

        if (searchTypeSetting.equals(getString(R.string.pref_search_type_track_key))) {
            mSearchType = MPDCommands.MPD_SEARCH_TYPE.MPD_SEARCH_TRACK;
            mSelectSpinner.setSelection(0);
        } else if (searchTypeSetting.equals(getString(R.string.pref_search_type_album_key))) {
            mSearchType = MPDCommands.MPD_SEARCH_TYPE.MPD_SEARCH_ALBUM;
            mSelectSpinner.setSelection(1);
        } else if (searchTypeSetting.equals(getString(R.string.pref_search_type_artist_key))) {
            mSearchType = MPDCommands.MPD_SEARCH_TYPE.MPD_SEARCH_ARTIST;
            mSelectSpinner.setSelection(2);
        } else if (searchTypeSetting.equals(getString(R.string.pref_search_type_file_key))) {
            mSearchType = MPDCommands.MPD_SEARCH_TYPE.MPD_SEARCH_FILE;
            mSelectSpinner.setSelection(3);
        } else if (searchTypeSetting.equals(getString(R.string.pref_search_type_any_key))) {
            mSearchType = MPDCommands.MPD_SEARCH_TYPE.MPD_SEARCH_ANY;
            mSelectSpinner.setSelection(4);
        }
    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.context_menu_search_track, menu);
    }

    @Override
    public void onPause() {
        super.onPause();
        closeKeyboard();
    }

    /**
     * Hook called when an menu item in the context menu is selected.
     *
     * @param item The menu item that was selected.
     * @return True if the hook was consumed here.
     */
    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        int position;
        if (info == null) {
            if (mContextMenuPosition == -1) {
                return super.onContextItemSelected(item);
            }
            position = mContextMenuPosition;
            mContextMenuPosition = -1;
        } else {
            position = info.position;
        }


        MPDTrack track = (MPDTrack) mAdapter.getItem(position);

        mListView.requestFocus();

        final int itemId = item.getItemId();

        if (itemId == R.id.action_song_play) {
            MPDQueryHandler.playSong(track.getPath());
            return true;
        } else if (itemId == R.id.action_song_enqueue) {
            MPDQueryHandler.addPath(track.getPath());
            return true;
        } else if (itemId == R.id.action_song_enqueue_at_start) {
            MPDQueryHandler.addPathAtStart(track.getPath());
            return true;
        } else if (itemId == R.id.action_song_play_next) {
            MPDQueryHandler.playSongNext(track.getPath());
            return true;
        } else if (itemId == R.id.action_add_to_saved_playlist) {
            // open dialog in order to save the current playlist as a playlist in the mediastore
            ChoosePlaylistDialog choosePlaylistDialog = ChoosePlaylistDialog.newInstance(true);

            choosePlaylistDialog.setCallback(new AddPathToPlaylist((MPDFileEntry) mAdapter.getItem(position), getContext()));
            choosePlaylistDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "ChoosePlaylistDialog");
            return true;
        } else if (itemId == R.id.action_show_details) {
            // Open song details dialog
            SongDetailsDialog songDetailsDialog = SongDetailsDialog.createDialog((MPDTrack) mAdapter.getItem(position), false);
            songDetailsDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "SongDetails");
            return true;
        } else if (itemId == R.id.action_add_album) {
            String artist = track.getStringTag(MPDTrack.StringTagTypes.ALBUMARTIST);
            if (artist.isEmpty()) {
                artist = track.getStringTag(MPDTrack.StringTagTypes.ARTIST);
            }
            MPDQueryHandler.addArtistAlbum(track.getStringTag(MPDTrack.StringTagTypes.ALBUM), artist, track.getStringTag(MPDTrack.StringTagTypes.ALBUM_MBID));
            return true;
        } else if (itemId == R.id.action_play_album) {
            String artist = track.getStringTag(MPDTrack.StringTagTypes.ALBUMARTIST);
            if (artist.isEmpty()) {
                artist = track.getStringTag(MPDTrack.StringTagTypes.ARTIST);
            }
            MPDQueryHandler.playArtistAlbum(track.getStringTag(MPDTrack.StringTagTypes.ALBUM), artist, track.getStringTag(MPDTrack.StringTagTypes.ALBUM_MBID));
            return true;
        } else if (itemId == R.id.action_add_artist) {
            MPDQueryHandler.addArtist(track.getStringTag(MPDTrack.StringTagTypes.ARTIST), mAlbumSortOrder);
            return true;
        } else if (itemId == R.id.action_play_artist) {
            MPDQueryHandler.playArtist(track.getStringTag(MPDTrack.StringTagTypes.ARTIST), mAlbumSortOrder);
            return true;
        } else if (itemId == R.id.menu_group_album) {
            // Save position for later use
            mContextMenuPosition = info.position;
        } else if (itemId == R.id.menu_group_artist) {
            // Save position for later use
            mContextMenuPosition = info.position;
        }

        return super.onContextItemSelected(item);
    }

    /**
     * Initialize the options menu.
     * Be sure to call {@link #setHasOptionsMenu} before.
     *
     * @param menu         The container for the custom options menu.
     * @param menuInflater The inflater to instantiate the layout.
     */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.fragment_menu_search_tracks, menu);

        // get tint color
        int tintColor = ThemeUtils.getThemeColor(getContext(), R.attr.malp_color_text_accent);

        Drawable drawable = menu.findItem(R.id.action_add_search_result).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_add_search_result).setIcon(drawable);

        super.onCreateOptionsMenu(menu, menuInflater);
    }

    /**
     * Hook called when an menu item in the options menu is selected.
     *
     * @param item The menu item that was selected.
     * @return True if the hook was consumed here.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add_search_result) {
            if (!mSearchText.isEmpty()) {
                MPDQueryHandler.searchAddFiles(mSearchText, mSearchType);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        switch (mClickAction) {
            case ACTION_SHOW_DETAILS: {
                // Open song details dialog
                SongDetailsDialog songDetailsDialog = SongDetailsDialog.createDialog((MPDTrack) mAdapter.getItem(position), false);
                songDetailsDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "SongDetails");
            }
            break;
            case ACTION_ADD_SONG: {
                MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                MPDQueryHandler.addPath(track.getPath());
            }
            break;
            case ACTION_ADD_SONG_AT_START: {
                MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                MPDQueryHandler.addPathAtStart(track.getPath());
            }
            break;
            case ACTION_PLAY_SONG: {
                MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                MPDQueryHandler.playSong(track.getPath());
            }
            break;
            case ACTION_PLAY_SONG_NEXT: {
                MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                MPDQueryHandler.playSongNext(track.getPath());
            }
            break;
        }
    }

    private void showFAB(boolean active) {
        if (null != mFABCallback) {
            mFABCallback.setupFAB(active, active ? new FABOnClickListener() : null);
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v.equals(mSearchView) && !hasFocus) {
            closeKeyboard();
        }
    }

    private void closeKeyboard() {
        final InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
    }

    private void openKeyboard() {
        final InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(0, InputMethodManager.SHOW_IMPLICIT);
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
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
            SharedPreferences.Editor prefEditor = sharedPref.edit();
            switch (position) {
                case 0:
                    mSearchType = MPDCommands.MPD_SEARCH_TYPE.MPD_SEARCH_TRACK;
                    prefEditor.putString(getString(R.string.pref_search_type_key), getString(R.string.pref_search_type_track_key));
                    break;
                case 1:
                    mSearchType = MPDCommands.MPD_SEARCH_TYPE.MPD_SEARCH_ALBUM;
                    prefEditor.putString(getString(R.string.pref_search_type_key), getString(R.string.pref_search_type_album_key));
                    break;
                case 2:
                    mSearchType = MPDCommands.MPD_SEARCH_TYPE.MPD_SEARCH_ARTIST;
                    prefEditor.putString(getString(R.string.pref_search_type_key), getString(R.string.pref_search_type_artist_key));
                    break;
                case 3:
                    mSearchType = MPDCommands.MPD_SEARCH_TYPE.MPD_SEARCH_FILE;
                    prefEditor.putString(getString(R.string.pref_search_type_key), getString(R.string.pref_search_type_file_key));
                    break;
                case 4:
                    mSearchType = MPDCommands.MPD_SEARCH_TYPE.MPD_SEARCH_ANY;
                    prefEditor.putString(getString(R.string.pref_search_type_key), getString(R.string.pref_search_type_any_key));
                    break;
            }

            // Write settings values
            prefEditor.apply();

            if (mFABCallback.getNowPlayingDragStatus() == NowPlayingView.NowPlayingDragStatusReceiver.DRAG_STATUS.DRAGGED_DOWN) {
                mSearchView.requestFocus();
                // Open the keyboard again
                openKeyboard();
            } else {
                // close keyboard if NPV is shown
                closeKeyboard();
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    }

    private class SearchViewQueryListener implements SearchView.OnQueryTextListener {

        @Override
        public boolean onQueryTextSubmit(String query) {
            mSearchText = query;
            ((SearchResultViewModel) getViewModel()).setSearchOptions(mSearchText, mSearchType);
            refreshContent();
            return false;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            return false;
        }
    }

}
