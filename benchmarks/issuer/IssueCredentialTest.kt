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
package eu.europa.ec.eudi.pidissuer.port.input

import arrow.core.NonEmptySet
import arrow.core.nonEmptyListOf
import arrow.core.nonEmptySetOf
import arrow.core.raise.Raise
import com.eygraber.uri.Uri
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid.PidMsoMdocScope
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid.PidMsoMdocV1CredentialConfigurationId
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid.pidMsoMdocV1
import eu.europa.ec.eudi.pidissuer.domain.*
import eu.europa.ec.eudi.pidissuer.jwtProof
import eu.europa.ec.eudi.pidissuer.port.out.attestation.AttestationIssuer
import eu.europa.ec.eudi.pidissuer.port.out.jose.EncryptCredentialResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import org.slf4j.LoggerFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
class IssueCredentialTest {

    private val clock = Clock.System
    private val testKey = ECKeyGenerator(Curve.P_256).generate()

    private val log = LoggerFactory.getLogger(IssueCredentialTest::class.java)

    private val msoMdocConfig =
        pidMsoMdocV1(
            credentialSigningAlgorithm = CoseAlgorithm(-7),
            deviceBinding =
                DeviceBinding.Required.ts3(
                    nonEmptySetOf(JWSAlgorithm.ES256),
                    PreferredKeyStorageStatusPeriod(60.days),
                ),
            validity = 60.days,
        )

    private val attestationIssuer =
        object : AttestationIssuer {
            override val configuration: CredentialConfiguration = msoMdocConfig

            context(_: Raise<IssueCredentialError>, authorizationContext: AuthorizationContext)
            override suspend fun invoke(request: AuthorizedCredentialRequest): CredentialResponse =
                CredentialResponse.Issued(nonEmptyListOf(JsonPrimitive("test-credential")))
        }
    // After validation, the request is authorized.
//
// During my investigation I learned that this step determines
// whether the requested credential can actually be issued and
// selects the appropriate issuer implementation.
    private val metaData =
        CredentialIssuerMetaData(
            id = HttpsUrl.unsafe("https://issuer.example.com"),
            authorizationServers = listOf(HttpsUrl.unsafe("https://auth.example.com")),
            credentialEndPoint = HttpsUrl.unsafe("https://issuer.example.com/credential"),
            batchCredentialIssuance = BatchCredentialIssuance.Supported(batchSize = 3),
            credentialRequestEncryption = CredentialRequestEncryption.NotSupported,
            credentialResponseEncryption = CredentialResponseEncryption.NotSupported,
            attestationIssuers = nonEmptyListOf(attestationIssuer),
            preferredClientStatusPeriod = PreferredClientStatusPeriod(400.days),
        )

    private val encryptCredentialResponse =
        EncryptCredentialResponse { _, _ ->
            IssueCredentialResponse.EncryptedJwtIssued("encrypted-jwt")
        }

    private val issueCredential =
        IssueCredential(
            credentialIssuerMetadata = metaData,
            encryptCredentialResponse = encryptCredentialResponse,
            clock = clock,
        )
    /*
     * This function contains the main credential issuance workflow.
     *
     * From my understanding, the request is first validated, then authorized,
     * the appropriate attestation issuer is selected, and finally the response
     * is generated and returned.
     *
     * Since my benchmark measures the execution time of this function,
     * understanding every step here became essential before interpreting
     * the latency results.
     */
    private fun authorizationContext(
        scopes: NonEmptySet<Scope> = nonEmptySetOf(PidMsoMdocScope),
        clientStatusExpiresAt: Instant = clock.now() + 500.days,
    ): AuthorizationContext =
        AuthorizationContext(
            username = "test-user",
            accessToken = DPoPAccessToken("test-token"),
            scopes = scopes,
            clientStatus =
                ClientStatus(
                    status =
                        StatusClaim(
                            statusList =
                                StatusListToken(
                                    statusList = Uri.parse("https://example.com/issuer-status"),
                                    index = 0u,
                                ),
                        ),
                    expiresAt = clientStatusExpiresAt,
                ),
        )

    private fun jwtProofString(): String = jwtProof(metaData.id, clock, "test-nonce", testKey).serialize()

