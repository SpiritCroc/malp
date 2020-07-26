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

package org.gateshipone.malp.application.artwork;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.android.volley.NetworkResponse;
import com.android.volley.VolleyError;

import org.gateshipone.malp.BuildConfig;
import org.gateshipone.malp.R;
import org.gateshipone.malp.application.artwork.network.ArtworkRequestModel;
import org.gateshipone.malp.application.artwork.network.InsertImageTask;
import org.gateshipone.malp.application.artwork.network.artprovider.ArtProvider;
import org.gateshipone.malp.application.artwork.network.artprovider.HTTPAlbumImageProvider;
import org.gateshipone.malp.application.artwork.network.artprovider.MPDAlbumImageProvider;
import org.gateshipone.malp.application.artwork.network.responses.ImageResponse;
import org.gateshipone.malp.application.artwork.storage.ArtworkDatabaseManager;
import org.gateshipone.malp.application.artwork.storage.ImageNotFoundException;
import org.gateshipone.malp.application.utils.FormatHelper;
import org.gateshipone.malp.application.utils.NetworkUtils;
import org.gateshipone.malp.mpdservice.ConnectionManager;
import org.gateshipone.malp.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseAlbumList;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseArtistList;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseFileList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;
import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class BulkDownloadService extends Service implements InsertImageTask.ImageSavedCallback, ArtProvider.ArtFetchError {
    private static final String TAG = BulkDownloadService.class.getSimpleName();

    private static final int NOTIFICATION_ID = 2;

    private static final String NOTIFICATION_CHANNEL_ID = "BulkDownloader";

    public static final String ACTION_CANCEL = "org.gateshipone.malp.cancel_download";

    public static final String ACTION_START_BULKDOWNLOAD = "org.gateshipone.malp.start_download";

    public static final String BUNDLE_KEY_ARTIST_PROVIDER = "org.gateshipone.malp.artist_provider";

    public static final String BUNDLE_KEY_ALBUM_PROVIDER = "org.gateshipone.malp.album_provider";

    public static final String BUNDLE_KEY_HTTP_COVER_REGEX = "org.gateshipone.malp.http_cover_regex";

    public static final String BUNDLE_KEY_MPD_COVER_ENABLED = "org.gateshipone.malp.mpd_cover_enabled";

    public static final String BUNDLE_KEY_WIFI_ONLY = "org.gateshipone.malp.wifi_only";

    private NotificationManager mNotificationManager;

    private NotificationCompat.Builder mBuilder;

    private ConnectionStateHandler mConnectionHandler;

    private int mSumArtworkRequests;

    private ActionReceiver mBroadcastReceiver;

    private PowerManager.WakeLock mWakelock;

    private ConnectionStateReceiver mConnectionStateChangeReceiver;

    private boolean mWifiOnly;

    final private LinkedList<ArtworkRequestModel> mArtworkRequestQueue = new LinkedList<>();

    private ArtworkManager mArtworkManager;

    private ArtworkDatabaseManager mDatabaseManager;

    private String mAlbumProvider;

    private String mArtistProvider;

    /**
     * Called when the service is created because it is requested by an activity
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        if (null == mConnectionHandler) {
            mConnectionHandler = new ConnectionStateHandler(this, getMainLooper());

            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Registering connection state listener");
            }

            MPDInterface.mInstance.addMPDConnectionStateChangeListener(mConnectionHandler);
        }

        mConnectionStateChangeReceiver = new ConnectionStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mConnectionStateChangeReceiver, filter);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mBroadcastReceiver);
        unregisterReceiver(mConnectionStateChangeReceiver);

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && intent.getAction() != null && intent.getAction().equals(ACTION_START_BULKDOWNLOAD)) {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Starting bulk download in service with thread id: " + Thread.currentThread().getId());
            }

            // reset counter
            mSumArtworkRequests = 0;

            mArtistProvider = getString(R.string.pref_artwork_provider_artist_default);
            mAlbumProvider = getString(R.string.pref_artwork_provider_album_default);
            mWifiOnly = true;

            // read setting from extras
            Bundle extras = intent.getExtras();
            if (extras != null) {
                mArtistProvider = extras.getString(BUNDLE_KEY_ARTIST_PROVIDER, getString(R.string.pref_artwork_provider_artist_default));
                mAlbumProvider = extras.getString(BUNDLE_KEY_ALBUM_PROVIDER, getString(R.string.pref_artwork_provider_album_default));
                mWifiOnly = intent.getBooleanExtra(BUNDLE_KEY_WIFI_ONLY, true);

                final String mHTTPRegex = intent.getStringExtra(BUNDLE_KEY_HTTP_COVER_REGEX);
                if (mHTTPRegex != null && !mHTTPRegex.isEmpty()) {
                    HTTPAlbumImageProvider.getInstance(getApplicationContext()).setRegex(mHTTPRegex);
                }
                MPDAlbumImageProvider.getInstance().setActive(intent.getBooleanExtra(BUNDLE_KEY_MPD_COVER_ENABLED, false));
            }

            if (!NetworkUtils.isDownloadAllowed(this, mWifiOnly)) {
                return START_NOT_STICKY;
            }

            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            mWakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "malp:wakelock:bulkDownloader");

            // FIXME do some timeout checking. e.g. 5 minutes no new image then cancel the process
            mWakelock.acquire();

            mArtworkManager = ArtworkManager.getInstance(getApplicationContext());
            mArtworkManager.initialize(mArtistProvider, mAlbumProvider, mWifiOnly);

            mDatabaseManager = ArtworkDatabaseManager.getInstance(getApplicationContext());

            runAsForeground();

            ConnectionManager.getInstance(getApplicationContext()).reconnectLastServer(this);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onImageSaved(final ArtworkRequestModel artworkRequestModel, final Context applicationContext) {
        mArtworkManager.onImageSaved(artworkRequestModel, applicationContext);

        performNextRequest();
    }

    @Override
    public void fetchJSONException(final ArtworkRequestModel model, final Context context, final JSONException exception) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "JSONException fetching: " + model.getLoggingString());
        }

        ImageResponse imageResponse = new ImageResponse();
        imageResponse.model = model;
        imageResponse.image = null;
        imageResponse.url = null;
        new InsertImageTask(context, this).execute(imageResponse);
    }

    @Override
    public void fetchVolleyError(final ArtworkRequestModel model, final Context context, final VolleyError error) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "VolleyError for request: " + model.getLoggingString());
        }

        if (error != null) {
            NetworkResponse networkResponse = error.networkResponse;
            if (networkResponse != null && networkResponse.statusCode == 503) {
                finishedLoading();
                return;
            }
        }

        ImageResponse imageResponse = new ImageResponse();
        imageResponse.model = model;
        imageResponse.image = null;
        imageResponse.url = null;
        new InsertImageTask(context, this).execute(imageResponse);
    }

    private void runAsForeground() {
        if (mBroadcastReceiver == null) {
            mBroadcastReceiver = new ActionReceiver();

            // Create a filter to only handle certain actions
            IntentFilter intentFilter = new IntentFilter();

            intentFilter.addAction(ACTION_CANCEL);

            registerReceiver(mBroadcastReceiver, intentFilter);
        }

        mBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.downloader_notification_initialize))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.downloader_notification_remaining_images) + ' ' + 0))
                .setProgress(0, 0, false)
                .setSmallIcon(R.drawable.ic_notification_24dp);

        openChannel();

        mBuilder.setOngoing(true);

        // Cancel action
        Intent nextIntent = new Intent(BulkDownloadService.ACTION_CANCEL);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 1, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        androidx.core.app.NotificationCompat.Action cancelAction = new androidx.core.app.NotificationCompat.Action.Builder(R.drawable.ic_cancel_24dp, getResources().getString(R.string.dialog_action_cancel), nextPendingIntent).build();

        mBuilder.addAction(cancelAction);

        Notification notification = mBuilder.build();
        startForeground(NOTIFICATION_ID, notification);
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void createArtworkRequestQueue() {
        mArtworkRequestQueue.clear();

        if (HTTPAlbumImageProvider.getInstance(getApplicationContext()).getActive() || MPDAlbumImageProvider.getInstance().getActive()) {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Try to get all tracks from MPD");
            }

            MPDQueryHandler.getAllTracks(new MPDResponseFileList() {
                @Override
                public void handleTracks(List<MPDFileEntry> trackList, int windowstart, int windowend) {
                    if (BuildConfig.DEBUG) {
                        Log.v(TAG, "Received track count: " + trackList.size());
                    }

                    final HashMap<String, MPDTrack> albumPaths = new HashMap<>();

                    // Get a list of unique album folders
                    for (MPDFileEntry track : trackList) {
                        if (track instanceof MPDTrack) {
                            String dirPath = FormatHelper.getDirectoryFromPath(track.getPath());
                            if (!albumPaths.containsKey(dirPath)) {
                                albumPaths.put(FormatHelper.getDirectoryFromPath(track.getPath()), (MPDTrack) track);
                            }
                        }
                    }


                    if (BuildConfig.DEBUG) {
                        Log.v(TAG, "Unique path count: " + albumPaths.size());
                    }

                    for (MPDTrack track : albumPaths.values()) {
                        mArtworkRequestQueue.add(new ArtworkRequestModel(track));
                    }

                    fetchAllArtists();
                }
            });
        } else {
            fetchAllAlbums();
        }
    }

    private void fetchAllAlbums() {
        if (!mAlbumProvider.equals(getApplicationContext().getString((R.string.pref_artwork_provider_none_key)))) {
            MPDQueryHandler.getAlbums(new MPDResponseAlbumList() {
                @Override
                public void handleAlbums(List<MPDAlbum> albumList) {
                    if (BuildConfig.DEBUG) {
                        Log.v(TAG, "Received " + albumList.size() + " albums for bulk loading");
                    }

                    for (MPDAlbum album : albumList) {
                        mArtworkRequestQueue.add(new ArtworkRequestModel(album));
                    }

                    fetchAllArtists();
                }
            });
        } else {
            fetchAllArtists();
        }
    }

    private void fetchAllArtists() {
        if (!mArtistProvider.equals(getApplicationContext().getString((R.string.pref_artwork_provider_none_key)))) {
            MPDQueryHandler.getArtists(new MPDResponseArtistList() {
                @Override
                public void handleArtists(List<MPDArtist> artistList) {
                    if (BuildConfig.DEBUG) {
                        Log.v(TAG, "Received " + artistList.size() + " artists for bulk loading");
                    }

                    for (MPDArtist artist : artistList) {
                        mArtworkRequestQueue.add(new ArtworkRequestModel(artist));
                    }

                    startBulkDownload();
                }
            });
        } else {
            startBulkDownload();
        }
    }

    private void startBulkDownload() {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Bulkloading started with: " + mArtworkRequestQueue.size());
        }

        mSumArtworkRequests = mArtworkRequestQueue.size();

        mBuilder.setContentTitle(getString(R.string.downloader_notification_remaining_images));

        if (mArtworkRequestQueue.isEmpty()) {
            finishedLoading();
        } else {
            performNextRequest();
        }
    }

    private void performNextRequest() {
        ArtworkRequestModel requestModel;
        while (true) {
            synchronized (mArtworkRequestQueue) {
                updateNotification(mArtworkRequestQueue.size());

                requestModel = mArtworkRequestQueue.pollFirst();
            }

            if (requestModel != null) {
                if (checkRequest(requestModel)) {
                    createRequest(requestModel);
                    return;
                }
            } else {
                finishedLoading();
                return;
            }
        }
    }

    private boolean checkRequest(@NonNull final ArtworkRequestModel requestModel) {
        switch (requestModel.getType()) {
            case ALBUM: {
                try {
                    mDatabaseManager.getAlbumImage(getApplicationContext(), (MPDAlbum) requestModel.getGenericModel());
                } catch (ImageNotFoundException e) {
                    return true;
                }
            }
            break;
            case ARTIST: {
                try {
                    mDatabaseManager.getArtistImage(getApplicationContext(), (MPDArtist) requestModel.getGenericModel());
                } catch (ImageNotFoundException e) {
                    return true;
                }
            }
            break;
            case TRACK: {
                try {
                    mDatabaseManager.getTrackImage(getApplicationContext(), (MPDTrack) requestModel.getGenericModel());
                } catch (ImageNotFoundException e) {
                    return true;
                }
            }
        }
        return false;
    }

    private void createRequest(@NonNull final ArtworkRequestModel requestModel) {
        switch (requestModel.getType()) {
            case ALBUM:
                mArtworkManager.fetchImage((MPDAlbum) requestModel.getGenericModel(), this, this);
                break;
            case ARTIST:
                mArtworkManager.fetchImage((MPDArtist) requestModel.getGenericModel(), this, this);
                break;
            case TRACK:
                mArtworkManager.fetchImage((MPDTrack) requestModel.getGenericModel(), this, this);
                break;
        }
    }

    private void finishedLoading() {
        mArtworkRequestQueue.clear();

        ArtworkManager.getInstance(getApplicationContext()).cancelAllRequests(getApplicationContext());

        mNotificationManager.cancel(NOTIFICATION_ID);
        stopForeground(true);
        MPDInterface.mInstance.removeMPDConnectionStateChangeListener(mConnectionHandler);
        stopSelf();
        if (mWakelock != null && mWakelock.isHeld()) {
            mWakelock.release();
        }
    }

    private void updateNotification(final int pendingRequests) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Remaining requests: " + pendingRequests);
        }

        int finishedRequests = mSumArtworkRequests - pendingRequests;

        if (finishedRequests % 10 == 0) {
            mBuilder.setProgress(mSumArtworkRequests, finishedRequests, false);
            mBuilder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(getString(R.string.downloader_notification_remaining_images) + ' ' + finishedRequests + '/' + mSumArtworkRequests));
            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        }
    }

    private void openChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, this.getResources().getString(R.string.notification_channel_name_bulk_download), android.app.NotificationManager.IMPORTANCE_LOW);
            // Disable lights & vibration
            channel.enableVibration(false);
            channel.enableLights(false);
            channel.setVibrationPattern(null);

            // Allow lockscreen control
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            // Register the channel
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    private static class ConnectionStateHandler extends MPDConnectionStateChangeHandler {
        private final WeakReference<BulkDownloadService> mService;

        private ConnectionStateHandler(BulkDownloadService service, Looper looper) {
            super(looper);
            mService = new WeakReference<>(service);
        }

        @Override
        public void onConnected() {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Connected to mpd host");
            }

            // Disable MPD albumart provider if no support is available on at the server side
            if (MPDAlbumImageProvider.getInstance().getActive() && !MPDInterface.mInstance.getServerCapabilities().hasAlbumArt()) {
                MPDAlbumImageProvider.getInstance().setActive(false);
            }

            mService.get().createArtworkRequestQueue();
        }

        @Override
        public void onDisconnected() {

        }
    }

    private class ActionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Broadcast requested");
            }

            if (ACTION_CANCEL.equals(intent.getAction())) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Cancel requested");
                }

                finishedLoading();
            }
        }
    }

    private class ConnectionStateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!NetworkUtils.isDownloadAllowed(context, mWifiOnly)) {
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "Cancel all downloads because of connection change");
                }

                // Cancel all downloads
                finishedLoading();
            }

        }
    }
}
