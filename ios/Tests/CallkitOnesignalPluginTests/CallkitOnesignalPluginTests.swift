import XCTest
@testable import CallkitOnesignalPlugin

class CallkitOnesignalPluginTests: XCTestCase {
    override func setUp() {
        super.setUp()
        // Put setup code here. This method is called before the invocation of each test method in the class.
    }

    override func tearDown() {
        // Put tearDown code here. This method is called after the invocation of each test method in the class.
        super.tearDown()
    }

    func testPluginInitialization() {
        // Test that the plugin can be initialized
        let plugin = CallkitOnesignalPlugin()
        XCTAssertNotNil(plugin)
        XCTAssertEqual(plugin.identifier, "CallkitOnesignalPlugin")
        XCTAssertEqual(plugin.jsName, "CallkitOnesignal")
    }

    func testImplementationInitialization() {
        // Test that the implementation can be initialized
        let implementation = CallkitOnesignal()
        XCTAssertNotNil(implementation)
    }

    func testApnsEnvironmentDetection() {
        // Test APNs environment detection
        let implementation = CallkitOnesignal()
        let environment = implementation.getApnsEnvironment()
        
        #if DEBUG
        XCTAssertEqual(environment, "debug")
        #else
        XCTAssertEqual(environment, "production")
        #endif
    }
}
