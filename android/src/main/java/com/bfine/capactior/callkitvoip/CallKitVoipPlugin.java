package com.bfine.capactior.callkitvoip;

import com.bfine.capactior.callkitvoip.androidcall.CallNotificationService;
import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginHandle;
import com.getcapacitor.PluginMethod;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "CallKitVoip")

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
        String callerId = data.getStringExtra("callerId");
        String group = data.getStringExtra("group");
        String message = data.getStringExtra("message");
        String organization = data.getStringExtra("organization");
        String roomname = data.getStringExtra("roomname");
        String source = data.getStringExtra("source");
        String title = data.getStringExtra("title");
        String type = data.getStringExtra("type");
        String duration = data.getStringExtra("duration");
        String media = data.getStringExtra("media");
        String eventName = data.getStringExtra("eventName");
    }

    @PluginMethod
    public void register(PluginCall call) {
        Log.d("CallKitVoip","register");
        Logger.debug("CallKit: Subscribed");
        call.resolve();
    }
    
    public void notifyEvent(String eventName, String callerId,String group, String message,String organization,String roomname, String source,String title,String type, String duration,String media){
       JSObject data = new JSObject();
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
       notifyListeners(eventName, data);
    }

    public static CallKitVoipPlugin getInstance() {
        if (staticBridge == null || staticBridge.getWebView() == null)
            return  null;

        PluginHandle handler = staticBridge.getPlugin("CallKitVoip");
        return handler == null ? null : (CallKitVoipPlugin) handler.getInstance();
    }

    @PluginMethod
    public void show_call_notification(PluginCall call) {

        String callerId = call.getString("callerId");
        String group = call.getString("group");
        String message = call.getString("message");
        String organization = call.getString("organization");
        String roomname = call.getString("roomname");
        String source = call.getString("source");
        String title = call.getString("title");
        String type = call.getString("type");
        String duration = call.getString("duration");
        String media = call.getString("media");

        Intent serviceIntent = new Intent(context, CallNotificationService.class);
        serviceIntent.putExtra("call_type","video");
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
           context.startForegroundService(serviceIntent);
       } else {
           context.startService(serviceIntent);
       }

    }

    @PluginMethod
    public void abortCall(PluginCall call) {
        Log.d("abortCall","Called");
        Intent serviceIntent = new Intent(context, CallNotificationService.class);
        context.stopService(serviceIntent);
    }


}
