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
@file:Suppress("SpringBootApplicationProperties")

package eu.europa.ec.eudi.pidissuer.adapter.input.web

import arrow.core.nonEmptyListOf
import com.eygraber.uri.Uri
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.crypto.factories.DefaultJWEDecrypterFactory
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import eu.europa.ec.eudi.pidissuer.*
import eu.europa.ec.eudi.pidissuer.adapter.input.web.security.DPoPTokenAuthentication
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid.*
import eu.europa.ec.eudi.pidissuer.domain.*
import eu.europa.ec.eudi.pidissuer.port.input.*
import eu.europa.ec.eudi.pidissuer.port.out.attestation.GetAttestationAttributes
import eu.europa.ec.eudi.pidissuer.port.out.nonce.GenerateNonce
import eu.europa.ec.eudi.pidissuer.port.out.status.AllocateStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.context.support.GenericApplicationContext
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import java.util.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Base class for [WalletApi] tests.
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection", "ProtectedInFinal")
@PidIssuerApplicationTest(
    classes = [BaseWalletApiTest.WalletApiTestConfig::class],
)
class BaseWalletApiTest {
    @Autowired
    protected lateinit var applicationContext: ApplicationContext

    @Autowired
    protected lateinit var clock: Clock

    @Autowired
    protected lateinit var timeZone: TimeZone

    @Autowired
    protected lateinit var generateNonce: GenerateNonce

    @Autowired
    protected lateinit var credentialIssuerMetadata: CredentialIssuerMetaData

    protected final fun client(): WebTestClient =
        WebTestClient
            .bindToApplicationContext(applicationContext)
            .apply(springSecurity())
            .configureClient()
            .build()

    protected suspend fun jwtProofWithKeyAttestation(extraKeys: Int = 3) =
        jwtProofWithKeyAttestation(
            clock,
            credentialIssuerMetadata.id,
            generateNonce(clock.now(), 5L.minutes),
            extraKeys,
        )

    @TestConfiguration
    class WalletApiTestConfig {
        @Bean
        @Primary
        fun getPidData(
            clock: Clock,
            timeZone: TimeZone,
        ): GetAttestationAttributes<PidAttributes> =
            GetAttestationAttributes {
                val pid =
                    Pid(
                        familyName = FamilyName("Surname"),
                        givenName = GivenName("Firstname"),
                        birthDate = LocalDate(1989, Month.AUGUST, 22),
                        placeOfBirth =
                            PlaceOfBirth(
                                country = IsoCountry("GR"),
                                region = State("Attica"),
                                locality = City("Athens"),
                            ),
                        nationalities = nonEmptyListOf(Nationality("GR")),
                        personalAdministrativeNumber = null,
                    )
                val issuingCountry = IsoCountry("GR")
                val pidMetaData =
                    PidMetaData(
                        issuanceDate = clock.now().toLocalDateTime(timeZone).date,
                        expiryDate = LocalDate(2030, 11, 10),
                        documentNumber = null,
                        issuingAuthority = IssuingAuthority.MemberState(issuingCountry),
                        issuingCountry = issuingCountry,
                        issuingJurisdiction = null,
                    )
                PidAttributes(pid, pidMetaData)
            }

        @Bean
        @Primary
        fun generateStatusListToken(): AllocateStatus =
            AllocateStatus { _, _ ->
                StatusListToken(
                    statusList = Uri.parse("https://example.com/status-list"),
                    index = 0u,
                )
            }
    }
}

/**
 * Test cases for [WalletApi] when encryption is optional. Key Attestations are **NOT** required.
 */
@TestPropertySource(
    properties = [
        "issuer.credentialRequestEncryption.required=false",
        "issuer.credentialResponseEncryption.required=false",
        "issuer.credentialEndpoint.batchIssuance.enabled=true",
        "issuer.credentialEndpoint.batchIssuance.batchSize=3",
    ],
)
internal class WalletApiEncryptionOptionalKeyAttestationsNotRequiredTest : BaseWalletApiTest() {
    /**
     * Verifies credential endpoint is not accessible by anonymous users.
     */
    @Test
    fun `requires authorization`() =
        runTest {
            client()
                .post()
                .uri(WalletApi.CREDENTIAL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestByCredentialConfigurationId())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectNoContentSecurityPolicy()
                .expectStatus()
                .isUnauthorized()
        }

    /**
     * Verifies that unknown credential formats cannot be deserialized.
     * The Application is expected to fail.
     */
    @Test
    fun `fails with unknown credential configuration id`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(
                        requestByCredentialConfigurationId(
                            credentialConfigurationId = "foo",
                            proofs = CredentialRequestTO.ProofsTO(jwtProofs = listOf("proof")),
                        ),
                    ).accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .responseBody

