// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Maximilien0405CapacitorCallkitOnesignal",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "Maximilien0405CapacitorCallkitOnesignal",
            targets: ["CallkitOnesignalPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "CallkitOnesignalPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/CallkitOnesignalPlugin"),
        .testTarget(
            name: "CallkitOnesignalPluginTests",
            dependencies: ["CallkitOnesignalPlugin"],
            path: "ios/Tests/CallkitOnesignalPluginTests")
    ]
)
