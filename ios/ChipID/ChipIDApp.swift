import ChipCore
import NFCPassportReader
import SwiftUI

enum Route { case home, scan, manual, nfc, help, result, details, about }
enum DocType { case passport, idCard }

@MainActor
final class AppModel: ObservableObject {
    @Published var route = Route.home
    @Published var docType = DocType.passport
    @Published var trust: TrustStore?
    /// MRZ key from a camera scan, offered for correction on the manual screen.
    @Published var prefill: AccessKey?
    @Published var progress: ChipProgress?
    @Published var error: String?
    @Published var report: ReadReport?
    @Published var reading = false
    private var key: AccessKey?

    init() {
        Task.detached(priority: .userInitiated) {
            let t = TrustStore.load()
            await MainActor.run {
                self.trust = t
                #if DEBUG
                self.openScreenFromLaunchArguments()
                #endif
            }
        }
    }

    #if DEBUG
    /// `-screen result|details|about|manual|nfc|scan|idcard` jumps straight to a screen (screenshots).
    private func openScreenFromLaunchArguments() {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-screen"), i + 1 < args.count else { return }
        switch args[i + 1] {
        case "result": openDemo()
        case "details": openDemo(); route = .details
        case "about": route = .about
        case "manual": route = .manual
        case "idcard": docType = .idCard; route = .manual
        case "scan": route = .scan
        case "nfc": startRead(.mrz(documentNumber: "L898902C3", dateOfBirth: "740812", dateOfExpiry: "120415"))
        default: break
        }
    }
    #endif

    func startRead(_ k: AccessKey) {
        key = k
        progress = nil
        error = nil
        route = .nfc
        read()
    }

    func read() {
        guard let k = key, !reading else { return }
        guard ChipReader.nfcAvailable else {
            error = "This device cannot read NFC chips (an iPhone XS or newer is needed; the simulator has no NFC)."
            return
        }
        reading = true
        error = nil
        progress = nil
        Task {
            defer { reading = false }
            do {
                let r = try await ChipReader.read(key: k, anchors: trust?.anchors ?? []) { [weak self] p in self?.progress = p }
                report = r
                key = nil // the key has done its job; do not keep it around
                prefill = nil
                route = .result
            } catch let e as ChipReadError where e.cancelled {
                progress = nil
            } catch {
                self.error = "\(error)"
            }
        }
    }

    func openDemo() {
        report = ChipReader.demo(anchors: trust?.anchors ?? [])
        if report != nil { route = .result }
    }

    func cancelRead() {
        key = nil
        route = .home
    }

    func resetAll() {
        key = nil; prefill = nil; report = nil; progress = nil; error = nil
        route = .home
    }
}

@main
struct ChipIDApp: App {
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                // Personal data on screen: keep it out of the app switcher snapshot.
                .overlay { if phase != .active { PrivacyCover() } }
                .preferredColorScheme(.dark)
                .tint(C.primary)
        }
    }
}

struct RootView: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        ZStack {
            switch m.route {
            case .home: HomeView()
            case .scan: ScanView()
            case .manual: ManualView()
            case .nfc: NfcView()
            case .help: HelpView()
            case .result: if let r = m.report { ResultView(report: r) }
            case .details: if let r = m.report { DetailsView(report: r) }
            case .about: AboutView()
            }
        }
        .animation(.easeInOut(duration: 0.18), value: m.route)
    }
}

private struct PrivacyCover: View {
    var body: some View {
        ZStack {
            C.bg.ignoresSafeArea()
            Image("Mark").resizable().renderingMode(.template).foregroundStyle(C.primary).frame(width: 64, height: 64)
        }
    }
}
