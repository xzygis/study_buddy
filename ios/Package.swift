// swift-tools-version: 6.0
import PackageDescription

// The app compiles these same Core files directly. This package runs failure-path tests on macOS.
let package = Package(
    name: "StudyAlarmCore",
    platforms: [.macOS(.v14)],
    products: [.library(name: "StudyAlarmCore", targets: ["StudyAlarmCore"])],
    targets: [
        .target(name: "StudyAlarmCore", path: "StudyAlarm/Core"),
        .testTarget(name: "StudyAlarmCoreTests", dependencies: ["StudyAlarmCore"], path: "Tests")
    ]
)
