/*
 * Copyright (c) 2023-2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.verifier.endpoint.adapter.input.web

import arrow.core.right
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEEncrypter
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTClaimsSet
import eu.europa.ec.eudi.verifier.endpoint.VerifierApplicationTest
import eu.europa.ec.eudi.verifier.endpoint.domain.Clock
import eu.europa.ec.eudi.verifier.endpoint.domain.RequestId
import eu.europa.ec.eudi.verifier.endpoint.domain.TransactionId
import eu.europa.ec.eudi.verifier.endpoint.domain.VerifierConfig
import eu.europa.ec.eudi.verifier.endpoint.port.input.InitTransactionResponse
import eu.europa.ec.eudi.verifier.endpoint.port.input.ResponseModeTO
import eu.europa.ec.eudi.verifier.endpoint.port.input.WalletResponseTO
import eu.europa.ec.eudi.verifier.endpoint.port.out.cfg.GenerateRequestId
import eu.europa.ec.eudi.verifier.endpoint.port.out.jose.GenerateEphemeralEncryptionKeyPair
import eu.europa.ec.eudi.verifier.endpoint.port.out.presentation.ValidateVerifiablePresentation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toKotlinFixedOffsetTimeZone
import kotlinx.serialization.json.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import java.time.ZoneOffset
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Collections

@VerifierApplicationTest([WalletResponseDirectPostJwtValidationsDisabledTest.Config::class])
@TestPropertySource(
    properties = [
        "verifier.maxAge=PT6400M",
        "verifier.response.mode=DirectPostJwt",
        "verifier.clientMetadata.responseEncryption.algorithm=ECDH-ES",
        "verifier.clientMetadata.responseEncryption.method=A128GCM",
        "verifier.jwk.embed=ByValue",
        "verifier.requestJwt.embed=ByReference",
        "verifier.alwaysAcceptWalletResponse=false",
    ],
)
@TestMethodOrder(OrderAnnotation::class)
internal class WalletResponseDirectPostJwtValidationsDisabledTest {
    private val log: Logger = LoggerFactory.getLogger(WalletResponseDirectPostJwtValidationsDisabledTest::class.java)

    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var config: VerifierConfig

    @TestConfiguration
    internal class Config {
        @Bean
        @Primary
        fun validateVerifiablePresentation(): ValidateVerifiablePresentation = ValidateVerifiablePresentation.NoOp
    }

    /**
     * Unit test of flow:
     * - verifier to verifier backend, to post DCQL query
     * - wallet to verifier backend, to post wallet response, an idToken
     * - verifier to verifier backend, to get wallet response
     *
     * @see: <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-response-mode-direct_postjw">OpenId4vp Response Mode "direct_post.jwt"</a>
     */
    @Test
    @Order(value = 1)
    fun `direct_post_jwt vp_token end to end`() =
        runTest {


            val iterations = 100

            var totalInit = 0L
            var totalResponse = 0L
            var totalVerification = 0L

            var minInit = Long.MAX_VALUE
            var maxInit = Long.MIN_VALUE

            var minResponse = Long.MAX_VALUE
            var maxResponse = Long.MIN_VALUE

            var minVerification = Long.MAX_VALUE
            var maxVerification = Long.MIN_VALUE

            fun test(
                query: String,
                vpToken: String,
                asserter: (WalletResponseTO) -> Unit,
            ) {
                // given
                val initTransaction = VerifierApiClient.loadInitTransactionTO(query)

                val initStart = System.nanoTime()

                val transactionInitialized =
                    assertIs<InitTransactionResponse.JwtSecuredAuthorizationRequestTO>(
                        VerifierApiClient.initTransaction(client, initTransaction),
                    )

                val initEnd = System.nanoTime()

                val initLatency = initEnd - initStart

                totalInit += initLatency
                minInit = minOf(minInit, initLatency)
                maxInit = maxOf(maxInit, initLatency)

                val requestId =
                    RequestId(transactionInitialized.requestUri?.removePrefix("http://localhost:0/wallet/request.jwt/")!!)
                val requestObjectJsonResponse: JsonObject =
                    WalletApiClient.getRequestObjectJsonResponse(client, transactionInitialized.requestUri)
                val transactionId = TransactionId(transactionInitialized.transactionId)

                val supportedEncryptionMethods = assertNotNull(requestObjectJsonResponse.supportedEncryptionMethods())
                assertEquals(config.clientMetaData.responseEncryptionOption.encryptionMethods, supportedEncryptionMethods)

                val ecKey = requestObjectJsonResponse.ecKey()
                assertNotNull(ecKey)
                assertNotNull(ecKey.algorithm)
                val supportedAlgorithm = JWEAlgorithm.parse(ecKey.algorithm.name)
                assertEquals(config.clientMetaData.responseEncryptionOption.algorithm, supportedAlgorithm)

                // (wallet) generate JWT with claims
                val jwtClaims: JWTClaimsSet =
                    buildJsonObject {
                        put("state", requestId.value)
                        put("vp_token", Json.decodeFromString(TestUtils.loadResource(vpToken)))
                    }.run { JWTClaimsSet.parse(Json.encodeToString(this)) }

                log.info("plaintextJwtClaims: ${jwtClaims.toJSONObject()}")

                // Request JWT encrypted with ECDH-ES
                val jweHeader =
                    JWEHeader
                        .Builder(supportedAlgorithm, supportedEncryptionMethods.first())
                        .agreementPartyVInfo(Base64URL.encode(initTransaction.nonce!!))
                        .build()
                log.info("header = ${jweHeader.toJSONObject()}")

                // Create the encrypted JWT object
                val encryptedJWT = EncryptedJWT(jweHeader, jwtClaims)

                // Create an encrypter with the specified public EC key
                val encrypter: JWEEncrypter = ECDHEncrypter(ecKey)

                // Do the actual encryption
                encryptedJWT.encrypt(encrypter)

                // Serialise to JWT compact form
                val jwtString: String = encryptedJWT.serialize()
                log.info("jwtString = $jwtString")

                // create a post form url encoded body
                val formEncodedBody: MultiValueMap<String, Any> = LinkedMultiValueMap()
                formEncodedBody.add("response", jwtString)

                // send the wallet response
                val responseStart = System.nanoTime()

                WalletApiClient.directPostJwt(client, requestId, formEncodedBody)

                val responseEnd = System.nanoTime()

                val responseLatency = responseEnd - responseStart

                totalResponse += responseLatency
                minResponse = minOf(minResponse, responseLatency)
                maxResponse = maxOf(maxResponse, responseLatency)
                // when
                val verifyStart = System.nanoTime()

                val response = VerifierApiClient.getWalletResponse(client, transactionId)

                val verifyEnd = System.nanoTime()

                val verificationLatency = verifyEnd - verifyStart

                totalVerification += verificationLatency
                minVerification = minOf(minVerification, verificationLatency)
                maxVerification = maxOf(maxVerification, verificationLatency)
                // then
                assertNotNull(response, "response is null")
                asserter(response)
            }

            repeat(10) {
                test("02-dcql.json", "02-vpToken.json") {
                    val vpToken = assertNotNull(it.vpToken)

                    val waDriverLicence = assertIs<JsonArray>(vpToken["wa_driver_license"])
                    assertEquals(1, waDriverLicence.size)
                    assertIs<JsonObject>(waDriverLicence[0])
                }
            }

            totalInit = 0
            totalResponse = 0
            totalVerification = 0

            minInit = Long.MAX_VALUE
            maxInit = Long.MIN_VALUE

            minResponse = Long.MAX_VALUE
            maxResponse = Long.MIN_VALUE

            minVerification = Long.MAX_VALUE
            maxVerification = Long.MIN_VALUE

            repeat(iterations) {

                test("02-dcql.json", "02-vpToken.json") {
                    val vpToken = assertNotNull(it.vpToken)

                    val waDriverLicence = assertIs<JsonArray>(vpToken["wa_driver_license"])
                    assertEquals(1, waDriverLicence.size)
                    assertIs<JsonObject>(waDriverLicence[0])
                }

            }

            log.info("")
            log.info("================================================")
            log.info("               BENCHMARK RESULTS")
            log.info("================================================")
            log.info("Iterations : {}", iterations)
            log.info("")

            log.info("Init Transaction")
            log.info("   Avg : {} ms", "%.2f".format(totalInit.toDouble() / iterations / 1_000_000))
            log.info("   Min : {} ms", "%.2f".format(minInit / 1_000_000.0))
            log.info("   Max : {} ms", "%.2f".format(maxInit / 1_000_000.0))
            log.info("")

            log.info("Wallet Response")
            log.info("   Avg : {} ms", "%.2f".format(totalResponse.toDouble() / iterations / 1_000_000))
            log.info("   Min : {} ms", "%.2f".format(minResponse / 1_000_000.0))
            log.info("   Max : {} ms", "%.2f".format(maxResponse / 1_000_000.0))
            log.info("")

            log.info("Verification")
            log.info("   Avg : {} ms", "%.2f".format(totalVerification.toDouble() / iterations / 1_000_000))
            log.info("   Min : {} ms", "%.2f".format(minVerification / 1_000_000.0))
            log.info("   Max : {} ms", "%.2f".format(maxVerification / 1_000_000.0))
            log.info("")

            log.info("================================================")
// Test with multiple Verifiable Presentation ...

            // Test with multiple Verifiable Presentation -- single JsonArray that contains one JsonPrimitive and one JsonObject
/*
test("03-dcql.json", "03-vpToken.json") {
    val vpToken = assertNotNull(it.vpToken)

    val employmentInput = assertIs<JsonArray>(vpToken["employment_input"])
    assertEquals(1, employmentInput.size)
    assertIs<JsonPrimitive>(employmentInput[0])

    val employmentInput2 = assertIs<JsonArray>(vpToken["employment_input_2"])
    assertEquals(1, employmentInput2.size)
    assertIs<JsonObject>(employmentInput2[0])
}
 */
}

