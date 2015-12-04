package com.walmartlabs.classwork.rideone.service;

import android.app.Activity;
import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.walmartlabs.classwork.rideone.R;
import com.walmartlabs.classwork.rideone.models.Ride;
import com.walmartlabs.classwork.rideone.models.User;

import java.util.Date;

import static com.walmartlabs.classwork.rideone.models.User.COLUMN_LOGIN_USER_ID;

/**
 * Created by mkrish4 on 11/24/15.
 */
public class StatusCheckService extends IntentService {
    public static boolean currUserAction = false;
    private static User user;
    private static Ride ride;

    public StatusCheckService() {
        super(StatusCheckService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Fetch data passed into the intent on start
        if (user == null) {
            user = ((User) intent.getSerializableExtra("user")).rebuild();
            if (user.getRideId() != null) {
                ride = fetchRide(user.getRideId());
            }
        }
        User newUser = fetchUser(user.getLoginUserId());
        Ride newRide = null;
        if (newUser != null) {
            if (newUser.getRideId() != null) {
                newRide = fetchRide(newUser.getRideId());
                if (ride == null) {
                    ride = newRide;
                }
            }
            String userStatusChanged = getUserStatusChangeString(newUser);
            String userRiderChanged = getRiderChangedString(newRide);
            if (userStatusChanged != null || userRiderChanged != null) {
                // Construct an Intent tying it to the ACTION (arbitrary event namespace)
                String message = null;
                if (userStatusChanged != null) {
                    message = "Your ride request has been " + userStatusChanged;
                } else {
                    message = userRiderChanged;
                }
                broadcast(message, userStatusChanged);
            }
            user = newUser;
            ride = newRide;
        }

    }

    private void broadcast(String message, String userStatus) {
        Intent in = new Intent(getClass().getName());
        // Put extras into the intent as usual
        in.putExtra("resultCode", Activity.RESULT_OK);
        in.putExtra("message", message);
        in.putExtra("userStatus", userStatus);
        // Fire the broadcast with intent packaged
        LocalBroadcastManager.getInstance(this).sendBroadcast(in);
        notification(message);

    }

    private void notification(String message) {
        NotificationCompat.Builder mBuilder =
                (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_car)
                        .setContentTitle("RideOne")
                        .setContentText(message);
        // Sets an ID for the notification
        int mNotificationId = 001;
        // Gets an instance of the NotificationManager service
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Builds the notification and issues it.
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }

    private String getUserStatusChangeString(User newUser) {
        if (currUserAction) {
            currUserAction = false;
            return null;
        }
        Date currDate = user.getUpdatedAt();
        Date newDate = newUser.getUpdatedAt();
        if (isAfter(currDate, newDate)) {
            if (user.getStatus() != User.Status.PASSENGER && newUser.getStatus() == User.Status.PASSENGER) {
                return "confirmed";
            } else if (user.getStatus() != User.Status.NO_RIDE && newUser.getStatus() == User.Status.NO_RIDE) {
                return "denied";
            }
        }
        return null;
    }

    private boolean isAfter(Date currDate, Date newDate) {
        return newDate == null || currDate == null || newDate.after(currDate);
    }

    private String getRiderChangedString(Ride newRide) {
        if (newRide != null && ride != null && isDriver(ride) && ride != newRide && isNotUpdatedByUser(newRide)) {
            Date currDate = ride.getUpdatedAt();
            Date newDate = newRide.getUpdatedAt();
            if (isAfter(currDate, newDate) && !ride.getRiderIds().equals(newRide.getRiderIds())) {
                if (newRide.getRiderIds().size() > ride.getRiderIds().size())
                    return "You have a new ride request";
                else
                    return "One of your riders has dropped out";
            }
        }
        return null;
    }

    private boolean isNotUpdatedByUser(Ride newRide) {
        return newRide == null || newRide.getLastUpdatedBy() == null || !newRide.getLastUpdatedBy().equals(user.getLoginUserId());
    }

    private boolean isDriver(Ride ride) {
        return user.getObjectId().equals(ride.getDriverId());
    }

    private User fetchUser(final String loginUserId) {
        return fetchEntity(loginUserId, COLUMN_LOGIN_USER_ID, User.class);
    }

    private Ride fetchRide(final String rideId) {
        return fetchEntity(rideId, "objectId", Ride.class);
    }

    private <T extends ParseObject> T fetchEntity(final String entityId, String idColName, Class<T> tClass) {
        ParseQuery<T> query = ParseQuery.getQuery(tClass);
        query.whereEqualTo(idColName, entityId);
//        query.whereGreaterThan("updatedAt", lastSyncTime);
        try {
            return query.getFirst();
        } catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), "Failed to get entity for entityId " + entityId, e);
        }
        return null;
    }

}
