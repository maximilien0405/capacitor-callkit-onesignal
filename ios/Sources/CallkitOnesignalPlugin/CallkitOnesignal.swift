import Foundation
import UIKit
import CallKit
import PushKit
import AVFoundation
import Intents
import Network

@objc public class CallkitOnesignal: NSObject {
    private static var sharedInstance: CallkitOnesignal?
        
    // Private Properties
    
    weak var pluginDelegate: CallkitOnesignalDelegate?
    private var provider: CXProvider?
    private let voipRegistry = PKPushRegistry(queue: nil)
    private var connectionIdRegistry: [UUID: CallConfig] = [:]
    private var voipToken: String?
    private let registryAccessQueue = DispatchQueue(label: "registryAccessQueue", attributes: .concurrent)
    private var answeredCalls: Set<UUID> = []
    private var incomingCalls: Set<UUID> = []
    private var canceledCalls: Set<UUID> = []
    private let callStateQueue = DispatchQueue(label: "callStateQueue", attributes: .concurrent)
    private var pendingEvents: [[String: Any]] = []
    private let pendingEventsQueue = DispatchQueue(label: "pendingEventsQueue", attributes: .concurrent)
    private var outgoingCalls: Set<UUID> = []
    private static var pendingUserActivities: [NSUserActivity] = []
    private static let pendingUserActivitiesQueue = DispatchQueue(label: "pendingUserActivitiesQueue", attributes: .concurrent)
    private var isAppFullyLoaded = false
    
    // Computed Properties
    
    private var isCallOngoing: Bool {
        let callObserver = CXCallObserver()
        let activeCalls = !callObserver.calls.isEmpty
        
        var hasOutgoingCalls = false
        callStateQueue.sync {
            hasOutgoingCalls = !self.outgoingCalls.isEmpty
        }
        
        let result = activeCalls || hasOutgoingCalls
        if result {
            NSLog("[CallkitOnesignal] isCallOngoing=true - activeCalls: \(activeCalls), hasOutgoingCalls: \(hasOutgoingCalls), outgoingCalls count: \(outgoingCalls.count)")
        }
        
        return result
    }
    
    // Initialization
    
