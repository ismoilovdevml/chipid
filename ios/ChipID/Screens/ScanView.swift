import AVFoundation
import ChipCore
import SwiftUI
import Vision

struct ScanView: View {
    @EnvironmentObject var m: AppModel
    @StateObject private var scanner = MrzScanner()

    var body: some View {
        ScreenFrame(bg: C.scanBg, gap: 16, scroll: false) {
            TopBar(title: "Step 1 of 3") {
                RoundIconButton(icon: Ic.back, label: "Back") { m.route = .home }
            } right: {
                if scanner.hasTorch {
                    RoundIconButton(icon: Ic.flash, label: "Toggle torch", tint: scanner.torch ? C.warn : C.text) { scanner.toggleTorch() }
                }
            }
            StepBar(step: 1)
            VStack(spacing: 6) {
                Text(m.docType == .passport ? "Fit the photo page in the frame" : "Fit the back of the card in the frame")
                    .font(T.h2).foregroundStyle(C.text)
                Text(m.docType == .passport ? "The two lines at the bottom (MRZ) must be sharp" : "The three lines of text (MRZ) must be sharp")
                    .font(T.inter(14)).foregroundStyle(C.muted)
            }
            .multilineTextAlignment(.center).frame(maxWidth: .infinity)

            ZStack {
                switch scanner.state {
                case .running:
                    CameraPreview(session: scanner.session)
                    FrameOverlay(found: scanner.result != nil)
                    VStack {
                        Spacer()
                        StatusPill(
                            text: scanner.result != nil ? "MRZ read and check digits valid" : scanner.near ? "MRZ found — reading…" : "Looking for the MRZ…",
                            color: scanner.result != nil || scanner.near ? C.success : C.text2)
                            .padding(.bottom, 18)
                    }
                case .denied:
                    CameraMessage(text: "ChipID needs the camera to read the MRZ, the lines of text that unlock the chip.",
                                  button: "Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    }
                case .unavailable:
                    CameraMessage(text: "No camera is available here. Enter the details by hand instead.", button: "Enter manually") { m.route = .manual }
                case .idle:
                    ProgressView().tint(C.primary)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(C.surface2)
            .clipShape(RoundedRectangle(cornerRadius: 24))

            HStack(spacing: 8) {
                let r = scanner.result
                let color = r != nil ? C.success : C.faint
                ValueTile(label: "Number", value: r.map { ReportModel.mask($0.documentNumber) } ?? "—", valueColor: color, compact: true)
                ValueTile(label: "Born", value: r.map { ReportModel.date($0.dateOfBirth, birth: true) } ?? "—", valueColor: color, compact: true)
                ValueTile(label: "Expires", value: r.map { ReportModel.date($0.dateOfExpiry) } ?? "—", valueColor: color, compact: true)
            }
            if scanner.result != nil {
                GhostButton(text: "Scan again", height: 40) { scanner.reset() }
            }
        } bottom: {
            PrimaryButton(text: "Correct — continue", enabled: scanner.result != nil) {
                if let r = scanner.result {
                    m.prefill = r.accessKey
                    m.startRead(r.accessKey)
                }
            }
            GhostButton(text: "Enter manually", icon: Ic.pencil) {
                if let r = scanner.result { m.prefill = r.accessKey }
                m.route = .manual
            }
        }
        .onAppear { scanner.start() }
        .onDisappear { scanner.stop() }
        .sensoryFeedback(.success, trigger: scanner.result != nil)
    }
}

private struct CameraMessage: View {
    let text: String
    let button: String
    let action: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            IconTile(icon: Ic.camera, bg: C.iconBg, tint: C.primary)
            Text(text).font(T.body).foregroundStyle(C.text).multilineTextAlignment(.center)
            Text("Frames are recognised on the phone and never saved or sent.").font(T.small).foregroundStyle(C.muted).multilineTextAlignment(.center)
            PrimaryButton(text: button, action: action).frame(width: 220)
        }
        .padding(24)
    }
}

private struct StatusPill: View {
    let text: String
    let color: Color

    var body: some View {
        HStack(spacing: 8) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(text).font(T.inter(13, .medium)).foregroundStyle(color)
        }
        .padding(.horizontal, 14).padding(.vertical, 8)
        .background(Color.black.opacity(0.6), in: Capsule())
    }
}

/// Corner brackets and an MRZ band hint over the camera preview; the outside is dimmed.
private struct FrameOverlay: View {
    let found: Bool