            assertNotNull(response)
            assertEquals(CredentialErrorTypeTo.UNKNOWN_CREDENTIAL_CONFIGURATION, response.type)
            assertEquals("Unsupported Credential Configuration Id 'foo'", response.errorDescription)
        }

    /**
     * Verifies that when Access Token does not include client_status, Credential Request fails
     */
    @Test
    fun `fails when client_status is not included inside the access token`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock, includeClientStatus = false)

            client()
                .mutateWith(mockAuthentication(authentication))
                .post()
                .uri(WalletApi.CREDENTIAL_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    requestByCredentialConfigurationId(
                        credentialConfigurationId = "foo",
                        proofs = CredentialRequestTO.ProofsTO(jwtProofs = listOf("proof")),
                    ),
                ).accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectNoContentSecurityPolicy()
                .expectStatus()
                .is5xxServerError
                .returnResult()
        }

    @Test
    fun `fails when client_status exp is before preferred client status period`() =
        runTest {
            val authentication =
                dPoPTokenAuthentication(
                    clock = clock,
                    clientStatusExpiresAt = clock.now() + 25.days,
                )

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(
                        requestByCredentialConfigurationId(
                            proofs = jwtProofWithKeyAttestation(0).toJwtProofs(),
                        ),
                    ).accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.CREDENTIAL_REQUEST_DENIED, response.type)
            assertEquals(
                "Invalid Client Status: Client Status expires before preferred client status period",
                response.errorDescription,
            )
        }

    /**
     * Verifies that proof of possession is required.
     * The Application is expected to fail.
     */
    @Test
    fun `fails when proof is not provided`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isEqualTo(HttpStatus.BAD_REQUEST)
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .responseBody

            val error = assertIs<IssueCredentialResponse.FailedTO>(response)
            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, error.type)
            assertEquals("The Credential Request must include Proof of Possession", error.errorDescription)
        }

    /**
     * Verifies that when an incorrect scope is used, issuance fails.
     * The Application is expected to fail.
     */
    @Test
    fun `fails when using incorrect scope`() =
        runTest {
            val authentication =
                dPoPTokenAuthentication(
                    clock = clock,
                    scopes = listOf(PidSdJwtVcScope),
                )

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(
                        requestByCredentialConfigurationId(
                            proofs =
                                CredentialRequestTO.ProofsTO(
                                    jwtProofs =
                                        listOf(
                                            "proof",
                                        ),
                                ),
                        ),
                    ).accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isEqualTo(HttpStatus.BAD_REQUEST)
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .responseBody

            val error = assertIs<IssueCredentialResponse.FailedTO>(response)
            assertEquals(CredentialErrorTypeTo.INVALID_CREDENTIAL_REQUEST, error.type)
            assertEquals("Wrong scope. Expected ${PidMsoMdocScope.value}", error.errorDescription)
        }

    @Test
    fun `fails when multiple proof types are provided`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)

            val proofs = CredentialRequestTO.ProofsTO(jwtProofs = listOf("jwt"), attestations = listOf("attestation"))

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, response.type)
            assertEquals("Only a single proof type is allowed", response.errorDescription)
        }

    @Test
    fun `fails when providing multiple proofs`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val nonce = generateNonce(clock.now(), 5L.minutes)
            val proof1 =
                jwtProofWithKeyAttestation(
                    clock,
                    credentialIssuerMetadata.id,
                    nonce,
                    3,
                )
            val proof2 =
                jwtProofWithKeyAttestation(
                    clock,
                    credentialIssuerMetadata.id,
                    nonce,
                    3,
                )
            val proofs = CredentialRequestTO.ProofsTO(jwtProofs = listOf(proof1.serialize(), proof2.serialize()))

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isBadRequest
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, response.type)
            assertEquals("You can provide at most 1 proof", response.errorDescription)
        }

    /**
     * Verifies issuance success.
     * Creates a CNonce value before doing the request.
     * Does the request.
     * Verifies response values.
     */
    @Test
    fun `issuance success by credential configuration id`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val proofs = jwtProofWithKeyAttestation(0).toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isOk()
                    .expectBody<IssueCredentialResponse.PlainTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val issuedCredentials = assertNotNull(response.credentials)
            assertEquals(1, issuedCredentials.size)
            assertNull(response.transactionId)
        }
    /*
     * Initial benchmark implementation
     *
     * This benchmark was developed during the initial performance evaluation phase
     * to measure credential issuance latency through the Wallet API.
     *
     * It represents the first measurement approach used in this study.
     * During the subsequent analysis, the benchmark methodology was refined and
     * the dedicated IssueCredentialTest benchmark was introduced to isolate
     * credential issuance performance more clearly.
     *
     * This implementation is retained as part of the development history and
     * methodological evolution of the benchmark.
     */
    @Test
    fun `issuance latency benchmark`() =
        runTest {
            // Benchmark sırasında gönderilecek toplam istek sayısı.
            // İstenirse bu değer 10, 100 veya 1000 gibi farklı değerlere değiştirilerek
            // farklı yük senaryoları test edilebilir.
            val defaultRequestCount = 1000

            // Eğer dışarıdan bir request sayısı verilirse onu kullan,
            // verilmezse yukarıdaki varsayılan değeri kullan.
            val requestCount = System.getProperty("requests")?.toIntOrNull() ?: defaultRequestCount

            // Toplam geçen süreyi hesaplamak için kullanılır.
            var totalTime = 0L

            // En düşük ve en yüksek gecikme sürelerini tutar.
            var minTime = Long.MAX_VALUE
            var maxTime = Long.MIN_VALUE

            // Her isteğin gecikme süresini saklar.
            // Daha sonra median (ortanca) değeri hesaplamak için kullanılır.
            val latencies = mutableListOf<Long>()

            println("============================================================")
            println("             EUDI PID Issuer Performance Benchmark")
            println("============================================================")
            println("Operation       : Credential Issuance")
            println("Execution Mode  : Sequential")
            println("Request Count   : $requestCount")
            println()
            println("Running benchmark...")
            println()

            // Belirlenen request sayısı kadar credential issuance işlemi yapılır.
            repeat(requestCount) { index ->

                // Test için gerekli authentication bilgisi oluşturulur.
                val authentication = dPoPTokenAuthentication(clock = clock)

                // Credential request içerisinde kullanılacak proof oluşturulur.
                val proofs = jwtProofWithKeyAttestation(0).toJwtProofs()

                // İstek gönderilmeden hemen önce zaman ölçümü başlatılır.
                val startTime = System.nanoTime()

                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isOk()
                    .expectBody<IssueCredentialResponse.PlainTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

                // İstek tamamlandıktan sonra geçen süre hesaplanır.
                val elapsed = System.nanoTime() - startTime

                // Toplam süre güncellenir.
                totalTime += elapsed

                // Minimum ve maksimum gecikme süreleri güncellenir.
                minTime = minOf(minTime, elapsed)
                maxTime = maxOf(maxTime, elapsed)

                // Median hesaplamak için her isteğin süresi listeye eklenir.
                latencies += elapsed

                // Her 100 istekte bir ilerleme bilgisi ekrana yazdırılır.
                if ((index + 1) % 100 == 0 || index == requestCount - 1) {
                    println("Completed ${index + 1}/$requestCount requests")
                }
            }

            // Toplam süre milisaniyeye çevrilir.
            val totalTimeMs = totalTime / 1_000_000.0

            // Ortalama gecikme hesaplanır.
            val averageLatencyMs = totalTimeMs / requestCount

            // Minimum ve maksimum gecikmeler milisaniyeye çevrilir.
            val minTimeMs = minTime / 1_000_000.0
            val maxTimeMs = maxTime / 1_000_000.0

            // Throughput (saniyede işlenen istek sayısı) hesaplanır.
            val throughput = requestCount / (totalTime / 1_000_000_000.0)

            // Median hesaplayabilmek için gecikmeler küçükten büyüğe sıralanır.
            val sorted = latencies.sorted()

            // İstek sayısı çift ise ortadaki iki değerin ortalaması alınır,
            // tek ise tam ortadaki değer median olarak seçilir.
            val medianLatencyMs =
                if (sorted.size % 2 == 0) {
                    (
                        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                    ) / 1_000_000.0
                } else {
                    sorted[sorted.size / 2] / 1_000_000.0
                }

            // Hesaplanan tüm performans metrikleri ekrana yazdırılır.
            println()
            println("============================================================")
            println("Benchmark Results")
            println("============================================================")
            println("Operation         : Credential Issuance")
            println("Execution Mode    : Sequential")
            println("Request Count     : $requestCount")
            println()
            println("Total Time        : %.2f ms (%.3f s)".format(totalTimeMs, totalTimeMs / 1000))
            println("Average Latency   : %.2f ms".format(averageLatencyMs))
            println("Median Latency    : %.2f ms".format(medianLatencyMs))
            println("Minimum Latency   : %.2f ms".format(minTimeMs))
            println("Maximum Latency   : %.2f ms".format(maxTimeMs))
            println("Throughput        : %.2f req/s".format(throughput))
            println()
            println("Status            : SUCCESS")
            println("============================================================")
        }
