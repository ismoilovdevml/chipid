//
//  Logging.swift
//  NFCTest
//
//  Created by Andy Qua on 11/06/2019.
//  Copyright © 2019 Andy Qua. All rights reserved.
//

import Foundation
import OSLog


// ChipID: every logger is disabled. Upstream logs MRZ keys, session keys and APDUs at debug level,
// and none of that may reach the unified log.
extension Logger {
    static let passportReader = Logger(.disabled)
    static let tagReader = Logger(.disabled)
    static let secureMessaging = Logger(.disabled)
    static let openSSL = Logger(.disabled)
    static let bac = Logger(.disabled)
    static let chipAuth = Logger(.disabled)
    static let pace = Logger(.disabled)
}
