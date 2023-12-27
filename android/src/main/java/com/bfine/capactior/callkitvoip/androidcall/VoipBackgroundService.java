package com.bfine.capactior.callkitvoip.androidcall;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.media.MediaPlayer;


public class VoipBackgroundService extends Service
{
    public static boolean isServiceRunningInForeground(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                if (service.foreground) {
                    return true;
                }

            }
        }
        return false;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent)
    {
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d("MySipService", "onStartCommand");
        if(intent.hasExtra("callerId") && intent.hasExtra("organization"))
        {
            String callerId = intent.getStringExtra("callerId");
            String group = intent.getStringExtra("group");
            String message = intent.getStringExtra("message");
            String organization = intent.getStringExtra("organization");
            String roomname = intent.getStringExtra("roomname");
            String source = intent.getStringExtra("source");
            String title = intent.getStringExtra("title");
            String type = intent.getStringExtra("type");
            String duration = intent.getStringExtra("duration");
            String media = intent.getStringExtra("media");
            if(!isServiceRunningInForeground(VoipBackgroundService.this,CallNotificationService.class)) {
                show_call_notification("incoming",callerId, group, message, organization, roomname, source, title, type, duration, media);
                KeyguardManager km = (KeyguardManager) getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
                KeyguardManager.KeyguardLock kl = km.newKeyguardLock("name");
                kl.disableKeyguard();
            }

            
            
        }
        return START_NOT_STICKY;
    }


    public void show_call_notification(String action,String callerId,String group, String message,String organization,String roomname, String source,String title,String type, String duration,String media)
    {
        Log.d("show_call_notification",action);
        Intent serviceIntent = new Intent(this, CallNotificationService.class);
        serviceIntent.setAction(action);
        serviceIntent.putExtra("callerId", callerId);
        serviceIntent.putExtra("group", group);
        serviceIntent.putExtra("message", message);
        serviceIntent.putExtra("organization", organization);
        serviceIntent.putExtra("roomname", roomname);
        serviceIntent.putExtra("source", source);
        serviceIntent.putExtra("title", title);
        serviceIntent.putExtra("type", type);
        serviceIntent.putExtra("duration", duration);
        serviceIntent.putExtra("media", media);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            VoipBackgroundService.this.startForegroundService(serviceIntent);

        } else {
            VoipBackgroundService.this.startService(serviceIntent);
        }
    }

    public void abortCall() {
        getApplicationContext().stopService(new Intent(this, CallNotificationService.class));
    }



    @Override
    public void onCreate()
    {
        super.onCreate();
    }

}