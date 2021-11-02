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
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.FileAdapter;
import org.gateshipone.malp.application.callbacks.AddPathToPlaylist;
import org.gateshipone.malp.application.callbacks.PlaylistCallback;
import org.gateshipone.malp.application.utils.PreferenceHelper;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.FilesViewModel;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDCommandHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDCapabilities;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDDirectory;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPlaylist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.util.List;

public class FilesFragment extends GenericMPDFragment<MPDFileEntry> implements AbsListView.OnItemClickListener {

    public static final String TAG = FilesFragment.class.getSimpleName();

    private static final String EXTRA_FILENAME = "filename";

    /**
     * Main ListView of this fragment
     */
    private ListView mListView;

    private FilesCallback mCallback;

    private PlaylistCallback mPlaylistCallback;

    private String mPath;

    /**
     * Save the last position here. Gets reused when the user returns to this view after selecting sme
     * albums.
     */
    private int mLastPosition = -1;

    /**
     * Saved search string when user rotates devices
     */
    private String mSearchString;

    /**
     * Constant for state saving
     */
    public static final String FILESFRAGMENT_SAVED_INSTANCE_SEARCH_STRING = "FilesFragment.SearchString";

    private PreferenceHelper.LIBRARY_TRACK_CLICK_ACTION mClickAction;

    public static FilesFragment newInstance(@NonNull final String fileName) {
        final Bundle args = new Bundle();
        args.putString(EXTRA_FILENAME, fileName);

        final FilesFragment fragment = new FilesFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.listview_layout_refreshable, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());

        boolean useTags = sharedPref.getBoolean(getString(R.string.pref_use_tags_in_filebrowser_key), getResources().getBoolean(R.bool.pref_use_tags_in_filebrowser_default));
        mClickAction = PreferenceHelper.getClickAction(sharedPref, getContext());

        // Get the main ListView of this fragment
        mListView = view.findViewById(R.id.main_listview);

        Bundle args = getArguments();

        mPath = args.getString(EXTRA_FILENAME);

        // Create the needed adapter for the ListView
        mAdapter = new FileAdapter(getActivity(), true, true, false, useTags);

        // Combine the two to a happy couple
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        registerForContextMenu(mListView);

