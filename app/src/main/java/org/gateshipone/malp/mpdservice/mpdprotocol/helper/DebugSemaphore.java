/*
 *  Copyright (C) 2024 Team Gateship-One
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

package org.gateshipone.malp.mpdservice.mpdprotocol.helper;

import android.util.Log;

import java.util.concurrent.Semaphore;

public class DebugSemaphore extends Semaphore {
    private static final String TAG = DebugSemaphore.class.getSimpleName();
    public DebugSemaphore(int permits) {
        super(permits);
    }

    @Override
    public void acquire() throws InterruptedException {
        super.acquire();
        Log.v(TAG,"Acquired: " + availablePermits());
    }

    @Override
    public boolean tryAcquire() {
        boolean success = super.tryAcquire();
        Log.v(TAG, "tryAcquire: " + success);
        return success;
    }

    @Override
    public void release() {
        super.release();
        Log.v(TAG, "Released: " + availablePermits());
        if (availablePermits() > 1) {
            Log.e(TAG, "DOUBLE FREE OF RELEASE OF LOCK");
            new Exception().printStackTrace();
        }
    }
}
