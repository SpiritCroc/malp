/*
 *  Copyright (C) 2019 Team Gateship-One
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

package org.gateshipone.malp.application.artwork.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Looper;
import android.util.Log;

import org.gateshipone.malp.BuildConfig;
import org.gateshipone.malp.application.utils.FileUtils;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;


public class ArtworkDatabaseManager extends SQLiteOpenHelper {
    private static final String TAG = ArtworkDatabaseManager.class.getSimpleName();
    /**
     * The name of the database
     */
    private static final String DATABASE_NAME = "OdysseyArtworkDB";

    /**
     * The version of the database
     */
    private static final int DATABASE_VERSION = 22;

    private static ArtworkDatabaseManager mInstance;

    private static final String DIRECTORY_ALBUM_IMAGES = "albumArt";

    private static final String DIRECTORY_ARTIST_IMAGES = "artistArt";

    private ArtworkDatabaseManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    private void checkMainThread() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            Log.e(TAG, "Database called from UI thread!");
            new Exception().printStackTrace();
        }
    }


    public static synchronized ArtworkDatabaseManager getInstance(Context context) {
        if (null == mInstance) {
            mInstance = new ArtworkDatabaseManager(context);
        }
        return mInstance;
    }

    /**
     * Creates the database tables if they are not already existing
     *
     * @param db The {@link SQLiteDatabase} instance that will be used to create the tables.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        AlbumArtTable.createTable(db);
        ArtistArtTable.createTable(db);
    }

    @Override
    public synchronized void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        if (newVersion == 22) {
            AlbumArtTable.dropTable(db);
            ArtistArtTable.dropTable(db);
            onCreate(db);
        }
    }

    /**
     * Tries to fetch an image for given track, by album mbid, by album name and artist name or only by album name.
     *
     * @param context The current application context to retrieve the file.
     * @param track   The track to search for
     * @return The path to the raw image file.
     * @throws ImageNotFoundException If the image is not in the database and it was not searched for before.
     */
    public String getTrackImage(final Context context, final MPDTrack track) throws ImageNotFoundException {
        final String mbid = track.getTrackAlbumMBID();
        final String albumName = track.getTrackAlbum();
        final String artistName = track.getTrackAlbumArtist().isEmpty() ? track.getTrackArtist() : track.getTrackAlbumArtist();

        return getAlbumImage(context, mbid, albumName, artistName);
    }

    /**
     * Tries to fetch an image for given album, by mbid, by album name and artist name or only by album name.
     *
     * @param context The current application context to retrieve the file.
     * @param album   The album to search for
     * @return The path to the raw image file.
     * @throws ImageNotFoundException If the image is not in the database and it was not searched for before.
     */
    public String getAlbumImage(final Context context, final MPDAlbum album) throws ImageNotFoundException {
        final String mbid = album.getMBID();
        final String albumName = album.getName();
        final String artistName = album.getArtistName();

        return getAlbumImage(context, mbid, albumName, artistName);
    }

    /**
     * Tries to fetch an image for the given album parameters.
     *
     * @param context    The current application context to retrieve the file.
     * @param mbid       The musicbrainz id of the album or empty.
     * @param albumName  The album name.
     * @param artistName The artist name or empty.
     * @return The path to the raw image file.
     * @throws ImageNotFoundException If the image is not in the database and it was not searched for before.
     */
    private String getAlbumImage(final Context context, final String mbid, final String albumName, final String artistName) throws ImageNotFoundException {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        String selection;
        String selectionArguments[];

        if (!mbid.isEmpty()) {
            selection = AlbumArtTable.COLUMN_ALBUM_MBID + "=?";
            selectionArguments = new String[]{mbid};
        } else if (!artistName.isEmpty()) {
            selection = AlbumArtTable.COLUMN_ALBUM_NAME + "=? AND " + AlbumArtTable.COLUMN_ARTIST_NAME + "=?";
            selectionArguments = new String[]{albumName, artistName};
        } else {
            selection = AlbumArtTable.COLUMN_ALBUM_NAME + "=?";
            selectionArguments = new String[]{albumName};
        }
        final Cursor requestCursor;
        final SQLiteDatabase database = getReadableDatabase();
        synchronized (this) {
            requestCursor = database.query(AlbumArtTable.TABLE_NAME, new String[]{AlbumArtTable.COLUMN_IMAGE_FILE_PATH, AlbumArtTable.COLUMN_IMAGE_NOT_FOUND},
                    selection, selectionArguments, null, null, null);
        }

        // Check if an image was found
        if (requestCursor.moveToFirst()) {
            // If the not_found flag is set then return null here, to indicate that the image is not here but was searched for before.
            if (requestCursor.getInt(requestCursor.getColumnIndex(AlbumArtTable.COLUMN_IMAGE_NOT_FOUND)) == 1) {
                requestCursor.close();
                database.close();
                return null;
            }
            final String artworkFilename = requestCursor.getString(requestCursor.getColumnIndex(AlbumArtTable.COLUMN_IMAGE_FILE_PATH));

            requestCursor.close();
            database.close();
            return FileUtils.getFullArtworkFilePath(context, artworkFilename, DIRECTORY_ALBUM_IMAGES);
        }

        // If we reach this, no entry was found for the given request. Throw an exception
        requestCursor.close();
        database.close();
        throw new ImageNotFoundException();
    }

    /**
     * Tries to fetch an image for the artist with the given id (android artist id).
     *
     * @param context The current application context to retrieve the file.
     * @param artist  The artist to search for
     * @return The path to the raw image file.
     * @throws ImageNotFoundException If the image is not in the database and it was not searched for before.
     */
    public String getArtistImage(final Context context, final MPDArtist artist) throws ImageNotFoundException {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        final String artistName = artist.getArtistName();
        String mbid = "";

        final int mbidCount = artist.getMBIDCount();
        if (mbidCount > 0) {
            final StringBuilder mbids = new StringBuilder();
            for (int i = 0; i < artist.getMBIDCount(); i++) {
                mbids.append(artist.getMBID(i));
            }
            mbid = mbids.toString();
        }

        String selection;
        String selectionArguments[];

        if (!mbid.isEmpty()) {
            selection = ArtistArtTable.COLUMN_ARTIST_MBID + "=?";
            selectionArguments = new String[]{mbid};
        } else {
            selection = ArtistArtTable.COLUMN_ARTIST_NAME + "=?";
            selectionArguments = new String[]{artistName};
        }

        final SQLiteDatabase database = getReadableDatabase();
        final Cursor requestCursor;
        synchronized (this) {
            requestCursor = database.query(ArtistArtTable.TABLE_NAME, new String[]{ArtistArtTable.COLUMN_ARTIST_MBID, ArtistArtTable.COLUMN_IMAGE_FILE_PATH, ArtistArtTable.COLUMN_IMAGE_NOT_FOUND},
                    selection, selectionArguments, null, null, null);
        }

        // Check if an image was found
        if (requestCursor.moveToFirst()) {
            // If the not_found flag is set then return null here, to indicate that the image is not here but was searched for before.
            if (requestCursor.getInt(requestCursor.getColumnIndex(ArtistArtTable.COLUMN_IMAGE_NOT_FOUND)) == 1) {
                requestCursor.close();
                database.close();
                return null;
            }

            // get the filename for the image
            final String artworkFilename = requestCursor.getString(requestCursor.getColumnIndex(ArtistArtTable.COLUMN_IMAGE_FILE_PATH));

            requestCursor.close();
            database.close();
            return FileUtils.getFullArtworkFilePath(context, artworkFilename, DIRECTORY_ARTIST_IMAGES);
        }

        // If we reach this, no entry was found for the given request. Throw an exception
        requestCursor.close();
        database.close();
        throw new ImageNotFoundException();
    }

    /**
     * Inserts the given byte[] image to the artists table.
     *
     * @param artist Artist for the associated image byte[].
     * @param image  byte[] containing the raw image that was downloaded. This can be null in which case
     *               the database entry will have the not_found flag set.
     */
    public void insertArtistImage(final Context context, final MPDArtist artist, final byte[] image) {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        final StringBuilder mbids = new StringBuilder();
        for (int i = 0; i < artist.getMBIDCount(); i++) {
            mbids.append(artist.getMBID(i));
        }

        final String artistName = artist.getArtistName();

        String artworkFilename = null;
        if (image != null) {
            try {
                artworkFilename = FileUtils.createSHA256HashForString(mbids.toString(), artistName) + ".jpg";
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                return;
            }

            try {
                FileUtils.saveArtworkFile(context, artworkFilename, DIRECTORY_ARTIST_IMAGES, image);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        final ContentValues values = new ContentValues();
        values.put(ArtistArtTable.COLUMN_ARTIST_MBID, mbids.toString());
        values.put(ArtistArtTable.COLUMN_ARTIST_NAME, artist.getArtistName());
        values.put(ArtistArtTable.COLUMN_IMAGE_FILE_PATH, artworkFilename);

        // If null was given as byte[] set the not_found flag for this entry.
        values.put(ArtistArtTable.COLUMN_IMAGE_NOT_FOUND, image == null ? 1 : 0);

        final SQLiteDatabase database = getWritableDatabase();
        synchronized (this) {
            database.replace(ArtistArtTable.TABLE_NAME, "", values);
            database.close();
        }
    }

    /**
     * Inserts the given byte[] image to the albums table.
     *
     * @param album Album for the associated image byte[].
     * @param image byte[] containing the raw image that was downloaded. This can be null in which case
     *              the database entry will have the not_found flag set.
     */
    public void insertAlbumImage(final Context context, final MPDAlbum album, final byte[] image) {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        final String albumMBID = album.getMBID();
        final String albumName = album.getName();
        final String albumArtistName = album.getArtistName();

        String artworkFilename = null;
        if (image != null) {
            try {
                artworkFilename = FileUtils.createSHA256HashForString(albumMBID, albumName, albumArtistName) + ".jpg";
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                return;
            }

            try {
                FileUtils.saveArtworkFile(context, artworkFilename, DIRECTORY_ALBUM_IMAGES, image);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        final ContentValues values = new ContentValues();
        values.put(AlbumArtTable.COLUMN_ALBUM_MBID, album.getMBID());
        values.put(AlbumArtTable.COLUMN_ALBUM_NAME, album.getName());
        values.put(AlbumArtTable.COLUMN_ARTIST_NAME, album.getArtistName());
        values.put(AlbumArtTable.COLUMN_IMAGE_FILE_PATH, artworkFilename);

        // If null was given as byte[] set the not_found flag for this entry.
        values.put(AlbumArtTable.COLUMN_IMAGE_NOT_FOUND, image == null ? 1 : 0);
        final SQLiteDatabase database = getWritableDatabase();
        synchronized (this) {
            database.replace(AlbumArtTable.TABLE_NAME, "", values);
            database.close();
        }
    }

    /**
     * Removes all lines from the artists table
     */
    public synchronized void clearArtistImages(final Context context) {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        SQLiteDatabase database = getWritableDatabase();

        database.delete(ArtistArtTable.TABLE_NAME, null, null);

        database.close();

        FileUtils.removeArtworkDirectory(context, DIRECTORY_ARTIST_IMAGES);
    }

    /**
     * Removes all lines from the albums table
     */
    public synchronized void clearAlbumImages(final Context context) {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        SQLiteDatabase database = getWritableDatabase();

        database.delete(AlbumArtTable.TABLE_NAME, null, null);

        database.close();

        FileUtils.removeArtworkDirectory(context, DIRECTORY_ALBUM_IMAGES);
    }

    public synchronized void clearBlockedArtistImages() {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        SQLiteDatabase database = getWritableDatabase();

        String where = ArtistArtTable.COLUMN_IMAGE_NOT_FOUND + "=?";
        String whereArgs[] = {"1"};

        database.delete(ArtistArtTable.TABLE_NAME, where, whereArgs);

        database.close();
    }

    public synchronized void clearBlockedAlbumImages() {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        SQLiteDatabase database = getWritableDatabase();

        String where = AlbumArtTable.COLUMN_IMAGE_NOT_FOUND + "=?";
        String whereArgs[] = {"1"};

        database.delete(AlbumArtTable.TABLE_NAME, where, whereArgs);

        database.close();
    }

    public void removeArtistImage(final Context context, final MPDArtist artist) {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        String where;
        String whereArgs[];

        if (artist.getMBIDCount() == 0) {
            where = ArtistArtTable.COLUMN_ARTIST_NAME + "=?";
            whereArgs = new String[]{artist.getArtistName()};
        } else {
            where = ArtistArtTable.COLUMN_ARTIST_MBID + "=? OR " + ArtistArtTable.COLUMN_ARTIST_NAME + "=?";
            whereArgs = new String[]{artist.getMBID(0), artist.getArtistName()};
        }
        final SQLiteDatabase database = getWritableDatabase();
        final Cursor requestCursor;
        synchronized (this) {
            requestCursor = database.query(ArtistArtTable.TABLE_NAME, new String[]{ArtistArtTable.COLUMN_IMAGE_FILE_PATH},
                    where, whereArgs, null, null, null);
            database.delete(ArtistArtTable.TABLE_NAME, where, whereArgs);
        }

        if (requestCursor.moveToFirst()) {
            final String artworkFilename = requestCursor.getString(requestCursor.getColumnIndex(ArtistArtTable.COLUMN_IMAGE_FILE_PATH));
            FileUtils.removeArtworkFile(context, artworkFilename, DIRECTORY_ARTIST_IMAGES);
        }

        requestCursor.close();
        database.close();
    }

    public void removeAlbumImage(final Context context, final MPDAlbum album) {
        if (BuildConfig.DEBUG) {
            checkMainThread();
        }
        String where;
        String whereArgs[];

        // Check if a MBID is present or not
        if (album.getMBID().isEmpty()) {
            where = "(" + AlbumArtTable.COLUMN_ALBUM_NAME + "=? AND " + AlbumArtTable.COLUMN_ARTIST_NAME + "=? ) ";
            whereArgs = new String[]{album.getName(), album.getArtistName()};
        } else {
            where = AlbumArtTable.COLUMN_ALBUM_MBID + "=? OR (" + AlbumArtTable.COLUMN_ALBUM_NAME + "=? AND " + AlbumArtTable.COLUMN_ARTIST_NAME + "=? ) ";
            whereArgs = new String[]{album.getMBID(), album.getName(), album.getArtistName()};
        }
        final SQLiteDatabase database = getWritableDatabase();
        final Cursor requestCursor;

        synchronized (this) {
            requestCursor = database.query(AlbumArtTable.TABLE_NAME, new String[]{AlbumArtTable.COLUMN_IMAGE_FILE_PATH},
                    where, whereArgs, null, null, null);
            database.delete(AlbumArtTable.TABLE_NAME, where, whereArgs);
        }

        if (requestCursor.moveToFirst()) {
            final String artworkFilename = requestCursor.getString(requestCursor.getColumnIndex(AlbumArtTable.COLUMN_IMAGE_FILE_PATH));
            FileUtils.removeArtworkFile(context, artworkFilename, DIRECTORY_ALBUM_IMAGES);
        }

        requestCursor.close();
        database.close();
    }

    private enum AsyncOperationType {
        CLEAR_BLOCKED_ARTISTS,
        CLEAR_BLOCKED_ALBUMS,
        CLEAR_ARTIST_IMAGES,
        CLEAR_ALBUM_IMAGES,
    }

    public static class AsyncOperationTask extends AsyncTask<AsyncOperationType, Object, Object> {
        private Context mContext;

        private AsyncOperationTask(Context context) {
            mContext = context;
        }

        @Override
        protected Object doInBackground(AsyncOperationType... objects) {
            if (objects.length > 0) {
                switch (objects[0]) {
                    case CLEAR_BLOCKED_ARTISTS:
                        getInstance(mContext).clearBlockedArtistImages();
                        break;
                    case CLEAR_BLOCKED_ALBUMS:
                        getInstance(mContext).clearBlockedArtistImages();
                        break;
                    case CLEAR_ARTIST_IMAGES:
                        getInstance(mContext).clearArtistImages(mContext);
                        break;
                    case CLEAR_ALBUM_IMAGES:
                        getInstance(mContext).clearArtistImages(mContext);
                        break;
                    default:
                        break;
                }
            }
            return null;
        }

        static public void clearBlockedArtists(Context context) {
            AsyncOperationType type = AsyncOperationType.CLEAR_BLOCKED_ARTISTS;
            new AsyncOperationTask(context).execute(type);
        }

        static public void clearBlockedAlbums(Context context) {
            AsyncOperationType type = AsyncOperationType.CLEAR_BLOCKED_ALBUMS;
            new AsyncOperationTask(context).execute(type);
        }

        static public void clearArtists(Context context) {
            AsyncOperationType type = AsyncOperationType.CLEAR_ARTIST_IMAGES;
            new AsyncOperationTask(context).execute(type);
        }

        static public void clearAlbums(Context context) {
            AsyncOperationType type = AsyncOperationType.CLEAR_ALBUM_IMAGES;
            new AsyncOperationTask(context).execute(type);
        }
    }
}