/**
* Verifies that a Transaction expecting a direct_post.jwt Wallet response, doesn't accept a direct_post Wallet response.
*/

@Test
@Order(2)
fun `parallel direct_post_jwt benchmark`() =
    runBlocking {

        val requestCounts = listOf(100)

        println()
        println("Starting parallel verifier benchmark...")

        requestCounts.forEach { concurrentRequests ->

            val responseTimes = Collections.synchronizedList(mutableListOf<Long>())
            val verificationTimes = Collections.synchronizedList(mutableListOf<Long>())
            val start = System.nanoTime()


            coroutineScope {

                val jobs =
                    List(concurrentRequests) {
                        async {

                            val initTransaction =
                                VerifierApiClient.loadInitTransactionTO("02-dcql.json")

                            val transactionInitialized =
                                assertIs<InitTransactionResponse.JwtSecuredAuthorizationRequestTO>(
                                    VerifierApiClient.initTransaction(client, initTransaction),
                                )

                            val requestId =
                                RequestId(
                                    transactionInitialized.requestUri!!
                                        .removePrefix("http://localhost:0/wallet/request.jwt/")
                                )

                            val requestObjectJsonResponse =
                                WalletApiClient.getRequestObjectJsonResponse(
                                    client,
                                    transactionInitialized.requestUri,
                                )

                            val transactionId =
                                TransactionId(transactionInitialized.transactionId)


                            val supportedEncryptionMethods =
                                assertNotNull(requestObjectJsonResponse.supportedEncryptionMethods())

                            val ecKey =
                                assertNotNull(requestObjectJsonResponse.ecKey())

                            val supportedAlgorithm =
                                JWEAlgorithm.parse(ecKey.algorithm.name)

                            val jwtClaims: JWTClaimsSet =
                                buildJsonObject {
                                    put("state", requestId.value)
                                    put(
                                        "vp_token",
                                        Json.decodeFromString(TestUtils.loadResource("02-vpToken.json")),
                                    )
                                }.run { JWTClaimsSet.parse(Json.encodeToString(this)) }

                            val jweHeader =
                                JWEHeader
                                    .Builder(supportedAlgorithm, supportedEncryptionMethods.first())
                                    .agreementPartyVInfo(Base64URL.encode(initTransaction.nonce!!))
                                    .build()

                            val encryptedJWT = EncryptedJWT(jweHeader, jwtClaims)

                            val encrypter: JWEEncrypter = ECDHEncrypter(ecKey)

                            encryptedJWT.encrypt(encrypter)

                            val jwtString = encryptedJWT.serialize()

                            val formEncodedBody: MultiValueMap<String, Any> = LinkedMultiValueMap()
                            formEncodedBody.add("response", jwtString)

                            val responseStart = System.nanoTime()

                            WalletApiClient.directPostJwt(
                                client,
                                requestId,
                                formEncodedBody,
                            )

                            val responseEnd = System.nanoTime()

                            responseTimes += (responseEnd - responseStart)
                            val verificationStart = System.nanoTime()

                            val response =
                                VerifierApiClient.getWalletResponse(
                                    client,
                                    transactionId,
                                )

                            val verificationEnd = System.nanoTime()

                            verificationTimes += (verificationEnd - verificationStart)
                            assertNotNull(response)

                            val vpToken = assertNotNull(response.vpToken)

                            val waDriverLicence =
                                assertIs<JsonArray>(vpToken["wa_driver_license"])

                            assertEquals(1, waDriverLicence.size)

                            assertIs<JsonObject>(waDriverLicence[0])

                        }
                    }

                jobs.awaitAll()

                val averageResponse =
                    responseTimes.average() / 1_000_000.0

                val averageVerification =
                    verificationTimes.average() / 1_000_000.0

                val minResponse =
                    responseTimes.minOrNull()!! / 1_000_000.0

                val maxResponse =
                    responseTimes.maxOrNull()!! / 1_000_000.0

                val minVerification =
                    verificationTimes.minOrNull()!! / 1_000_000.0

                val maxVerification =
                    verificationTimes.maxOrNull()!! / 1_000_000.0
                val elapsed = System.nanoTime() - start

                println()
                println("==================================================")
                println("      PARALLEL VERIFIER BENCHMARK")
                println("==================================================")
                println("Concurrent Requests : $concurrentRequests")
                println("--------------------------------------------------")

                println("Wallet Response")
                println("   Avg : %.2f ms".format(averageResponse))
                println("   Min : %.2f ms".format(minResponse))
                println("   Max : %.2f ms".format(maxResponse))
                println()

                println("Verification")
                println("   Avg : %.2f ms".format(averageVerification))
                println("   Min : %.2f ms".format(minVerification))
                println("   Max : %.2f ms".format(maxVerification))
                println()

                println("Total Time          : %.2f ms".format(elapsed / 1_000_000.0))
                println(
                    "Throughput          : %.2f req/s".format(
                        concurrentRequests / (elapsed / 1_000_000_000.0)
                    )
                )
                println("Status              : SUCCESS")
                println("==================================================")
            }

        }
























    }