        // get swipe layout
        mSwipeRefreshLayout = view.findViewById(R.id.refresh_layout);
        // set swipe colors
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent),
                ThemeUtils.getThemeColor(getContext(), R.attr.colorPrimary));
        // set swipe refresh listener
        mSwipeRefreshLayout.setOnRefreshListener(this::refreshContent);

        // try to resume the saved search string
        if (savedInstanceState != null) {
            mSearchString = savedInstanceState.getString(FILESFRAGMENT_SAVED_INSTANCE_SEARCH_STRING);
        }

        setHasOptionsMenu(true);

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);
    }

    @Override
    GenericViewModel<MPDFileEntry> getViewModel() {
        return new ViewModelProvider(this, new FilesViewModel.FilesViewModelFactory(getActivity().getApplication(), mPath)).get(FilesViewModel.class);
    }

    /**
     * Starts the loader to make sure the data is up-to-date after resuming the fragment (from background)
     */
    @Override
    public void onResume() {
        super.onResume();

        if (null != mFABCallback) {
            mFABCallback.setupFAB(true, new FABListener());
            if (mPath.equals("")) {
                mFABCallback.setupToolbar(getString(R.string.menu_files), false, true, false);
            } else {
                String[] pathSplit = mPath.split("/");
                if (pathSplit.length > 0) {
                    mFABCallback.setupToolbar(pathSplit[pathSplit.length - 1], false, false, false);
                } else {
                    mFABCallback.setupToolbar(mPath, false, false, false);
                }
            }
        }
    }

    /**
     * Called when the observed {@link androidx.lifecycle.LiveData} is changed.
     * <p>
     * This method will update the related adapter and the {@link androidx.swiperefreshlayout.widget.SwipeRefreshLayout} if present.
     *
     * @param model The data observed by the {@link androidx.lifecycle.LiveData}.
     */
    @Override
    protected void onDataReady(List<MPDFileEntry> model) {
        super.onDataReady(model);

        // Reset old scroll position
        if (mLastPosition >= 0) {
            mListView.setSelection(mLastPosition);
            mLastPosition = -1;
        }
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
            mCallback = (FilesCallback) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnArtistSelectedListener");
        }

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mPlaylistCallback = (PlaylistCallback) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnArtistSelectedListener");
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // save the already typed search string (or null if nothing is entered)
        outState.putString(FILESFRAGMENT_SAVED_INSTANCE_SEARCH_STRING, mSearchString);
    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        MenuInflater inflater = getActivity().getMenuInflater();
        int position = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;

        MPDFileEntry file = (MPDFileEntry) mAdapter.getItem(position);

        if (file instanceof MPDTrack) {
            inflater.inflate(R.menu.context_menu_track, menu);
        } else if (file instanceof MPDDirectory) {
            inflater.inflate(R.menu.context_menu_directory, menu);
        } else if (file instanceof MPDPlaylist) {
            inflater.inflate(R.menu.context_menu_playlist, menu);
        }
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

        if (itemId == R.id.action_song_enqueue) {
            MPDQueryHandler.addPath(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_add_directory) {
            MPDQueryHandler.addPath(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_song_enqueue_at_start) {
            MPDQueryHandler.addPathAtStart(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_song_play) {
            MPDQueryHandler.playSong(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_song_play_next) {
            MPDQueryHandler.playSongNext(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_add_to_saved_playlist) {
            // open dialog in order to save the current playlist as a playlist in the mediastore
            ChoosePlaylistDialog choosePlaylistDialog = ChoosePlaylistDialog.newInstance(true);

            choosePlaylistDialog.setCallback(new AddPathToPlaylist((MPDFileEntry) mAdapter.getItem(info.position), getActivity()));
            choosePlaylistDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "ChoosePlaylistDialog");
            return true;
        } else if (itemId == R.id.action_play_playlist) {
            MPDQueryHandler.playPlaylist(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_add_playlist) {
            MPDQueryHandler.loadPlaylist(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_play_directory) {
            MPDQueryHandler.playDirectory(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_show_details) {
            // Open song details dialog
            SongDetailsDialog songDetailsDialog = SongDetailsDialog.createDialog((MPDTrack) mAdapter.getItem(info.position), false);
            songDetailsDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "SongDetails");
            return true;
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
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater menuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.fragment_menu_files, menu);

        // get tint color
        int tintColor = ThemeUtils.getThemeColor(getContext(), R.attr.malp_color_text_accent);

        Drawable drawable = menu.findItem(R.id.action_add_directory).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_add_directory).setIcon(drawable);

        drawable = menu.findItem(R.id.action_search).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_search).setIcon(drawable);

        MPDCapabilities serverCaps = MPDInterface.getGenericInstance().getServerCapabilities();
        if (null != serverCaps) {
            if (serverCaps.hasListFiltering()) {
                menu.findItem(R.id.action_show_albums_from_here).setVisible(true);
            }
        }

        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();

        // Check if a search string is saved from before
        if (mSearchString != null) {
            // Expand the view
            searchView.setIconified(false);
            menu.findItem(R.id.action_search).expandActionView();
            // Set the query string
            searchView.setQuery(mSearchString, false);

            // Notify the adapter
            applyFilter(mSearchString);
        }

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
        final int itemId = item.getItemId();

        if (itemId == R.id.action_add_directory) {
            MPDQueryHandler.addPath(mPath);
            return true;
        } else if (itemId == R.id.action_show_albums_from_here) {
            mCallback.showAlbumsForPath(mPath);
            return true;
        } else if (itemId == R.id.action_update_database_here) {
            MPDQueryHandler.updateDatabase(mPath);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
        mLastPosition = position;

        MPDFileEntry file = (MPDFileEntry) mAdapter.getItem(position);

        if (file instanceof MPDDirectory) {
            mCallback.openPath(file.getPath());
        } else if (file instanceof MPDPlaylist) {
            mPlaylistCallback.openPlaylist(file.getPath());
        } else if (file instanceof MPDTrack) {
            switch (mClickAction) {
                case ACTION_SHOW_DETAILS: {
                    // Open song details dialog
                    SongDetailsDialog songDetailsDialog = SongDetailsDialog.createDialog((MPDTrack) mAdapter.getItem(position), false);
                    songDetailsDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "SongDetails");
                    break;
                }
                case ACTION_ADD_SONG: {
                    MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                    MPDQueryHandler.addPath(track.getPath());
                    break;
                }
                case ACTION_ADD_SONG_AT_START: {
                    MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                    MPDQueryHandler.addPathAtStart(track.getPath());
                    break;
                }
                case ACTION_PLAY_SONG: {
                    MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                    MPDQueryHandler.playSong(track.getPath());
                    break;
                }
                case ACTION_PLAY_SONG_NEXT: {
                    MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                    MPDQueryHandler.playSongNext(track.getPath());
                    break;
                }
            }
        }
    }

    public interface FilesCallback {
        void openPath(String path);

        void showAlbumsForPath(String path);
    }

    private class FABListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            MPDCommandHandler.setRandom(false);
            MPDCommandHandler.setRepeat(false);
            MPDQueryHandler.playDirectory(mPath);
        }
    }

    public void applyFilter(String name) {
        mAdapter.applyFilter(name);
    }

    public void removeFilter() {
        mAdapter.removeFilter();
    }

    private class SearchTextObserver implements SearchView.OnQueryTextListener {

        @Override
        public boolean onQueryTextSubmit(String query) {
            if (!query.isEmpty()) {
                mSearchString = query;
                applyFilter(query);
            } else {
                mSearchString = null;
                removeFilter();
            }
            return false;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            if (!newText.isEmpty()) {
                mSearchString = newText;
                applyFilter(newText);
            } else {
                mSearchString = null;
                removeFilter();
            }

            return true;
        }

    }

}