/*
Initially I implemented an end-to-end benchmark in WalletApiTest, which measures the entire HTTP credential endpoint.
Later I realized this includes networking and controller overhead,
so I moved the benchmark into IssueCredentialTest to isolate the core issuance logic.
Finally, while tracing the code, I discovered that IssueCredential.fromPlainRequest() eventually invokes the test AttestationIssuer,
which is why I'm now investigating whether the benchmark reflects the real credential generation or only the test implementation.
 */
    @Test
    fun `batch issuance success by credential configuration id`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val proofs = jwtProofWithKeyAttestation(2).toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isOk()
                    .expectBody<IssueCredentialResponse.PlainTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val issuedCredentials = assertNotNull(response.credentials)
            assertEquals(3, issuedCredentials.size)
            assertNull(response.transactionId)
        }

    @Test
    fun `fails when credential identifier is used`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val proofs = jwtProofWithKeyAttestation(0).toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(
                        CredentialRequestTO(
                            credentialIdentifier = "eu.europa.ec.eudi.pid_mso_mdoc",
                            proofs = proofs,
                        ),
                    ).accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.UNKNOWN_CREDENTIAL_IDENTIFIER, response.type)
        }

    @Test
    fun `issuance success by credential configuration id with encrypted request`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val credentialRequestEncryption = credentialIssuerMetadata.credentialRequestEncryption
            require(credentialRequestEncryption is CredentialRequestEncryption.Optional)
            val proofs = jwtProofWithKeyAttestation().toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.parseMediaType("application/jwt"))
                    .bodyValue(
                        requestByCredentialConfigurationId(proofs = proofs)
                            .encrypt(credentialRequestEncryption.parameters),
                    ).accept(MediaType.parseMediaType("application/jwt"))
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isOk()
                    .expectBody<IssueCredentialResponse.PlainTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val issuedCredentials = assertNotNull(response.credentials)
            assertTrue(issuedCredentials.isNotEmpty())
            assertNull(response.transactionId)
        }
}

/**
 * Test cases for [WalletApi] when encryption is optional. Key Attestations are **REQUIRED**.
 */
@TestPropertySource(
    properties = [
        "issuer.credentialRequestEncryption.required=false",
        "issuer.credentialResponseEncryption.required=false",
        "issuer.credentialEndpoint.batchIssuance.enabled=true",
        "issuer.credentialEndpoint.batchIssuance.batchSize=3",
    ],
)
internal class WalletApiEncryptionOptionalKeyAttestationsRequiredTest : BaseWalletApiTest() {
    @Test
    fun `fail when the sent key attestation does not match the expected attack protection requirements`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val keyAttestationCNonce = generateNonce(clock.now(), 5L.minutes)
            val jwtProofSigningKey = ECKeyGenerator(Curve.P_256).generate()

