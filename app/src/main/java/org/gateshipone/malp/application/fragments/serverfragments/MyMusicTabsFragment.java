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
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.callbacks.FABFragmentCallback;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.SearchViewModel;

public class MyMusicTabsFragment extends Fragment implements TabLayout.OnTabSelectedListener {
    public static final String TAG = MyMusicTabsFragment.class.getSimpleName();

    private static final String MY_MUSIC_REQUESTED_TAB = "ARG_REQUESTED_TAB";
    private static final String MY_MUSIC_REQUESTED_TAG_NAME = "ARG_TAG_NAME";
    private static final String MY_MUSIC_REQUESTED_TAG_VALUE = "ARG_TAG_VALUE";

    private MyMusicPagerAdapter mMyMusicPagerAdapter;

    public enum DEFAULTTAB {
        ARTISTS, ALBUMS
    }

    private FABFragmentCallback mFABCallback = null;

    private ViewPager mViewPager;

    /**
     * Saved search string when user rotates devices
     */
    private String mSearchString;

    private SearchView mSearchView;

    private String mTagName;
    private String mTagValue;

    /**
     * Constant for state saving
     */
    public static final String MYMUSICFRAGMENT_SAVED_INSTANCE_SEARCH_STRING = "MyMusicFragment.SearchString";

