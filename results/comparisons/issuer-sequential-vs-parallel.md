# Issuer Sequential vs Parallel Benchmark

## Overview

This document compares the dedicated issuer credential issuance benchmark under sequential and parallel execution.

The benchmark was implemented in:

`IssueCredentialTest.kt`

The purpose of this comparison is to observe how increasing concurrency affects credential issuance latency, total execution time, and throughput.

The results below are based on the benchmark executions performed during the development of this study. Since benchmark measurements can vary between executions, the values recorded here correspond to the actual runs used for this project.

---

## Sequential Issuance

### Configuration

| Parameter      | Value                    |
| -------------- | -----------------------: |
| Test File      | `IssueCredentialTest.kt` |
| Operation      | Credential Issuance      |
| Execution Mode | Sequential               |
| Iterations     | 100                      |
| Status         | SUCCESS                  |

### Results

| Metric          | Result       |
| --------------- | -----------: |
| Average Latency | 6.89 ms      |
| Median Latency  | 1.87 ms      |
| Minimum Latency | 1.26 ms      |
| Maximum Latency | 485.40 ms    |
| Throughput      | 145.23 req/s |

The sequential benchmark completed successfully.

The median latency was considerably lower than the average latency. This indicates that most requests completed very quickly, while a smaller number of slower executions increased the average.

The maximum latency of 485.40 ms also demonstrates that occasional slower executions occurred even though the typical request latency was much lower.

---

## Parallel Issuance

The same credential issuance operation was also evaluated under increasing levels of concurrency.

### Parallel — 100 Concurrent Requests

| Metric              | Result       |
| ------------------- | -----------: |
| Concurrent Requests | 100          |
| Total Time          | 3371.22 ms   |
| Average per Request | 33.71 ms     |
| Throughput          | 29.66 req/s  |
| Status              | SUCCESS      |

### Parallel — 500 Concurrent Requests

| Metric              | Result       |
| ------------------- | -----------: |
| Concurrent Requests | 500          |
| Total Time          | 1151.98 ms   |
| Average per Request | 2.30 ms      |
| Throughput          | 434.04 req/s |
| Status              | SUCCESS      |

### Parallel — 1000 Concurrent Requests

| Metric              | Result       |
| ------------------- | -----------: |
| Concurrent Requests | 1000         |
| Total Time          | 1414.87 ms   |
| Average per Request | 1.41 ms      |
| Throughput          | 706.78 req/s |
| Status              | SUCCESS      |

---

## Comparison

| Execution Mode | Requests | Avg. Latency / Request | Throughput   |
| -------------- | -------: | ---------------------: | -----------: |
| Sequential     | 100      | 6.89 ms                | 145.23 req/s |
| Parallel       | 100      | 33.71 ms               | 29.66 req/s  |
| Parallel       | 500      | 2.30 ms                | 434.04 req/s |
| Parallel       | 1000     | 1.41 ms                | 706.78 req/s |

The results show that increasing concurrency does not automatically improve performance at every concurrency level.

At 100 concurrent requests, the parallel benchmark produced a lower throughput than the sequential benchmark. This indicates that the overhead associated with introducing concurrency can become significant at relatively low concurrency levels.

When concurrency was increased to 500 and 1000 requests, however, the system achieved substantially higher throughput. The throughput increased from 29.66 req/s at 100 concurrent requests to 434.04 req/s at 500 concurrent requests and 706.78 req/s at 1000 concurrent requests.

The results therefore suggest that the benchmarked issuance logic can benefit significantly from parallel execution when the level of concurrency is sufficiently high.

The 1000-request parallel execution achieved the highest measured throughput at 706.78 req/s, while the average measured time per request was 1.41 ms.

---

## Why Does the Issuer Benchmark Appear So Fast?

The very low latency values observed in the issuer benchmark should not be interpreted as the end-to-end performance of the complete production EUDI Wallet credential issuance workflow.

During the investigation, the benchmark execution flow was traced step by step to understand exactly what was being measured and why the results were significantly lower than expected.

### 1. The Benchmark Measures an Internal Execution Path

The benchmark does not measure the complete Wallet-to-Issuer credential issuance interaction.

Instead, the measurement is performed around a specific part of the issuer-side credential issuance logic. This means that the measured latency represents the execution time of the code covered by the benchmark rather than the total time required for a real credential issuance request.

The measurement boundary is therefore narrower than the complete production workflow.

### 2. The Benchmark Runs Inside a Controlled Test Environment

The benchmark is executed inside a controlled testing infrastructure.

This provides a much more stable and lightweight environment than a complete production deployment. The benchmark is designed to evaluate the internal behavior of the tested components rather than the complete system-level cost of credential issuance.

### 3. Predefined Test Credentials Are Used

The benchmark uses predefined test credentials and test data.

As a result, the benchmark does not reproduce every operation that would normally be required when processing dynamically generated production data.

This reduces the amount of work performed during each measured iteration and contributes to the low observed latency.

### 4. Test and Mocked Implementations Are Involved

The benchmark infrastructure uses test implementations and mocked components where appropriate.

This is important when interpreting the results because mocked or simplified components can avoid the processing cost associated with real external services or production implementations.