            val extraKeysNo = 3
            val keyAttestationJwt =
                keyAttestationJWT(
                    proofSigningKey = jwtProofSigningKey,
                    keyStorageConstraints = listOf("iso_18045_enhanced-basic"),
                    userAuthorizationConstraints = listOf("iso_18045_enhanced-basic"),
                    cNonce = keyAttestationCNonce,
                ) {
                    (0..<extraKeysNo).map {
                        ECKeyGenerator(Curve.P_256).generate()
                    }
                }

            val keyAttestationJwtProof =
                jwtProof(credentialIssuerMetadata.id, clock, keyAttestationCNonce, jwtProofSigningKey) {
                    customParam("key_attestation", keyAttestationJwt.serialize())
                }

            val proofs = listOf(keyAttestationJwtProof).toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, response.type)
            assertEquals(
                "The provided key storage's attack resistance does not match the expected one.",
                response.errorDescription,
            )
        }

    @Test
    fun `when duplicate keys exists in key attestation issuance fails`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val cNonce = generateNonce(clock.now(), 5L.minutes)
            val jwtProofSigningKey = ECKeyGenerator(Curve.P_256).generate()
            val extraKeysNo = 5
            val keyAttestationJwt =
                keyAttestationJWT(proofSigningKey = jwtProofSigningKey, cNonce = cNonce) {
                    val key = ECKeyGenerator(Curve.P_256).generate()
                    (0..<extraKeysNo).map { key }
                }

            val proofs =
                jwtProof(credentialIssuerMetadata.id, clock, cNonce, jwtProofSigningKey) {
                    customParam("key_attestation", keyAttestationJwt.serialize())
                }.toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, response.type)
            assertEquals("Invalid proof JWT: Duplicate keys provided in credential request", response.errorDescription)
        }

    @Test
    fun `when key attestation in jwt proof, it must contain valid 'exp' claim, otherwise issuance fails`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val previousCNonce = generateNonce(clock.now(), 5L.minutes)
            val keyAttestationCNonce = generateNonce(clock.now(), 5L.minutes)
            val jwtProofSigningKey = ECKeyGenerator(Curve.P_256).generate()
            val extraKeysNo = 3

            // /////////////////////////////////
            // key attestation missing 'exp' //
            // /////////////////////////////////

            var keyAttestationJwt =
                keyAttestationJWT(
                    proofSigningKey = jwtProofSigningKey,
                    cNonce = keyAttestationCNonce,
                    includeExpiresAt = false,
                ) {
                    (0..<extraKeysNo).map {
                        ECKeyGenerator(Curve.P_256).generate()
                    }
                }

            var proofs =
                jwtProof(credentialIssuerMetadata.id, clock, previousCNonce, jwtProofSigningKey) {
                    customParam("key_attestation", keyAttestationJwt.serialize())
                }.toJwtProofs()

            var response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, response.type)
            assertEquals("Invalid Key Attestation JWT", response.errorDescription)

            // ///////////////////////////
            // key attestation expired //
            // ///////////////////////////

            keyAttestationJwt =
                keyAttestationJWT(
                    proofSigningKey = jwtProofSigningKey,
                    cNonce = keyAttestationCNonce,
                    expiresAt = clock.now().minus(3L.minutes),
                ) {
                    (0..<extraKeysNo).map {
                        ECKeyGenerator(Curve.P_256).generate()
                    }
                }
            proofs =
                jwtProof(credentialIssuerMetadata.id, clock, previousCNonce, jwtProofSigningKey) {
                    customParam("key_attestation", keyAttestationJwt.serialize())
                }.toJwtProofs()

            response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, response.type)
            assertEquals("Invalid proof JWT: Expired JWT", response.errorDescription)
        }

    @Test
    fun `issuance with jwt proof that contains key attestation with nonce is successful`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val cNonce = generateNonce(clock.now(), 5L.minutes)
            val jwtProofSigningKey = ECKeyGenerator(Curve.P_256).generate()
            val extraKeysNo = 3
            val keyAttestationJwt =
                keyAttestationJWT(proofSigningKey = jwtProofSigningKey, cNonce = cNonce) {
                    (0..<extraKeysNo).map {
                        ECKeyGenerator(Curve.P_256).generate()
                    }
                }

            val proofs =
                jwtProof(credentialIssuerMetadata.id, clock, cNonce, jwtProofSigningKey) {
                    customParam("key_attestation", keyAttestationJwt.serialize())
                }.toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isOk()
                    .expectBody<IssueCredentialResponse.PlainTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val issuedCredentials = assertNotNull(response.credentials)
            assertEquals(extraKeysNo + 1, issuedCredentials.size)
            assertNull(response.transactionId)
        }

    @Test
    fun `issuance with jwt proof that contains key attestation without nonce is successful`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val jwtProofSigningKey = ECKeyGenerator(Curve.P_256).generate()
            val cNonce = generateNonce(clock.now(), 5L.minutes)
            val keyAttestationJwt =
                keyAttestationJWT(proofSigningKey = jwtProofSigningKey) {
                    (0..<3).map {
                        ECKeyGenerator(Curve.P_256).generate()
                    }
                }
            val proofs =
                jwtProof(credentialIssuerMetadata.id, clock, cNonce, jwtProofSigningKey) {
                    customParam("key_attestation", keyAttestationJwt.serialize())
                }.toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .is2xxSuccessful()
                    .expectBody<IssueCredentialResponse.PlainTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val issuedCredentials = assertNotNull(response.credentials)
            assertTrue(issuedCredentials.isNotEmpty())
            assertNull(response.transactionId)
        }

    @Test
    fun `issuance with jwt proof that contains key attestation with difference nonce fails`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val jwtProofSigningKey = ECKeyGenerator(Curve.P_256).generate()
            val keyAttestationCNonce = generateNonce(clock.now(), 5L.minutes)
            val keyAttestationJwt =
                keyAttestationJWT(proofSigningKey = jwtProofSigningKey, cNonce = keyAttestationCNonce) {
                    (0..<3).map {
                        ECKeyGenerator(Curve.P_256).generate()
                    }
                }

            val jwtProofCNonce = generateNonce(clock.now(), 5L.minutes)
            val proofs =
                jwtProof(credentialIssuerMetadata.id, clock, jwtProofCNonce, jwtProofSigningKey) {
                    customParam("key_attestation", keyAttestationJwt.serialize())
                }.toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, response.type)
            assertEquals(
                "Key Attestation 'nonce' does not match JWT Proof 'nonce'",
                response.errorDescription,
            )
        }

    @Test
    internal fun `issuance with jwt proof that contains key attestation fails when proof is signed with non-attested key`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val cNonce = generateNonce(clock.now(), 5L.minutes)
            val nonAttestedProofKey = ECKeyGenerator(Curve.P_256).generate()
            val attestedKey = ECKeyGenerator(Curve.P_256).generate()
            val keyAttestationJwt = keyAttestationJWT(proofSigningKey = attestedKey)

            val proofs =
                jwtProof(credentialIssuerMetadata.id, clock, cNonce, nonAttestedProofKey) {
                    customParam("key_attestation", keyAttestationJwt.serialize())
                }.toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, response.type)
            assertEquals(
                "Invalid proof JWT: Signed JWT rejected: Invalid signature",
                response.errorDescription,
            )
        }

    @Test
    fun `issuance with expired attestation proof fails`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val keyAttestationCNonce = generateNonce(clock.now(), 5L.minutes)
            val jwtProofSigningKey = ECKeyGenerator(Curve.P_256).generate()
            val extraKeysNo = 3
            val proofs =
                keyAttestationJWT(
                    proofSigningKey = jwtProofSigningKey,
                    cNonce = keyAttestationCNonce,
                    expiresAt = clock.now().minus(3L.minutes),
                ) {
                    (0..<extraKeysNo).map {
                        ECKeyGenerator(Curve.P_256).generate()
                    }
                }.toAttestationProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, response.type)
            assertEquals("Invalid proof Attestation: Expired JWT", response.errorDescription)
        }

    @Test
    fun `when key attestations does not contain cNonce, issuance fails`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val jwtProofSigningKey = ECKeyGenerator(Curve.P_256).generate()
            val extraKeysNo = 3
            val keyAttestationJwt =
                keyAttestationJWT(proofSigningKey = jwtProofSigningKey) {
                    (0..<extraKeysNo).map {
                        ECKeyGenerator(Curve.P_256).generate()
                    }
                }

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = keyAttestationJwt.toAttestationProofs()))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, response.type)
            assertEquals(
                "Key attestation does not contain a c_nonce.",
                response.errorDescription,
            )
        }

    @Test
    fun `issuance success by credential configuration id with encrypted request`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val cNonce = generateNonce(clock.now(), 5L.minutes)

            val credentialRequestEncryption = credentialIssuerMetadata.credentialRequestEncryption
            require(credentialRequestEncryption is CredentialRequestEncryption.Optional)

            val jwtProofSigningKey = ECKeyGenerator(Curve.P_256).generate()
            val extraKeysNo = 3
            val keyAttestationJwt =
                keyAttestationJWT(proofSigningKey = jwtProofSigningKey, cNonce = cNonce) {
                    (0..<extraKeysNo).map {
                        ECKeyGenerator(Curve.P_256).generate()
                    }
                }

            val proofs =
                jwtProof(credentialIssuerMetadata.id, clock, cNonce, jwtProofSigningKey) {
                    customParam("key_attestation", keyAttestationJwt.serialize())
                }.toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.parseMediaType("application/jwt"))
                    .bodyValue(
                        requestByCredentialConfigurationId(proofs = proofs)
                            .encrypt(credentialRequestEncryption.parameters),
                    ).accept(MediaType.parseMediaType("application/jwt"))
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isOk()
                    .expectBody<IssueCredentialResponse.PlainTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val issuedCredentials = assertNotNull(response.credentials)
            assertTrue(issuedCredentials.isNotEmpty())
            assertNull(response.transactionId)
        }

    @Test
    fun `issuance fails when key attestation expiration is before credential expiration`() =
        runTest {
            val keyAttestationExpiresAt = clock.now() + 1L.minutes
            val authentication = dPoPTokenAuthentication(clock = clock)
            val cNonce = generateNonce(clock.now(), 5L.minutes)
            val jwtProofSigningKey = ECKeyGenerator(Curve.P_256).generate()
            val keyAttestationJwt =
                keyAttestationJWT(
                    proofSigningKey = jwtProofSigningKey,
                    cNonce = cNonce,
                    expiresAt = keyAttestationExpiresAt,
                ) {
                    (0..<3).map { ECKeyGenerator(Curve.P_256).generate() }
                }

            val proofs =
                jwtProof(credentialIssuerMetadata.id, clock, cNonce, jwtProofSigningKey) {
                    customParam("key_attestation", keyAttestationJwt.serialize())
                }.toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, response.type)
            assertEquals(
                "Key Storage Status expiration date does not meet the preferred key storage status period",
                response.errorDescription,
            )
        }

    @Test
    fun `issuance fails when client status expiration is before credential expiration`() =
        runTest {
            val authentication =
                dPoPTokenAuthentication(clock = clock, clientStatusExpiresAt = clock.now() + 1L.minutes)
            val cNonce = generateNonce(clock.now(), 5L.minutes)
            val jwtProofSigningKey = ECKeyGenerator(Curve.P_256).generate()
            val keyAttestationJwt =
                keyAttestationJWT(proofSigningKey = jwtProofSigningKey, cNonce = cNonce) {
                    (0..<3).map { ECKeyGenerator(Curve.P_256).generate() }
                }

            val proofs =
                jwtProof(credentialIssuerMetadata.id, clock, cNonce, jwtProofSigningKey) {
                    customParam("key_attestation", keyAttestationJwt.serialize())
                }.toJwtProofs()

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestByCredentialConfigurationId(proofs = proofs))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.CREDENTIAL_REQUEST_DENIED, response.type)
            assertEquals(
                "Invalid Client Status: Client Status expires before preferred client status period",
                response.errorDescription,
            )
        }
}

