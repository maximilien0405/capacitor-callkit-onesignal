package com.maximilien0405.callkitonesignal;

import android.util.Log;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages call states and configurations in a thread-safe manner
 */
public class CallStateManager {
    private static CallStateManager instance;
    private static final Object lock = new Object();
    
    // Call states
    public enum CallState {
        INCOMING,
        ANSWERED,
        DECLINED,
        ENDED,
        OUTGOING
    }
    
    // Call configuration storage
    private final ConcurrentHashMap<String, CallConfig> callConfigs = new ConcurrentHashMap<>();
    
    // Call state tracking sets
    private final CopyOnWriteArraySet<String> incomingCalls = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<String> answeredCalls = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<String> outgoingCalls = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<String> canceledCalls = new CopyOnWriteArraySet<>();
    
    private CallStateManager() {
        // Private constructor for singleton
    }
    
    public static CallStateManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new CallStateManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Generate a new UUID for a call
     */
    public String generateUUID() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Register a new incoming call
     */
    public void registerIncomingCall(String uuid, String callerId, String username, String media) {
        CallConfig config = new CallConfig(uuid, callerId, username, media);
        callConfigs.put(uuid, config);
        incomingCalls.add(uuid);
        Log.d("CallStateManager", "Registered incoming call: " + uuid + " for callerId: " + callerId);
    }

    /**
     * Register a new incoming call with profile picture
     */
    public void registerIncomingCall(String uuid, String callerId, String username, String media, String profilePictureUrl) {
        CallConfig config = new CallConfig(uuid, callerId, username, media, profilePictureUrl);
        callConfigs.put(uuid, config);
        incomingCalls.add(uuid);
        Log.d("CallStateManager", "Registered incoming call: " + uuid + " for callerId: " + callerId);
    }
    
    /**
     * Register a new outgoing call
     */
    public void registerOutgoingCall(String uuid, String callerId, String username, String media) {
        CallConfig config = new CallConfig(uuid, callerId, username, media);
        callConfigs.put(uuid, config);
        outgoingCalls.add(uuid);
        Log.d("CallStateManager", "Registered outgoing call: " + uuid + " for callerId: " + callerId);
    }
    
    /**
     * Mark a call as answered
     */
    public void markCallAnswered(String uuid) {
        if (uuid != null && callConfigs.containsKey(uuid)) {
            answeredCalls.add(uuid);
            incomingCalls.remove(uuid);
            outgoingCalls.remove(uuid);
            Log.d("CallStateManager", "Call marked as answered: " + uuid);
        }
    }
    
    /**
     * Mark a call as declined
     */
    public void markCallDeclined(String uuid) {
        if (uuid != null && callConfigs.containsKey(uuid)) {
            canceledCalls.add(uuid);
            incomingCalls.remove(uuid);
            Log.d("CallStateManager", "Call marked as declined: " + uuid);
        }
    }
    
    /**
     * End a call and clean up
     */
    public void endCall(String uuid) {
        if (uuid != null) {
            answeredCalls.remove(uuid);
            incomingCalls.remove(uuid);
            outgoingCalls.remove(uuid);
            canceledCalls.remove(uuid);
            callConfigs.remove(uuid);
            Log.d("CallStateManager", "Call ended and cleaned up: " + uuid);
        }
    }
    
    /**
     * Get call configuration by UUID
     */
    public CallConfig getCallConfig(String uuid) {
        return callConfigs.get(uuid);
    }
    
    /**
     * Get call configuration by callerId
     */
    public CallConfig getCallConfigByCallerId(String callerId) {
        for (CallConfig config : callConfigs.values()) {
            if (config.getCallerId().equals(callerId)) {
                return config;
            }
        }
        return null;
    }
    
    /**
     * Get UUID by callerId
     */
    public String getUUIDByCallerId(String callerId) {
        for (String uuid : callConfigs.keySet()) {
            CallConfig config = callConfigs.get(uuid);
            if (config != null && config.getCallerId().equals(callerId)) {
                return uuid;
            }
        }
        return null;
    }
    
    /**
     * Check if call was answered before ending
     */
    public boolean wasCallAnswered(String uuid) {
        return answeredCalls.contains(uuid);
    }
    
    /**
     * Check if call was incoming
     */
    public boolean wasCallIncoming(String uuid) {
        return incomingCalls.contains(uuid);
    }
    
    /**
     * Check if there are any active calls
     */
    public boolean hasActiveCalls() {
        return !callConfigs.isEmpty();
    }
    
    /**
     * Get count of active calls
     */
    public int getActiveCallCount() {
        return callConfigs.size();
    }
    
    /**
     * Clear all call state (use with caution)
     */
    public void clearAllCalls() {
        incomingCalls.clear();
        answeredCalls.clear();
        outgoingCalls.clear();
        canceledCalls.clear();
        callConfigs.clear();
        Log.d("CallStateManager", "All call state cleared");
    }
    
    /**
     * Inner class to store call configuration
     */
    public static class CallConfig {
        private final String uuid;
        private final String callerId;
        private final String username;
        private String media; // Not final so it can be updated
        private final String profilePictureUrl;
        
        public CallConfig(String uuid, String callerId, String username, String media) {
            this.uuid = uuid;
            this.callerId = callerId;
            this.username = username;
            this.media = media;
            this.profilePictureUrl = null;
        }

        public CallConfig(String uuid, String callerId, String username, String media, String profilePictureUrl) {
            this.uuid = uuid;
            this.callerId = callerId;
            this.username = username;
            this.media = media;
            this.profilePictureUrl = profilePictureUrl;
        }
        
        public String getUuid() {
            return uuid;
        }
        
        public String getCallerId() {
            return callerId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getMedia() {
            return media;
        }
        
        public void setMedia(String media) {
            this.media = media;
        }

        public String getProfilePictureUrl() {
            return profilePictureUrl;
        }
    }
}