    public override init() {
        super.init()
        setupCallKit()
        setupNotificationObservers()
        CallkitOnesignal.sharedInstance = self
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            CallkitOnesignal.processPendingUserActivities()
        }
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
        provider?.invalidate()
        provider = nil
    }
    
    private func setupCallKit() {
        guard provider == nil else {
            NSLog("[CallkitOnesignal] CallKit provider already exists, skipping setup")
            return
        }
        
        voipRegistry.delegate = self
        voipRegistry.desiredPushTypes = [.voIP]
        
        let config = CXProviderConfiguration()
        config.supportsVideo = true
        config.supportedHandleTypes = [.generic]
        config.maximumCallGroups = 1
        config.maximumCallsPerCallGroup = 1
        config.includesCallsInRecents = true
    
        if let iconImage = UIImage(named: "LogoVoip"),
           let imageData = iconImage.pngData() {
            config.iconTemplateImageData = imageData
            NSLog("[CallkitOnesignal] CallKit app icon set successfully")
        } else {
            NSLog("[CallkitOnesignal] Warning: Could not load CallKit app icon 'LogoVoip'. Make sure to add this icon to your main app bundle.")
        }
        
        provider = CXProvider(configuration: config)
        provider?.setDelegate(self, queue: .main)
        NSLog("[CallkitOnesignal] CallKit provider created and configured")
    }
    
    private func setupNotificationObservers() {
        // NotificationCenter.default.addObserver(
        //     self,
        //     selector: #selector(appDidEnterBackground),
        //     name: UIApplication.didEnterBackgroundNotification,
        //     object: nil
        // )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(audioRouteChanged),
            name: AVAudioSession.routeChangeNotification,
            object: nil
        )
    }

    @objc private func appWillEnterForeground() {
        if isCallOngoing {
            NSLog("[CallkitOnesignal] App entering foreground during call. Configuring audio session again.")
            configureAudioSession(activate: false, forceTakeover: false)
        }
    }
    
    @objc private func audioRouteChanged(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
            return
        }
        
        // Only notify for specific route change reasons
        switch reason {
        case .newDeviceAvailable, .oldDeviceUnavailable, .categoryChange, .override:
            let currentRoute = getCurrentAudioRoute()
            
            let eventData: [String: Any] = [
                "route": currentRoute
            ]
            
            NSLog("[CallkitOnesignal] Audio route changed: \(currentRoute)")
            
            DispatchQueue.main.async { [weak self] in
                self?.sendEvent(eventName: "audioRouteChanged", data: eventData)
            }
        default:
            break
        }
    }
    
    // User activity handling
    
    private static func processPendingUserActivities() {
        pendingUserActivitiesQueue.async(flags: .barrier) {
            let activities = pendingUserActivities
            pendingUserActivities.removeAll()
            
            DispatchQueue.main.async {
                for activity in activities {
                    if let instance = sharedInstance {
                        instance.handleUserActivityDirect(userActivity: activity)
                    }
                }
            }
        }
    }
    
    @objc public static func handleUserActivityFromAppDelegate(_ userActivity: NSUserActivity) {
        guard let instance = sharedInstance else {
            pendingUserActivitiesQueue.async(flags: .barrier) {
                pendingUserActivities.append(userActivity)
            }
            return
        }
        
        instance.handleUserActivityDirect(userActivity: userActivity)
    }

    @objc public func handleUserActivityDirect(userActivity: NSUserActivity) {
        guard userActivity.activityType == "INStartAudioCallIntent" || userActivity.activityType == "INStartVideoCallIntent" else {
            return
        }
        
        var callerId: String?
        var extractedMedia = "audio"
        
        if let interaction = userActivity.interaction {
            NSLog("[CallkitOnesignal] Found interaction: \(interaction)")
            
            if let intent = interaction.intent as? INStartCallIntent {
                switch intent.callCapability {
                case .videoCall:
                    extractedMedia = "video"
                default:
                    extractedMedia = "audio"
                }
                if let contacts = intent.contacts, let firstContact = contacts.first, let personHandle = firstContact.personHandle {
                    callerId = personHandle.value
                    NSLog("[CallkitOnesignal] Extracted callerId from INStartCallIntent contacts: \(callerId ?? "nil")")
                }
            }
            // Handle legacy INStartAudioCallIntent and INStartVideoCallIntent
            else if let audioIntent = interaction.intent as? INStartAudioCallIntent {
                extractedMedia = "audio"
                if let contacts = audioIntent.contacts, let firstContact = contacts.first, let personHandle = firstContact.personHandle {
                    callerId = personHandle.value
                    NSLog("[CallkitOnesignal] Extracted callerId from INStartAudioCallIntent contacts: \(callerId ?? "nil")")
                }
            } else if let videoIntent = interaction.intent as? INStartVideoCallIntent {
                extractedMedia = "video"
                if let contacts = videoIntent.contacts, let firstContact = contacts.first, let personHandle = firstContact.personHandle {
                    callerId = personHandle.value
                    NSLog("[CallkitOnesignal] Extracted callerId from INStartVideoCallIntent contacts: \(callerId ?? "nil")")
                }
            }
        }
        
        guard let finalCallerId = callerId else {
            return
        }
        
        let callObserver = CXCallObserver()
        let hasActiveCall = !callObserver.calls.isEmpty
        let eventData: [String: Any] = [
            "connectionId": finalCallerId,
            "username": hasActiveCall ? "Current Call" : "Recent Call",
            "callerId": finalCallerId,
            "media": extractedMedia
        ]
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.sendEvent(eventName: "callFromHistory", data: eventData)
        }
    }

    // Public Methods
    
    @objc public func getToken() -> String? {
        return voipToken
    }
    
    /// Mark the app as fully loaded and ready to receive events
    @objc public func setAppFullyLoaded() {
        isAppFullyLoaded = true
        NSLog("[CallkitOnesignal] App marked as fully loaded - events will now be sent immediately")
    }
    
    /// Send an event to the plugin delegate or store it as pending
    private func sendEvent(eventName: String, data: [String: Any]? = nil, uuid: UUID? = nil) {
        if let uuid = uuid {
            registryAccessQueue.async { [weak self] in
                guard let self = self else { return }
                let config: CallConfig? = self.connectionIdRegistry[uuid]
                DispatchQueue.main.async {
                    guard let config = config else { 
                        NSLog("[CallkitOnesignal] No config found for uuid: \(uuid), event: \(eventName)")
                        return 
                    }

                    let eventData: [String: Any] = [
                        "connectionId": config.connectionId,
                        "username": config.username,
                        "callerId": config.callerId,
                        "media": config.media,
                        "uuid": uuid.uuidString
                    ]
                    
                    self.sendEventInternal(eventName: eventName, data: eventData)
                }
            }
        } else if let data = data {
            sendEventInternal(eventName: eventName, data: data)
        }
    }
    
    private func sendEventInternal(eventName: String, data: [String: Any]) {
        if isAppFullyLoaded && pluginDelegate != nil {
            NSLog("[CallkitOnesignal] App is fully loaded - sending \(eventName) event immediately")
            pluginDelegate?.notifyEvent(eventName: eventName, data: data)
        } else {
            NSLog("[CallkitOnesignal] App is not fully loaded - storing \(eventName) event as pending")
            var pendingEventData = data
            pendingEventData["eventName"] = eventName
            pendingEventsQueue.async(flags: .barrier) {
                let isDuplicate = self.pendingEvents.contains { existing in
                    guard let existingName = existing["eventName"] as? String, existingName == eventName else { return false }
                    let aConnectionId = existing["connectionId"] as? String
                    let bConnectionId = pendingEventData["connectionId"] as? String
                    return aConnectionId == bConnectionId
                }
                if !isDuplicate {
                    self.pendingEvents.append(pendingEventData)
                }
            }
        }
    }

    /// Returns the APNs environment (development or production)
    @objc public func getApnsEnvironment() -> String {
        #if DEBUG
        return "development"
        #else
        return "production"
        #endif
    }

    /// Checks if the app was launched or resumed from a VoIP call
    @objc public func wasLaunchedFromVoIP() -> Bool {
        let callObserver = CXCallObserver()
        let activeCalls = callObserver.calls
        
        var hasPendingEvents = false
        pendingEventsQueue.sync {
            hasPendingEvents = !self.pendingEvents.isEmpty
        }
        
        let wasLaunched = !activeCalls.isEmpty || hasPendingEvents
        
        if wasLaunched {
            NSLog("[CallkitOnesignal] App was launched/resumed from VoIP call - activeCalls: \(activeCalls.count), pendingEvents: \(hasPendingEvents)")
        } else {
            NSLog("[CallkitOnesignal] App was not launched from VoIP call")
        }
        
        return wasLaunched
    }

    /// Replays all pending events that were stored while the app was backgrounded
    @objc public func replayPendingEvents() {
        var events: [[String: Any]] = []
        
        pendingEventsQueue.sync {
            events = self.pendingEvents
        }
        
        if events.isEmpty {
            NSLog("[CallkitOnesignal] No pending events to replay")
            return
        }
                
        pendingEventsQueue.async(flags: .barrier) {
            self.pendingEvents.removeAll()
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
            guard let self = self else { return }
            
            for event in events {
                if let eventName = event["eventName"] as? String {
                    NSLog("[CallkitOnesignal] Replaying event: \(eventName)")
                    
                    var eventData = event
                    eventData.removeValue(forKey: "eventName")
                    
                    if let delegate = self.pluginDelegate {
                        delegate.notifyEvent(eventName: eventName, data: eventData)
                    } else {
                        self.pendingEventsQueue.async(flags: .barrier) {
                            self.pendingEvents.append(event)
                        }
                    }
                }
            }
        }
    }

    /// Starts an outgoing call with CallKit UI
    @objc public func startOutgoingCall(callerId: String, username: String, media: String) {
        let callObserver = CXCallObserver()
        let activeCalls = callObserver.calls
        if !activeCalls.isEmpty {
            NSLog("[CallkitOnesignal] Outgoing call not started: VoIP UI already present (activeCalls: \(activeCalls.count))")
            return
        }
        
        
        let callUUID = UUID()
        let handle = CXHandle(type: .generic, value: callerId)
        
        callStateQueue.async(flags: .barrier) { [weak self] in
            self?.outgoingCalls.insert(callUUID)
        }
                
        let startCallAction = CXStartCallAction(call: callUUID, handle: handle)
        startCallAction.isVideo = (media == "video")
        let transaction = CXTransaction(action: startCallAction)
        let controller = CXCallController()
        
        controller.request(transaction) { [weak self] error in
            if let error = error {
                NSLog("[CallkitOnesignal] Failed to start outgoing call: \(error.localizedDescription)")
                self?.callStateQueue.async(flags: .barrier) {
                    self?.outgoingCalls.remove(callUUID)
                }
            } else {
                NSLog("[CallkitOnesignal] Outgoing call started: \(callUUID.uuidString)")
                let update = CXCallUpdate()
                update.remoteHandle = handle
                update.hasVideo = (media == "video")
                update.localizedCallerName = username
                update.supportsDTMF = false
                update.supportsHolding = false
                update.supportsGrouping = false
                update.supportsUngrouping = false
                self?.provider?.reportCall(with: callUUID, updated: update)
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in
                    self?.provider?.reportOutgoingCall(with: callUUID, connectedAt: Date())
                    NSLog("[CallkitOnesignal] Call timer started automatically: \(callUUID.uuidString)")
                }
            }
        }
        
        let config = CallConfig(
            connectionId: callerId,
            username: username,
            callerId: callerId,
            media: media
        )
        registryAccessQueue.async(flags: .barrier) { [weak self] in
            self?.connectionIdRegistry[callUUID] = config
        }
    }    
    
    /// Configures the audio session for VoIP calls with optional activation
    private func configureAudioSession(activate: Bool = false, forceTakeover: Bool = false) {
        do {
            let session = AVAudioSession.sharedInstance()
            if forceTakeover && (session.isOtherAudioPlaying || session.category != .playAndRecord) {
                try session.setActive(false, options: [.notifyOthersOnDeactivation])
                usleep(50000)
            }
            
            try session.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetooth, .allowBluetoothA2DP, .mixWithOthers])
            if activate {
                try session.setActive(true, options: [.notifyOthersOnDeactivation])
            }
            
        } catch {
            NSLog("[CallkitOnesignal] Failed to configure audio session: \(error.localizedDescription)")
        }
    }
    
    /// Prepares the audio session for an outgoing call
    @objc public func prepareAudioSessionForCall() {
        configureAudioSession(activate: false, forceTakeover: true)
    }

    /// Updates the CallKit UI for an ongoing call
    @objc public func updateCallUI(uuid: UUID, media: String) {
        registryAccessQueue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            guard let existingConfig = self.connectionIdRegistry[uuid] else {
                NSLog("[CallkitOnesignal] updateCallUI: No call config found for uuid \(uuid)")
                return
            }
            
            let updatedConfig = CallConfig(
                connectionId: existingConfig.connectionId,
                username: existingConfig.username,
                callerId: existingConfig.callerId,
                media: media
            )
            self.connectionIdRegistry[uuid] = updatedConfig
            
            DispatchQueue.main.async {
                let update = CXCallUpdate()
                update.remoteHandle = CXHandle(type: .generic, value: updatedConfig.callerId)
                update.hasVideo = (media == "video")
                update.localizedCallerName = updatedConfig.username
                update.supportsDTMF = false
                update.supportsHolding = false
                update.supportsGrouping = false
                update.supportsUngrouping = false
                self.provider?.reportCall(with: uuid, updated: update)
                NSLog("[CallkitOnesignal] updateCallUI: Updated call \(uuid) to media=\(media)")
            }
        }
    }

    /// Sets the audio output route for the current call
    @objc public func setAudioOutput(route: String) throws {
        guard isCallOngoing else {
            NSLog("[CallkitOnesignal] No ongoing call - skipping audio output configuration")
            return
        }
        
        configureAudioSession(activate: true, forceTakeover: false)
        let session = AVAudioSession.sharedInstance()
        
        do {
            switch route.lowercased() {
            case "speaker":
                try session.overrideOutputAudioPort(.speaker)
                NSLog("[CallkitOnesignal] Audio output set to speaker")
            case "earpiece":
                try session.overrideOutputAudioPort(.none)
                NSLog("[CallkitOnesignal] Audio output set to earpiece")
            default:
                throw NSError(domain: "CallkitOnesignal", code: 400, userInfo: [
                    NSLocalizedDescriptionKey: "Invalid audio route. Must be 'speaker' or 'earpiece'"
                ])
            }
        } catch {
            NSLog("[CallkitOnesignal] Failed to set audio output to \(route): \(error.localizedDescription)")
            throw error
        }
    }
    
    /// Gets the current audio route as a string
    private func getCurrentAudioRoute() -> String {
        let session = AVAudioSession.sharedInstance()
        let currentRoute = session.currentRoute
        let outputPorts = currentRoute.outputs
        
        for port in outputPorts {
            switch port.portType {
            case .builtInSpeaker:
                return "speaker"
            case .builtInReceiver:
                return "earpiece"
            case .bluetoothA2DP, .bluetoothHFP, .bluetoothLE:
                return "bluetooth"
            case .headphones:
                return "headphones"
            default:
                continue
            }
        }
        
        if session.outputVolume > 0 && session.currentRoute.outputs.contains(where: { $0.portType == .builtInSpeaker }) {
            return "speaker"
        }
        
        return "earpiece"
    }

    /// Ends all active CallKit calls
    public func endAllCalls() {
        let callObserver = CXCallObserver()
        let activeCalls = callObserver.calls
        let controller = CXCallController()
        
        if activeCalls.isEmpty {
            NSLog("[CallkitOnesignal] No active calls to end")
        } else {
            NSLog("[CallkitOnesignal] Ending \(activeCalls.count) active calls")
        }
        
        for call in activeCalls {
            let endAction = CXEndCallAction(call: call.uuid)
            let transaction = CXTransaction(action: endAction)
            controller.request(transaction) { error in
                if let error = error {
                    NSLog("[CallkitOnesignal] Failed to end call \(call.uuid): \(error.localizedDescription)")
                }
            }
        }
        
        // Clean up all call state
        callStateQueue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            self.outgoingCalls.removeAll()
            self.incomingCalls.removeAll()
            self.answeredCalls.removeAll()
            self.canceledCalls.removeAll()
            NSLog("[CallkitOnesignal] Call state cleaned up")
        }
        
        // Clean up call configurations
        registryAccessQueue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            let configCount = self.connectionIdRegistry.count
            self.connectionIdRegistry.removeAll()
            NSLog("[CallkitOnesignal] Call configurations cleaned up (\(configCount) configs removed)")
        }
    }
    
    /// Sets the mute state for the current call and updates CallKit UI
    @objc public func setMuted(_ isMuted: Bool) {
        let callObserver = CXCallObserver()
        let activeCalls = callObserver.calls
        
        guard !activeCalls.isEmpty else {
            NSLog("[CallkitOnesignal] No active calls to mute/unmute")
            return
        }
        
        let callUUID = activeCalls.first!.uuid
        let setMutedAction = CXSetMutedCallAction(call: callUUID, muted: isMuted)
        let transaction = CXTransaction(action: setMutedAction)
        let controller = CXCallController()
        
        controller.request(transaction) { error in
            if let error = error {
                NSLog("[CallkitOnesignal] Failed to set mute state to \(isMuted): \(error.localizedDescription)")
            } else {
                NSLog("[CallkitOnesignal] Successfully set mute state to \(isMuted) for call: \(callUUID)")
            }
        }
    }
    
    /// Check if the app is truly in the foreground
    public func isAppInForeground() -> Bool {
        if Thread.isMainThread {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let window = windowScene.windows.first else {
                return false
            }
            return UIApplication.shared.applicationState == .active && 
                   window.isKeyWindow && 
                   !window.isHidden
        } else {
            var result = false
            DispatchQueue.main.sync {
                result = UIApplication.shared.applicationState == .active
            }
            return result
        }
    }
}

