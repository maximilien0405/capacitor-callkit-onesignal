package com.bfine.capactior.callkitvoip.androidcall;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.media.MediaPlayer;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.bfine.capactior.callkitvoip.CallKitVoipPlugin;

public class CallNotificationActionReceiver extends BroadcastReceiver {


    Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        this.mContext=context;
        if (intent != null && intent.getExtras() != null) {
        
            String action = intent.getStringExtra("ACTION_TYPE");
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
            String eventName = intent.getStringExtra("eventName");
            Log.d("onReceive!!", roomname);
            if (action != null&& !action.equalsIgnoreCase("")) {
                performClickAction(context, action, callerId, group, message, organization, roomname, source, title, type, duration, media);
            }

            // Close the notification after the click action is performed.
            // Intent iclose = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            // context.sendBroadcast(iclose);
            context.stopService(new Intent(context, CallNotificationService.class));
        }


    }
    
    private void performClickAction(Context context, String action, String callerId,String group, String message,String organization,String roomname, String source,String title,String type, String duration,String media) {
        Log.d("performClickAction","action "+action + "   "+roomname);
        context.stopService(new Intent(context, CallNotificationService.class));
        // Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        // context.sendBroadcast(it);
        end_call(action, callerId, group, message, organization, roomname, source, title, type, duration, media);

    }

    public void end_call(String action, String callerId,String group, String message,String organization,String roomname, String source,String title,String type, String duration,String media) {
        CallKitVoipPlugin instance = CallKitVoipPlugin.getInstance();
        String eventName = "";
        if (action != null&& action.equalsIgnoreCase("RECEIVE_CALL")) {
            eventName = "callAnswered";
        } else if (action != null&& action.equalsIgnoreCase("CANCEL_CALL")) {
            eventName = "callEnded";
        } else if (action != null&& action.equalsIgnoreCase("DIALOG_CALL")) {
            eventName = "callAnswered";
        } else {
            eventName = "callEnded";
        }
        instance.notifyEvent(eventName, callerId, group, message, organization, roomname, source, title, type, duration, media);
    }

    private Boolean checkAppPermissions() {
        return hasReadPermissions() && hasWritePermissions() && hasCameraPermissions() && hasAudioPermissions();
    }

    private boolean hasAudioPermissions() {
        return (ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    }

    private boolean hasReadPermissions() {
        return (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    private boolean hasWritePermissions() {
        return (ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    private boolean hasCameraPermissions() {
        return (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
    }

}