/**
 * Test cases for [WalletApi] when encryption is required.
 */
@Suppress("SpringBootApplicationProperties")
@TestPropertySource(
    properties = [
        "issuer.credentialResponseEncryption.required=true",
        "issuer.credentialEndpoint.batchIssuance.enabled=true",
        "issuer.credentialEndpoint.batchIssuance.batchSize=3",
    ],
)
internal class WalletApiResponseEncryptionRequiredTest : BaseWalletApiTest() {
    /**
     * Verifies issuance fails when encryption is not requested.
     * Creates a CNonce value before doing the request.
     * Does the request.
     * Verifies response values.
     */
    @Test
    fun `issuance failure by credential configuration id when encryption is not requested`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val previousCNonce = generateNonce(clock.now(), 5L.minutes)

            val key = ECKeyGenerator(Curve.P_256).generate()
            val proofs =
                jwtProof(credentialIssuerMetadata.id, clock, previousCNonce, key) {
                    jwk(key.toPublicJWK())
                }.toJwtProofs()

            val credentialRequestEncryption =
                assertIs<CredentialRequestEncryption.Required>(credentialIssuerMetadata.credentialRequestEncryption)

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.parseMediaType("application/jwt"))
                    .bodyValue(
                        requestByCredentialConfigurationId(proofs = proofs)
                            .encrypt(credentialRequestEncryption.parameters),
                    ).accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_ENCRYPTION_PARAMETERS, response.type)
            assertEquals(
                "Invalid Credential Response Encryption Parameters: credential response encryption is required",
                response.errorDescription,
            )
        }

    /**
     * Verifies issuance succeeds when encryption is requested.
     * Creates a CNonce value before doing the request.
     * Does the request.
     * Verifies response values.
     */
    @Test
    fun `issuance success by credential configuration id when encryption is requested`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val proofs = jwtProofWithKeyAttestation(0).toJwtProofs()
            val encryptionKey =
                ECKeyGenerator(Curve.P_256).algorithm(JWEAlgorithm.ECDH_ES).keyUse(KeyUse.ENCRYPTION).generate()
            val encryptionParameters = encryptionResponseParameters(encryptionKey)

            val credentialRequestEncryption =
                assertIs<CredentialRequestEncryption.Required>(credentialIssuerMetadata.credentialRequestEncryption)

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.parseMediaType("application/jwt"))
                    .bodyValue(
                        requestByCredentialConfigurationId(
                            proofs = proofs,
                            credentialResponseEncryption = encryptionParameters,
                        ).encrypt(credentialRequestEncryption.parameters),
                    ).accept(MediaType.parseMediaType("application/jwt"))
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isOk()
                    .expectBody<String>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val claims =
                run {
                    val jwt =
                        EncryptedJWT
                            .parse(response)
                            .also {
                                it.decrypt(
                                    DefaultJWEDecrypterFactory().createJWEDecrypter(
                                        it.header,
                                        encryptionKey.toECPrivateKey(),
                                    ),
                                )
                            }
                    jwt.jwtClaimsSet
                }

            val issuedCredentials = assertNotNull(claims.getListClaim("credentials"))
            assertEquals(1, issuedCredentials.size)
        }

    /**
     * Verifies issuance succeeds when encryption is requested.
     * Creates a CNonce value before doing the request.
     * Does the request.
     * Verifies response values.
     */
    @Test
    fun `batch issuance success by credential configuration id when encryption is requested`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val proofs = jwtProofWithKeyAttestation(2).toJwtProofs()
            val encryptionKey =
                ECKeyGenerator(Curve.P_256).algorithm(JWEAlgorithm.ECDH_ES).keyUse(KeyUse.ENCRYPTION).generate()
            val encryptionParameters = encryptionResponseParameters(encryptionKey)

            val credentialRequestEncryption =
                assertIs<CredentialRequestEncryption.Required>(credentialIssuerMetadata.credentialRequestEncryption)

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.parseMediaType("application/jwt"))
                    .bodyValue(
                        requestByCredentialConfigurationId(
                            proofs = proofs,
                            credentialResponseEncryption = encryptionParameters,
                        ).encrypt(credentialRequestEncryption.parameters),
                    ).accept(MediaType.parseMediaType("application/jwt"))
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isOk()
                    .expectBody<String>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val claims =
                run {
                    val jwt =
                        EncryptedJWT
                            .parse(response)
                            .also {
                                it.decrypt(
                                    DefaultJWEDecrypterFactory().createJWEDecrypter(
                                        it.header,
                                        encryptionKey.toECPrivateKey(),
                                    ),
                                )
                            }
                    jwt.jwtClaimsSet
                }
            val credentials = assertNotNull(claims.getListClaim("credentials"))
            assertEquals(3, credentials.size)
        }

    @Test
    fun `issuance fails when credential response is encrypted but credential request is not encrypted`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val previousCNonce = generateNonce(clock.now(), 5L.minutes)

            val key = ECKeyGenerator(Curve.P_256).generate()
            val proofs =
                jwtProof(credentialIssuerMetadata.id, clock, previousCNonce, key) {
                    jwk(key.toPublicJWK())
                }.toJwtProofs()
            val encryptionKey =
                ECKeyGenerator(Curve.P_256).algorithm(JWEAlgorithm.ECDH_ES).keyUse(KeyUse.ENCRYPTION).generate()
            val encryptionParameters = encryptionResponseParameters(encryptionKey)

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(
                        requestByCredentialConfigurationId(
                            proofs = proofs,
                            credentialResponseEncryption = encryptionParameters,
                        ),
                    ).accept(MediaType.parseMediaType("application/jwt"))
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody<IssueCredentialResponse.FailedTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            assertEquals(CredentialErrorTypeTo.INVALID_CREDENTIAL_REQUEST, response.type)
            assertEquals(
                "Credential response encryption requires an encrypted credential request",
                response.errorDescription,
            )
        }
}

