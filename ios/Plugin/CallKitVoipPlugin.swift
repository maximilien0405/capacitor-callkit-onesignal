import Foundation
import Capacitor
import UIKit
import CallKit
import PushKit
import FirebaseAuth
import FirebaseCore

/**
 * CallKit Voip Plugin provides native PushKit functionality with apple CallKit to capacitor
 */
@objc(CallKitVoipPlugin)
public class CallKitVoipPlugin: CAPPlugin {

    private var provider: CXProvider?
    private let voipRegistry = PKPushRegistry(queue: nil)
    private var connectionIdRegistry: [UUID: CallConfig] = [:]
    private var uuid: UUID?
    private var voipToken: String?
    private let realTimeDataService = RealTimeDataService()
    private var answeredFromOtherDevices: String?
    private let registryAccessQueue = DispatchQueue(label: "registryAccessQueue") // Serial queue for thread safety
    private let firebaseAuthQueue = DispatchQueue(label: "firebaseAuthQueue")

    override public func load() {
        // Initialize Firebase only once at plugin load, on a background thread.
        // firebaseAuthQueue.async {
        //     if FirebaseApp.app() == nil {
        //         FirebaseApp.configure()
        //     }
        // }

        voipRegistry.delegate = self
        voipRegistry.desiredPushTypes = [.voIP]

        let config = CXProviderConfiguration(localizedName: "Secure Call")
        config.supportsVideo = true
        config.supportedHandleTypes = [.generic]
        config.maximumCallGroups = 1
        config.maximumCallsPerCallGroup = 1
        provider = CXProvider(configuration: config)
        provider?.setDelegate(self, queue: .main)
    }

    @objc func register(_ call: CAPPluginCall) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
            self.notifyListeners("registration", data: ["value": self.voipToken ?? ""])
        }
        call.resolve()
    }

    @objc func authenticateWithCustomToken(_ call: CAPPluginCall) {
        let auth = Auth.auth()

        if let currentUser = auth.currentUser, !currentUser.isAnonymous {
            currentUser.reload { [weak self] error in
                guard let self = self else { return }
                if let error = error {
                    NSLog("Error reloading user: \(error.localizedDescription)") // Log the error
                    self.handleCustomTokenAuthentication(call, auth: auth)
                    return
                }
                if let refreshedUser = auth.currentUser, !refreshedUser.isAnonymous{
                    call.resolve(["uid": refreshedUser.uid])
                } else {
                    self.handleCustomTokenAuthentication(call, auth: auth)
                }
            }
        } else {
            handleCustomTokenAuthentication(call, auth: auth)
        }
    }

    private func handleCustomTokenAuthentication(_ call: CAPPluginCall, auth: Auth) {
        guard let customToken = call.getString("token"), !customToken.isEmpty else {
            call.reject("Custom token is required.")
            return
        }

        firebaseAuthQueue.async {
            auth.signIn(withCustomToken: customToken) { authResult, error in
                DispatchQueue.main.async {
                    if let error = error {
                        call.reject("Authentication failed: \(error.localizedDescription)")
                        return
                    }
                    if let user = auth.currentUser {
                        call.resolve(["uid": user.uid])
                    } else {
                        call.reject("User not found after authentication.")
                    }
                }
            }
        }
    }

    @objc func logoutFromFirebase(_ call: CAPPluginCall) {
        firebaseAuthQueue.async {
            do {
                try Auth.auth().signOut()
                DispatchQueue.main.async {
                    call.resolve(["success": true, "message": "Successfully logged out from Firebase."])
                }
            } catch {
                DispatchQueue.main.async {
                    call.reject("Error logging out from Firebase.")
                }
            }
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

    public func notifyEvent(eventName: String, uuid: UUID) {
        registryAccessQueue.async { [weak self] in
            guard let self = self else { return }
            let config: CallConfig? = self.connectionIdRegistry[uuid] //capture the dictionary
            DispatchQueue.main.async {
                guard let config = config else { return }
                self.notifyListeners(eventName, data: [
                    "connectionId": config.connectionId, "username": config.username, "callerId": config.callerId,
                    "group": config.group, "message": config.message, "organization": config.organization,
                    "roomname": config.roomname, "source": config.source, "title": config.title, "type": config.type,
                    "duration": config.duration, "media": config.media
                ])
            }
            self.registryAccessQueue.async(flags: .barrier) { [weak self] in //nested call
                guard let self = self else{return}
                self.connectionIdRegistry[uuid] = nil
            }
        }
    }

    public func endCall(uuid: UUID) {
        let controller = CXCallController()
        let transaction = CXTransaction(action: CXEndCallAction(call: uuid))
        controller.request(transaction) { error in
            if let error = error {
                NSLog("Error ending call: \(error.localizedDescription)") // Log errors.
            }
        }
    }

    @objc func abortCall(_ call: CAPPluginCall) {
        guard let callUUID = uuid else {
            print("No active call to abort")
            call.resolve()
            return
        }
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.endCall(uuid: callUUID)
            self.realTimeDataService.hideVideoCallConfirmation()
            call.resolve()
        }
    }

    private func abortCall(with uuid: UUID) {
        answeredFromOtherDevices = "answeredFromOtherDevice"
        endCall(uuid: uuid)
    }
}

