import Foundation
import Capacitor

/**
 * CallKit VoIP Plugin
 * Provides CallKit and PushKit functionality for VoIP calls with OneSignal support
 */
@objc(CallkitOnesignalPlugin)
public class CallkitOnesignalPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "CallkitOnesignalPlugin"
    public let jsName = "CallkitOnesignal"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "getToken", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "abortCall", returnType: CAPPluginReturnPromise)
    ]

    private let implementation = CallkitOnesignal()

    override public func load() {
        super.load()
        implementation.pluginDelegate = self
    }

    @objc func getToken(_ call: CAPPluginCall) {
        do {
            if let token = implementation.getToken() {
                call.resolve(["value": token])
            } else {
                call.reject("VoIP token not available yet. Please try again.")
            }
        } catch {
            call.reject("Failed to get VoIP token: \(error.localizedDescription)")
        }
    }


    @objc func abortCall(_ call: CAPPluginCall) {
        guard let uuidString = call.getString("uuid") else {
            call.reject("UUID parameter is required")
            return
        }
        
        guard let callUUID = UUID(uuidString: uuidString) else {
            call.reject("Invalid UUID format: \(uuidString)")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { 
                call.reject("Plugin instance no longer available")
                return 
            }
            
            do {
                self.implementation.abortCall(uuid: callUUID)
                call.resolve()
            } catch {
                call.reject("Failed to abort call: \(error.localizedDescription)")
            }
        }
    }
}

// MARK: CallkitOnesignalDelegate
extension CallkitOnesignalPlugin: CallkitOnesignalDelegate {
    
    func notifyEvent(eventName: String, data: [String: Any]) {
        notifyListeners(eventName, data: data)
    }
}