@TestPropertySource(
    properties = [
        "issuer.credentialRequestEncryption.required=false",
        "issuer.credentialResponseEncryption.required=false",
    ],
)
internal class WalletApiDeferredIssuanceResponseEncryptionOptionalTest : BaseWalletApiTest() {
    @Test
    fun `deferred issuance succeeds when credential request is plain`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)

            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(
                        requestDeferredByCredentialIdentifier(
                            proofs = jwtProofWithKeyAttestation().toJwtProofs(),
                        ),
                    ).accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isAccepted()
                    .expectBody<IssueCredentialResponse.PlainTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val transactionId = assertNotNull(response.transactionId)
            assertNotNull(response.interval)

            val getDeferredCredentialResponse =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.DEFERRED_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(DeferredCredentialRequestTO(transactionId))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isAccepted()
                    .expectBody<IssuancePendingTO>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val defTransactionId = assertNotNull(getDeferredCredentialResponse.transactionId)
            assertNotNull(getDeferredCredentialResponse.interval)
            assertEquals(transactionId, defTransactionId)
        }
}

@TestPropertySource(
    properties = [
        "issuer.credentialResponseEncryption.required=true",
        "issuer.pid.sd_jwt_vc.enabled=true",
    ],
)
internal class WalletApiDeferredIssuanceResponseEncryptionRequiredTest : BaseWalletApiTest() {
    @Test
    fun `deferred issuance succeeds when credential request is encrypted`() =
        runTest {
            val authentication = dPoPTokenAuthentication(clock = clock)
            val encryptionKey =
                ECKeyGenerator(Curve.P_256).algorithm(JWEAlgorithm.ECDH_ES).keyUse(KeyUse.ENCRYPTION).generate()
            val encryptionParameters = encryptionResponseParameters(encryptionKey)

            val credentialRequestEncryption =
                assertIs<CredentialRequestEncryption.Required>(credentialIssuerMetadata.credentialRequestEncryption)
            val response =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.CREDENTIAL_ENDPOINT)
                    .contentType(MediaType.parseMediaType("application/jwt"))
                    .bodyValue(
                        requestDeferredByCredentialIdentifier(
                            proofs = jwtProofWithKeyAttestation().toJwtProofs(),
                            credentialResponseEncryption = encryptionParameters,
                        ).encrypt(credentialRequestEncryption.parameters),
                    ).accept(MediaType.parseMediaType("application/jwt"))
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isOk()
                    .expectBody<String>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val claims =
                run {
                    val jwt =
                        EncryptedJWT
                            .parse(response)
                            .also {
                                it.decrypt(
                                    DefaultJWEDecrypterFactory().createJWEDecrypter(
                                        it.header,
                                        encryptionKey.toECPrivateKey(),
                                    ),
                                )
                            }
                    jwt.jwtClaimsSet
                }

