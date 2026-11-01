// swift-tools-version:5.9
import PackageDescription

// Platform-independent core of ChipID: MRZ parsing, access-key input, CSCA master list,
// Passive and Active Authentication verification. Runs (and is tested) on macOS and iOS.
let package = Package(
    name: "ChipCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "ChipCore", targets: ["ChipCore"])],
    dependencies: [
        .package(url: "https://github.com/krzyzanowskim/OpenSSL-Package.git", .upToNextMinor(from: "3.6.0001")),
    ],
    targets: [
        .target(name: "ChipCore", dependencies: [.product(name: "OpenSSL", package: "OpenSSL-Package")]),
        .testTarget(name: "ChipCoreTests", dependencies: ["ChipCore"], resources: [.copy("Fixtures")]),
    ]
)
