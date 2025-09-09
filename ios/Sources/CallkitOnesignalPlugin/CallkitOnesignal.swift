import Foundation
import UIKit
import CallKit
import PushKit

@objc public class CallkitOnesignal: NSObject {
    
    private var provider: CXProvider?
    private let voipRegistry = PKPushRegistry(queue: nil)
    private var connectionIdRegistry: [UUID: CallConfig] = [:]
    private var voipToken: String?
    var hasRegisteredListener = false
    var lastNotifiedToken: String? = nil
    private var answeredFromOtherDevices: String?
    private let registryAccessQueue = DispatchQueue(label: "registryAccessQueue")
    private var abortedCallRegistry = Set<UUID>()
    private let abortedCallQueue = DispatchQueue(label: "abortedCallQueue")
    
    weak var pluginDelegate: CallkitOnesignalDelegate?
    
    public override init() {
        super.init()
        setupCallKit()
    }
    
    private func setupCallKit() {
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
    
    @objc public func register() {
        hasRegisteredListener = true
        
        if let token = voipToken, token != lastNotifiedToken {
            lastNotifiedToken = token
            pluginDelegate?.notifyRegistration(token: token)
        }
    }
    
    @objc public func getApnsEnvironment() -> String {
        #if DEBUG
            return "debug"
        #else
            return "production"
        #endif
    }
    
    public func notifyEvent(eventName: String, uuid: UUID) {
        registryAccessQueue.async { [weak self] in
            guard let self = self else { return }
            let config: CallConfig? = self.connectionIdRegistry[uuid]
            DispatchQueue.main.async {
                guard let config = config else { return }
                self.pluginDelegate?.notifyEvent(eventName: eventName, data: [
                    "connectionId": config.connectionId, "username": config.username, "callerId": config.callerId,
                    "group": config.group, "message": config.message, "organization": config.organization,
                    "roomname": config.roomname, "source": config.source, "title": config.title, "type": config.type,
                    "duration": config.duration, "media": config.media, "uuid": uuid.uuidString
                ])
            }
            self.registryAccessQueue.async(flags: .barrier) { [weak self] in
                guard let self = self else{return}
                self.connectionIdRegistry[uuid] = nil
            }
        }
    }
    
    public func endCall(uuid: UUID) {
        let callObserver = CXCallObserver()
        let activeCalls = callObserver.calls

        guard activeCalls.contains(where: { $0.uuid == uuid }) else {
            NSLog("⚠️ Call UUID not found in active calls. Skipping endCall.")
            return
        }

        let controller = CXCallController()
        let endAction = CXEndCallAction(call: uuid)
        let transaction = CXTransaction(action: endAction)

        controller.request(transaction) { error in
            if let error = error {
                NSLog("❌ Error ending call: \(error.localizedDescription)")
            } else {
                NSLog("✅ Call successfully ended with UUID: \(uuid.uuidString)")
            }
        }
    }
    
    public func abortCall(uuid: UUID) {
        var alreadyAborted = false
        abortedCallQueue.sync {
            alreadyAborted = abortedCallRegistry.contains(uuid)
            if !alreadyAborted {
                abortedCallRegistry.insert(uuid)
            }
        }

        if alreadyAborted {
            NSLog("⏭️ Abort skipped: already aborted for UUID: \(uuid)")
            return
        }

        NSLog("🚫 Aborting call for UUID: \(uuid)")
        answeredFromOtherDevices = "answeredFromOtherDevice"
        endCall(uuid: uuid)
    }
}

// MARK: CallKit events handler
extension CallkitOnesignal: CXProviderDelegate {
    
    public func providerDidReset(_ provider: CXProvider) {
        NSLog("Provider did reset")
    }

    public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        notifyEvent(eventName: "callAnswered", uuid: action.callUUID)
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        if answeredFromOtherDevices != "answeredFromOtherDevice" {
            notifyEvent(eventName: "callEnded", uuid: action.callUUID)
        }
        
        answeredFromOtherDevices = nil
        
        abortedCallQueue.async { [uuid = action.callUUID] in
           self.abortedCallRegistry.remove(uuid)
        }
        
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        notifyEvent(eventName: "callStarted", uuid: action.callUUID)
        action.fulfill()
    }
}

// MARK: PushKit events handler
extension CallkitOnesignal: PKPushRegistryDelegate {

    public func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        let token = pushCredentials.token.map { String(format: "%02.2hhx", $0) }.joined()
        print("Token: \(token)")
        voipToken = token
        if hasRegisteredListener && token != lastNotifiedToken {
            lastNotifiedToken = token
            pluginDelegate?.notifyRegistration(token: token)
        }
    }

    public func pushRegistry(
        _ registry: PKPushRegistry,
        didReceiveIncomingPushWith payload: PKPushPayload,
        for type: PKPushType,
        completion: @escaping () -> Void
    ) {
        guard type == .voIP else {
            completion()
            return
        }
        
        if let action = payload.dictionaryPayload["action"] as? String, action == "cancel" {
            let callerId = payload.dictionaryPayload["callerId"] as? String
            let roomname = payload.dictionaryPayload["roomname"] as? String
            var matchedUUID: UUID?
            registryAccessQueue.sync {
                matchedUUID = self.connectionIdRegistry.first { (key, value) in
                    let callerMatch = callerId == nil || value.callerId == callerId
                    let roomMatch = roomname == nil || value.roomname == roomname
                    return callerMatch && roomMatch
                }?.key
            }
            if let uuid = matchedUUID {
                endCall(uuid: uuid)
            }
            completion()
            return
        }

        guard let callerId = payload.dictionaryPayload["callerId"] as? String else {
            completion()
            return
        }

        let username = payload.dictionaryPayload["Username"] as? String ?? "Anonymous"
        let callUUID = UUID()
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

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.pluginDelegate?.notifyEvent(eventName: "incoming", data: [
                "connectionId": config.connectionId,
                "username": config.username,
                "callerId": config.callerId,
                "group": config.group,
                "message": config.message,
                "organization": config.organization,
                "roomname": config.roomname,
                "source": config.source,
                "title": config.title,
                "type": config.type,
                "duration": config.duration,
                "media": config.media,
                "uuid": callUUID.uuidString
            ])
        }
    }
}

extension CallkitOnesignal {
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

protocol CallkitOnesignalDelegate: AnyObject {
    func notifyRegistration(token: String)
    func notifyEvent(eventName: String, data: [String: Any])
}