            val transactionId = assertNotNull(claims.getStringClaim("transaction_id"))
            assertNotNull(claims.getLongClaim("interval"))

            val getDeferredCredentialResponse =
                client()
                    .mutateWith(mockAuthentication(authentication))
                    .post()
                    .uri(WalletApi.DEFERRED_ENDPOINT)
                    .contentType(MediaType.parseMediaType("application/jwt"))
                    .bodyValue(
                        DeferredCredentialRequestTO(
                            transactionId,
                            encryptionParameters,
                        ).encrypt(credentialRequestEncryption.parameters),
                    ).accept(MediaType.parseMediaType("application/jwt"))
                    .exchange()
                    .expectNoContentSecurityPolicy()
                    .expectStatus()
                    .isAccepted()
                    .expectBody<String>()
                    .returnResult()
                    .let { assertNotNull(it.responseBody) }

            val defClaims =
                run {
                    val jwt =
                        EncryptedJWT
                            .parse(getDeferredCredentialResponse)
                            .also {
                                it.decrypt(
                                    DefaultJWEDecrypterFactory().createJWEDecrypter(
                                        it.header,
                                        encryptionKey.toECPrivateKey(),
                                    ),
                                )
                            }
                    jwt.jwtClaimsSet
                }

            val defTransactionId = assertNotNull(defClaims.getStringClaim("transaction_id"))
            assertEquals(transactionId, defTransactionId)
        }
}