@Test
@Order(value = 3)
fun `with response_mode direct_post_jwt, direct_post wallet responses are rejected`(): Unit =
runBlocking {
// given
val initTransaction =
    VerifierApiClient
        .loadInitTransactionTO(
            "02-dcql.json",
        ).copy(responseMode = ResponseModeTO.DirectPostJwt)
val transactionInitialized =
    assertIs<InitTransactionResponse.JwtSecuredAuthorizationRequestTO>(
        VerifierApiClient.initTransaction(
            client,
            initTransaction,
        ),
    )
val requestId =
    RequestId(transactionInitialized.requestUri?.removePrefix("http://localhost:0/wallet/request.jwt/")!!)
val requestObjectJsonResponse =
    WalletApiClient.getRequestObjectJsonResponse(client, transactionInitialized.requestUri)

val supportedEncryptionMethods = assertNotNull(requestObjectJsonResponse.supportedEncryptionMethods())
assertEquals(config.clientMetaData.responseEncryptionOption.encryptionMethods, supportedEncryptionMethods)

val ecKey = requestObjectJsonResponse.ecKey()
assertNotNull(ecKey)
val supportedAlgorithm = JWEAlgorithm.parse(ecKey.algorithm.name)
assertEquals(config.clientMetaData.responseEncryptionOption.algorithm, supportedAlgorithm)

// (wallet)
// create a post form url encoded body
val formEncodedBody: MultiValueMap<String, Any> = LinkedMultiValueMap()
formEncodedBody.add("state", requestId.value)
formEncodedBody.add("vp_token", TestUtils.loadResource("02-vpToken.json"))

// send the wallet response
// we expect the response submission to fail
try {
    WalletApiClient.directPost(client, requestId, formEncodedBody)
    fail("Expected direct_post submission to fail for direct_post.jwt Presentation")
} catch (error: AssertionError) {
    assertEquals("Status expected:<200 OK> but was:<400 BAD_REQUEST>", error.message)
}
}
}