Therefore, the measured execution time should be understood as the performance of the benchmarked implementation under controlled test conditions.

### 5. Execution Is Performed In Memory

A significant factor is that the benchmark performs the measured operations in memory.

The benchmark does not introduce the same persistence and communication overhead that would occur in a complete production workflow.

In-memory execution can therefore produce substantially lower latency than an end-to-end system involving external services, persistent storage, or network communication.

### 6. Network Communication Is Not Included

The benchmark does not measure the network communication between the Wallet and Issuer.

In a real credential issuance flow, network communication introduces additional latency caused by request transmission, response transmission, connection handling, and other networking factors.

Because these operations are outside the measured section, the benchmark latency is naturally lower than an end-to-end Wallet-to-Issuer measurement.

### 7. Database Communication Is Not Included

Database access is also not part of the measured benchmark path.

A production system may perform database reads and writes while processing a credential issuance request. Such operations introduce additional latency that is not represented in the benchmark results.

The absence of database communication therefore contributes to the lower measured execution times.

### 8. The Complete Production Cryptographic Workflow Is Not Reproduced

The benchmark does not execute the complete production credential generation and cryptographic workflow.

Instead, the benchmark uses test-oriented implementations and predefined data.

Therefore, the benchmark should not be interpreted as measuring the complete computational cost of production credential generation and all associated cryptographic operations.

### 9. Some Inputs Are Prepared Before the Measurement

Another important point is the location of the measurement boundary.

Some data required by the issuance operation is prepared before the measured section begins. This means that the benchmark does not necessarily include the full cost of preparing every input from the beginning of a real request.

The measured latency therefore represents the processing performed inside the selected benchmark section.

### 10. Spring Application Initialization Is Not Part of Each Measured Request

The benchmark runs inside an already initialized test application context.

Consequently, application startup and dependency initialization are not repeated for every benchmark iteration.

Components such as the Spring application context and the required issuer infrastructure are initialized before the actual benchmark operations are measured.

This prevents application startup overhead from affecting the per-request latency.

### 11. CredentialIssuerMetadata and Issuer Selection Are Already Available

During the investigation, the credential issuance flow was traced through the issuer architecture.

The application uses `CredentialIssuerMetadata` to manage the available issuer implementations and select the appropriate `AttestationIssuer`.

Because the benchmark runs inside the initialized application context, this infrastructure is already available when the measured operation begins.

Therefore, the benchmark does not include the cost of initializing the complete issuer infrastructure for every request.

### 12. JWT Proof Preparation Is Not Equivalent to a Complete Production Request

The benchmark also prepares the JWT proof used by the test environment before or around the measured execution path rather than reproducing every step of a complete production interaction for each request.

This further reduces the amount of work represented by the measured latency.

---

## Overall Interpretation

The low latency values are therefore understandable when the benchmark's measurement boundaries are considered.

The benchmark combines:

- controlled test infrastructure,
- predefined test credentials,
- test or mocked implementations,
- in-memory execution,
- no network communication,
- no database communication,
- a pre-initialized application context,
- and a measurement boundary focused on internal issuer processing.

These characteristics significantly reduce the amount of work performed during each measured operation.

For this reason, a result such as a few milliseconds should not be interpreted as meaning that a complete production credential issuance request can always be completed within the same time.

Instead, the result demonstrates the performance of the specific internal issuance logic measured by the benchmark under controlled conditions.

This distinction is essential when comparing benchmark results with real-world system performance.

The benchmark is therefore most useful for:

1. comparing different implementations of the same internal operation,
2. observing the effect of sequential versus parallel execution,
3. evaluating throughput under controlled workloads,
4. identifying performance changes during development,
5. and providing a reproducible baseline for future optimization.

It should not be used alone as an end-to-end performance measurement of the complete EUDI Wallet ecosystem.

---

## Benchmark Investigation and Measurement Boundaries

This investigation was an important part of the benchmark development process because the goal was not only to obtain latency values, but also to understand what those values actually represented.

The benchmark implementation was traced against the production architecture to identify the executed components and the exact boundaries of the measurement.

The investigation focused on:

- understanding where the benchmark execution begins,
- identifying which issuer components are actually executed,
- examining how the Spring application context is initialized,
- understanding how `CredentialIssuerMetadata` manages issuer implementations,
- examining how the appropriate `AttestationIssuer` is selected,
- identifying which operations are included in the measured section,
- and distinguishing the benchmark environment from the complete production workflow.

This analysis showed that the measured latency represents a controlled internal execution path rather than the complete end-to-end credential issuance process.

---

## Methodological Interpretation

The main purpose of the benchmark is therefore not to claim that credential issuance can always be completed in approximately 1–7 ms in a production environment.

Instead, the benchmark provides a controlled measurement of the implementation's internal processing performance and allows the effect of sequential versus parallel execution to be observed.

The large improvement in throughput at higher concurrency levels demonstrates that the tested issuance logic can process multiple requests concurrently under the benchmark conditions.

However, these results should be interpreted together with the benchmark scope and limitations described above.

The benchmark was developed iteratively during the project. The initial Wallet API test was later refined into a dedicated issuer benchmark, and parallel execution was subsequently added to investigate scalability under concurrent workloads.