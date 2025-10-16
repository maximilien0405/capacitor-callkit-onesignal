package com.maximilien0405.callkitonesignal;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.net.HttpURLConnection;
import java.net.URL;

public class CallNotificationActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("CallNotificationActionReceiver", "=== onReceive called ===");

        if (intent == null || intent.getExtras() == null) {
            Log.w("CallNotificationActionReceiver", "Intent or extras are null");
            return;
        }

        String action = intent.getStringExtra("ACTION_TYPE");
        String callerId = intent.getStringExtra("callerId");
        String username = intent.getStringExtra("Username");
        String media = intent.getStringExtra("media");
        String uuid = intent.getStringExtra("uuid");
        String declineApiUrl = intent.getStringExtra("declineApiUrl");

        Log.d("CallNotificationActionReceiver", "Action: " + action + " UUID: " + uuid);

        if (action == null || action.isEmpty()) return;

        performClickAction(context, action, callerId, username, media, uuid, declineApiUrl);
    }

    private void performClickAction(Context context, String action, String callerId, String username, String media, String uuid, String declineApiUrl) {
        Log.d("CallNotificationActionReceiver", "performClickAction: " + action);

        if (action.equalsIgnoreCase("END_CALL")) {
            context.stopService(new Intent(context, CallNotificationService.class));
        }

        handleCallAction(context, action, callerId, username, media, uuid, declineApiUrl);
    }

    private void handleCallAction(Context context, String action, String callerId, String username, String media, String uuid, String declineApiUrl) {
        CallKitVoipPlugin plugin = CallKitVoipPlugin.getInstance();
        CallStateManager stateManager = CallStateManager.getInstance();

        Log.d("CallNotificationActionReceiver", "Handling action: " + action);
        
        if ("DIALOG_CALL".equalsIgnoreCase(action)) {
            Log.d("CallNotificationActionReceiver", "Full-screen intent triggered - keeping notification alive");
        }
        else if ("CANCEL_CALL".equalsIgnoreCase(action)) {
            Log.d("CallNotificationActionReceiver", "Handling call decline with API call");
            
            if (uuid != null) {
                stateManager.markCallDeclined(uuid);
                stateManager.endCall(uuid);
            }
            
            context.stopService(new Intent(context, CallNotificationService.class));
            android.app.NotificationManager notificationManager = 
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancelAll();
            }
            
            if (declineApiUrl != null && !declineApiUrl.isEmpty()) {
                makeDeclineApiCall(declineApiUrl);
            } else {
                Log.w("CallNotificationActionReceiver", "No decline API URL provided");
            }
        }
        else if ("END_CALL".equalsIgnoreCase(action)) {
            if (uuid != null) stateManager.endCall(uuid);

            CallKitVoipPlugin.addPendingEvent("callEnded", callerId, username, media, uuid);
            
            if (plugin != null) {
                plugin.notifyEvent("callEnded", callerId, username, media, uuid);
                plugin.setInCallState(false);
            }
        }
    }

    private void makeDeclineApiCall(String apiUrl) {
        new Thread(() -> {
            try {
                Log.d("CallNotificationActionReceiver", "Making decline API call to: " + apiUrl);
                
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000); // 10 second timeout
                connection.setReadTimeout(10000);
                
                int responseCode = connection.getResponseCode();
                Log.d("CallNotificationActionReceiver", "API response code: " + responseCode);
                
                if (responseCode >= 200 && responseCode < 300) {
                    Log.d("CallNotificationActionReceiver", "Decline API call successful");
                } else {
                    Log.w("CallNotificationActionReceiver", "Decline API call failed with code: " + responseCode);
                }
                
                connection.disconnect();
                
            } catch (Exception e) {
                Log.e("CallNotificationActionReceiver", "Failed to make decline API call: " + e.getMessage());
            }
        }).start();
    }

}