private fun dPoPTokenAuthentication(
    subject: String = "user",
    clock: Clock,
    expiresIn: Duration = 10L.minutes,
    scopes: List<Scope> = listOf(PidMsoMdocScope, PidSdJwtVcScope),
    authorities: List<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_USER")),
    includeClientStatus: Boolean = true,
    clientStatusExpiresAt: Instant = (clock.now() + 32.days),
): DPoPTokenAuthentication {
    val issuedAt = clock.now()
    val principal =
        DefaultOAuth2AuthenticatedPrincipal(
            subject,
            buildMap {
                put(OAuth2TokenIntrospectionClaimNames.ACTIVE, true)
                put(OAuth2TokenIntrospectionClaimNames.USERNAME, subject)
                put(OAuth2TokenIntrospectionClaimNames.CLIENT_ID, "wallet-dev")
                put(OAuth2TokenIntrospectionClaimNames.SCOPE, scopes.map { it.value })
                put(OAuth2TokenIntrospectionClaimNames.TOKEN_TYPE, TokenType.BEARER.value)
                put(OAuth2TokenIntrospectionClaimNames.EXP, (issuedAt + expiresIn))
                put(OAuth2TokenIntrospectionClaimNames.IAT, issuedAt)
                put(OAuth2TokenIntrospectionClaimNames.NBF, issuedAt)
                put(OAuth2TokenIntrospectionClaimNames.SUB, subject)
                put(OAuth2TokenIntrospectionClaimNames.JTI, UUID.randomUUID().toString())
                if (includeClientStatus) {
                    put(
                        TS3.CLIENT_STATUS,
                        buildMap {
                            put(
                                TokenStatusListSpec.STATUS,
                                buildMap {
                                    put(
                                        TokenStatusListSpec.STATUS_LIST,
                                        buildMap {
                                            put(TokenStatusListSpec.URI, "https://revocation_url/wia-statuslists/42")
                                            put(TokenStatusListSpec.IDX, 1337)
                                        },
                                    )
                                },
                            )
                            put(RFC7519.EXPIRES_AT, clientStatusExpiresAt.epochSeconds)
                        },
                    )
                }
            },
            authorities + scopes.map { SimpleGrantedAuthority("SCOPE_${it.value}") },
        )

    val accessToken = DPoPAccessToken("token")

    return DPoPTokenAuthentication
        .unauthenticated(
            SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).build(), JWTClaimsSet.Builder().build()),
            accessToken,
            HttpMethod.GET,
            Uri.parse("/"),
        ).authenticate(principal)
}

private fun requestByCredentialConfigurationId(
    credentialConfigurationId: String = "eu.europa.ec.eudi.pid_mso_mdoc",
    proofs: CredentialRequestTO.ProofsTO? = null,
    credentialResponseEncryption: CredentialResponseEncryptionTO? = null,
): CredentialRequestTO =
    CredentialRequestTO(
        credentialConfigurationId = credentialConfigurationId,
        proofs = proofs,
        credentialResponseEncryption = credentialResponseEncryption,
    )

private fun requestDeferredByCredentialIdentifier(
    proofs: CredentialRequestTO.ProofsTO? = null,
    credentialResponseEncryption: CredentialResponseEncryptionTO? = null,
): CredentialRequestTO =
    CredentialRequestTO(
        credentialConfigurationId = "eu.europa.ec.eudi.pid_vc_sd_jwt_deferred",
        proofs = proofs,
        credentialResponseEncryption = credentialResponseEncryption,
    )

private fun encryptionResponseParameters(key: ECKey): CredentialResponseEncryptionTO =
    CredentialResponseEncryptionTO(
        key = Json.decodeFromString(key.toPublicJWK().toJSONString()),
        method = "A128GCM",
        zipAlgorithm = "DEF",
    )

private fun encryptPayload(
    payload: String,
    encParams: CredentialRequestEncryptionSupportedParameters,
): String =
    JWEObject(
        JWEHeader
            .Builder(JWEAlgorithm.ECDH_ES, encParams.methodsSupported.head)
            .keyID(encParams.encryptionKeys.keys[0].keyID)
            .jwk(encParams.encryptionKeys.keys[0].toPublicJWK())
            .type(JOSEObjectType.JWT)
            .build(),
        Payload(payload),
    ).apply {
        encrypt(
            ECDHEncrypter(encParams.encryptionKeys.keys[0].toECKey()),
        )
    }.serialize()

private fun CredentialRequestTO.encrypt(encParams: CredentialRequestEncryptionSupportedParameters): String =
    encryptPayload(Json.encodeToString(this), encParams)

private fun DeferredCredentialRequestTO.encrypt(encParams: CredentialRequestEncryptionSupportedParameters): String =
    encryptPayload(Json.encodeToString(this), encParams)

private fun SignedJWT.toAttestationProofs() = CredentialRequestTO.ProofsTO(attestations = listOf(serialize()))

private fun SignedJWT.toJwtProofs() = CredentialRequestTO.ProofsTO(jwtProofs = listOf(serialize()))

private fun Iterable<SignedJWT>.toJwtProofs() = CredentialRequestTO.ProofsTO(jwtProofs = map { it.serialize() })
