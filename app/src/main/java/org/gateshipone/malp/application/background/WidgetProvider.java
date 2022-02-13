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

package org.gateshipone.malp.application.background;


import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.activities.MainActivity;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.utils.CoverBitmapLoader;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.lang.ref.WeakReference;

public class WidgetProvider extends AppWidgetProvider {
    private static final String TAG = WidgetProvider.class.getSimpleName();
    /**
     * Statically save the last track and status and image. This allows loading the cover image
     * only if it really changed.
     */
    private static MPDTrack mLastTrack;
    private static MPDCurrentStatus mLastStatus;
    private static Bitmap mLastCover = null;


    /**
     * Intent IDs used for controlling action.
     */
    private static final int INTENT_OPENGUI = 0;
    private static final int INTENT_PREVIOUS = 1;
    private static final int INTENT_PLAYPAUSE = 2;
    private static final int INTENT_STOP = 3;
    private static final int INTENT_NEXT = 4;

    private static final int PENDING_INTENT_CANCEL_CURRENT_FLAG =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_CANCEL_CURRENT;

    private static final int PENDING_INTENT_UPDATE_CURRENT_FLAG =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;

    /**
     * Update the widgets
     *
     * @param context          Context for updateing
     * @param appWidgetManager appWidgetManager to update the widgets
     * @param appWidgetIds     Widget IDs that need updating.
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);


        // Call the updateWidget method which will update all instances.
        updateWidget(context);
    }

    /**
     * Called when widgets are removed
     *
     * @param context context used for deletion
     */
    public void onDisabled(Context context) {
        super.onDisabled(context);
        mLastTrack = null;
        mLastStatus = null;
    }


    /**
     * Updates the widget by creating a new RemoteViews object and setting all the intents for the
     * buttons and the TextViews correctly.
     *
     * @param context Context to use for updating the widgets contents
     */
    private void updateWidget(Context context) {
        boolean nowPlaying = false;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_malp_big);
        // Check if valid object
        if (mLastStatus != null && mLastTrack != null) {
            // Check if track title is set, otherwise use track name, otherwise path
            String title = mLastTrack.getVisibleTitle();
            views.setTextViewText(R.id.widget_big_trackName, title);

            views.setTextViewText(R.id.widget_big_ArtistAlbum, mLastTrack.getSubLine(context));

            if (mLastCover != null) {
                // Use the saved image
                views.setImageViewBitmap(R.id.widget_big_cover, mLastCover);
            } else {
                // Reuse the image from last calls if the album is the same
                views.setImageViewResource(R.id.widget_big_cover, R.drawable.icon_outline_256dp);
            }


            // Set the images of the play button dependent on the playback state.
            MPDCurrentStatus.MPD_PLAYBACK_STATE playState = mLastStatus.getPlaybackState();

            if (playState == MPDCurrentStatus.MPD_PLAYBACK_STATE.MPD_PLAYING) {
                // Show pause icon
                nowPlaying = true;
                views.setImageViewResource(R.id.widget_big_play, R.drawable.ic_pause_48dp);
            } else {
                // Show play icon
                views.setImageViewResource(R.id.widget_big_play, R.drawable.ic_play_arrow_48dp);
            }


            // set button actions
            // Main action
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            if (nowPlaying) {
                // add intent only if playing is active
                mainIntent.putExtra(MainActivity.MAINACTIVITY_INTENT_EXTRA_REQUESTEDVIEW, MainActivity.REQUESTEDVIEW.NOWPLAYING.ordinal());
            }
            PendingIntent mainPendingIntent = PendingIntent.getActivity(context, INTENT_OPENGUI, mainIntent, PENDING_INTENT_UPDATE_CURRENT_FLAG);
            views.setOnClickPendingIntent(R.id.widget_big_cover, mainPendingIntent);

            // Play/Pause action
            Intent playPauseIntent = new Intent(context, BackgroundService.class);
            playPauseIntent.setAction(BackgroundService.ACTION_PLAY);
            PendingIntent playPausePendingIntent = PendingIntent.getService(context, INTENT_PLAYPAUSE, playPauseIntent, PENDING_INTENT_CANCEL_CURRENT_FLAG);
            views.setOnClickPendingIntent(R.id.widget_big_play, playPausePendingIntent);

            // Stop action
            Intent stopIntent = new Intent(context, BackgroundService.class);
            stopIntent.setAction(BackgroundService.ACTION_STOP);
            PendingIntent stopPendingIntent = PendingIntent.getService(context, INTENT_STOP, stopIntent, PENDING_INTENT_CANCEL_CURRENT_FLAG);
            views.setOnClickPendingIntent(R.id.widget_big_stop, stopPendingIntent);

            // Previous song action
            Intent prevIntent = new Intent(context, BackgroundService.class);
            prevIntent.setAction(BackgroundService.ACTION_PREVIOUS);
            PendingIntent prevPendingIntent = PendingIntent.getService(context, INTENT_PREVIOUS, prevIntent, PENDING_INTENT_CANCEL_CURRENT_FLAG);
            views.setOnClickPendingIntent(R.id.widget_big_previous, prevPendingIntent);

            // Next song action
            Intent nextIntent = new Intent(context, BackgroundService.class);
            nextIntent.setAction(BackgroundService.ACTION_NEXT);
            PendingIntent nextPendingIntent = PendingIntent.getService(context, INTENT_NEXT, nextIntent, PENDING_INTENT_CANCEL_CURRENT_FLAG);
            views.setOnClickPendingIntent(R.id.widget_big_next, nextPendingIntent);
            views.setViewVisibility(R.id.widget_control_layout, View.VISIBLE);
            views.setViewVisibility(R.id.widget_disconnected_layout, View.GONE);
        } else {
            // connect action
            Intent connectIntent = new Intent(context, BackgroundService.class);
            connectIntent.setAction(BackgroundService.ACTION_CONNECT);
            PendingIntent connectPendingIntent;

            // As of Android O it is not allowed to spawn background services
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connectPendingIntent = PendingIntent.getForegroundService(context, INTENT_NEXT, connectIntent, PENDING_INTENT_CANCEL_CURRENT_FLAG);
            } else {
                connectPendingIntent = PendingIntent.getService(context, INTENT_NEXT, connectIntent, PENDING_INTENT_CANCEL_CURRENT_FLAG);
            }