    public static MyMusicTabsFragment newInstance(final DEFAULTTAB defaulttab) {
        final Bundle args = new Bundle();
        args.putInt(MY_MUSIC_REQUESTED_TAB, defaulttab.ordinal());

        final MyMusicTabsFragment fragment = new MyMusicTabsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static MyMusicTabsFragment newInstance(final DEFAULTTAB defaulttab, String tagName, String tagValue) {
        final Bundle args = new Bundle();
        args.putInt(MY_MUSIC_REQUESTED_TAB, defaulttab.ordinal());
        args.putString(MY_MUSIC_REQUESTED_TAG_NAME, tagName);
        args.putString(MY_MUSIC_REQUESTED_TAG_VALUE, tagValue);

        final MyMusicTabsFragment fragment = new MyMusicTabsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tab_pager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // create tabs
        final TabLayout tabLayout = view.findViewById(R.id.my_music_tab_layout);
        final Bundle args = getArguments();
        if (args != null) {
            mTagName = args.getString(MY_MUSIC_REQUESTED_TAG_NAME);
            mTagValue = args.getString(MY_MUSIC_REQUESTED_TAG_VALUE);
            Log.e(TAG, "Tag[" + mTagName + "]=" + mTagValue);
        }


        // setup viewpager
        mViewPager = view.findViewById(R.id.my_music_viewpager);
        mMyMusicPagerAdapter = new MyMusicPagerAdapter(getChildFragmentManager(), mTagName, mTagValue);
        mViewPager.setAdapter(mMyMusicPagerAdapter);
        tabLayout.setupWithViewPager(mViewPager, false);
        tabLayout.addOnTabSelectedListener(this);

        // setup icons for tabs
        final ColorStateList tabColors = tabLayout.getTabTextColors();
        final Resources res = getResources();
        Drawable drawable = null;
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            switch (i) {
                case 0:
                    drawable = ResourcesCompat.getDrawable(res, R.drawable.ic_recent_actors_24dp, null);
                    break;
                case 1:
                    drawable = ResourcesCompat.getDrawable(res, R.drawable.ic_album_24dp, null);
                    break;
            }

            if (drawable != null) {
                Drawable icon = DrawableCompat.wrap(drawable);
                DrawableCompat.setTintList(icon, tabColors);
                tabLayout.getTabAt(i).setIcon(icon);
            }
        }
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        // try to resume the saved search string
        if (savedInstanceState != null) {
            mSearchString = savedInstanceState.getString(MYMUSICFRAGMENT_SAVED_INSTANCE_SEARCH_STRING);
        }

        // activate options menu in toolbar
        setHasOptionsMenu(true);

        // set start page


        if (args != null && savedInstanceState == null) {
            final DEFAULTTAB tab = DEFAULTTAB.values()[args.getInt(MY_MUSIC_REQUESTED_TAB)];
            switch (tab) {
                case ARTISTS:
                    mViewPager.setCurrentItem(0, false);
                    break;
                case ALBUMS:
                    mViewPager.setCurrentItem(1, false);
                    break;
            }
        }
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        // set view pager to current page
        mViewPager.setCurrentItem(tab.getPosition());

        final GenericMPDFragment<?> fragment = mMyMusicPagerAdapter.getRegisteredFragment(tab.getPosition());
        if (fragment != null) {
            fragment.getContent();
        }
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {

    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {

    }


    @Override
    public void onResume() {
        super.onResume();

        if (null != mFABCallback) {
            mFABCallback.setupFAB(false, null);
            String title;
            if (mTagValue == null || mTagValue.isEmpty()) {
                title = getString(R.string.app_name);
            } else {
                title = mTagValue;
            }
            mFABCallback.setupToolbar(title, true, true, false);
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
            mFABCallback = (FABFragmentCallback) context;
        } catch (ClassCastException e) {
            mFABCallback = null;
        }

        if (mSearchView != null) {
            mSearchView.setOnQueryTextListener(new SearchTextObserver());
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (mSearchView != null) {
            mSearchView.setOnQueryTextListener(null);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // save the already typed search string (or null if nothing is entered)
        outState.putString(MYMUSICFRAGMENT_SAVED_INSTANCE_SEARCH_STRING, mSearchString);
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
        menuInflater.inflate(R.menu.fragment_menu_library, menu);

        // get tint color
        final int tintColor = ThemeUtils.getThemeColor(requireContext(), R.attr.app_color_on_surface);

        Drawable drawable = menu.findItem(R.id.action_search).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_search).setIcon(drawable);

        mSearchView = (SearchView) menu.findItem(R.id.action_search).getActionView();

        // Check if a search string is saved from before
        if (mSearchString != null) {
            // Expand the view
            mSearchView.setIconified(false);
            menu.findItem(R.id.action_search).expandActionView();
            // Set the query string
            mSearchView.setQuery(mSearchString, true);
        }

        mSearchView.setOnQueryTextListener(new SearchTextObserver());

        super.onCreateOptionsMenu(menu, menuInflater);
    }

    private SearchViewModel getViewModel() {
        return new ViewModelProvider(this).get(SearchViewModel.class);
    }

    private static class MyMusicPagerAdapter extends FragmentStatePagerAdapter {
        static final int NUMBER_OF_PAGES = 2;

        private final SparseArray<GenericMPDFragment<?>> mRegisteredFragments;

        private String mTagName = null;
        private String mTagValue = null;

        public MyMusicPagerAdapter(FragmentManager fm, String tagName, String tagValue) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            mRegisteredFragments = new SparseArray<>();
            mTagName = tagName;
            mTagValue = tagValue;
            Log.e(TAG, "Tag[" + mTagName + "]=" + mTagValue);
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            GenericMPDFragment<?> fragment = (GenericMPDFragment<?>) super.instantiateItem(container, position);
            mRegisteredFragments.put(position, fragment);
            return fragment;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            mRegisteredFragments.remove(position);
            super.destroyItem(container, position, object);
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case 0:
                    return ArtistsFragment.newInstance(new Pair<>(mTagName, mTagValue));
                case 1:
                    return AlbumsFragment.newInstance(null, new Pair<>(mTagName, mTagValue));
                default:
                    // should not happen throw exception
                    throw new IllegalStateException("No fragment defined to return");
            }
        }

        @Override
        public int getCount() {
            // this is done in order to reload all tabs
            return NUMBER_OF_PAGES;
        }

        public GenericMPDFragment<?> getRegisteredFragment(int position) {
            return mRegisteredFragments.get(position);
        }
    }

    private class SearchTextObserver implements SearchView.OnQueryTextListener {

        @Override
        public boolean onQueryTextSubmit(String query) {
            applyFilter(query);

            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            applyFilter(newText);
            return true;
        }

        private void applyFilter(String filter) {
            final SearchViewModel searchViewModel = getViewModel();
            if (filter.isEmpty()) {
                mSearchString = null;
                searchViewModel.clearSearchString();
            } else {
                mSearchString = filter;
                searchViewModel.setSearchString(filter);
            }
        }
    }
}
