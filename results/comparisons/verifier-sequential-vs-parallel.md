# Verifier Sequential vs Parallel Benchmark

## Overview

This document compares the verifier benchmark under sequential and parallel execution.

The benchmark was implemented in:

`WalletResponseDirectPostJwtTest.kt`

The purpose of this comparison is to observe the behavior of the verifier workflow under sequential execution and concurrent requests, with particular attention to the main processing stages:

- Init Transaction
- Wallet Response
- Verification

The results below correspond to the benchmark executions performed during the development of this study.

---

## Sequential Verifier Benchmark

### Configuration

| Parameter      | Value                              |
| -------------- | ---------------------------------: |
| Test File      | `WalletResponseDirectPostJwtTest.kt` |
| Execution Mode | Sequential                         |
| Iterations     | 100                                |
| Status         | SUCCESS                            |

### Results

#### Init Transaction

| Metric | Result |
| ------ | -----: |
| Average | 32.27 ms |
| Minimum | 15.40 ms |
| Maximum | 132.44 ms |

#### Wallet Response

| Metric | Result |
| ------ | -----: |
| Average | 29.59 ms |
| Minimum | 14.99 ms |
| Maximum | 76.33 ms |

#### Verification

| Metric | Result |
| ------ | -----: |
| Average | 20.61 ms |
| Minimum | 8.14 ms |
| Maximum | 75.93 ms |

The sequential benchmark completed successfully for 100 iterations.

Among the measured stages, Init Transaction had the highest average latency at 32.27 ms, followed by Wallet Response at 29.59 ms and Verification at 20.61 ms.

The results show that the verification stage itself was not the most expensive part of the measured workflow. The initialization and wallet-response processing stages contributed more latency on average in this benchmark execution.

---

## Parallel Verifier Benchmark

The verifier workflow was subsequently evaluated using concurrent requests.

Only the 100-request parallel configuration is included because this was the parallel configuration actually executed and recorded during the project.

### Parallel — 100 Concurrent Requests

| Parameter | Result |
| --------- | -----: |
| Concurrent Requests | 100 |
| Total Time | 27440.39 ms |
| Throughput | 3.64 req/s |
| Status | SUCCESS |

#### Wallet Response

| Metric | Result |
| ------ | -----: |
| Average | 52.89 ms |
| Minimum | 19.65 ms |
| Maximum | 560.74 ms |

#### Verification

| Metric | Result |
| ------ | -----: |
| Average | 30.62 ms |
| Minimum | 11.58 ms |
| Maximum | 259.42 ms |

---

## Sequential vs Parallel Comparison

| Execution Mode | Requests | Init Transaction Avg. | Wallet Response Avg. | Verification Avg. | Throughput |
| -------------- | -------: | --------------------: | -------------------: | ----------------: | ---------: |
| Sequential | 100 | 32.27 ms | 29.59 ms | 20.61 ms | Not recorded |
| Parallel | 100 | Not separately recorded | 52.89 ms | 30.62 ms | 3.64 req/s |

The two executions show different latency characteristics.

For the Wallet Response stage, the average latency increased from 29.59 ms in the sequential benchmark to 52.89 ms in the parallel benchmark.

Similarly, the average Verification latency increased from 20.61 ms to 30.62 ms.

This behavior is different from the issuer benchmark, where increasing concurrency to higher levels produced a substantial increase in throughput.

In the verifier benchmark, the parallel execution introduces additional concurrency-related overhead while multiple requests are being processed simultaneously. Therefore, the parallel result should not be interpreted simply as "faster" or "slower" than the sequential result.

The purpose of the parallel test is to observe how the verifier behaves under concurrent workload rather than to assume that parallel execution must always reduce individual request latency.

---

## Latency Behavior Under Concurrency

The increase in average Wallet Response latency from 29.59 ms to 52.89 ms indicates that concurrent execution can increase the processing time experienced by individual requests.

The same pattern can be observed in the Verification stage, where the average latency increased from 20.61 ms to 30.62 ms.

The maximum values also increased considerably:

- Wallet Response maximum latency increased from 76.33 ms to 560.74 ms.
- Verification maximum latency increased from 75.93 ms to 259.42 ms.

This indicates that under concurrent workload, some requests experienced significantly longer execution times than the typical request.

However, these maximum values should be interpreted carefully because a benchmark execution can contain occasional outliers caused by scheduling, contention, JVM behavior, or other runtime effects.

---

## Why Are the Verifier Results Different From the Issuer Results?

The verifier and issuer benchmarks should not be expected to produce identical performance characteristics.

They measure different operations and different processing paths.

The issuer benchmark focuses on credential issuance, while the verifier benchmark measures a workflow involving transaction initialization, wallet response processing, and verification.

The verifier workflow therefore contains different computational and processing stages.

In addition, the benchmark measurements are performed under controlled test conditions and should not be interpreted as complete end-to-end measurements of a production EUDI Wallet ecosystem.

The results are most useful for understanding the behavior of the specific implementation and benchmark path that was executed.

---

## Benchmark Scope and Limitations

The verifier benchmark represents a controlled measurement of the tested verifier workflow.

It should not be interpreted as a complete production performance measurement including every possible external dependency and system-level operation.

In particular, the benchmark results depend on:

- the selected test implementation,
- the benchmark execution environment,
- the number of iterations or concurrent requests,
- JVM runtime behavior,
- coroutine scheduling,
- and the exact measurement boundaries defined by the test.

Consequently, individual latency values can vary between executions.

For this reason, the recorded values in this document represent the actual benchmark runs used during the development of this project rather than universal performance guarantees.

---

## Methodological Interpretation

The verifier benchmark was introduced to complement the issuer measurements and provide a separate view of verifier-side performance.

The benchmark was intentionally kept separate from the issuer benchmark because credential issuance and credential verification represent different operations and should not be treated as the same workload.

The sequential benchmark provides a baseline for the verifier workflow, while the parallel benchmark demonstrates how the same general workflow behaves when multiple requests are processed concurrently.

The results show that concurrency can increase individual-stage latency in the tested verifier configuration.

Therefore, the verifier results should primarily be used as a baseline for understanding the behavior of the tested implementation and for future performance comparisons or optimizations.

---

## Development Note

The benchmark was developed iteratively during the project.

The initial performance measurements were followed by a more structured separation of issuer and verifier benchmarks. This allowed the performance characteristics of credential issuance and verification to be evaluated independently.

The verifier benchmark was then extended with a parallel execution scenario to investigate its behavior under concurrent workload.

Only configurations that were actually executed and recorded are documented here. No 500- or 1000-request verifier results are included because those configurations were not part of the recorded verifier benchmark runs.