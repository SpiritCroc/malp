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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.AlbumsRecyclerViewAdapter;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.callbacks.AlbumCallback;
import org.gateshipone.malp.application.listviewitems.AbsImageListViewItem;
import org.gateshipone.malp.application.listviewitems.GenericViewItemHolder;
import org.gateshipone.malp.application.utils.CoverBitmapLoader;
import org.gateshipone.malp.application.utils.PreferenceHelper;
import org.gateshipone.malp.application.utils.RecyclerScrollSpeedListener;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.AlbumsViewModel;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.application.views.MalpRecyclerView;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;

import java.util.List;

public class ArtistAlbumsFragment extends GenericMPDRecyclerFragment<MPDAlbum, GenericViewItemHolder> implements CoverBitmapLoader.CoverBitmapListener, ArtworkManager.onNewArtistImageListener, MalpRecyclerView.OnItemClickListener {
    public final static String TAG = ArtistAlbumsFragment.class.getSimpleName();

    /**
     * Definition of bundled extras
     */
    private static final String BUNDLE_STRING_EXTRA_ARTIST = "artist";

    private static final String BUNDLE_STRING_EXTRA_BITMAP = "bitmap";

    private MPDArtist mArtist;

    private AlbumCallback mAlbumSelectCallback;

    private boolean mUseArtistSort;

    private Bitmap mBitmap;

    private CoverBitmapLoader mBitmapLoader;

    private MPDAlbum.MPD_ALBUM_SORT_ORDER mSortOrder;

    /**
     * Save the last position here. Gets reused when the user returns to this view after selecting sme
     * albums.
     */
    private int mLastPosition = -1;

    public static ArtistAlbumsFragment newInstance(@NonNull final MPDArtist artist, @Nullable final Bitmap bitmap) {
        final Bundle args = new Bundle();
        args.putParcelable(BUNDLE_STRING_EXTRA_ARTIST, artist);
        args.putParcelable(BUNDLE_STRING_EXTRA_BITMAP, bitmap);

        final ArtistAlbumsFragment fragment = new ArtistAlbumsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.recycler_list_refresh, container, false);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        String libraryView = sharedPref.getString(getString(R.string.pref_library_view_key), getString(R.string.pref_library_view_default));

        final boolean useList = libraryView.equals(getString(R.string.pref_library_view_list_key));

        mRecyclerView = rootView.findViewById(R.id.recycler_view);

        mAdapter = new AlbumsRecyclerViewAdapter(getContext().getApplicationContext(), useList);

        if (useList) {
            mRecyclerView.setAdapter(mAdapter);

            setLinearLayoutManagerAndDecoration();
        } else {
            mRecyclerView.setAdapter(mAdapter);

            setGridLayoutManagerAndDecoration();
        }

        mRecyclerView.addOnScrollListener(new RecyclerScrollSpeedListener(mAdapter));
        mRecyclerView.addOnItemClicklistener(this);

        registerForContextMenu(mRecyclerView);
        mSortOrder = PreferenceHelper.getMPDAlbumSortOrder(sharedPref, getContext());

        mUseArtistSort = sharedPref.getBoolean(getString(R.string.pref_use_artist_sort_key), getResources().getBoolean(R.bool.pref_use_artist_sort_default));

        /* Check if an artistname was given in the extras */
        Bundle args = getArguments();
        mArtist = args.getParcelable(BUNDLE_STRING_EXTRA_ARTIST);
        mBitmap = args.getParcelable(BUNDLE_STRING_EXTRA_BITMAP);

        setHasOptionsMenu(true);

