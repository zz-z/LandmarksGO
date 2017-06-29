package com.example.mosis.landmarksgo.other;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.example.mosis.landmarksgo.MainActivity;
import com.example.mosis.landmarksgo.R;
import com.google.android.gms.maps.model.Marker;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.example.mosis.landmarksgo.MainActivity.friendsMarker;
import static com.example.mosis.landmarksgo.MainActivity.landmarksMarker;

//TODO: Implement LocationListener and upload user location to server
public class BackgroundService extends Service implements LocationListener {
    private static final String TAG = "BackgroundService";
    private static boolean serviceRunning;
    private LocationManager locationManager;
    private String provider;
    public static Double currentLat = null;
    public static Double currentLon = null;

    private int settingsGpsRefreshTime;
    private String loggedUserUid;

    public BackgroundService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "BackgroundService onCreate started");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, false);
        Log.d(TAG,"Location provider is selected: " + provider);

        Log.d(TAG,"BackgroundService onCreate ended");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG,"BackgroundService onStartCommand started");
        settingsGpsRefreshTime = intent.getIntExtra("settingsGpsRefreshTime",1);
        loggedUserUid = intent.getStringExtra("loggedUserUid");

        locationManager.requestLocationUpdates(provider, settingsGpsRefreshTime*1000, 0, this); //Actual time to get a new location is a little big higher- 3s instead of 1, 6s instead 5, 12s instead 10
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            //return 0;
        }else{
            //Location location = locationManager.getLastKnownLocation(provider);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"BackgroundService onDestroy");
        locationManager.removeUpdates(this);
        serviceRunning=false;
        super.onDestroy();
    }

    //>LOCATION
    @Override
    public void onLocationChanged(Location location) {
        if(isInternetAvailable() && isNetworkConnected()){
            double myNewLat, myNewLon;
            currentLat = location.getLatitude();
            currentLon = location.getLongitude();

            myNewLat = currentLat;
            myNewLon = currentLon;

            Log.d(TAG,"New location: " + myNewLat + " " + myNewLon);
            DatabaseReference users = FirebaseDatabase.getInstance().getReference("users");
            users.child(loggedUserUid).child("lat").setValue(myNewLat);
            users.child(loggedUserUid).child("lon").setValue(myNewLon);

            //friendsMarker is from the MainActivity
            for (String key: friendsMarker.keySet()) {
                Marker marker = friendsMarker.get(key);
                Float distanceFromMarker = distFrom((float)myNewLat,(float)myNewLon,(float)marker.getPosition().latitude, (float)marker.getPosition().longitude);
                if(distanceFromMarker < 100000){ //TODO: Change this
                    showNotification("The friend is nearby", marker.getTitle() + " is " + Math.round(distanceFromMarker) + " meters away from you!", false);
                }
                Log.d(TAG,"Iz servisa citam friendsMarker: " + key + " " + marker.getPosition() + " " + marker.getTitle() + " distance: " + distanceFromMarker);
            }

            //landmarksMarker is from the MainActivity
            for (String key: landmarksMarker.keySet()) {
                Marker marker = landmarksMarker.get(key);
                Float distanceFromMarker = distFrom((float)myNewLat,(float)myNewLon,(float)marker.getPosition().latitude, (float)marker.getPosition().longitude);
                if(distanceFromMarker < 100000){ //TODO: Change this
                    showNotification("The landmark is nearby", marker.getTitle() + " is " + Math.round(distanceFromMarker) + " meters away from you!", true);
                }
                Log.d(TAG,"Iz servisa citam landmarksMarker: " + key + " " + marker.getPosition() + " " + marker.getTitle() + " distance: " + distanceFromMarker);
            }
        }
    }

    //Different Id's will show up as different notifications
    private int mNotificationId = 1;
    //Some things we only have to set the first time.
    private boolean firstTime = true;
    int i=0;
    NotificationCompat.Builder mBuilder = null;
    private void showNotification(String title, String text, boolean landmark) {

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if(firstTime){
            firstTime=false;

            mBuilder = new NotificationCompat.Builder(this)
                                .setContentTitle(title)
                                .setDefaults(Notification.DEFAULT_SOUND)
                                .setDefaults(Notification.DEFAULT_VIBRATE)
                                .setOnlyAlertOnce(true);

            // Creates an explicit intent for an Activity in your app
            Intent resultIntent = new Intent(this, MainActivity.class);

            // The stack builder object will contain an artificial back stack for the started Activity.
            // This ensures that navigating backward from the Activity leads out of your application to the Home screen.
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            // Adds the back stack for the Intent (but not the Intent itself)
            stackBuilder.addParentStack(MainActivity.class);
            // Adds the Intent that starts the Activity to the top of the stack
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);
        }
        if(landmark){
            mBuilder
                    .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.icon))
                    .setSmallIcon(R.drawable.icon);
        }else{
            mBuilder
                    .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.person_friend))
                    .setSmallIcon(R.drawable.icon);
        }

        mBuilder.setContentText(text);
        mNotificationManager.notify(mNotificationId, mBuilder.build());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG,"Location provider is enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG,"Location provider is disabled: " + provider);
    }

    public static float distFrom(float lat1, float lng1, float lat2, float lng2) {
        double earthRadius = 6371000; //meters
        double dLat = Math.toRadians(lat2-lat1);
        double dLng = Math.toRadians(lng2-lng1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng/2) * Math.sin(dLng/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        float dist = (float) (earthRadius * c);

        return dist;
    }

    private boolean isNetworkConnected() {
        final ConnectivityManager connectivityManager = ((ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE));
        return connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected();
    }

    public boolean isInternetAvailable() {
            try {
                InetAddress address = InetAddress.getByName("landmarkgo-d1a7c.firebaseio.com");
                return !address.equals("");
            } catch (UnknownHostException e) {
                // Log error
            }
            return false;
    }
}
