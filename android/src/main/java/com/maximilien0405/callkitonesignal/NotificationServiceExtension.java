package com.maximilien0405.callkitonesignal;

import androidx.annotation.Keep;
import com.onesignal.notifications.INotificationServiceExtension;
import com.onesignal.notifications.INotificationReceivedEvent;
import com.onesignal.notifications.IDisplayableMutableNotification;
import org.json.JSONObject;
import android.util.Log;
import android.content.Context;
import android.content.Intent;

@Keep
public class NotificationServiceExtension implements INotificationServiceExtension {

    @Override
    public void onNotificationReceived(INotificationReceivedEvent event) {
        try {
            Log.d("NotifSvcExt", "=== OneSignal notification received ===");
            Log.d("NotifSvcExt", "App state: " + (CallKitVoipPlugin.getInstance() != null ? "Plugin loaded" : "Plugin not loaded"));
            
            IDisplayableMutableNotification notif = event.getNotification();
            JSONObject data = notif.getAdditionalData();
            
            Log.d("NotifSvcExt", "Notification data: " + (data != null ? data.toString() : "null"));
            
            notif.setExtender(builder -> {
                builder.setSmallIcon(R.drawable.ic_stat_call);
                Log.d("NotifSvcExt", "Set custom icon: ic_stat_call");
                return builder;
            });
            
            if (data != null) {
                String callerId = data.optString("callerId", null);
                boolean cancelCall = data.optBoolean("cancelCall", false);
                
                Log.d("NotifSvcExt", "CallerId: " + callerId + ", CancelCall: " + cancelCall);
                
                if (callerId != null && !callerId.isEmpty()) {
                    if (cancelCall) {
                        try {
                            Context ctx = getAppContext();
                            if (ctx != null) {
                                Log.d("NotifSvcExt", "Handling call cancellation for callerId: " + callerId);
                                
                                Intent stopIntent = new Intent(ctx, CallNotificationService.class);
                                ctx.stopService(stopIntent);
                                
                                android.app.NotificationManager notificationManager = 
                                    (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                                if (notificationManager != null) {
                                    notificationManager.cancelAll();
                                    Log.d("NotifSvcExt", "All notifications cancelled");
                                }
                                
                                CallStateManager.getInstance().clearAllCalls();
                                Log.d("NotifSvcExt", "All call state cleared");
                                
                                android.media.AudioManager audioManager = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                                if (audioManager != null) {
                                    audioManager.setMode(android.media.AudioManager.MODE_NORMAL);
                                    audioManager.setSpeakerphoneOn(false);
                                    audioManager.abandonAudioFocus(null);
                                    Log.d("NotifSvcExt", "Audio manager reset");
                                }
                                
                                Log.d("NotifSvcExt", "Call cancellation cleanup completed");
                            }
                        } catch (Throwable t) {
                            Log.e("NotifSvcExt", "Failed to handle call cancellation", t);
                        }
                        event.preventDefault();
                        return;
                    }

                    String username = data.optString("Username", "");
                    String media = data.optString("media", "audio");
                    String profilePictureUrl = data.optString("profilePictureUrl", null);
                    String notificationText = data.optString("notificationText", null);
                    String notificationSummary = data.optString("notificationSummary", null);
                    String declineApiUrl = data.optString("declineApiUrl", null);

                    Log.d("NotifSvcExt", "Processing VoIP call - Username: " + username + ", Media: " + media);

                    String uuid = CallStateManager.getInstance().generateUUID();
                    Log.d("NotifSvcExt", "Generated UUID: " + uuid);
                    
                    CallKitVoipPlugin.addPendingEvent("incoming", callerId, username, media, uuid);
                    
                    CallKitVoipPlugin instance = CallKitVoipPlugin.getInstance();
                    if (instance != null) {
                        Log.d("NotifSvcExt", "Plugin instance found, firing incoming event");
                        instance.notifyEvent("incoming", callerId, username, media, uuid);
                    } else {
                        Log.w("NotifSvcExt", "Plugin instance is null - app is closed, will launch app");
                        try {
                            Context ctx = getAppContext();
                            if (ctx != null) {
                                Intent launchIntent = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                                        Intent.FLAG_ACTIVITY_SINGLE_TOP |
                                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                                    launchIntent.putExtra("callerId", callerId);
                                    launchIntent.putExtra("Username", username);
                                    launchIntent.putExtra("media", media);
                                    launchIntent.putExtra("uuid", uuid);
                                    launchIntent.putExtra("fromNotification", true);
                                    launchIntent.putExtra("CALL_EVENT", "incoming");
                                    ctx.startActivity(launchIntent);
                                    Log.d("NotifSvcExt", "App launched to background for incoming call");
                                }
                            }
                        } catch (Throwable t) {
                            Log.e("NotifSvcExt", "Failed to launch app for incoming call", t);
                        }
                    }

                    Context ctx = getAppContext();
                    if (ctx != null) {
                        Log.d("NotifSvcExt", "Starting CallNotificationService");
                        Intent serviceIntent = new Intent(ctx, CallNotificationService.class);
                        serviceIntent.putExtra("callerId", callerId);
                        serviceIntent.putExtra("Username", username);
                        serviceIntent.putExtra("media", media);
                        serviceIntent.putExtra("uuid", uuid);
                        serviceIntent.putExtra("profilePictureUrl", profilePictureUrl);
                        serviceIntent.putExtra("notificationText", notificationText);
                        serviceIntent.putExtra("notificationSummary", notificationSummary);
                        serviceIntent.putExtra("declineApiUrl", declineApiUrl);
                        serviceIntent.setPackage(ctx.getPackageName());
                        
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                ctx.startForegroundService(serviceIntent);
                            } else {
                                ctx.startService(serviceIntent);
                            }
                            Log.d("NotifSvcExt", "CallNotificationService started successfully");
                        } catch (Throwable t) {
                            Log.e("NotifSvcExt", "Failed to start CallNotificationService", t);
                        }
                    } else {
                        Log.w("NotifSvcExt", "Context is null, cannot start CallNotificationService");
                    }

                    Log.d("NotifSvcExt", "Preventing default notification display for VoIP call");
                    event.preventDefault();
                    return;
                }
            }
            
            Log.d("NotifSvcExt", "Allowing notification to display normally");
            
        } catch (Throwable t) {
            Log.e("NotifSvcExt", "Error in onNotificationReceived", t);
        }
    }
    

    private Context getAppContext() {
        try {
            CallKitVoipPlugin plugin = CallKitVoipPlugin.getInstance();
            if (plugin != null) {
                return plugin.getContext();
            }
        } catch (Throwable ignored) {}
        
        try {
            if (CallKitVoipPlugin.staticBridge != null && CallKitVoipPlugin.staticBridge.getContext() != null) {
                return CallKitVoipPlugin.staticBridge.getContext();
            }
        } catch (Throwable ignored) {}
        
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentApplication").invoke(null);
            if (activityThread instanceof Context) {
                return (Context) activityThread;
            }
        } catch (Throwable ignored) {}
        
        return null;
    }
}