    @Test
    fun `successful issuance by credential configuration id`() =
        runTest {
            val authContext = authorizationContext()
            val request =
                CredentialRequestTO(
                    credentialConfigurationId = PidMsoMdocV1CredentialConfigurationId.value,
                    proofs =
                        CredentialRequestTO.ProofsTO(
                            jwtProofs = listOf(jwtProofString()),
                        ),
                )
// The incoming credential request is validated first.
// Here the application checks whether the request is complete
// and whether it satisfies the expected credential format.

            val iterations = 100

            log.info("")
            log.info("Starting issuance benchmark...")

            var totalTime = 0L
            var minTime = Long.MAX_VALUE
            var maxTime = Long.MIN_VALUE

            val latencies = mutableListOf<Long>()

            repeat(iterations) {

                val start = System.nanoTime()

                val result = issueCredential.fromPlainRequest(authContext, request)

                val end = System.nanoTime()

                val elapsed = end - start

                latencies += elapsed

                totalTime += elapsed
                minTime = minOf(minTime, elapsed)
                maxTime = maxOf(maxTime, elapsed)

                assertIs<IssueCredentialResponse.PlainTO>(result)
            }

            val totalTimeMs = totalTime / 1_000_000.0

            val averageLatencyMs = totalTimeMs / iterations

            val throughput = iterations / (totalTime / 1_000_000_000.0)

            val sorted = latencies.sorted()

            val medianLatencyMs =
                if (sorted.size % 2 == 0) {
                    (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0 / 1_000_000.0
                } else {
                    sorted[sorted.size / 2] / 1_000_000.0
                }
            println()
            println("==================================================")
            println("           ISSUANCE BENCHMARK RESULTS")
            println("==================================================")
            println("Iterations        : $iterations")
            println("Average Latency   : %.2f ms".format(averageLatencyMs))
            println("Median Latency    : %.2f ms".format(medianLatencyMs))
            println("Minimum Latency   : %.2f ms".format(minTime / 1_000_000.0))
            println("Maximum Latency   : %.2f ms".format(maxTime / 1_000_000.0))
            println("Throughput        : %.2f req/s".format(throughput))
            println("Status            : SUCCESS")
            println("==================================================")
        }

    /*
     * Investigation Note:
     *
     * After obtaining very low latency values, I investigated this function to understand
     * what was actually being measured. Initially, I expected the benchmark to represent
     * the complete credential issuance process. However, after tracing the execution flow,
     * I found that the benchmark measures only the internal issuance logic executed inside
     * IssueCredential.fromPlainRequest().
     *
     * The benchmark does not include the complete end-to-end workflow such as external
     * service communication, network latency, Wallet interactions, or Verifier operations.
     * In addition, the test configuration uses a test AttestationIssuer that returns a
     * predefined credential response. This explains why the measured latency is
     * significantly lower than expected and why the benchmark reflects the execution time
     * of the internal issuance logic rather than the complete EUDI issuance process.
     */
    @Test
    fun `benchmark - parallel issuance`() =
        runTest {

            val authContext = authorizationContext()

            val request =
                CredentialRequestTO(
                    credentialConfigurationId = PidMsoMdocV1CredentialConfigurationId.value,
                    proofs =
                        CredentialRequestTO.ProofsTO(
                            jwtProofs = listOf(jwtProofString()),
                        ),
                )

            println()
            println("Starting parallel issuance benchmark...")
            println("Running JVM warm-up...")

            // JVM warm-up
            coroutineScope {
                List(20) {
                    async {
                        issueCredential.fromPlainRequest(authContext, request)
                    }
                }.awaitAll()
            }

            /*
 * While tracing the benchmark execution, I found that every issuance benchmark
 * eventually reaches this function.
 *
 * At first I assumed this method was only a simple wrapper, but after exploring
 * the project I realized that it is actually the main entry point of the
 * credential issuance use case.
 *
 * For this reason I continued following the execution flow starting from here
 * to understand what is really measured by my benchmark.
 */
            val requestCounts = listOf(100, 500, 1000)

            requestCounts.forEach { concurrentRequests ->

                val start = System.nanoTime()

                coroutineScope {
                    val jobs =
                        List(concurrentRequests) {
                            async {
                                val result =
                                    issueCredential.fromPlainRequest(authContext, request)

                                assertIs<IssueCredentialResponse.PlainTO>(result)
                            }
                        }

                    jobs.awaitAll()
                }

                val elapsed = System.nanoTime() - start

                println()
                println("==================================================")
                println("          PARALLEL ISSUANCE BENCHMARK")
                println("==================================================")
                println("Concurrent Requests : $concurrentRequests")
                println("--------------------------------------------------")
                println("Total Time          : %.2f ms".format(elapsed / 1_000_000.0))
                println(
                    "Average per Request : %.2f ms".format(
                        (elapsed / 1_000_000.0) / concurrentRequests
                    )
                )
                println(
                    "Throughput          : %.2f req/s".format(
                        concurrentRequests / (elapsed / 1_000_000_000.0)
                    )
                )
                println("Status              : SUCCESS")
                println("==================================================")
            }

            println()
            println("Parallel benchmark completed successfully.")
        }

    @Test
    fun `fails when both identifiers missing`() =
        runTest {
            val authContext = authorizationContext()
            val request =
                CredentialRequestTO(
                    proofs =
                        CredentialRequestTO.ProofsTO(
                            jwtProofs = listOf(jwtProofString()),
                        ),
                )

            val result = issueCredential.fromPlainRequest(authContext, request)

            val failed = assertIs<IssueCredentialResponse.FailedTO>(result)
            assertEquals(CredentialErrorTypeTo.INVALID_CREDENTIAL_REQUEST, failed.type)
        }

    @Test
    fun `fails when both identifiers provided`() =
        runTest {
            val authContext = authorizationContext()
            val request =
                CredentialRequestTO(
                    credentialConfigurationId = "some-id",
                    credentialIdentifier = "some-identifier",
                    proofs =
                        CredentialRequestTO.ProofsTO(
                            jwtProofs = listOf(jwtProofString()),
                        ),
                )

            val result = issueCredential.fromPlainRequest(authContext, request)

            val failed = assertIs<IssueCredentialResponse.FailedTO>(result)
            assertEquals(CredentialErrorTypeTo.INVALID_CREDENTIAL_REQUEST, failed.type)
        }

    @Test
    fun `fails with unknown credential configuration id`() =
        runTest {
            val authContext = authorizationContext()
            val request =
                CredentialRequestTO(
                    credentialConfigurationId = "unknown-id",
                    proofs =
                        CredentialRequestTO.ProofsTO(
                            jwtProofs = listOf(jwtProofString()),
                        ),
                )

            val result = issueCredential.fromPlainRequest(authContext, request)

            val failed = assertIs<IssueCredentialResponse.FailedTO>(result)
            assertEquals(CredentialErrorTypeTo.UNKNOWN_CREDENTIAL_CONFIGURATION, failed.type)
        }

    @Test
    fun `fails with client status expired before preferred period`() =
        runTest {
            val authContext =
                authorizationContext(
                    clientStatusExpiresAt = clock.now() + 10.days,
                )
            val request =
                CredentialRequestTO(
                    credentialConfigurationId = PidMsoMdocV1CredentialConfigurationId.value,
                    proofs =
                        CredentialRequestTO.ProofsTO(
                            jwtProofs = listOf(jwtProofString()),
                        ),
                )

            val result = issueCredential.fromPlainRequest(authContext, request)

            val failed = assertIs<IssueCredentialResponse.FailedTO>(result)
            assertEquals(CredentialErrorTypeTo.CREDENTIAL_REQUEST_DENIED, failed.type)
        }

    @Test
    fun `fails with wrong scope`() =
        runTest {
            val authContext =
                authorizationContext(
                    scopes = nonEmptySetOf(Scope("wrong.scope")),
                )
            val request =
                CredentialRequestTO(
                    credentialConfigurationId = PidMsoMdocV1CredentialConfigurationId.value,
                    proofs =
                        CredentialRequestTO.ProofsTO(
                            jwtProofs = listOf(jwtProofString()),
                        ),
                )

            val result = issueCredential.fromPlainRequest(authContext, request)

            val failed = assertIs<IssueCredentialResponse.FailedTO>(result)
            assertEquals(CredentialErrorTypeTo.INVALID_CREDENTIAL_REQUEST, failed.type)
        }

    @Test
    fun `fails with multiple proof types`() =
        runTest {
            val authContext = authorizationContext()
            val request =
                CredentialRequestTO(
                    credentialConfigurationId = PidMsoMdocV1CredentialConfigurationId.value,
                    proofs =
                        CredentialRequestTO.ProofsTO(
                            jwtProofs = listOf(jwtProofString()),
                            attestations = listOf(jwtProofString()),
                        ),
                )

            val result = issueCredential.fromPlainRequest(authContext, request)

            val failed = assertIs<IssueCredentialResponse.FailedTO>(result)
            assertEquals(CredentialErrorTypeTo.INVALID_PROOF, failed.type)
        }
}
