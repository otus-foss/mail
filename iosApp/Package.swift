// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "PushSupport",
    platforms: [.macOS(.v12), .iOS(.v15)],
    products: [.library(name: "PushSupport", targets: ["PushSupport"])],
    targets: [
        .target(name: "PushSupport", path: "PushSupport"),
        .testTarget(name: "PushSupportTests", dependencies: ["PushSupport"], path: "PushSupportTests")
    ]
)