@VerifierApplicationTest
@TestPropertySource(
properties = [
"verifier.maxAge=PT6400M",
"verifier.response.mode=DirectPostJwt",
"verifier.clientMetadata.responseEncryption.algorithm=ECDH-ES",
"verifier.clientMetadata.responseEncryption.method=A128GCM",
"verifier.jwk.embed=ByValue",
"verifier.requestJwt.embed=ByReference",
"verifier.alwaysAcceptWalletResponse=false",
],
)
internal class WalletResponseDirectPostJwtValidationsEnabledTest {
@Autowired
private lateinit var client: WebTestClient

@Autowired
private lateinit var config: VerifierConfig

@Test
fun `verifier accepts unencrypted error responses even when direct_post_jwt is expected`() =
runTest {
val initTransaction = VerifierApiClient.loadInitTransactionTO("06-pidPlusMdl-dcql.json")

val transactionDetails =
    assertIs<InitTransactionResponse.JwtSecuredAuthorizationRequestTO>(
        VerifierApiClient.initTransaction(client, initTransaction),
    )
WalletApiClient.getRequestObjectJsonResponse(client, transactionDetails.requestUri!!)

val requestId = RequestId(transactionDetails.requestUri.removePrefix("http://localhost:0/wallet/request.jwt/"))

val walletResponse =
    LinkedMultiValueMap<String, Any>()
        .apply {
            add("state", requestId.value)
            add("error", "error")
        }

WalletApiClient.directPost(client, requestId, walletResponse)

val expectedWalletResponseTO =
    WalletResponseTO(
        error = "error",
    )
assertEquals(
    expectedWalletResponseTO,
    VerifierApiClient.getWalletResponse(client, TransactionId(transactionDetails.transactionId)),
)
}

@Test
fun `when wallet posts sd-jwt-vc with invalid status list details, post fails`() =
runTest {
val initTransaction = VerifierApiClient.loadInitTransactionTO("07-ehicSdJwtVc-dcql.json")
val transactionDetails =
    assertIs<InitTransactionResponse.JwtSecuredAuthorizationRequestTO>(
        VerifierApiClient.initTransaction(
            client,
            initTransaction,
        ),
    )
val requestObjectJsonResponse =
    WalletApiClient.getRequestObjectJsonResponse(client, transactionDetails.requestUri!!)

val supportedEncryptionMethods = assertNotNull(requestObjectJsonResponse.supportedEncryptionMethods())
assertEquals(config.clientMetaData.responseEncryptionOption.encryptionMethods, supportedEncryptionMethods)

val ecKey = requestObjectJsonResponse.ecKey()
assertNotNull(ecKey)
assertNotNull(ecKey.algorithm)
val supportedAlgorithm = JWEAlgorithm.parse(ecKey.algorithm.name)

val requestId =
    RequestId(transactionDetails.requestUri.removePrefix("http://localhost:0/wallet/request.jwt/"))
val encryptedJwt =
    run {
        val jwtClaims: JWTClaimsSet =
            buildJsonObject {
                put("state", requestId.value)
                put("vp_token", Json.decodeFromString(TestUtils.loadResource("07-ehicSdJwtVc-vpToken.json")))
            }.run { JWTClaimsSet.parse(Json.encodeToString(this)) }

        val jweHeader =
            JWEHeader
                .Builder(supportedAlgorithm, supportedEncryptionMethods.first())
                .agreementPartyVInfo(Base64URL.encode(initTransaction.nonce!!))
                .build()

        EncryptedJWT(jweHeader, jwtClaims)
    }.apply { encrypt(ECDHEncrypter(ecKey)) }

val walletResponse =
    LinkedMultiValueMap<String, Any>()
        .apply {
            add("response", encryptedJwt.serialize())
        }

try {
    WalletApiClient.directPostJwt(client, requestId, walletResponse)
    fail("Expected to fail but didn't")
} catch (error: AssertionError) {
    assertEquals("Status expected:<200 OK> but was:<400 BAD_REQUEST>", error.message)
}
}
}