// CallKit Delegate

extension CallkitOnesignal: CXProviderDelegate {
    
    public func providerDidReset(_ provider: CXProvider) {
        NSLog("[CallkitOnesignal] providerDidReset - clearing call state and configurations")

        callStateQueue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            self.outgoingCalls.removeAll()
            self.incomingCalls.removeAll()
            self.answeredCalls.removeAll()
            self.canceledCalls.removeAll()
        }

        registryAccessQueue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            self.connectionIdRegistry.removeAll()
        }

        do {
            try AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
        } catch {
            NSLog("[CallkitOnesignal] Failed to deactivate audio session in providerDidReset: \(error.localizedDescription)")
        }
    }
    
    public func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        do {
            try audioSession.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetooth, .allowBluetoothA2DP, .mixWithOthers])
            try audioSession.setActive(true, options: [.notifyOthersOnDeactivation])
            NSLog("[CallkitOnesignal] Audio session activated and configured for CallKit")
        } catch {
            NSLog("[CallkitOnesignal] Failed to configure audio session in didActivate: \(error.localizedDescription)")
        }
    }

    public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        NSLog("[CallkitOnesignal] Call answered: \(action.callUUID)")
        
        callStateQueue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            
            // Track that this call was answered
            self.answeredCalls.insert(action.callUUID)
            self.outgoingCalls.remove(action.callUUID)
            
            NSLog("[CallkitOnesignal] Call state updated - answered calls: \(self.answeredCalls.count), incoming calls: \(self.incomingCalls.count)")
        }
        
        // Start the call timer when the call is answered
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in
            self?.provider?.reportOutgoingCall(with: action.callUUID, connectedAt: Date())
            NSLog("[CallkitOnesignal] Call timer started for answered call: \(action.callUUID)")
        }
        
        sendEvent(eventName: "callAnswered", uuid: action.callUUID)
        DispatchQueue.main.async {
            UIApplication.shared.requestSceneSessionActivation(nil, userActivity: nil, options: nil, errorHandler: nil)
        }

        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        NSLog("[CallkitOnesignal] Mute action: \(action.isMuted ? "muted" : "unmuted") for call: \(action.callUUID)")
        let eventData: [String: Any] = [
            "isMuted": action.isMuted
        ]
        
        DispatchQueue.main.async { [weak self] in
            self?.sendEvent(eventName: "muteStateChanged", data: eventData)
        }
        
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        NSLog("[CallkitOnesignal] Call ended: \(action.callUUID)")
        
        var configSnapshot: CallConfig?
        registryAccessQueue.sync { [weak self] in
            guard let self = self else { return }
            configSnapshot = self.connectionIdRegistry[action.callUUID]
        }

        var wasIncoming = false
        var wasAnswered = false
        callStateQueue.sync { [weak self] in
            guard let self = self else { return }
            wasIncoming = self.incomingCalls.contains(action.callUUID)
            wasAnswered = self.answeredCalls.contains(action.callUUID)
        }

        let eventName = (wasIncoming && !wasAnswered) ? "callDeclined" : "callEnded"

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if let config = configSnapshot {
                let eventData: [String: Any] = [
                    "connectionId": config.connectionId,
                    "username": config.username,
                    "callerId": config.callerId,
                    "media": config.media,
                    "uuid": action.callUUID.uuidString
                ]
                self.sendEvent(eventName: eventName, data: eventData)
            }
        }

        // Clean up all call state
        callStateQueue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            self.answeredCalls.remove(action.callUUID)
            self.incomingCalls.remove(action.callUUID)
            self.canceledCalls.remove(action.callUUID)
            self.outgoingCalls.remove(action.callUUID)
        }

        // Clean up the call configuration
        registryAccessQueue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            if self.connectionIdRegistry[action.callUUID] != nil {
                self.connectionIdRegistry[action.callUUID] = nil
                NSLog("[CallkitOnesignal] Removed config for ended call: \(action.callUUID)")
            }
        }
        
        action.fulfill()
    }
}