// MARK: CallKit events handler
extension CallKitVoipPlugin: CXProviderDelegate {

    public func providerDidReset(_ provider: CXProvider) {
        NSLog("Provider did reset")
    }

    public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        uuid = action.callUUID
        notifyEvent(eventName: "callAnswered", uuid: action.callUUID)
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        uuid = action.callUUID
        realTimeDataService.hideVideoCallConfirmation()
        if answeredFromOtherDevices != "answeredFromOtherDevice" {
            notifyEvent(eventName: "callEnded", uuid: action.callUUID)
        }
        answeredFromOtherDevices = nil // Reset flag
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        uuid = action.callUUID
        notifyEvent(eventName: "callStarted", uuid: action.callUUID)
        action.fulfill()
    }
}

// MARK: PushKit events handler
extension CallKitVoipPlugin: PKPushRegistryDelegate {

    public func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        let token = pushCredentials.token.map { String(format: "%02.2hhx", $0) }.joined()
        print("Token: \(token)")
        voipToken = token
        notifyListeners("registration", data: ["value": token])
    }

    public func pushRegistry(
        _ registry: PKPushRegistry,
        didReceiveIncomingPushWith payload: PKPushPayload,
        for type: PKPushType,
        completion: @escaping () -> Void
    ) {
        guard type == .voIP, let callerId = payload.dictionaryPayload["callerId"] as? String else {
            completion()
            return
        }

        let username = payload.dictionaryPayload["Username"] as? String ?? "Anonymous"
        let callUUID = UUID()
        uuid = callUUID

        let config = CallConfig(
                connectionId: callerId, username: username, callerId: callerId,
                group: payload.dictionaryPayload["group"] as? String ?? "Anonymous",
                message: payload.dictionaryPayload["message"] as? String ?? "Anonymous",
                organization: payload.dictionaryPayload["organization"] as? String ?? "Anonymous",
                roomname: payload.dictionaryPayload["roomname"] as? String ?? "Anonymous",
                source: payload.dictionaryPayload["source"] as? String ?? "Anonymous",
                title: payload.dictionaryPayload["title"] as? String ?? "Anonymous",
                type: payload.dictionaryPayload["type"] as? String ?? "Anonymous",
                duration: payload.dictionaryPayload["duration"] as? String ?? "60",
                media: payload.dictionaryPayload["media"] as? String ?? "video"
            )

        registryAccessQueue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            self.connectionIdRegistry[callUUID] = config
        }

        answeredFromOtherDevices = nil

        guard let provider = provider else {
            print("CXProvider is nil, skipping call report.")
            completion()
            return
        }

        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: username)
        update.hasVideo = (payload.dictionaryPayload["media"] as? String ?? "video") == "video"
        update.supportsDTMF = false
        update.supportsHolding = true
        update.supportsGrouping = false
        update.supportsUngrouping = false

        provider.reportNewIncomingCall(with: callUUID, update: update) { error in
            if let error = error {
                NSLog("Error reporting call: \(error.localizedDescription)")
            }
            completion()
        }

        // Handle Firebase listener separately
        firebaseAuthQueue.async {
            guard let user = Auth.auth().currentUser else {
                print("Firebase user not found")
                return
            }
            DispatchQueue.main.async {
                self.realTimeDataService.handleRealtimeListener(
                    orgId: payload.dictionaryPayload["organization"] as? String ?? "Anonymous",
                    userId: user.uid,
                    roomName: payload.dictionaryPayload["roomname"] as? String ?? "Anonymous"
                ) { [weak self] in
                    guard let self = self else { return } // Safely access self
                    NSLog("Data changed, aborting call.")
                    self.abortCall(with: callUUID)
                }
            }
        }
    }
}

extension CallKitVoipPlugin {
    struct CallConfig {
        let connectionId: String
        let username: String
        let callerId: String
        let group: String
        let message: String
        let organization: String
        let roomname: String
        let source: String
        let title: String
        let type: String
        let duration: String
        let media: String
    }
}
