import Foundation
import FirebaseAuth
import FirebaseDatabase
import UserNotifications

class RealTimeDataService {
    private var databaseReference: DatabaseReference!
    private var groupCallListener: DatabaseHandle?

    // Function to handle real-time listener with auto-abort on conditions
    func handleRealtimeListener(orgId: String, userId: String, roomName: String, abortCall: @escaping () -> Void) {
        guard let user = Auth.auth().currentUser else {
            print("Error: User not logged in.")
            return
        }
        
        let loggedInUserId = user.uid
        let realtimePath = "Realtime/\(orgId)/Users/\(loggedInUserId)/groupCallsInProgress/\(roomName)"
        print("Realtime Path: \(realtimePath)")
        
        databaseReference = Database.database().reference().child(realtimePath)
        
        // Start observing changes in the database
        groupCallListener = databaseReference.observe(.value, with: { snapshot in
            guard snapshot.exists(), let details = snapshot.value as? [String: Any] else {
                self.hideVideoCallConfirmation()
                abortCall()
                return
            }
            
            if let groupCallDetails = RoomDetails(dictionary: details) {
                groupCallDetails.isGroup ? self.handleGroupVideoCall(details: groupCallDetails, userId: loggedInUserId) {abortCall()} :
                self.handleP2PVideoCall(details: groupCallDetails, userId: loggedInUserId) {abortCall()}
            } else {
                self.hideVideoCallConfirmation()
                abortCall()
            }
        })
    }
    
    // Handle Group Video Call
    private func handleGroupVideoCall(details: RoomDetails, userId: String, callback: @escaping () -> Void) {
        guard let members = details.members else { return }
        
        for user in members where user.userId == userId {
            if let status = user.status, ["cancel", "declined", "removed", "unanswered", "accepted"].contains(status) {
                callback()
                hideVideoCallConfirmation()
            }
        }
    }
    
    // Handle Peer-to-Peer Video Call
    private func handleP2PVideoCall(details: RoomDetails, userId: String, callback: @escaping () -> Void) {
        guard let members = details.members else { return }
        
        for user in members where user.userId == userId {
            if let status = user.status, ["cancel", "declined", "removed", "unanswered", "accepted"].contains(status) {
                callback()
                hideVideoCallConfirmation()
            }
        }
    }
    
    // Hide Video Call Confirmation
    private func hideVideoCallConfirmation() {
        print("Hiding video call confirmation.")

        // Cancel notifications with specific identifiers
        let notificationCenter = UNUserNotificationCenter.current()
        notificationCenter.removeDeliveredNotifications(withIdentifiers: ["2"]) // Update identifier as needed
        
        // Remove listener from Firebase if active
        if let listener = groupCallListener {
            databaseReference.removeObserver(withHandle: listener)
            groupCallListener = nil
        }
    }
}

// Model for Room Details
class RoomDetails {
    var isGroup: Bool
    var members: [User]?
    
    init?(dictionary: [String: Any]) {
        guard let isGroup = dictionary["group"] as? Bool else { return nil }
        self.isGroup = isGroup
        self.members = (dictionary["members"] as? [[String: Any]])?.compactMap { User(dictionary: $0) }
    }
}

// Model for User Data
class User {
    var userId: String
    var status: String?
    
    init(dictionary: [String: Any]) {
        self.userId = dictionary["userId"] as? String ?? ""
        self.status = dictionary["status"] as? String
    }
}