// PushKit Delegate

extension CallkitOnesignal: PKPushRegistryDelegate {

    private func extractString(forKey key: String, from payload: [AnyHashable: Any]) -> String? {
        if let value = payload[key] as? String { return value }
        if let custom = payload["custom"] as? [AnyHashable: Any],
           let additional = custom["a"] as? [AnyHashable: Any],
           let value = additional[key] as? String {
            return value
        }
        return nil
    }

    public func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        let token = pushCredentials.token.map { String(format: "%02.2hhx", $0) }.joined()
        voipToken = token
    }

    public func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType, completion: @escaping () -> Void) {
        guard type == .voIP else {
            completion()
            return
        }

        if provider == nil {
            setupCallKit()
        }

        endAllCalls()

        let callUUID = UUID()
        let username = extractString(forKey: "username", from: payload.dictionaryPayload) ?? "Unknown"
        let callerId = extractString(forKey: "callerId", from: payload.dictionaryPayload) ?? callUUID.uuidString
        let media = extractString(forKey: "media", from: payload.dictionaryPayload) ?? "audio"

        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: callerId)
        update.hasVideo = (media == "video")
        update.localizedCallerName = username
        
        update.supportsDTMF = false
        update.supportsHolding = false
        update.supportsGrouping = false
        update.supportsUngrouping = false

        // Store call configuration in registry for later reference
        let config = CallConfig(
            connectionId: callerId,
            username: username,
            callerId: callerId,
            media: media
        )
        registryAccessQueue.async(flags: .barrier) { [weak self] in
            self?.connectionIdRegistry[callUUID] = config
        }

        provider?.reportNewIncomingCall(with: callUUID, update: update) { [weak self] error in
            if let error = error {
                NSLog("[CallkitOnesignal] Failed to report incoming call: \(error.localizedDescription)")
            } else {
                // Track that this call was reported as incoming
                self?.callStateQueue.async(flags: .barrier) {
                    guard let self = self else { return }
                    self.incomingCalls.insert(callUUID)
                    NSLog("[CallkitOnesignal] Incoming call reported and tracked: \(callUUID) - total incoming: \(self.incomingCalls.count)")
                }
            }
            
            completion()
            DispatchQueue.main.async {
                let eventData: [String: Any] = [
                    "connectionId": callerId,
                    "username": username,
                    "callerId": callerId,
                    "media": media,
                    "uuid": callUUID.uuidString
                ]
                
                self?.sendEvent(eventName: "incoming", data: eventData)
            }
        }
    }
}

// Supporting Types

extension CallkitOnesignal {
    struct CallConfig {
        let connectionId: String
        let username: String
        let callerId: String
        let media: String
    }
    
}

protocol CallkitOnesignalDelegate: AnyObject {
    func notifyEvent(eventName: String, data: [String: Any])
}
