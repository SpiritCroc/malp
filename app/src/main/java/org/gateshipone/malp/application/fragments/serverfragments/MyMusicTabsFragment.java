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
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
    public final static String TAG = MyMusicTabsFragment.class.getSimpleName();

    private final static String MY_MUSIC_REQUESTED_TAB = "ARG_REQUESTED_TAB";

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

    private int mCurrentTab = -1;

    /**
     * Constant for state saving
     */
    public final static String MYMUSICFRAGMENT_SAVED_INSTANCE_SEARCH_STRING = "MyMusicFragment.SearchString";
    public final static String MYMUSICFRAGMENT_SAVED_INSTANCE_CURRENT_TAB = "MyMusicFragment.CurrentTab";

    public static MyMusicTabsFragment newInstance(final DEFAULTTAB defaulttab) {
        final Bundle args = new Bundle();
        args.putInt(MY_MUSIC_REQUESTED_TAB, defaulttab.ordinal());

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

        // Icons
        final ColorStateList tabColors = tabLayout.getTabTextColors();
        final Resources res = getResources();
        Drawable drawable = ResourcesCompat.getDrawable(res, R.drawable.ic_recent_actors_24dp, null);
        if (drawable != null) {
            Drawable icon = DrawableCompat.wrap(drawable);
            DrawableCompat.setTintList(icon, tabColors);
            tabLayout.addTab(tabLayout.newTab().setIcon(icon));
        }
        drawable = ResourcesCompat.getDrawable(res, R.drawable.ic_album_24dp, null);
        if (drawable != null) {
            Drawable icon = DrawableCompat.wrap(drawable);
            DrawableCompat.setTintList(icon, tabColors);
            tabLayout.addTab(tabLayout.newTab().setIcon(icon));
        }

        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        mViewPager = view.findViewById(R.id.my_music_viewpager);
        mMyMusicPagerAdapter = new MyMusicPagerAdapter(getChildFragmentManager());
        mViewPager.setAdapter(mMyMusicPagerAdapter);
        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(this);

        // try to resume the saved search string
        if (savedInstanceState != null) {
            mSearchString = savedInstanceState.getString(MYMUSICFRAGMENT_SAVED_INSTANCE_SEARCH_STRING);
            mCurrentTab = savedInstanceState.getInt(MYMUSICFRAGMENT_SAVED_INSTANCE_CURRENT_TAB);
            mViewPager.setCurrentItem(mCurrentTab, false);
        }

        // activate options menu in toolbar
        setHasOptionsMenu(true);

        // set start page
        final Bundle args = getArguments();

        if (args != null && savedInstanceState == null && mCurrentTab == -1) {
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

        mCurrentTab = tab.getPosition();

        // set view pager to current page
        mViewPager.setCurrentItem(mCurrentTab);

        final GenericMPDFragment<?> fragment = mMyMusicPagerAdapter.getRegisteredFragment(mCurrentTab);
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
            mFABCallback.setupToolbar(getString(R.string.app_name), true, true, false);
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
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // save the already typed search string (or null if nothing is entered)
        outState.putString(MYMUSICFRAGMENT_SAVED_INSTANCE_SEARCH_STRING, mSearchString);
        outState.putInt(MYMUSICFRAGMENT_SAVED_INSTANCE_CURRENT_TAB, mCurrentTab);
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
        final int tintColor = ThemeUtils.getThemeColor(requireContext(), R.attr.malp_color_text_accent);

        Drawable drawable = menu.findItem(R.id.action_search).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_search).setIcon(drawable);

        final SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();

        // Check if a search string is saved from before
        if (mSearchString != null) {
            // Expand the view
            searchView.setIconified(false);
            menu.findItem(R.id.action_search).expandActionView();
            // Set the query string
            searchView.setQuery(mSearchString, true);
        }

        searchView.setOnQueryTextListener(new SearchTextObserver());

        super.onCreateOptionsMenu(menu, menuInflater);
    }

    private static class MyMusicPagerAdapter extends FragmentStatePagerAdapter {
        static final int NUMBER_OF_PAGES = 2;

        private final SparseArray<GenericMPDFragment<?>> mRegisteredFragments;

        public MyMusicPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            mRegisteredFragments = new SparseArray<>();
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
                    return ArtistsFragment.newInstance();
                case 1:
                    return AlbumsFragment.newInstance(null);
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
            final SearchViewModel searchViewModel = new ViewModelProvider(requireActivity()).get(SearchViewModel.class);

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
