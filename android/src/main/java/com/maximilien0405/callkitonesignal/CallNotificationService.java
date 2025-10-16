package com.maximilien0405.callkitonesignal;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.maximilien0405.callkitonesignal.R;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class CallNotificationService extends Service {
    private String INCOMING_CHANNEL_ID = "IncomingCallChannel";
    private String INCOMING_CHANNEL_NAME = "Incoming Call Channel";
    private String ONGOING_CHANNEL_ID = "OngoingCallChannel";
    private String ONGOING_CHANNEL_NAME = "Ongoing Call Channel";
    private MediaPlayer mediaPlayer;
    private Vibrator mvibrator;

    Timer timer = new Timer();
    TimerTask task = new TimerTask() {
        @Override
        public void run() {
            getApplicationContext().stopService(new Intent(getApplicationContext(), CallNotificationService.class));
            timer.cancel();
        }
    };


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle data = null;
        String username="", callerId="", media="", uuid="", profilePictureUrl="", notificationText="", notificationSummary="", declineApiUrl="";
        int NOTIFICATION_ID=120;
        Log.d("CallNotificationService", "=== onStartCommand called ===");

        boolean isOutgoing = false;
        boolean isUpdate = false;
        
        if (intent != null && intent.getExtras() != null) {
        
            data = intent.getExtras();
            callerId = intent.getStringExtra("callerId");
            username = intent.getStringExtra("Username");
            media = intent.getStringExtra("media");
            uuid = intent.getStringExtra("uuid");
            profilePictureUrl = intent.getStringExtra("profilePictureUrl");
            notificationText = intent.getStringExtra("notificationText");
            notificationSummary = intent.getStringExtra("notificationSummary");
            declineApiUrl = intent.getStringExtra("declineApiUrl");
            isOutgoing = intent.getBooleanExtra("isOutgoing", false);
            isUpdate = intent.getBooleanExtra("isUpdate", false);
            
            Log.d("CallNotificationService", "Intent extras - callerId: " + callerId + ", username: " + username + ", uuid: " + uuid + ", isOutgoing: " + isOutgoing + ", isUpdate: " + isUpdate);
            
            if (uuid == null || uuid.isEmpty()) {
                uuid = CallStateManager.getInstance().generateUUID();
                Log.d("CallNotificationService", "Generated new UUID: " + uuid);
            }
            
            if (isOutgoing) {
                CallStateManager.getInstance().registerOutgoingCall(uuid, callerId, username, media);
                Log.d("CallNotificationService", "Registered outgoing call - callerId: " + callerId + ", uuid: " + uuid);
            } else if (!isUpdate) {
                CallStateManager.getInstance().registerIncomingCall(uuid, callerId, username, media, profilePictureUrl);
                Log.d("CallNotificationService", "Registered incoming call - callerId: " + callerId + ", uuid: " + uuid);
            }
        }
        try {
            NotificationCompat.Builder notificationBuilder = null;
            
            if (data != null) {
                Uri ringUri = Settings.System.DEFAULT_RINGTONE_URI;
                String notificationContent = "Incoming call request";
                
                Log.d("CallNotificationService", "Creating notification - Title: " + username + ", Content: " + notificationContent + ", isOutgoing: " + isOutgoing + ", isUpdate: " + isUpdate);
                
                if (isOutgoing || isUpdate) {
                    Log.d("CallNotificationService", "Creating ongoing call notification");
                    notificationBuilder = createOngoingCallNotification(username, callerId, media, uuid, NOTIFICATION_ID);
                    // For outgoing calls, we need microphone access
                    if (notificationBuilder != null) {
                        Notification ongoingNotification = notificationBuilder.build();
                        startForegroundService(NOTIFICATION_ID, ongoingNotification, true);
                    }
                } else {
                    Log.d("CallNotificationService", "Creating incoming call notification");
                    notificationBuilder = createIncomingCallNotification(username, callerId, media, uuid, NOTIFICATION_ID, ringUri, profilePictureUrl, notificationText, notificationSummary, declineApiUrl);
                }
                
                if (notificationBuilder == null) {
                    Log.d("CallNotificationService", "Notification builder not immediately available (likely waiting for image)");
                }
            } else {
                Log.w("CallNotificationService", "Intent data is null, cannot create notification");
            }

            // Only start foreground service here if we haven't already started it for outgoing calls
            if (notificationBuilder != null && !isOutgoing && !isUpdate) {
                Notification incomingCallNotification = notificationBuilder.build();
                timer.schedule(task, 30000);
                // For incoming calls, we don't need microphone access initially
                startForegroundService(NOTIFICATION_ID, incomingCallNotification, false);
            } else if (notificationBuilder == null && !isOutgoing && !isUpdate) {
                Log.d("CallNotificationService", "Deferring startForeground until image load completes");
                timer.schedule(task, 30000);
            }

        
        } catch (Exception e) {
            e.printStackTrace();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
        releaseVibration();
    }

    private void startForegroundService(int notificationId, Notification notification, boolean needsMicrophone) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                int serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL;
                
                if (needsMicrophone) {
                    serviceType |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                    Log.d("CallNotificationService", "Starting foreground service with microphone type for outgoing call");
                } else {
                    Log.d("CallNotificationService", "Starting foreground service without microphone type for incoming call");
                }
                
                startForeground(notificationId, notification, serviceType);
            } else {
                startForeground(notificationId, notification);
            }
        } catch (Exception e) {
            Log.e("CallNotificationService", "Failed to start foreground service: " + e.getMessage());
            
            if (needsMicrophone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    Log.d("CallNotificationService", "Retrying without microphone type");
                    startForeground(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
                } catch (Exception ex) {
                    Log.e("CallNotificationService", "Retry without microphone also failed: " + ex.getMessage());
                    try {
                        startForeground(notificationId, notification);
                    } catch (Exception finalEx) {
                        Log.e("CallNotificationService", "Final fallback startForeground also failed: " + finalEx.getMessage());
                    }
                }
            } else {
                try {
                    startForeground(notificationId, notification);
                } catch (Exception ex) {
                    Log.e("CallNotificationService", "Fallback startForeground also failed: " + ex.getMessage());
                }
            }
        }
    }
    
    private boolean hasMicrophonePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return getApplicationContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            }
            return true; // For older Android versions, assume permission is granted
        } catch (Exception e) {
            Log.e("CallNotificationService", "Failed to check microphone permission: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Update the foreground service to include microphone access when call is answered
     */
    public void updateToActiveCall(int notificationId, Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                int serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL;
                
                // Add microphone type if we have permission
                if (hasMicrophonePermission()) {
                    serviceType |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                    Log.d("CallNotificationService", "Updating to active call with microphone access");
                } else {
                    Log.w("CallNotificationService", "Microphone permission not available for active call");
                }
                
                startForeground(notificationId, notification, serviceType);
            } else {
                startForeground(notificationId, notification);
            }
        } catch (Exception e) {
            Log.e("CallNotificationService", "Failed to update to active call: " + e.getMessage());
            // Fallback to regular startForeground
            try {
                startForeground(notificationId, notification);
            } catch (Exception ex) {
                Log.e("CallNotificationService", "Fallback startForeground also failed: " + ex.getMessage());
            }
        }
    }

    public void createChannel() {
        Log.d("createChannel", "called");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationManager notificationManager = Objects.requireNonNull(getApplicationContext().getSystemService(NotificationManager.class));
                Uri ringUri = Settings.System.DEFAULT_RINGTONE_URI;
                
                NotificationChannel incomingChannel = new NotificationChannel(INCOMING_CHANNEL_ID, INCOMING_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                incomingChannel.setDescription("Incoming Call Notifications");
                incomingChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                incomingChannel.setSound(ringUri,
                        new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setLegacyStreamType(AudioManager.STREAM_RING)
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build());
                notificationManager.createNotificationChannel(incomingChannel);
                
                NotificationChannel ongoingChannel = new NotificationChannel(ONGOING_CHANNEL_ID, ONGOING_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
                ongoingChannel.setDescription("Ongoing Call Notifications");
                ongoingChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                ongoingChannel.setSound(null, null);
                notificationManager.createNotificationChannel(ongoingChannel);
                
                Log.d("createChannel", "Both notification channels created successfully");
                
            } catch (Exception e) {
                Log.e("createChannel", "Failed to create notification channels", e);
                e.printStackTrace();
            }
        }
    }

    public void releaseVibration(){
        try {
            if(mvibrator!=null){
                if (mvibrator.hasVibrator()) {
                    mvibrator.cancel();
                }
                mvibrator=null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void releaseMediaPlayer() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                    mediaPlayer.reset();
                    mediaPlayer.release();
                }
                mediaPlayer = null;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadProfilePicture(String imageUrl, ProfilePictureCallback callback) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            callback.onProfilePictureLoaded(null);
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(5000); // 5 second timeout
                connection.setReadTimeout(5000);
                connection.connect();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream input = connection.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    input.close();
                    connection.disconnect();
                    
                    if (bitmap != null) {
                        int size = (int) (64 * getResources().getDisplayMetrics().density);
                        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true);
                        callback.onProfilePictureLoaded(resizedBitmap);
                    } else {
                        callback.onProfilePictureLoaded(null);
                    }
                } else {
                    Log.w("CallNotificationService", "Failed to load profile picture, HTTP: " + connection.getResponseCode());
                    callback.onProfilePictureLoaded(null);
                }
            } catch (Exception e) {
                Log.e("CallNotificationService", "Error loading profile picture: " + e.getMessage());
                callback.onProfilePictureLoaded(null);
            }
        }).start();
    }

    private interface ProfilePictureCallback {
        void onProfilePictureLoaded(Bitmap bitmap);
    }


    private NotificationCompat.Builder createNotificationBuilder(String title, String bigText, String summaryText, Bitmap profilePicture, int notificationId, PendingIntent receiveIntent, PendingIntent cancelIntent,  PendingIntent fullScreenIntent, PendingIntent contentIntent, Uri ringUri) {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, INCOMING_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(bigText)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .addAction(R.drawable.ic_stat_call, getString(R.string.reject), cancelIntent)
                .addAction(R.drawable.ic_stat_call, getString(R.string.answer), receiveIntent)
                .setAutoCancel(true)
                .setSound(ringUri)
                .setFullScreenIntent(fullScreenIntent, true)
                .setOngoing(true)
                .setTimeoutAfter(30000)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(contentIntent)
                .setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(bigText)
                    .setSummaryText(summaryText));

            if (profilePicture != null) {
                builder.setLargeIcon(profilePicture);
                Log.d("CallNotificationService", "Profile picture set in notification");
            }

            return builder;
        } catch (Exception e) {
            Log.e("CallNotificationService", "Failed to create notification builder: " + e.getMessage());
            return null;
        }
    }

    private NotificationCompat.Builder createIncomingCallNotification(String username, String callerId, String media, String uuid, int notificationId, Uri ringUri, String profilePictureUrl, String notificationText, String notificationSummary, String declineApiUrl) {
        try {
            Intent receiveCallAction = getApplicationContext().getPackageManager()
                    .getLaunchIntentForPackage(getApplicationContext().getPackageName());
            if (receiveCallAction != null) {
                receiveCallAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP |
                                        Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                receiveCallAction.putExtra("CALL_EVENT", "callAnswered");
                receiveCallAction.putExtra("callerId", callerId);
                receiveCallAction.putExtra("Username", username);
                receiveCallAction.putExtra("media", media);
                receiveCallAction.putExtra("uuid", uuid);
                receiveCallAction.putExtra("fromNotification", true);
                receiveCallAction.putExtra("ACTION_TYPE", "RECEIVE_CALL");
            }

            Intent cancelCallAction = new Intent(getApplicationContext(), CallNotificationActionReceiver.class);
            cancelCallAction.setAction("ConstantApp.CALL_DECLINE_ACTION");
            cancelCallAction.putExtra("ACTION_TYPE", "CANCEL_CALL");
            cancelCallAction.putExtra("callerId", callerId);
            cancelCallAction.putExtra("Username", username);
            cancelCallAction.putExtra("media", media);
            cancelCallAction.putExtra("uuid", uuid);
            cancelCallAction.putExtra("declineApiUrl", declineApiUrl);

            Log.d("CallNotificationService", "Creating incoming call notification for callerId: " + callerId);
            PendingIntent receiveCallPendingIntent = PendingIntent.getActivity(getApplicationContext(), 1200, receiveCallAction, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent cancelCallPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 1201, cancelCallAction, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            android.content.Intent fullScreenIntent;
            fullScreenIntent = new android.content.Intent(getApplicationContext(), CallNotificationActionReceiver.class);
            fullScreenIntent.setAction("ConstantApp.CALL_FULLSCREEN_ACTION");
            fullScreenIntent.putExtra("ACTION_TYPE", "DIALOG_CALL");
            fullScreenIntent.putExtra("callerId", callerId);
            fullScreenIntent.putExtra("Username", username);
            fullScreenIntent.putExtra("media", media);
            fullScreenIntent.putExtra("uuid", uuid);

            PendingIntent fullScreenPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 1203, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            android.content.Intent contentIntent;
            contentIntent = getApplicationContext().getPackageManager().getLaunchIntentForPackage(getApplicationContext().getPackageName());
            if (contentIntent != null) {
                contentIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                                     android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                     android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP |
                                     android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                contentIntent.putExtra("callerId", callerId);
                contentIntent.putExtra("Username", username);
                contentIntent.putExtra("media", media);
                contentIntent.putExtra("uuid", uuid);
                contentIntent.putExtra("fromNotification", true);
                contentIntent.putExtra("CALL_EVENT", "incoming");
            } else {
                contentIntent = new android.content.Intent(getApplicationContext(), CallNotificationActionReceiver.class);
                contentIntent.setAction("ConstantApp.CALL_FULLSCREEN_ACTION");
                contentIntent.putExtra("ACTION_TYPE", "DIALOG_CALL");
                contentIntent.putExtra("callerId", callerId);
                contentIntent.putExtra("Username", username);
                contentIntent.putExtra("media", media);
                contentIntent.putExtra("uuid", uuid);
            }

            PendingIntent contentPendingIntent;
            if (contentIntent.getAction() == null) {
                contentPendingIntent = PendingIntent.getActivity(getApplicationContext(), 1204, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                contentPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 1204, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }

            String notificationTitle = username != null ? username : "Unknown Caller";
            String bigText = notificationText != null ? notificationText : "Incoming call request";
            String summaryText = notificationSummary != null ? notificationSummary : "Tap to answer or decline";
            if (profilePictureUrl != null && !profilePictureUrl.isEmpty()) {
                Log.d("CallNotificationService", "Loading profile picture from: " + profilePictureUrl);
                loadProfilePicture(profilePictureUrl, new ProfilePictureCallback() {
                    @Override
                    public void onProfilePictureLoaded(Bitmap bitmap) {
                        try {
                            NotificationCompat.Builder builder = createNotificationBuilder(notificationTitle, bigText, summaryText, bitmap, notificationId, receiveCallPendingIntent, cancelCallPendingIntent, fullScreenPendingIntent, contentPendingIntent, ringUri);
                            if (builder == null) {
                                builder = createNotificationBuilder(notificationTitle, bigText, summaryText, null, notificationId, receiveCallPendingIntent, cancelCallPendingIntent, fullScreenPendingIntent, contentPendingIntent, ringUri);
                            }
                            if (builder != null) {
                                Notification notification = builder.build();
                                // For incoming calls, we don't need microphone access initially
                                startForegroundService(notificationId, notification, false);
                                Log.d("CallNotificationService", "Foreground notification started after image load");
                            }
                        } catch (Exception e) {
                            Log.e("CallNotificationService", "Failed to create and start foreground notification: " + e.getMessage());
                            try {
                                NotificationCompat.Builder fallback = createNotificationBuilder(notificationTitle, bigText, summaryText, null, notificationId, receiveCallPendingIntent, cancelCallPendingIntent, fullScreenPendingIntent, contentPendingIntent, ringUri);
                                if (fallback != null) {
                                    // For incoming calls, we don't need microphone access initially
                                    startForegroundService(notificationId, fallback.build(), false);
                                    Log.d("CallNotificationService", "Foreground notification started without image due to error");
                                }
                            } catch (Exception ex) {
                                Log.e("CallNotificationService", "Failed to start fallback foreground notification: " + ex.getMessage());
                            }
                        }
                    }
                });
                return null;
            } else {
                return createNotificationBuilder(notificationTitle, bigText, summaryText, null, notificationId,
                                               receiveCallPendingIntent, cancelCallPendingIntent, fullScreenPendingIntent,
                                               contentPendingIntent, ringUri);
            }
        } catch (Exception e) {
            Log.e("CallNotificationService", "Failed to create incoming call notification", e);
            return null;
        }
    }

    private NotificationCompat.Builder createOngoingCallNotification(String username, String callerId, String media, String uuid, int notificationId) {
        try {
            Intent endCallAction = new Intent(getApplicationContext(), CallNotificationActionReceiver.class);
            endCallAction.setAction("ConstantApp.CALL_END_ACTION");
            endCallAction.putExtra("ACTION_TYPE", "END_CALL");
            endCallAction.putExtra("NOTIFICATION_ID", notificationId);
            endCallAction.putExtra("callerId", callerId);
            endCallAction.putExtra("Username", username);
            endCallAction.putExtra("media", media);
            endCallAction.putExtra("uuid", uuid);

            Log.d("CallNotificationService", "Creating ongoing call notification for callerId: " + callerId);
            PendingIntent endCallPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 1202, endCallAction, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String ongoingTitle = username != null ? username : "Unknown Caller";
            
            return new NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
                .setContentTitle(ongoingTitle)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .addAction(R.drawable.ic_stat_call, getString(R.string.end_call), endCallPendingIntent)
                .setOngoing(true)
                .setAutoCancel(false);
        } catch (Exception e) {
            Log.e("CallNotificationService", "Failed to create ongoing call notification", e);
            return null;
        }
    }
}