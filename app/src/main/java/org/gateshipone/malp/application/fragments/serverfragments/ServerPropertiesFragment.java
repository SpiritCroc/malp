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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.callbacks.FABFragmentCallback;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDCapabilities;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;

public class ServerPropertiesFragment extends Fragment implements TabLayout.OnTabSelectedListener {
    public static final String TAG = ServerPropertiesFragment.class.getSimpleName();

    private FABFragmentCallback mFABCallback = null;

    private ViewPager mViewPager;

    private boolean mPartitionSupport;

    public static ServerPropertiesFragment newInstance() {
        return new ServerPropertiesFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mPartitionSupport = MPDInterface.getGenericInstance().getServerCapabilities().hasPartitions();
        return inflater.inflate(R.layout.fragment_tab_pager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // create tabs
        TabLayout tabLayout = view.findViewById(R.id.my_music_tab_layout);

        // setup viewpager
        mViewPager = view.findViewById(R.id.my_music_viewpager);
        ServerPropertiesTabAdapter tabAdapter = new ServerPropertiesTabAdapter(getChildFragmentManager(), mPartitionSupport);
        mViewPager.setAdapter(tabAdapter);
        tabLayout.setupWithViewPager(mViewPager, false);
        tabLayout.addOnTabSelectedListener(this);


        // setup icons for tabs
        final ColorStateList tabColors = tabLayout.getTabTextColors();
        final Resources res = getResources();
        Drawable drawable = null;
        String title = "";
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            switch (i) {
                case 0:
                    drawable = ResourcesCompat.getDrawable(res, R.drawable.ic_statistics_black_24dp, null);
                    title = getString(R.string.menu_statistic);
                    break;
                case 1:
                    if (mPartitionSupport) {
                        drawable = ResourcesCompat.getDrawable(res, R.drawable.ic_partitions_24dp, null);
                        title = getString(R.string.menu_partitions);
                    } else {
                        drawable = ResourcesCompat.getDrawable(res, R.drawable.ic_hearing_black_24dp, null);
                        title = getString(R.string.menu_outputs);
                    }
                    break;
                case 2:
                    drawable = ResourcesCompat.getDrawable(res, R.drawable.ic_hearing_black_24dp, null);
                    title = getString(R.string.menu_outputs);
                    break;
            }

            if (drawable != null) {
                Drawable icon = DrawableCompat.wrap(drawable);
                DrawableCompat.setTintList(icon, tabColors);
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                tab.setIcon(icon);
                tab.setText(title);
            }
        }
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        mViewPager.setCurrentItem(0);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (null != mFABCallback) {
            mFABCallback.setupFAB(false, null);
            if (mViewPager.getCurrentItem() == 0) {
                mFABCallback.setupToolbar(getString(R.string.menu_statistic), false, true, false);
            } else if (mPartitionSupport && mViewPager.getCurrentItem() == 1) {
                mFABCallback.setupToolbar(getString(R.string.menu_partitions), false, true, false);
            } else if ((mPartitionSupport && mViewPager.getCurrentItem() == 2) || (!mPartitionSupport && mViewPager.getCurrentItem() == 1)) {
                mFABCallback.setupToolbar(getString(R.string.menu_outputs), false, true, false);
            }
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
    public void onTabSelected(TabLayout.Tab tab) {
        View view = this.getView();

        if (view != null) {
            ViewPager myMusicViewPager = view.findViewById(R.id.my_music_viewpager);
            myMusicViewPager.setCurrentItem(tab.getPosition());

            if (null != mFABCallback) {
                if (tab.getPosition() == 0) {
                    mFABCallback.setupToolbar(getString(R.string.menu_statistic), false, true, false);
                } else if (mPartitionSupport && tab.getPosition() == 1) {
                    mFABCallback.setupToolbar(getString(R.string.menu_partitions), false, true, false);
                } else if ((mPartitionSupport && tab.getPosition() == 2) || (!mPartitionSupport && tab.getPosition() == 1)) {
                    mFABCallback.setupToolbar(getString(R.string.menu_outputs), false, true, false);
                }
            }
        }
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {

    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {

    }

    private static class ServerPropertiesTabAdapter extends FragmentStatePagerAdapter {
        int mPages = 3;

        boolean mPartitions;
        ServerPropertiesTabAdapter(FragmentManager fm, boolean partitions) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            mPages = partitions ? 3 : 2;
            mPartitions = partitions;
        }

        @Override
        public int getCount() {
            return mPages;
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
                if (i == 0) {
                    return ServerStatisticFragment.newInstance();
                } else if (mPartitions && i == 1) {
                    return PartitionsFragment.newInstance();
                } else if ((mPartitions && i == 2) || (!mPartitions && i == 1)) {
                    return OutputsFragment.newInstance();
                } else {
                    throw new IllegalStateException("No fragment defined to return");
                }
        }
    }
}