    var body: some View {
        GeometryReader { g in
            let accent = found ? C.success : C.primary
            let inset: CGFloat = 20
            // Frame keeps a document's 1.42:1 aspect inside whatever height the box gets.
            let frameH = min((g.size.width - 2 * inset) / 1.42, g.size.height - 32)
            let rect = CGRect(x: inset, y: (g.size.height - frameH) / 2, width: g.size.width - 2 * inset, height: frameH)
            let band = CGRect(x: rect.minX + 12, y: rect.maxY - rect.height * 0.30, width: rect.width - 24, height: rect.height * 0.30 - 12)
            ZStack {
                Path { p in
                    p.addRect(CGRect(origin: .zero, size: g.size))
                    p.addRoundedRect(in: rect, cornerSize: CGSize(width: 10, height: 10))
                }
                .fill(Color.black.opacity(0.45), style: FillStyle(eoFill: true))
                RoundedRectangle(cornerRadius: 8).fill(accent.opacity(found ? 0.18 : 0.08)).frame(width: band.width, height: band.height).position(x: band.midX, y: band.midY)
                RoundedRectangle(cornerRadius: 8).stroke(accent.opacity(0.9), lineWidth: 2).frame(width: band.width, height: band.height).position(x: band.midX, y: band.midY)
                Path { p in
                    let arm: CGFloat = 28
                    for (x, y, dx, dy) in [(rect.minX, rect.minY, 1.0, 1.0), (rect.maxX, rect.minY, -1.0, 1.0), (rect.minX, rect.maxY, 1.0, -1.0), (rect.maxX, rect.maxY, -1.0, -1.0)] {
                        p.move(to: CGPoint(x: x, y: y + dy * arm))
                        p.addLine(to: CGPoint(x: x, y: y + dy * 10))
                        p.addQuadCurve(to: CGPoint(x: x + dx * 10, y: y), control: CGPoint(x: x, y: y))
                        p.addLine(to: CGPoint(x: x + dx * arm, y: y))
                    }
                }
                .stroke(accent, style: StrokeStyle(lineWidth: 3, lineCap: .round))
            }
        }
        .allowsHitTesting(false)
    }
}

private struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var preview: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    func makeUIView(context: Context) -> PreviewView {
        let v = PreviewView()
        v.preview.session = session
        v.preview.videoGravity = .resizeAspectFill
        return v
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}
}

/// Camera frames -> Vision text recognition -> MRZ parser. Reports an MRZ once two consecutive
/// frames agree on the chip key (number, birth date, expiry). Frames stay in memory only.
@MainActor
final class MrzScanner: NSObject, ObservableObject {
    enum State { case idle, running, denied, unavailable }

    @Published var state = State.idle
    @Published var result: Mrz.Result?
    @Published var near = false
    @Published var torch = false
    @Published var hasTorch = false

    nonisolated let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "chipid.camera")
    private var device: AVCaptureDevice?
    private nonisolated(unsafe) var busy = false
    private nonisolated(unsafe) var done = false
    private nonisolated(unsafe) var last: AccessKey?

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configure()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                Task { @MainActor in ok ? self.configure() : (self.state = .denied) }
            }
        default: state = .denied
        }
    }

    private func configure() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device) else {
            state = .unavailable
            return
        }
        self.device = device
        hasTorch = device.hasTorch
        let output = AVCaptureVideoDataOutput()
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(self, queue: queue)
        let session = self.session
        queue.async {
            session.beginConfiguration()
            session.sessionPreset = .hd1920x1080
            if session.inputs.isEmpty, session.canAddInput(input) { session.addInput(input) }
            if session.outputs.isEmpty, session.canAddOutput(output) { session.addOutput(output) }
            session.commitConfiguration()
            session.startRunning()
        }
        if (try? device.lockForConfiguration()) != nil {
            if device.isFocusModeSupported(.continuousAutoFocus) { device.focusMode = .continuousAutoFocus }
            device.unlockForConfiguration()
        }
        state = .running
    }

    func stop() {
        let session = self.session
        queue.async { session.stopRunning() }
        torch = false
    }

    func reset() {
        queue.async { self.last = nil; self.done = false }
        result = nil
        near = false
    }

    func toggleTorch() {
        guard let device, device.hasTorch, (try? device.lockForConfiguration()) != nil else { return }
        torch.toggle()
        device.torchMode = torch ? .on : .off
        device.unlockForConfiguration()
    }
}

extension MrzScanner: AVCaptureVideoDataOutputSampleBufferDelegate {
    nonisolated func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard !done, !busy, let pixels = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        busy = true
        defer { busy = false }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        // The back camera delivers landscape frames; the phone is held upright.
        try? VNImageRequestHandler(cvPixelBuffer: pixels, orientation: .right).perform([request])
        let lines = (request.results ?? []).compactMap { $0.topCandidates(1).first?.string }
        // MRZ filler visible: tell the UI we are close.
        let close = lines.contains { $0.filter { $0 == "<" }.count >= 5 }
        let found = Mrz.find(lines)
        var confirmed: Mrz.Result?
        if let found {
            if found.accessKey == last, !done { done = true; confirmed = found }
            last = found.accessKey
        }
        Task { @MainActor in
            self.near = close
            if let confirmed { self.result = confirmed }
        }
    }
}
