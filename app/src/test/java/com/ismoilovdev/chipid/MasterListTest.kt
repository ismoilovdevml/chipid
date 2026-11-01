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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MasterListTest {
    @Test
    fun parsesSyntheticMasterList() {
        val a = TestPki.hierarchy("UZ")
        val b = TestPki.hierarchy("TJ")
        val signer = TestPki.hierarchy("DE")

        val certs = ASN1EncodableVector().apply {
            add(Certificate.getInstance(a.csca.encoded))
            add(Certificate.getInstance(b.csca.encoded))
        }
        val content = DERSequence(arrayOf(ASN1Integer(0), DERSet(certs))).encoded
        val gen = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(
                JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().setProvider(Bc.provider).build())
                    .build(JcaContentSignerBuilder("SHA256withECDSA").setProvider(Bc.provider).build(signer.dscKeys.private), signer.dsc),
            )
            addCertificates(JcaCertStore(listOf(signer.dsc)))
        }
        val cms = gen.generate(
            CMSProcessableByteArray(ASN1ObjectIdentifier("2.23.136.1.1.2"), content), true,
        ).encoded

        val ml = MasterList.parse(cms)
        assertEquals(2, ml.certificates.size)
        assertTrue(ml.signatureValid)
        assertEquals(setOf("UZ", "TJ"), ml.countries())
    }

    /** Runs only when the bundled BSI list is present; proves Uzbekistan's CSCAs are in it. */
    @Test
    fun bundledBsiListContainsUzbekistan() {
        val f = File("src/main/assets/masterlist.ml")
        if (!f.exists()) return
        val ml = MasterList.parse(f.readBytes())
        assertTrue(ml.signatureValid)
        assertTrue(ml.certificates.size > 500)
        assertTrue(ml.byCountry("UZ").size >= 4)
    }
}
