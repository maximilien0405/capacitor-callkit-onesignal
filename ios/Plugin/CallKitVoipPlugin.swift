import Foundation
import Capacitor
import UIKit
import CallKit
import PushKit
import FirebaseAuth
import FirebaseCore
/**
 *  CallKit Voip Plugin provides native PushKit functionality with apple CallKit to capacitor
 */
@objc(CallKitVoipPlugin)
public class CallKitVoipPlugin: CAPPlugin {

    private var provider: CXProvider?
    private let voipRegistry            = PKPushRegistry(queue: nil)
    private var connectionIdRegistry : [UUID: CallConfig] = [:]
    private var uuid : UUID?
    private var realTimeDataService = RealTimeDataService()
    private var answeredFromOtherDevices: String?

    @objc func register(_ call: CAPPluginCall) {
        // Ensure VoIP registry is initialized once
        if voipRegistry.delegate == nil {
            voipRegistry.delegate = self
            voipRegistry.desiredPushTypes = [.voIP]
        }

        let config = CXProviderConfiguration(localizedName: "Secure Call")
        config.maximumCallGroups = 1
        config.maximumCallsPerCallGroup = 1
        config.supportsVideo = true
        config.supportedHandleTypes = [.generic]

        // Ensure provider is initialized correctly
        provider = CXProvider(configuration: config)
        provider?.setDelegate(self, queue: .main)

        // Initialize Firebase only if it's not already configured
        if FirebaseApp.app() == nil {
            DispatchQueue.global(qos: .background).async {
                FirebaseApp.configure()
            }
        }

        call.resolve()
    }

    @objc func authenticateWithCustomToken(_ call: CAPPluginCall) {
        let auth = Auth.auth()
        
        // Check if a user is already authenticated
        if let currentUser = auth.currentUser {
            currentUser.reload { error in
                if error == nil, let refreshedUser = auth.currentUser, !refreshedUser.isAnonymous {
                    // User is authenticated and not anonymous
                    let result = [
                        "uid": refreshedUser.uid
                    ]
                    call.resolve(result)
                    return
                }
                
                // User session is invalid, proceed with authentication
                self.handleCustomTokenAuthentication(call, auth: auth)
            }
        } else {
            // No authenticated user, proceed with authentication
            self.handleCustomTokenAuthentication(call, auth: auth)
        }
    }

    private func handleCustomTokenAuthentication(_ call: CAPPluginCall, auth: Auth) {
        guard let customToken = call.getString("token"), !customToken.isEmpty else {
            call.reject("Custom token is required.")
            return
        }
        
        auth.signIn(withCustomToken: customToken) { authResult, error in
            if let error = error {
                // Authentication failed
                call.reject("Authentication failed: \(error.localizedDescription)")
                return
            }
            
            // Authentication success
            if let user = auth.currentUser {
                let result = [
                    "uid": user.uid
                ]
                call.resolve(result)
            } else {
                call.reject("User not found after authentication.")
            }
        }
    }

    // New logout method
    @objc func logoutFromFirebase(_ call: CAPPluginCall) {
        do {
            // Attempt to sign out from Firebase
            try Auth.auth().signOut()
            call.resolve([
                "success": true,
                "message": "Successfully logged out from Firebase."
            ])
        } catch let error as NSError {
            call.reject("Error logging out from Firebase.")
        }
    }

    @objc func getApnsEnvironment(_ call: CAPPluginCall) {
        #if DEBUG
            let environment = "debug"
        #else
            let environment = "production"
        #endif
        
        print("🚀 APNs Environment Detected:", environment)
        call.resolve(["environment": environment])
    }


    public func notifyEvent(eventName: String, uuid: UUID){
        if let config = connectionIdRegistry[uuid] {
            notifyListeners(eventName, data: [
                "connectionId": config.connectionId,
                "username"    : config.username,
                "callerId": config.callerId, 
                "group": config.group, 
                "message": config.message, 
                "organization": config.organization, 
                "roomname": config.roomname, 
                "source": config.source, 
                "title": config.title, 
                "type": config.type,
                "duration": config.duration,
                "media": config.media
            ])
            connectionIdRegistry[uuid] = nil
        }
    }
    
    public func endCall(uuid: UUID) {
        let controller = CXCallController()
        let transaction = CXTransaction(action: CXEndCallAction(call: uuid));
        controller.request(transaction,completion: { error in })
    }
    
