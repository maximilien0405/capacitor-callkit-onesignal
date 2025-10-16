package com.maximilien0405.callkitonesignal;

import com.maximilien0405.callkitonesignal.CallNotificationService;
import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginHandle;
import com.getcapacitor.PluginMethod;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "CallkitOnesignal")

public class CallKitVoipPlugin extends Plugin {
    public static Bridge staticBridge = null;
    public Context context;
    private AudioManager audioManager;
    private AudioRouteChangeReceiver audioRouteChangeReceiver;
    private AudioFocusChangeListener audioFocusChangeListener;
    private final java.util.List<JSObject> pendingEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
    private boolean isAppFullyLoaded = false;
    private boolean isInCall = false;

    @Override
    public void load(){
        Log.d("CallKitVoipPlugin", "Plugin load() called");
        staticBridge = this.bridge;
        context = this.getActivity().getApplicationContext();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioFocusChangeListener = new AudioFocusChangeListener();
        setupAudioRouteChangeListener();
        Log.d("CallKitVoipPlugin", "Plugin loaded successfully");
    }
    
    public void onDestroy() {
        if (audioRouteChangeReceiver != null) {
            try {
                context.unregisterReceiver(audioRouteChangeReceiver);
            } catch (Exception e) {
                Log.e("CallKitVoipPlugin", "Failed to unregister audio route change receiver: " + e.getMessage());
            }
        }
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        Log.d("CallKitVoipPlugin", "App resumed - isInCall: " + isInCall);
        
        if (isInCall && audioManager != null) {
            int result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            );
            
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                Log.d("CallKitVoipPlugin", "Audio focus regained on app resume");
            } else {
                Log.w("CallKitVoipPlugin", "Failed to regain audio focus on app resume");
            }
        }
    }
    
    @Override
    protected void handleOnPause() {
        super.handleOnPause();
        Log.d("CallKitVoipPlugin", "App paused - isInCall: " + isInCall);
    }

    @Override
    protected void handleOnNewIntent(Intent data) {
        super.handleOnNewIntent(data);
        Log.d("CallKitVoipPlugin", "handleOnNewIntent called with data: " + (data != null ? data.toString() : "null"));
        if (data == null) {
            return;
        }
        
        String actionType = data.getStringExtra("ACTION_TYPE");
        Log.d("CallKitVoipPlugin", "Action type from intent: " + actionType);
        if ("RECEIVE_CALL".equals(actionType)) {
            String callerId = data.getStringExtra("callerId");
            String username = data.getStringExtra("Username");
            String media = data.getStringExtra("media");
            String uuid = data.getStringExtra("uuid");
            
            if (uuid != null) {
                CallStateManager.getInstance().markCallAnswered(uuid);
                isInCall = true;
            }

            try {
                Intent serviceIntent = new Intent(context, CallNotificationService.class);
                context.stopService(serviceIntent);
                android.app.NotificationManager notificationManager = 
                    (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    notificationManager.cancelAll();
                }
            } catch (Exception e) {
                Log.w("CallKitVoipPlugin", "Failed to stop service/cancel notifications on answer: " + e.getMessage());
            }
            
            JSObject payload = new JSObject();
            if (callerId != null) payload.put("connectionId", callerId);
            if (username != null) payload.put("username", username);
            if (callerId != null) payload.put("callerId", callerId);
            if (media != null) payload.put("media", media);
            if (uuid != null) payload.put("uuid", uuid);
            
            notifyEventWithQueue("callAnswered", payload);
            return;
        }
        
        String event = data.getStringExtra("CALL_EVENT");
        if (event == null || event.isEmpty()) {
            return;
        }
        String callerId = data.getStringExtra("callerId");
        String username = data.getStringExtra("username");
        String media = data.getStringExtra("media");
        String uuid = data.getStringExtra("uuid");
        JSObject payload = new JSObject();
        if (callerId != null) payload.put("connectionId", callerId);
        if (username != null) payload.put("username", username);
        if (callerId != null) payload.put("callerId", callerId);
        if (media != null) payload.put("media", media);
        if (uuid != null) payload.put("uuid", uuid);
        notifyEventWithQueue(event, payload);
    }
    
    public void notifyEvent(String eventName, String callerId, String username, String media, String uuid){
       JSObject data = new JSObject();
       data.put("connectionId", callerId);
       data.put("username", username);
       data.put("callerId", callerId);
       data.put("media", media);
       if (uuid != null) { 
           data.put("uuid", uuid); 
       }
       notifyEventWithQueue(eventName, data);
    }
    
    private void notifyEventWithQueue(String eventName, JSObject data) {
        if (isAppFullyLoaded) {
            Log.d("CallKitVoipPlugin", "App is fully loaded - sending " + eventName + " event immediately");
            notifyListeners(eventName, data);
        } else {
            Log.d("CallKitVoipPlugin", "App is not fully loaded - storing " + eventName + " event as pending");
            data.put("eventName", eventName);
            
            boolean isDuplicate = false;
            for (JSObject existing : pendingEvents) {
                if (eventName.equals(existing.getString("eventName")) &&
                    data.getString("connectionId", "").equals(existing.getString("connectionId", ""))) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (!isDuplicate) {
                pendingEvents.add(data);
            }
        }
    }

    public static CallKitVoipPlugin getInstance() {
        if (staticBridge == null || staticBridge.getWebView() == null)
            return  null;

        PluginHandle handler = staticBridge.getPlugin("CallkitOnesignal");
        return handler == null ? null : (CallKitVoipPlugin) handler.getInstance();
    }
    
    public void stopCallServices() {
        Log.d("stopCallServices","Called");
        Intent serviceIntent = new Intent(context, CallNotificationService.class);
        context.stopService(serviceIntent);
    }
    
    public Context getContext() {
        return context;
    }
    
    public android.app.Activity getCurrentActivity() {
        return super.getActivity();
    }
    
    public void setInCallState(boolean inCall) {
        this.isInCall = inCall;
        Log.d("CallKitVoipPlugin", "Call state set to: " + inCall);
    }

    @PluginMethod
    public void setAudioOutput(PluginCall call) {
        String route = call.getString("route");
        if (route == null) {
            call.reject("route is required");
            return;
        }

        try {
            android.media.AudioManager audioManager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            
            int result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                android.media.AudioManager.STREAM_VOICE_CALL,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            );
            
            if (result != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w("CallKitVoipPlugin", "Audio focus not granted, but continuing with audio output change");
            }
            
            audioManager.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
            
            switch (route.toLowerCase()) {
                case "speaker":
                    audioManager.setSpeakerphoneOn(true);
                    Log.d("CallKitVoipPlugin", "Audio output set to speaker");
                    break;
                case "earpiece":
                    audioManager.setSpeakerphoneOn(false);
                    Log.d("CallKitVoipPlugin", "Audio output set to earpiece");
                    break;
                default:
                    call.reject("Invalid audio route. Must be 'speaker' or 'earpiece'");
                    return;
            }
            
            call.resolve();
        } catch (Exception e) {
            Log.e("CallKitVoipPlugin", "Failed to set audio output: " + e.getMessage());
            call.reject("Failed to set audio output: " + e.getMessage());
        }
    }
    
    private void setupAudioRouteChangeListener() {
        audioRouteChangeReceiver = new AudioRouteChangeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        
        try {
            context.registerReceiver(audioRouteChangeReceiver, filter);
            Log.d("CallKitVoipPlugin", "Audio route change listener registered");
        } catch (Exception e) {
            Log.e("CallKitVoipPlugin", "Failed to register audio route change listener: " + e.getMessage());
        }
    }
    
    private String getCurrentAudioRoute() {
        if (audioManager.isSpeakerphoneOn()) {
            return "speaker";
        } else if (audioManager.isBluetoothScoOn() || audioManager.isBluetoothA2dpOn()) {
            return "bluetooth";
        } else if (audioManager.isWiredHeadsetOn()) {
            return "headphones";
        } else {
            return "earpiece";
        }
    }
    
    private class AudioRouteChangeReceiver extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action) ||
                AudioManager.ACTION_HEADSET_PLUG.equals(action) ||
                Intent.ACTION_HEADSET_PLUG.equals(action)) {
                
                String currentRoute = getCurrentAudioRoute();
                
                Log.d("CallKitVoipPlugin", "Audio route changed: " + currentRoute);
                
                JSObject data = new JSObject();
                data.put("route", currentRoute);
                notifyListeners("audioRouteChanged", data);
            }
        }
    }

    @PluginMethod
    public void isAppInForeground(PluginCall call) {
        try {
            boolean isInForeground = isAppInForeground();
            JSObject result = new JSObject();
            result.put("value", isInForeground);
            call.resolve(result);
        } catch (Exception e) {
            Log.e("CallKitVoipPlugin", "Failed to check app foreground state: " + e.getMessage());
            call.reject("Failed to check app foreground state: " + e.getMessage());
        }
    }
    

    private boolean isAppInForeground() {
        try {
            android.app.ActivityManager activityManager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            
            if (activityManager == null) {
                return false;
            }
            
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> runningProcesses = activityManager.getRunningAppProcesses();
            
            if (runningProcesses == null) {
                return false;
            }
            
            String currentAppProcessName = context.getPackageName();
            
            for (android.app.ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                if (currentAppProcessName.equals(processInfo.processName)) {
                    return processInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
                }
            }
            
            return false;
        } catch (Exception e) {
            Log.e("CallKitVoipPlugin", "Error checking app foreground state: " + e.getMessage());
            return false;
        }
    }
  
    
    public static void addPendingEvent(String eventName, String callerId, String username, String media, String uuid) {
        JSObject data = new JSObject();
        data.put("connectionId", callerId);
        data.put("username", username);
        data.put("callerId", callerId);
        data.put("media", media);
        if (uuid != null) { 
            data.put("uuid", uuid); 
        }
        data.put("eventName", eventName);
        
        CallKitVoipPlugin instance = getInstance();
        if (instance != null) {
            instance.pendingEvents.add(data);
            Log.d("CallKitVoipPlugin", "Added pending event: " + eventName + " for callerId: " + callerId);
        } else {
            Log.d("CallKitVoipPlugin", "Plugin instance null, event will be queued when plugin loads: " + eventName);
        }
    }

    @PluginMethod
    public void setAppFullyLoaded(PluginCall call) {
        try {
            isAppFullyLoaded = true;
            Log.d("CallKitVoipPlugin", "App marked as fully loaded - events will now be sent immediately");
            call.resolve();
        } catch (Exception e) {
            Log.e("CallKitVoipPlugin", "Failed to set app fully loaded: " + e.getMessage());
            call.reject("Failed to set app fully loaded: " + e.getMessage());
        }
    }

    @PluginMethod
    public void wasLaunchedFromVoIP(PluginCall call) {
        try {
            boolean launched = !pendingEvents.isEmpty();
            
            if (launched) {
                Log.d("CallKitVoipPlugin", "App was launched/resumed from VoIP call - pendingEvents: " + pendingEvents.size());
            } else {
                Log.d("CallKitVoipPlugin", "App was not launched from VoIP call");
            }
            
            JSObject result = new JSObject();
            result.put("value", launched);
            call.resolve(result);
        } catch (Exception e) {
            Log.e("CallKitVoipPlugin", "Failed to check VoIP launch state: " + e.getMessage());
            call.reject("Failed to check VoIP launch state: " + e.getMessage());
        }
    }

    @PluginMethod
    public void replayPendingEvents(PluginCall call) {
        try {
            if (pendingEvents.isEmpty()) {
                Log.d("CallKitVoipPlugin", "No pending events to replay");
                call.resolve();
                return;
            }
            
            java.util.List<JSObject> eventsToReplay = new java.util.ArrayList<>(pendingEvents);
            pendingEvents.clear();
            
            Log.d("CallKitVoipPlugin", "Replaying " + eventsToReplay.size() + " pending events");
            
            for (JSObject event : eventsToReplay) {
                String eventName = event.getString("eventName");
                if (eventName != null) {
                    JSObject eventData = new JSObject();
                    for (java.util.Iterator<String> it = event.keys(); it.hasNext(); ) {
                        String key = it.next();
                        if (!"eventName".equals(key)) {
                            eventData.put(key, event.get(key));
                        }
                    }
                    
                    Log.d("CallKitVoipPlugin", "Replaying event: " + eventName);
                    notifyListeners(eventName, eventData);
                }
            }
            
            call.resolve();
        } catch (Exception e) {
            Log.e("CallKitVoipPlugin", "Failed to replay pending events: " + e.getMessage());
            call.reject("Failed to replay pending events: " + e.getMessage());
        }
    }

    @PluginMethod
    public void endCall(PluginCall call) {
        try {
            Intent serviceIntent = new Intent(context, CallNotificationService.class);
            context.stopService(serviceIntent);
            
            android.app.NotificationManager notificationManager = 
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancelAll();
            }
            
            CallStateManager.getInstance().clearAllCalls();
            isInCall = false;
            
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                audioManager.setSpeakerphoneOn(false);
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
            
            Log.d("CallKitVoipPlugin", "All calls ended and state cleaned up");
            call.resolve();
        } catch (Exception e) {
            Log.e("CallKitVoipPlugin", "Failed to end call: " + e.getMessage());
            call.reject("Failed to end call: " + e.getMessage());
        }
    }

    @PluginMethod
    public void startOutgoingCall(PluginCall call) {
        try {
            String callerId = call.getString("callerId");
            String username = call.getString("username");
            String media = call.getString("media");
            
            if (callerId == null || username == null || media == null) {
                call.reject("callerId, username, and media are required");
                return;
            }
            
            String uuid = CallStateManager.getInstance().generateUUID();
            CallStateManager.getInstance().registerOutgoingCall(uuid, callerId, username, media);
            isInCall = true;
            
            Intent serviceIntent = new Intent(context, CallNotificationService.class);
            serviceIntent.putExtra("callerId", callerId);
            serviceIntent.putExtra("Username", username);
            serviceIntent.putExtra("media", media);
            serviceIntent.putExtra("uuid", uuid);
            serviceIntent.putExtra("isOutgoing", true);
            
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (Exception e) {
                Log.e("CallKitVoipPlugin", "Failed to start outgoing call service: " + e.getMessage());
                call.reject("Failed to start outgoing call: " + e.getMessage());
                return;
            }
            
            Log.d("CallKitVoipPlugin", "Outgoing call started: " + uuid);
            call.resolve();
        } catch (Exception e) {
            Log.e("CallKitVoipPlugin", "Failed to start outgoing call: " + e.getMessage());
            call.reject("Failed to start outgoing call: " + e.getMessage());
        }
    }

    @PluginMethod
    public void prepareAudioSessionForCall(PluginCall call) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w("CallKitVoipPlugin", "RECORD_AUDIO permission not granted");
                    call.reject("RECORD_AUDIO permission is required for calls");
                    return;
                }
            }
            
            if (audioManager != null) {
                int result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN
                );
                
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    Log.d("CallKitVoipPlugin", "Audio session prepared for call");
                } else {
                    Log.w("CallKitVoipPlugin", "Audio focus not granted");
                }
            }
            call.resolve();
        } catch (Exception e) {
            Log.e("CallKitVoipPlugin", "Failed to prepare audio session: " + e.getMessage());
            call.reject("Failed to prepare audio session: " + e.getMessage());
        }
    }


    @PluginMethod
    public void updateCallUI(PluginCall call) {
        try {
            String uuidString = call.getString("uuid");
            String media = call.getString("media");
            
            if (uuidString == null || media == null) {
                call.reject("uuid and media are required");
                return;
            }
            
            CallStateManager stateManager = CallStateManager.getInstance();
            CallStateManager.CallConfig config = stateManager.getCallConfig(uuidString);
            
            if (config == null) {
                call.reject("No call found with uuid: " + uuidString);
                return;
            }
            
            config.setMedia(media);
            Intent updateIntent = new Intent(context, CallNotificationService.class);
            updateIntent.putExtra("callerId", config.getCallerId());
            updateIntent.putExtra("Username", config.getUsername());
            updateIntent.putExtra("media", media);
            updateIntent.putExtra("uuid", uuidString);
            updateIntent.putExtra("isUpdate", true);
            
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(updateIntent);
                } else {
                    context.startService(updateIntent);
                }
            } catch (Exception e) {
                Log.w("CallKitVoipPlugin", "Failed to update notification: " + e.getMessage());
            }
            
            Log.d("CallKitVoipPlugin", "Call UI updated for uuid: " + uuidString + " to media: " + media);
            call.resolve();
        } catch (Exception e) {
            Log.e("CallKitVoipPlugin", "Failed to update call UI: " + e.getMessage());
            call.reject("Failed to update call UI: " + e.getMessage());
        }
    }
    
    private class AudioFocusChangeListener implements AudioManager.OnAudioFocusChangeListener {
        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d("CallKitVoipPlugin", "Audio focus changed: " + focusChange);
            
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.d("CallKitVoipPlugin", "Audio focus gained");
                    if (isInCall && audioManager != null) {
                        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    }
                    break;
                    
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.w("CallKitVoipPlugin", "Audio focus lost permanently");
                    if (isInCall) {
                        if (audioManager != null) {
                            int result = audioManager.requestAudioFocus(
                                audioFocusChangeListener,
                                AudioManager.STREAM_VOICE_CALL,
                                AudioManager.AUDIOFOCUS_GAIN
                            );
                            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                                Log.d("CallKitVoipPlugin", "Audio focus regained after loss");
                            } else {
                                Log.w("CallKitVoipPlugin", "Failed to regain audio focus after loss");
                            }
                        }
                    }
                    break;
                    
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.w("CallKitVoipPlugin", "Audio focus lost temporarily");
                    break;
                    
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.w("CallKitVoipPlugin", "Audio focus lost temporarily, can duck");
                    break;
            }
        }
    }
}
