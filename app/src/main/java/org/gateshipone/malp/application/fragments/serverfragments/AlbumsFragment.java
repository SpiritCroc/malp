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
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.AlbumsAdapter;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.callbacks.AlbumCallback;
import org.gateshipone.malp.application.listviewitems.AbsImageListViewItem;
import org.gateshipone.malp.application.utils.ScrollSpeedListener;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.AlbumsViewModel;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.application.viewmodels.SearchViewModel;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;

import java.util.List;

public class AlbumsFragment extends GenericMPDFragment<MPDAlbum> implements AdapterView.OnItemClickListener {
    public final static String TAG = AlbumsFragment.class.getSimpleName();

    /**
     * Definition of bundled extras
     */
    private static final String BUNDLE_STRING_EXTRA_PATH = "album_path";

    /**
     * Save the root GridView for later usage.
     */
    private AbsListView mAdapterView;

    /**
     * Save the last position here. Gets reused when the user returns to this view after selecting sme
     * albums.
     */
    private int mLastPosition = -1;

    private String mAlbumsPath;

    private AlbumCallback mAlbumSelectCallback;

    public static AlbumsFragment newInstance(@Nullable final String albumPath) {
        final Bundle args = new Bundle();
        args.putString(BUNDLE_STRING_EXTRA_PATH, albumPath);

        final AlbumsFragment fragment = new AlbumsFragment();
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

        if (useList) {
            // get listview
            mAdapterView = (ListView) view.findViewById(R.id.main_listview);
        } else {
            // get gridview
            mAdapterView = (GridView) view.findViewById(R.id.grid_refresh_gridview);
        }

        mAdapter = new AlbumsAdapter(getActivity(), useList);

        /* Check if an artistname was given in the extras */
        Bundle args = getArguments();
        if (null != args) {
            mAlbumsPath = args.getString(BUNDLE_STRING_EXTRA_PATH);
        }

        mAdapterView.setAdapter(mAdapter);
        mAdapterView.setOnItemClickListener(this);
        mAdapterView.setOnScrollListener(new ScrollSpeedListener(mAdapter));

        // register for context menu
        registerForContextMenu(mAdapterView);

        // get swipe layout
        mSwipeRefreshLayout = view.findViewById(R.id.refresh_layout);
        // set swipe colors
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent),
                ThemeUtils.getThemeColor(getContext(), R.attr.colorPrimary));
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
    GenericViewModel<MPDAlbum> getViewModel() {
        return new ViewModelProvider(this, new AlbumsViewModel.AlbumViewModelFactory(getActivity().getApplication(), null, mAlbumsPath)).get(AlbumsViewModel.class);
    }

    @Override
    public void onResume() {
        super.onResume();

        setupToolbarAndStuff();

        ArtworkManager.getInstance(getContext()).registerOnNewAlbumImageListener((AlbumsAdapter) mAdapter);
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
            mAlbumSelectCallback = (AlbumCallback) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnArtistSelectedListener");
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
    protected void onDataReady(List<MPDAlbum> model) {
        super.onDataReady(model);

        // Reset old scroll position
        if (mLastPosition >= 0) {
            mAdapterView.setSelection(mLastPosition);
            mLastPosition = -1;
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        ArtworkManager.getInstance(getContext()).unregisterOnNewAlbumImageListener((AlbumsAdapter) mAdapter);
    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.context_menu_album, menu);
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

        if (itemId == R.id.fragment_albums_action_enqueue) {
            enqueueAlbum(info.position);
            return true;
        } else if (itemId == R.id.fragment_albums_action_play) {
            playAlbum(info.position);
            return true;
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mLastPosition = position;

        MPDAlbum album = (MPDAlbum) mAdapter.getItem(position);
        Bitmap bitmap = null;

        // Check if correct view type, to be safe
        if (view instanceof AbsImageListViewItem) {
            bitmap = ((AbsImageListViewItem) view).getBitmap();
        }

        // Check if the album already has an artist set. If not use the artist of the fragment
        mAlbumSelectCallback.onAlbumSelected(album, bitmap);
    }

    private void setupToolbarAndStuff() {
        if (null != mFABCallback) {
            if (mAlbumsPath != null && !mAlbumsPath.equals("")) {
                String lastPath = mAlbumsPath;
                String[] pathSplit = mAlbumsPath.split("/");
                if (pathSplit.length > 0) {
                    lastPath = pathSplit[pathSplit.length - 1];
                }
                mFABCallback.setupFAB(true, v -> MPDQueryHandler.playAlbumsInPath(mAlbumsPath));
                mFABCallback.setupToolbar(lastPath, false, false, false);
            } else {
                mFABCallback.setupFAB(false, null);
                mFABCallback.setupToolbar(getString(R.string.app_name), true, true, false);

            }
        }
    }

    /**
     * Enqueues the album selected by the user
     *
     * @param index Index of the selected album
     */
    private void enqueueAlbum(int index) {
        final MPDAlbum album = (MPDAlbum) mAdapter.getItem(index);

        MPDQueryHandler.addArtistAlbum(album.getName(), album.getArtistName(), album.getMBID());
    }

    /**
     * Plays the album selected by the user
     *
     * @param index Index of the selected album
     */
    private void playAlbum(int index) {
        final MPDAlbum album = (MPDAlbum) mAdapter.getItem(index);

        MPDQueryHandler.playArtistAlbum(album.getName(), album.getArtistName(), album.getMBID());
    }

    public void applyFilter(String name) {
        mAdapter.applyFilter(name);
    }

    public void removeFilter() {
        mAdapter.removeFilter();
    }
}
