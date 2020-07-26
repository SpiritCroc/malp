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
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
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
import org.gateshipone.malp.application.viewmodels.PlaylistTracksViewModel;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDCommandHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

public class PlaylistTracksFragment extends GenericMPDFragment<MPDFileEntry> implements AdapterView.OnItemClickListener {
    public final static String TAG = PlaylistTracksFragment.class.getSimpleName();

    private final static String EXTRA_PLAYLIST_NAME = "name";

    /**
     * Main ListView of this fragment
     */
    private ListView mListView;

    /**
     * Name of the playlist to load
     */
    private String mPath;

    private PreferenceHelper.LIBRARY_TRACK_CLICK_ACTION mClickAction;

    public static PlaylistTracksFragment newInstance(final String path) {
        final Bundle args = new Bundle();
        args.putString(EXTRA_PLAYLIST_NAME, path);

        final PlaylistTracksFragment fragment = new PlaylistTracksFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.listview_layout_refreshable, container, false);

        // Get the main ListView of this fragment
        mListView = rootView.findViewById(R.id.main_listview);

        Bundle args = getArguments();
        if (null != args) {
            mPath = args.getString(EXTRA_PLAYLIST_NAME);
        }

        // Check if sections should be shown
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean showVisibleSections = sharedPref.getBoolean(getContext().getString(R.string.pref_show_playlist_sections_key), getContext().getResources().getBoolean(R.bool.pref_show_playlist_sections_default));
        mClickAction = PreferenceHelper.getClickAction(sharedPref, getContext());

        // Create the needed adapter for the ListView
        mAdapter = new FileAdapter(getActivity(), false, false, showVisibleSections, true);

        // Combine the two to a happy couple
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        registerForContextMenu(mListView);

        // get swipe layout
        mSwipeRefreshLayout = rootView.findViewById(R.id.refresh_layout);
        // set swipe colors
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent),
                ThemeUtils.getThemeColor(getContext(), R.attr.colorPrimary));
        // set swipe refresh listener
        mSwipeRefreshLayout.setOnRefreshListener(this::refreshContent);

        setHasOptionsMenu(true);

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);

        // Return the ready inflated and configured fragment view.
        return rootView;
    }

    @Override
    GenericViewModel<MPDFileEntry> getViewModel() {
        return new ViewModelProvider(this, new PlaylistTracksViewModel.PlaylistTracksModelFactory(getActivity().getApplication(), mPath)).get(PlaylistTracksViewModel.class);
    }

    /**
     * Starts the loader to make sure the data is up-to-date after resuming the fragment (from background)
     */
    @Override
    public void onResume() {
        super.onResume();

        if (null != mFABCallback) {
            mFABCallback.setupFAB(true, new FABOnClickListener());
            mFABCallback.setupToolbar(mPath, false, false, false);
        }

    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.context_menu_track, menu);

        // Enable the remove from list action
        menu.findItem(R.id.action_remove_from_list).setVisible(true);
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
            case R.id.action_song_enqueue:
                enqueueTrack(info.position);
                return true;
            case R.id.action_song_enqueue_at_start:
                prependTrack(info.position);
                return true;
            case R.id.action_song_play:
                play(info.position);
                return true;
            case R.id.action_song_play_next:
                playNext(info.position);
                return true;
            case R.id.action_add_to_saved_playlist: {
                // open dialog in order to save the current playlist as a playlist in the mediastore
                ChoosePlaylistDialog choosePlaylistDialog = ChoosePlaylistDialog.newInstance(true);

                choosePlaylistDialog.setCallback(new AddPathToPlaylist((MPDFileEntry) mAdapter.getItem(info.position), getActivity()));
                choosePlaylistDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "ChoosePlaylistDialog");
                return true;
            }
            case R.id.action_remove_from_list:
                MPDQueryHandler.removeSongFromSavedPlaylist(mPath, info.position);
                refreshContent();
                return true;
            case R.id.action_show_details: {
                // Open song details dialog
                SongDetailsDialog songDetailsDialog = SongDetailsDialog.createDialog((MPDTrack) mAdapter.getItem(info.position), false);
                songDetailsDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "SongDetails");
                return true;
            }
            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * Initialize the options menu.
     * Be sure to call {@link #setHasOptionsMenu} before.
     *
     * @param menu         The container for the custom options menu.
     * @param menuInflater The inflater to instantiate the layout.
     */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater menuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.fragment_playlist_tracks, menu);

        // get tint color
        int tintColor = ThemeUtils.getThemeColor(getContext(), R.attr.malp_color_text_accent);

        Drawable drawable = menu.findItem(R.id.action_add_playlist).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_add_playlist).setIcon(drawable);

        drawable = menu.findItem(R.id.action_search).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_search).setIcon(drawable);

        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();

        searchView.setOnQueryTextListener(new SearchTextObserver());

        super.onCreateOptionsMenu(menu, menuInflater);
    }

    /**
     * Hook called when an menu item in the options menu is selected.
     *
     * @param item The menu item that was selected.
     * @return True if the hook was consumed here.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add_playlist) {
            MPDQueryHandler.loadPlaylist(mPath);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void enqueueTrack(int index) {
        MPDTrack track = (MPDTrack) mAdapter.getItem(index);

        MPDQueryHandler.addPath(track.getPath());
    }

    private void prependTrack(int index) {
        MPDTrack track = (MPDTrack) mAdapter.getItem(index);

        MPDQueryHandler.addPathAtStart(track.getPath());
    }

    private void play(int index) {
        MPDTrack track = (MPDTrack) mAdapter.getItem(index);

        MPDQueryHandler.playSong(track.getPath());
    }


    private void playNext(int index) {
        MPDTrack track = (MPDTrack) mAdapter.getItem(index);

        MPDQueryHandler.playSongNext(track.getPath());
    }

    public void applyFilter(String name) {
        mAdapter.applyFilter(name);
    }

    public void removeFilter() {
        mAdapter.removeFilter();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
        switch (mClickAction) {
            case ACTION_SHOW_DETAILS: {
                // Open song details dialog
                SongDetailsDialog songDetailsDialog = SongDetailsDialog.createDialog((MPDTrack) mAdapter.getItem(position),false);
                songDetailsDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "SongDetails");
                break;
            }
            case ACTION_ADD_SONG: {
                enqueueTrack(position);
                break;
            }
            case ACTION_ADD_SONG_AT_START: {
                prependTrack(position);
                break;

            }
            case ACTION_PLAY_SONG: {
                play(position);
                break;
            }
            case ACTION_PLAY_SONG_NEXT: {
                playNext(position);
                break;
            }
        }
    }

    private class FABOnClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            MPDCommandHandler.setRandom(false);
            MPDCommandHandler.setRepeat(false);

            MPDQueryHandler.playPlaylist(mPath);
        }
    }

    private class SearchTextObserver implements SearchView.OnQueryTextListener {

        @Override
        public boolean onQueryTextSubmit(String query) {
            if (!query.isEmpty()) {
                applyFilter(query);
            } else {
                removeFilter();
            }
            return false;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            if (!newText.isEmpty()) {
                applyFilter(newText);
            } else {
                removeFilter();
            }

            return true;
        }
    }
}
