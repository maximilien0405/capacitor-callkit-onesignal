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
import android.os.Build;
import android.util.Log;
import androidx.annotation.RequiresApi;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "CallkitOnesignal")

public class CallKitVoipPlugin extends Plugin {
    public  static  Bridge staticBridge = null;

    public Context context;

    @Override
    public void load(){
        staticBridge   = this.bridge;
        context = this.getActivity().getApplicationContext();
    }

    @Override
    protected void handleOnNewIntent(Intent data) {
        super.handleOnNewIntent(data);
    }

    @PluginMethod
    public void register(PluginCall call) {
        Log.d("CallKitVoip","register");
        Logger.debug("CallKit: Subscribed");
        call.resolve();
    }
    
    public void notifyEvent(String eventName, String callerId, String username, String group, String message, String organization, String roomname, String source, String title, String type, String duration, String media, String uuid){
       JSObject data = new JSObject();
       data.put("connectionId", callerId);
       data.put("username", username);
       data.put("callerId", callerId);
       data.put("group", group);
       data.put("message", message);
       data.put("organization", organization);
       data.put("roomname", roomname);
       data.put("source", source);
       data.put("title", title);
       data.put("type", type);
       data.put("duration", duration);
       data.put("media", media);
       if (uuid != null) {
           data.put("uuid", uuid);
       }
       notifyListeners(eventName, data);
    }

    public static CallKitVoipPlugin getInstance() {
        if (staticBridge == null || staticBridge.getWebView() == null)
            return  null;

        PluginHandle handler = staticBridge.getPlugin("CallkitOnesignal");
        return handler == null ? null : (CallKitVoipPlugin) handler.getInstance();
    }

    // show_call_notification removed to align with iOS API. Use OneSignal extender to start UI.

    @PluginMethod
    public void getApnsEnvironment(PluginCall call) {
        JSObject ret = new JSObject();
        boolean isDebuggable = (getContext().getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        ret.put("environment", isDebuggable ? "debug" : "production");
        call.resolve(ret);
    }

    @PluginMethod
    public void abortCall(PluginCall call) {
        Log.d("abortCall","Called");
        String uuid = call.getString("uuid"); // optional; not used currently
        Intent serviceIntent = new Intent(context, CallNotificationService.class);
        context.stopService(serviceIntent);
        call.resolve();
    }

    public void stopCallServices() {
        Log.d("stopCallServices","Called");
        Intent serviceIntent = new Intent(context, CallNotificationService.class);
        context.stopService(serviceIntent);
    }

    // isScreenLocked removed to align with iOS API.
}