            views.setOnClickPendingIntent(R.id.widget_connect_button, connectPendingIntent);

            // Main action
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            PendingIntent mainPendingIntent = PendingIntent.getActivity(context, INTENT_OPENGUI, mainIntent, PENDING_INTENT_UPDATE_CURRENT_FLAG);
            views.setOnClickPendingIntent(R.id.widget_big_cover, mainPendingIntent);

            // Set application icon outline as a image again
            views.setImageViewResource(R.id.widget_big_cover, R.drawable.icon_outline_256dp);

            views.setViewVisibility(R.id.widget_control_layout, View.GONE);
            views.setViewVisibility(R.id.widget_disconnected_layout, View.VISIBLE);
        }

        AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), views);
    }

    /**
     * This is the broadcast receiver for NowPlayingInformation objects sent by the PBS
     *
     * @param context Context used for this receiver
     * @param intent  Intent containing the NowPlayingInformation as a payload.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        final String action = intent.getAction();

        // Type checks
        switch (action) {
            case BackgroundService.ACTION_STATUS_CHANGED:

                // Extract the payload from the intent
                MPDCurrentStatus status = intent.getParcelableExtra(BackgroundService.INTENT_EXTRA_STATUS);

                // Check if a payload was sent
                if (null != status) {
                    // Save the information for later usage (when the asynchronous bitmap loader finishes)
                    mLastStatus = status;
                }
                break;
            case BackgroundService.ACTION_TRACK_CHANGED:

                // Extract the payload from the intent
                MPDTrack track = intent.getParcelableExtra(BackgroundService.INTENT_EXTRA_TRACK);

                // Check if a payload was sent
                if (null != track) {
                    boolean newImage = false;
                    // Check if new album is played and remove image if it is.
                    if (mLastTrack == null || !track.equalsStringTag(MPDTrack.StringTagTypes.ALBUM, mLastTrack) || !track.equalsStringTag(MPDTrack.StringTagTypes.ALBUM_MBID, mLastTrack)) {
                        mLastCover = null;
                        newImage = true;

                    }

                    // Save the information for later usage (when the asynchronous bitmap loader finishes)
                    mLastTrack = track;

                    if (newImage) {
                        CoverBitmapLoader coverLoader = new CoverBitmapLoader(context, new CoverReceiver(context, this));
                        coverLoader.getImage(track, false, -1, -1);
                    }
                }
                break;
            case BackgroundService.ACTION_SERVER_DISCONNECTED:
                mLastStatus = null;
                mLastTrack = null;
                break;
            case ArtworkManager.ACTION_NEW_ARTWORK_READY:
                // Check if the new artwork matches the currently playing track. If so reload artwork
                if (mLastTrack != null && mLastTrack.getStringTag(MPDTrack.StringTagTypes.ALBUM).equals(intent.getStringExtra(ArtworkManager.INTENT_EXTRA_KEY_ALBUM_NAME))) {
                    // Got new artwork
                    mLastCover = null;
                    CoverBitmapLoader coverLoader = new CoverBitmapLoader(context, new CoverReceiver(context, this));
                    coverLoader.getImage(mLastTrack, false, -1, -1);
                }
                break;
        }
        // Refresh the widget with the new information
        updateWidget(context);
    }

    private static class CoverReceiver implements CoverBitmapLoader.CoverBitmapListener {
        WeakReference<Context> mContext;
        WeakReference<WidgetProvider> mProvider;

        public CoverReceiver(Context context, WidgetProvider provider) {
            mContext = new WeakReference<>(context);
            mProvider = new WeakReference<>(provider);
        }

        /**
         * Sets the global image variable for this track and recall the update method to refresh
         * the views.
         *
         * @param bm Bitmap fetched for the currently running track.
         */
        @Override
        public void receiveBitmap(Bitmap bm, final CoverBitmapLoader.IMAGE_TYPE type) {
            final Context context = mContext.get();
            final WidgetProvider provider = mProvider.get();

            if (provider != null && context != null) {
                // Check if a valid image was found.
                if (type == CoverBitmapLoader.IMAGE_TYPE.ALBUM_IMAGE && bm != null) {
                    // Set the globally used variable
                    mLastCover = bm;

                    // Call the update method to refresh the view
                    provider.updateWidget(context);
                }
            }
        }
    }
}