    @objc func abortCall(_ call: CAPPluginCall) {
        if let callUUID = uuid {
            endCall(uuid: callUUID)
        }
        self.realTimeDataService.hideVideoCallConfirmation()
        call.resolve()
    }

    private func abortCall(with uuid: UUID) {
        self.answeredFromOtherDevices = "answeredFromOtherDevice"
        endCall(uuid: uuid)
    }


}


// MARK: CallKit events handler

extension CallKitVoipPlugin: CXProviderDelegate {

    public func providerDidReset(_ provider: CXProvider) {

    }

    public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        // Answers an incoming call
        print("CXAnswerCallAction answers an incoming call")
        uuid = action.callUUID
        notifyEvent(eventName: "callAnswered", uuid: action.callUUID)
        // endCall(uuid: action.callUUID)
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        // End the call
        print("CXEndCallAction represents ending call")
        uuid = action.callUUID
        self.realTimeDataService.hideVideoCallConfirmation()
        if answeredFromOtherDevices == "answeredFromOtherDevice" {
            // Reset the flag so that future calls can be notified properly
            self.answeredFromOtherDevices = ""
        } else {
            // Notify event only if the call was NOT answered from another device
            notifyEvent(eventName: "callEnded", uuid: action.callUUID)
        }
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        // Report connection started
        print("CXStartCallAction represents initiating an outgoing call")
        uuid = action.callUUID
        notifyEvent(eventName: "callStarted", uuid: action.callUUID)
        action.fulfill()
    }


}

// MARK: PushKit events handler
extension CallKitVoipPlugin: PKPushRegistryDelegate {

    public func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        let parts = pushCredentials.token.map { String(format: "%02.2hhx", $0) }
        let token = parts.joined()
        print("Token: \(token)")
        notifyListeners("registration", data: ["value": token])
    }
    
    public func pushRegistry(
        _ registry: PKPushRegistry,
        didReceiveIncomingPushWith payload: PKPushPayload,
        for type: PKPushType,
        completion: @escaping () -> Void
    ) {
        print("didReceiveIncomingPushWith")

        // Ensure callerId exists
        guard let callerId = payload.dictionaryPayload["callerId"] as? String else {
            print("callerId missing, aborting push processing.")
            completion()
            return
        }

        // Helper function to extract values with a default fallback
        func getValue(for key: String, default: String = "Anonymous") -> String {
            return payload.dictionaryPayload[key] as? String ?? `default`
        }

        let username     = getValue(for: "Username")
        let connectionId = callerId
        let group        = getValue(for: "group")
        let message      = getValue(for: "message")
        let organization = getValue(for: "organization")
        let roomname     = getValue(for: "roomname")
        let source       = getValue(for: "source")
        let title        = getValue(for: "title")
        let type         = getValue(for: "type")
        let duration     = getValue(for: "duration", default: "60")
        let media        = getValue(for: "media", default: "video")

        let callUUID = UUID()
        self.uuid = callUUID

        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: username)
        update.hasVideo = (media == "video")
        update.supportsDTMF = false
        update.supportsHolding = true
        update.supportsGrouping = false
        update.supportsUngrouping = false

        // Store call details
        connectionIdRegistry[callUUID] = .init(
            connectionId: connectionId,
            username: username,
            callerId: callerId,
            group: group,
            message: message,
            organization: organization,
            roomname: roomname,
            source: source,
            title: title,
            type: type,
            duration: duration,
            media: media
        )

        // Clear previous call states
        self.answeredFromOtherDevices = ""

        self.provider?.reportNewIncomingCall(with: callUUID, update: update) { error in
            if let error = error {
                print("Error reporting call: \(error.localizedDescription)")
            }
            completion()
        }

        // Handle Firebase listener separately
        DispatchQueue.global(qos: .background).async {
            if FirebaseApp.app() == nil {
                FirebaseApp.configure()
            }
            guard let user = Auth.auth().currentUser else {
                print("Firebase user not found")
                return
            }
            DispatchQueue.main.async {
                self.realTimeDataService.handleRealtimeListener(
                    orgId: organization,
                    userId: user.uid,
                    roomName: roomname
                ) {
                    print("Data changed, aborting call.")
                    self.abortCall(with: callUUID)
                }
            }
        }
    }

}


extension CallKitVoipPlugin {
    struct CallConfig {
        let connectionId : String
        let username     : String
        let callerId     : String
        let group        : String
        let message      : String
        let organization : String
        let roomname     : String
        let source       : String
        let title        : String
        let type         : String
        let duration     : String
        let media        : String
    }
}
