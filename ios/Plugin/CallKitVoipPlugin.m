#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(CallKitVoipPlugin, "CallKitVoip",
    CAP_PLUGIN_METHOD(register, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(abortCall, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(authenticateWithCustomToken, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(logoutFromFirebase, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getApnsEnvironment, CAPPluginReturnPromise);
)