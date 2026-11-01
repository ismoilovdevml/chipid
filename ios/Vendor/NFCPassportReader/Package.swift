// swift-tools-version:5.4
// Vendored from github.com/AndyQ/NFCPassportReader 2.3.3 (6e37f1a), MIT. ChipID changes: see CHANGES.md.

import PackageDescription

let package = Package(
    name: "NFCPassportReader",
    platforms: [.iOS("15.0")],
    products: [
        .library(name: "NFCPassportReader", targets: ["NFCPassportReader"]),
    ],
    dependencies: [
        .package(url: "https://github.com/krzyzanowskim/OpenSSL-Package.git", .upToNextMinor(from: "3.6.0001")),
    ],
    targets: [
        .target(name: "NFCPassportReader", dependencies: [.product(name: "OpenSSL", package: "OpenSSL-Package")]),
    ]
)
