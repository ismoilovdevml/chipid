package com.ismoilovdev.chipid

import com.ismoilovdev.chipid.core.Bc
import com.ismoilovdev.chipid.core.MasterList
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.x509.Certificate
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MasterListTest {
    private val fr = TestPki.hierarchy("FR")
    private val jp = TestPki.hierarchy("JP")

    /** A master list signed by [signer].dsc, whose issuer is [signer].csca (included in the list). */
    private fun masterList(signer: TestPki.Hierarchy): ByteArray {
        val certs = ASN1EncodableVector().apply {
            add(Certificate.getInstance(fr.csca.encoded))
            add(Certificate.getInstance(jp.csca.encoded))
            add(Certificate.getInstance(signer.csca.encoded))
        }
        val content = DERSequence(arrayOf(ASN1Integer(0), DERSet(certs))).encoded
        val gen = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(
                JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().setProvider(Bc.provider).build())
                    .build(JcaContentSignerBuilder("SHA256withECDSA").setProvider(Bc.provider).build(signer.dscKeys.private), signer.dsc),
            )
            addCertificates(JcaCertStore(listOf(signer.dsc)))
        }
        return gen.generate(CMSProcessableByteArray(ASN1ObjectIdentifier("2.23.136.1.1.2"), content), true).encoded
    }

    @Test
    fun parsesSyntheticMasterList() {
        val de = TestPki.hierarchy("DE")
        val ml = MasterList.parse(masterList(de), pinnedRoots = setOf(MasterList.spkiSha256(de.csca)))
        assertEquals(3, ml.certificates.size)
        assertTrue(ml.signatureValid)
        assertTrue(ml.signerTrusted)
        assertEquals(setOf("FR", "JP", "DE"), ml.countries())
    }

    @Test
    fun listSignedByUnpinnedAuthorityIsNotTrusted() {
        // An attacker re-signs the list with their own "CSCA": the CMS signature is intact,
        // but the signer does not chain to a pinned root.
        val de = TestPki.hierarchy("DE")
        val attacker = TestPki.hierarchy("DE")
        val ml = MasterList.parse(masterList(attacker), pinnedRoots = setOf(MasterList.spkiSha256(de.csca)))
        assertTrue(ml.signatureValid)
        assertFalse(ml.signerTrusted)
    }

    /** Runs only when the fetched BSI list is present: real list, production pins. */
    @Test
    fun bundledBsiListChainsToPinnedGermanCsca() {
        val f = File("src/main/assets/masterlist.ml")
        if (!f.exists()) return
        val ml = MasterList.parse(f.readBytes())
        assertTrue(ml.signatureValid)
        assertTrue(ml.signerTrusted)
        assertTrue(ml.certificates.size > 500)
        assertTrue(ml.countries().size > 100)
        assertTrue(ml.byCountry("DE").isNotEmpty())
    }
}
