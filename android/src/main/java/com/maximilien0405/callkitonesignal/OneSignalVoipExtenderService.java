package com.maximilien0405.callkitonesignal;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.onesignal.NotificationExtenderService;
import com.onesignal.OSNotificationReceivedResult;

import org.json.JSONObject;

public class OneSignalVoipExtenderService extends NotificationExtenderService {

    @Override
    protected boolean onNotificationProcessing(OSNotificationReceivedResult receivedResult) {
        try {
            JSONObject data = receivedResult.payload.additionalData;
            if (data == null) {
                return false; // Let OneSignal handle default display
            }

            String action = data.optString("action", "");
            String callerId = data.optString("callerId", null);
            if (callerId == null || callerId.isEmpty()) {
                return false;
            }

            // Handle cancel: stop any ringing service
            if ("cancel".equalsIgnoreCase(action)) {
                try {
                    Intent stopIntent = new Intent(getApplicationContext(), CallNotificationService.class);
                    getApplicationContext().stopService(stopIntent);
                } catch (Exception e) {
                    Log.e("VoipExtender", "Failed to stop CallNotificationService on cancel", e);
                }
                return true;
            }

            String username = data.optString("Username", "");
            String group = data.optString("group", "");
            String message = data.optString("message", "");
            String organization = data.optString("organization", "");
            String roomname = data.optString("roomname", "");
            String source = data.optString("source", "");
            String title = data.optString("title", "Incoming call");
            String type = data.optString("type", "");
            String duration = data.optString("duration", "60");
            String media = data.optString("media", "audio");

            Intent serviceIntent = new Intent(getApplicationContext(), CallNotificationService.class);
            serviceIntent.putExtra("call_type", media);
            serviceIntent.putExtra("callerId", callerId);
            serviceIntent.putExtra("Username", username);
            serviceIntent.putExtra("group", group);
            serviceIntent.putExtra("message", message);
            serviceIntent.putExtra("organization", organization);
            serviceIntent.putExtra("roomname", roomname);
            serviceIntent.putExtra("source", source);
            serviceIntent.putExtra("title", title);
            serviceIntent.putExtra("type", type);
            serviceIntent.putExtra("duration", duration);
            serviceIntent.putExtra("media", media);

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    getApplicationContext().startForegroundService(serviceIntent);
                } else {
                    getApplicationContext().startService(serviceIntent);
                }
            } catch (Exception e) {
                Log.e("VoipExtender", "Failed to start CallNotificationService", e);
            }

            return true; // Prevent OneSignal from displaying its own notification
        } catch (Throwable t) {
            Log.e("VoipExtender", "Error processing notification", t);
            return false;
        }
    }
}
