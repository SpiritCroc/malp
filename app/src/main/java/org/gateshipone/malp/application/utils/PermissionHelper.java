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

package org.gateshipone.malp.application.utils;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.StringRes;

import org.gateshipone.malp.R;

public class PermissionHelper {

    /**
     * Result code for the notifications permission request.
     */
    public static final int MY_PERMISSIONS_REQUEST_NOTIFICATIONS = 0;

    /**
     * Permission to show notifications. Empty if android version is below 13.
     */
    public static final String NOTIFICATION_PERMISSION = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? Manifest.permission.POST_NOTIFICATIONS : "";

    /**
     * Resource id for the notifications permission rationale text.
     */
    @StringRes
    public static final int NOTIFICATION_PERMISSION_RATIONALE_TEXT = R.string.permission_request_notifications_snackbar_explanation;

    /**
     * Method to check if odyssey is allowed to show notifications (besides the media notification).
     *
     * @param context The application context for the permission check.
     * @return True if notifications are allowed or device is running below API level 33.
     */
    public static boolean areNotificationsAllowed(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return notificationManager.areNotificationsEnabled();
        } else {
            return true;
        }
    }
}
