import Foundation
import Capacitor

/**
 * CallKit OneSignal Plugin
 * 
 * Capacitor plugin that provides CallKit and PushKit functionality for VoIP calls.
 * Integrates with OneSignal for push notifications and handles call management.
 */
@objc(CallkitOnesignalPlugin)
public class CallkitOnesignalPlugin: CAPPlugin, CAPBridgedPlugin {
    // Plugin Configuration
    
    public let identifier = "CallkitOnesignalPlugin"
    public let jsName = "CallkitOnesignal"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "getToken", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getApnsEnvironment", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "endCall", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "replayPendingEvents", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "wasLaunchedFromVoIP", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startOutgoingCall", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "prepareAudioSessionForCall", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updateCallUI", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setAudioOutput", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setMuted", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isAppInForeground", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setAppFullyLoaded", returnType: CAPPluginReturnPromise),
    ]

    // Private Properties
    
    private let implementation = CallkitOnesignal()

    // Plugin Lifecycle
    
    override public func load() {
        super.load()
        implementation.pluginDelegate = self
    }

    // Plugin Methods
    
    @objc func getToken(_ call: CAPPluginCall) {
        if let token = implementation.getToken() {
            call.resolve(["value": token])
        } else {
            call.reject("VoIP token not available yet. Please try again.")
        }
    }

    @objc func getApnsEnvironment(_ call: CAPPluginCall) {
        let env = implementation.getApnsEnvironment()
        call.resolve(["value": env])
    }

    @objc func endCall(_ call: CAPPluginCall) {
        implementation.endAllCalls()
        call.resolve()
    }

    @objc func replayPendingEvents(_ call: CAPPluginCall) {
        implementation.replayPendingEvents()
        call.resolve()
    }

    @objc func wasLaunchedFromVoIP(_ call: CAPPluginCall) {
        let wasLaunched = implementation.wasLaunchedFromVoIP()
        call.resolve(["value": wasLaunched])
    }

    @objc func startOutgoingCall(_ call: CAPPluginCall) {
        guard let callerId = call.getString("callerId"),
              let username = call.getString("username"),
              let media = call.getString("media") else {
            call.reject("callerId, username, and media are required")
            return
        }
        implementation.startOutgoingCall(callerId: callerId, username: username, media: media)
        call.resolve()
    }


    @objc func prepareAudioSessionForCall(_ call: CAPPluginCall) {
        implementation.prepareAudioSessionForCall()
        call.resolve()
    }

    @objc func updateCallUI(_ call: CAPPluginCall) {
        guard let uuidString = call.getString("uuid"),
              let uuid = UUID(uuidString: uuidString),
              let media = call.getString("media") else {
            call.reject("uuid and media are required")
            return
        }
        implementation.updateCallUI(uuid: uuid, media: media)
        call.resolve()
    }

    @objc func setAudioOutput(_ call: CAPPluginCall) {
        guard let route = call.getString("route") else {
            call.reject("route is required")
            return
        }
        
        do {
            try implementation.setAudioOutput(route: route)
            call.resolve()
        } catch {
            call.reject("Failed to set audio output: \(error.localizedDescription)")
        }
    }

    @objc func setMuted(_ call: CAPPluginCall) {
        guard let isMuted = call.getBool("isMuted") else {
            call.reject("isMuted is required")
            return
        }
        
        implementation.setMuted(isMuted)
        call.resolve()
    }

    @objc func isAppInForeground(_ call: CAPPluginCall) {
        let isInForeground = implementation.isAppInForeground()
        call.resolve(["value": isInForeground])
    }


    @objc func setAppFullyLoaded(_ call: CAPPluginCall) {
        implementation.setAppFullyLoaded()
        call.resolve()
    }

}

// CallkitOnesignalDelegate

extension CallkitOnesignalPlugin: CallkitOnesignalDelegate {
    
    func notifyEvent(eventName: String, data: [String: Any]) {
        notifyListeners(eventName, data: data)
    }
}