        // get swipe layout
        mSwipeRefreshLayout = rootView.findViewById(R.id.refresh_layout);
        // set swipe colors
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent),
                ThemeUtils.getThemeColor(getContext(), R.attr.colorPrimary));
        // set swipe refresh listener
        mSwipeRefreshLayout.setOnRefreshListener(this::refreshContent);

        mBitmapLoader = new CoverBitmapLoader(getContext(), this);

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);

        return rootView;
    }

    @Override
    GenericViewModel<MPDAlbum> getViewModel() {
        return new ViewModelProvider(this, new AlbumsViewModel.AlbumViewModelFactory(getActivity().getApplication(), mArtist.getArtistName(), null)).get(AlbumsViewModel.class);
    }

    @Override
    public void onResume() {
        super.onResume();

        setupToolbarAndStuff();

        ArtworkManager.getInstance(getContext()).registerOnNewArtistImageListener(this);
        ArtworkManager.getInstance(getContext()).registerOnNewAlbumImageListener((AlbumsRecyclerViewAdapter) mAdapter);
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
            mRecyclerView.getLayoutManager().scrollToPosition(mLastPosition);
            mLastPosition = -1;
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        ArtworkManager.getInstance(getContext()).unregisterOnNewArtistImageListener(this);
        ArtworkManager.getInstance(getContext()).unregisterOnNewAlbumImageListener((AlbumsRecyclerViewAdapter) mAdapter);
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
        MalpRecyclerView.RecyclerViewContextMenuInfo info =
                (MalpRecyclerView.RecyclerViewContextMenuInfo) item.getMenuInfo();

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
        if (null != mArtist && !mArtist.getArtistName().equals("")) {
            menuInflater.inflate(R.menu.fragment_menu_albums, menu);

            // get tint color
            int tintColor = ThemeUtils.getThemeColor(getContext(), R.attr.malp_color_text_accent);

            Drawable drawable = menu.findItem(R.id.action_add_artist).getIcon();
            drawable = DrawableCompat.wrap(drawable);
            DrawableCompat.setTint(drawable, tintColor);
            menu.findItem(R.id.action_add_artist).setIcon(drawable);

            menu.findItem(R.id.action_reset_artwork).setVisible(true);
        }
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

        if (itemId == R.id.action_reset_artwork) {
            setupToolbarAndStuff();
            ArtworkManager.getInstance(getContext()).resetArtistImage(mArtist);
            return true;
        } else if (itemId == R.id.action_add_artist) {
            enqueueArtist();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        // Do not save the bitmap for later use (too big for binder)
        Bundle args = getArguments();
        if (args != null) {
            getArguments().remove(BUNDLE_STRING_EXTRA_BITMAP);
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onItemClick(int position) {
        mLastPosition = position;

        MPDAlbum album = mAdapter.getItem(position);
        Bitmap bitmap = null;

        final View view = mRecyclerView.getChildAt(position);

        // Check if correct view type, to be safe
        if (view instanceof AbsImageListViewItem) {
            bitmap = ((AbsImageListViewItem) view).getBitmap();
        }

        // If artist albums are shown set artist for the album (necessary for old MPD version, which don't
        // support group commands and therefore do not provide artist tags for albums)
        if (mArtist != null && !mArtist.getArtistName().isEmpty() && album.getArtistName().isEmpty()) {
            album.setArtistName(mArtist.getArtistName());
            album.setArtistSortName(mArtist.getArtistName());
        }

        // send the event to the host activity
        mAlbumSelectCallback.onAlbumSelected(album, bitmap);
    }

    @Override
    public void receiveBitmap(final Bitmap bm, final CoverBitmapLoader.IMAGE_TYPE type) {
        if (type == CoverBitmapLoader.IMAGE_TYPE.ARTIST_IMAGE && null != mFABCallback && bm != null) {
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    mFABCallback.setupToolbar(mArtist.getArtistName(), false, false, true);
                    mFABCallback.setupToolbarImage(bm);
                    getArguments().putParcelable(BUNDLE_STRING_EXTRA_BITMAP, bm);
                });
            }
        }
    }

    private void setupToolbarAndStuff() {
        if (null != mFABCallback) {
            if (null != mArtist && !mArtist.getArtistName().isEmpty()) {
                mFABCallback.setupFAB(true, view -> {
                    if (mUseArtistSort) {
                        MPDQueryHandler.playArtistSort(mArtist.getArtistName(), mSortOrder);
                    } else {
                        MPDQueryHandler.playArtist(mArtist.getArtistName(), mSortOrder);
                    }
                });
                if (mBitmap == null) {
                    final View rootView = getView();
                    rootView.post(() -> {
                        final int size = rootView.getWidth();
                        mBitmapLoader.getArtistImage(mArtist, true, size, size);
                    });
                    mFABCallback.setupToolbar(mArtist.getArtistName(), false, false, false);
                } else {
                    // Reuse image
                    mFABCallback.setupToolbar(mArtist.getArtistName(), false, false, true);
                    mFABCallback.setupToolbarImage(mBitmap);
                    final View rootView = getView();
                    rootView.post(() -> {
                        final int size = rootView.getWidth();

                        // Image too small
                        if (mBitmap.getWidth() < size) {
                            mBitmapLoader.getArtistImage(mArtist, true, size, size);
                        }
                    });
                }
            }
        }
    }

    /**
     * Callback for asynchronous image fetching
     *
     * @param artist Artist for which a new image is received
     */
    @Override
    public void newArtistImage(MPDArtist artist) {
        if (artist.equals(mArtist)) {
            setupToolbarAndStuff();
        }
    }

    /**
     * Enqueues the album selected by the user
     *
     * @param index Index of the selected album
     */
    private void enqueueAlbum(int index) {
        MPDAlbum album = (MPDAlbum) mAdapter.getItem(index);

        // If artist albums are shown set artist for the album (necessary for old MPD version, which don't
        // support group commands and therefore do not provide artist tags for albums)
        if (mArtist != null && !mArtist.getArtistName().isEmpty() && album.getArtistName().isEmpty()) {
            album.setArtistName(mArtist.getArtistName());
            album.setArtistSortName(mArtist.getArtistName());
        }

        MPDQueryHandler.addArtistAlbum(album.getName(), album.getArtistName(), album.getMBID());
    }

    /**
     * Plays the album selected by the user
     *
     * @param index Index of the selected album
     */
    private void playAlbum(int index) {
        MPDAlbum album = (MPDAlbum) mAdapter.getItem(index);

        // If artist albums are shown set artist for the album (necessary for old MPD version, which don't
        // support group commands and therefore do not provide artist tags for albums)
        if (mArtist != null && !mArtist.getArtistName().isEmpty() && album.getArtistName().isEmpty()) {
            album.setArtistName(mArtist.getArtistName());
            album.setArtistSortName(mArtist.getArtistName());
        }

        MPDQueryHandler.playArtistAlbum(album.getName(), album.getArtistName(), album.getMBID());
    }

    /**
     * Enqueues the artist that is currently shown (if the fragment is not shown for all albums)
     */
    private void enqueueArtist() {
        MPDQueryHandler.addArtist(mArtist.getArtistName(), mSortOrder);
    }
}