@VerifierApplicationTest([DeviceResponseValidationTest.Config::class])
@TestPropertySource(
properties = [
"verifier.maxAge=PT6400M",
"verifier.response.mode=DirectPostJwt",
"verifier.clientMetadata.responseEncryption.algorithm=ECDH-ES",
"verifier.clientMetadata.responseEncryption.method=A128GCM",
"verifier.jwk.embed=ByValue",
"verifier.requestJwt.embed=ByReference",
"verifier.alwaysAcceptWalletResponse=false",
"verifier.mdoc.redirectUriClientIdInDeviceAuthHandover=false",
"verifier.validation.sdJwtVc.statusCheck.enabled=false",
"verifier.attestation-classifications.eaa[0].use-case=mDL",
"verifier.attestation-classifications.eaa[0].doc-types=org.iso.18013.5.1.mDL",
],
)
internal class DeviceResponseValidationTest {
@TestConfiguration
class Config {
@Bean
@Primary
fun clock(): Clock =
Clock.fixed(
    now = Instant.fromEpochSeconds(1766135977L),
    timeZone = ZoneOffset.ofHours(3).toKotlinFixedOffsetTimeZone(),
)

@Bean
@Primary
fun generateRequestId(): GenerateRequestId = GenerateRequestId.fixed(requestId)

@Bean
@Primary
fun generateEphemeralEncryptionKeyPair(): GenerateEphemeralEncryptionKeyPair =
GenerateEphemeralEncryptionKeyPair { ephemeralEncryptionKey }

companion object {
val requestId: RequestId = RequestId("1234567890")
val ephemeralEncryptionKey: ECKey =
    ECKey.parse(
        """
        {
            "kty": "EC",
            "d": "RD5iTzNDQpt7KeOM1AfMV1Un27-LY9QZSABS2ETfBc4",
            "use": "enc",
            "crv": "P-256",
            "kid": "de7eb521-eb6a-403d-a83f-a74333b936e5",
            "x": "rQARUEPijpGzfTIaZUv8G9h-09spX-J9mGXuEFyu06g",
            "y": "PEFq_diAbJH2aUV57z0f9ngrbWikTCN7Pczg-VkQBXE",
            "alg": "ECDH-ES"
        }
        """.trimIndent(),
    )
}
}

@Autowired
private lateinit var client: WebTestClient

@Autowired
private lateinit var config: VerifierConfig

@Test
@DirtiesContext
fun `when wallet responds with a deviceresponse that contains valid deviceauthentication, validations succeeds`() =
runTest {
val initTransaction = VerifierApiClient.loadInitTransactionTO("08-mdl-dcql.json")
val transactionDetails =
    assertIs<InitTransactionResponse.JwtSecuredAuthorizationRequestTO>(
        VerifierApiClient.initTransaction(client, initTransaction),
    )
WalletApiClient.getRequestObjectJsonResponse(client, transactionDetails.requestUri!!)

val encryptedJwt =
    run {
        val jwtClaims: JWTClaimsSet =
            buildJsonObject {
                put("state", Config.requestId.value)
                put("vp_token", Json.decodeFromString(TestUtils.loadResource("08-mdl-vpToken.json")))
            }.run { JWTClaimsSet.parse(Json.encodeToString(this)) }

        val jweHeader =
            JWEHeader
                .Builder(
                    JWEAlgorithm(Config.ephemeralEncryptionKey.algorithm.name),
                    config.clientMetaData.responseEncryptionOption.encryptionMethods
                        .first(),
                ).agreementPartyVInfo(Base64URL.encode(initTransaction.nonce!!))
                .build()

        EncryptedJWT(jweHeader, jwtClaims)
    }.apply { encrypt(ECDHEncrypter(Config.ephemeralEncryptionKey)) }

val walletResponse =
    LinkedMultiValueMap<String, Any>()
        .apply {
            add("response", encryptedJwt.serialize())
        }

WalletApiClient.directPostJwt(client, Config.requestId, walletResponse)

val transactionResponse =
    assertNotNull(
        VerifierApiClient.getWalletResponse(
            client,
            TransactionId(transactionDetails.transactionId),
        ),
    )

val vpToken = assertNotNull(transactionResponse.vpToken)
assertEquals(1, vpToken.size)

val mDL = assertIs<JsonArray>(vpToken["query_0"])
assertEquals(1, mDL.size)
assertIs<JsonPrimitive>(mDL[0])
}

@Test
@DirtiesContext
fun `when wallet responds with a deviceresponse that contains invalid deviceauthentication, validations fail`() =
runTest {
// Set a different Nonce that what is included in OpenID4VPHandoverInfo, to cause a validation failure
val initTransaction = VerifierApiClient.loadInitTransactionTO("08-mdl-dcql.json").copy(nonce = "nonce")
val transactionDetails =
    assertIs<InitTransactionResponse.JwtSecuredAuthorizationRequestTO>(
        VerifierApiClient.initTransaction(client, initTransaction),
    )
WalletApiClient.getRequestObjectJsonResponse(client, transactionDetails.requestUri!!)

val encryptedJwt =
    run {
        val jwtClaims: JWTClaimsSet =
            buildJsonObject {
                put("state", Config.requestId.value)
                put("vp_token", Json.decodeFromString(TestUtils.loadResource("08-mdl-vpToken.json")))
            }.run { JWTClaimsSet.parse(Json.encodeToString(this)) }

        val jweHeader =
            JWEHeader
                .Builder(
                    JWEAlgorithm(Config.ephemeralEncryptionKey.algorithm.name),
                    config.clientMetaData.responseEncryptionOption.encryptionMethods
                        .first(),
                ).agreementPartyVInfo(Base64URL.encode(initTransaction.nonce!!))
                .build()

        EncryptedJWT(jweHeader, jwtClaims)
    }.apply { encrypt(ECDHEncrypter(Config.ephemeralEncryptionKey)) }

val walletResponse =
    LinkedMultiValueMap<String, Any>()
        .apply {
            add("response", encryptedJwt.serialize())
        }

try {
    WalletApiClient.directPostJwt(client, Config.requestId, walletResponse)
    fail("Expected to fail but didn't")
} catch (error: AssertionError) {
    assertEquals("Status expected:<200 OK> but was:<400 BAD_REQUEST>", error.message)
}
}
}
