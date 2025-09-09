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
        CAPPluginMethod(name: "register", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getApnsEnvironment", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "abortCall", returnType: CAPPluginReturnPromise)
    ]

    private let implementation = CallkitOnesignal()

    override public func load() {
        super.load()
        implementation.pluginDelegate = self
    }

    @objc func register(_ call: CAPPluginCall) {
        do {
            implementation.register()
            call.resolve()
        } catch {
            call.reject("Failed to register for VoIP notifications: \(error.localizedDescription)")
        }
    }

    @objc func getApnsEnvironment(_ call: CAPPluginCall) {
        do {
            let environment = implementation.getApnsEnvironment()
            print("🚀 APNs Environment Detected:", environment)
            call.resolve(["environment": environment])
        } catch {
            call.reject("Failed to get APNs environment: \(error.localizedDescription)")
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
    
    func notifyRegistration(token: String) {
        notifyListeners("registration", data: ["value": token])
    }
    
    func notifyEvent(eventName: String, data: [String: Any]) {
        notifyListeners(eventName, data: data)
    }
}
