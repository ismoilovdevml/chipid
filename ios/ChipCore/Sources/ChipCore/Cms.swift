import Foundation
import OpenSSL

/// CMS SignedData (RFC 5652) as used by EF.SOD and CSCA master lists: one signer whose certificate
/// is embedded. The signature is checked against that certificate only; whether the certificate
/// itself is trustworthy is the caller's decision.
struct SignedData {
    let content: [UInt8]
    /// All certificates embedded in the CMS structure.
    let certificates: [Certificate]
    /// The embedded certificate that matches the SignerInfo, if any.
    let signer: Certificate?
    /// Signature over the signed attributes and the content digest both verified.
    let signatureValid: Bool
    let signatureAlgorithm: String?
    let verifyError: String?

    static func parse(_ der: [UInt8]) throws -> SignedData {
        try Bio.withMem(der) { inBio in
            guard let cms = d2i_CMS_bio(inBio, nil) else { throw CryptoError("not a CMS structure") }
            defer { CMS_ContentInfo_free(cms) }

            guard let pp = CMS_get0_content(cms), let os = pp.pointee else { throw CryptoError("CMS has no encapsulated content") }
            let content = Array(UnsafeBufferPointer(start: ASN1_STRING_get0_data(os), count: Int(ASN1_STRING_length(os))))

            var certs: [Certificate] = []
            if let stack = CMS_get1_certs(cms) {
                for i in 0..<OPENSSL_sk_num(stack) {
                    let x = OpaquePointer(OPENSSL_sk_value(stack, i))!
                    X509_up_ref(x)
                    certs.append(try Certificate(owning: x))
                }
                OPENSSL_sk_pop_free(stack, { X509_free(OpaquePointer($0)) })
            }

            var signer: Certificate?
            var sigAlg: String?
            if let infos = CMS_get0_SignerInfos(cms), OPENSSL_sk_num(infos) > 0 {
                let si = OpaquePointer(OPENSSL_sk_value(infos, 0))!
                signer = certs.first { CMS_SignerInfo_cert_cmp(si, $0.x509) == 0 }
                var sig: UnsafeMutablePointer<X509_ALGOR>? = nil
                CMS_SignerInfo_get0_algs(si, nil, nil, nil, &sig)
                if let sig {
                    var obj: OpaquePointer? = nil
                    X509_ALGOR_get0(&obj, nil, nil, sig)
                    sigAlg = Oid.name(obj)
                }
            }

            // Verify signature and content digest with the embedded signer certificate, no chain.
            ERR_clear_error()
            let out = BIO_new(BIO_s_mem())
            defer { BIO_free(out) }
            let ok = CMS_verify(cms, nil, nil, nil, out, UInt32(CMS_NO_SIGNER_CERT_VERIFY | CMS_BINARY)) == 1
            let error = ok ? nil : CryptoError("CMS signature does not verify").description
            return SignedData(content: content, certificates: certs, signer: signer, signatureValid: ok,
                              signatureAlgorithm: sigAlg, verifyError: error)
        }
    }
}
