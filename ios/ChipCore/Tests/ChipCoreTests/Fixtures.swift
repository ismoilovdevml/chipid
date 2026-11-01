import Foundation
@testable import ChipCore

/// Vectors made by BouncyCastle/JMRTD (android/app/src/test/.../IosFixtureGen.kt), so the OpenSSL code is
/// checked against an independent implementation.
enum BcFixtures {
    static let all: [String: String] = {
        let url = Bundle.module.url(forResource: "bc", withExtension: "json", subdirectory: "Fixtures")!
        return try! JSONSerialization.jsonObject(with: Data(contentsOf: url)) as! [String: String]
    }()

    static func bytes(_ key: String) -> [UInt8] { hexBytes(all[key]!) }
    static func cert(_ key: String) -> Certificate { try! Certificate(der: bytes(key)) }
}
