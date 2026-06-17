package org.dawid.cisowski.walletassistant.security

import spock.lang.Specification
import spock.lang.Title

import java.nio.charset.StandardCharsets
import java.util.Base64

@Title("HmacSignature utility")
class HmacSignatureSpec extends Specification {

    static final byte[] SECRET = "super-secret-key".getBytes(StandardCharsets.UTF_8)
    static final byte[] OTHER_SECRET = "another-secret-key".getBytes(StandardCharsets.UTF_8)
    static final String CANONICAL = "POST\n/api/expenses\n2025-01-15T10:00:00Z\nbody-hash"

    def "should produce deterministic output for the same input and secret"() {
        when: "the same canonical string is signed twice with the same secret"
        def first = HmacSignature.calculate(CANONICAL, SECRET)
        def second = HmacSignature.calculate(CANONICAL, SECRET)

        then: "both signatures are identical"
        first == second
    }

    def "should produce different output for different inputs"() {
        when: "two different canonical strings are signed with the same secret"
        def first = HmacSignature.calculate(CANONICAL, SECRET)
        def second = HmacSignature.calculate(CANONICAL + "-changed", SECRET)

        then: "the signatures differ"
        first != second
    }

    def "should produce different output for different secrets"() {
        when: "the same canonical string is signed with two different secrets"
        def first = HmacSignature.calculate(CANONICAL, SECRET)
        def second = HmacSignature.calculate(CANONICAL, OTHER_SECRET)

        then: "the signatures differ"
        first != second
    }

    def "should return a non-null, non-empty, Base64-decodable signature"() {
        when: "a canonical string is signed"
        def signature = HmacSignature.calculate(CANONICAL, SECRET)

        then: "the signature is present"
        signature != null
        !signature.isEmpty()

        and: "the signature is valid Base64 that decodes to a HmacSHA256 digest (32 bytes)"
        def decoded = Base64.getDecoder().decode(signature)
        decoded.length == 32
    }

    def "should verify two matching strings as equal"() {
        given: "a signature compared against an identical copy"
        def signature = HmacSignature.calculate(CANONICAL, SECRET)

        expect: "verification succeeds"
        HmacSignature.verify(signature, signature)
    }

    def "should reject two different strings"() {
        expect: "verification of differing strings fails"
        !HmacSignature.verify("signature-a", "signature-b")
    }

    def "should reject verification when expected is null"() {
        expect: "verification fails for a null expected value"
        !HmacSignature.verify(null, "some-signature")
    }

    def "should reject verification when actual is null"() {
        expect: "verification fails for a null actual value"
        !HmacSignature.verify("some-signature", null)
    }

    def "should verify a calculated signature against itself in a round-trip"() {
        given: "a freshly calculated signature"
        def calculated = HmacSignature.calculate(CANONICAL, SECRET)

        when: "the same canonical string is signed again and verified against the original"
        def recalculated = HmacSignature.calculate(CANONICAL, SECRET)

        then: "the round-trip verification succeeds"
        HmacSignature.verify(calculated, recalculated)
    }
}
