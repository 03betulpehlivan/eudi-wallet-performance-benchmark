# Initial Wallet API Test

## Overview

This was the initial benchmark test developed during the performance evaluation process.

The test was implemented in:

`WalletApiTest.kt`

This initial implementation was used to obtain a first performance baseline through the Wallet API before the benchmark methodology was refined into separate issuer and verifier benchmark scenarios.

## Test Configuration

| Parameter | Value |
|---|---:|
| Test File | `WalletApiTest.kt` |
| Operation | Credential Issuance |
| Execution Model | Sequential |
| Requests | 1000 |
| Status | SUCCESS |

## Benchmark Results

| Metric | Result |
|---|---:|
| Total Time | 33.093 s |
| Average Latency | 33.09 ms |
| Median Latency | 24.98 ms |
| Minimum Latency | 17.38 ms |
| Maximum Latency | 4229.26 ms |
| Throughput | 30.22 req/s |

## Analysis

The initial test successfully completed 1000 sequential credential issuance requests.

The measured average latency was 33.09 ms, while the median latency was 24.98 ms. The lower median compared with the average indicates that some requests took considerably longer than the typical request and increased the overall average.

The minimum observed latency was 17.38 ms, whereas the maximum latency reached 4229.26 ms. This large difference shows that occasional high-latency executions occurred during the benchmark.

The measured throughput was 30.22 requests per second.

## Why This Test Was Kept

This test represents the first measurement approach used during the benchmark development process. It is preserved in the repository to document the progression of the work rather than being treated as the final issuer benchmark.

The benchmark methodology was subsequently refined by separating the issuer and verifier measurements and introducing dedicated sequential and parallel benchmark implementations.

Therefore, these results should not be directly treated as equivalent to the later dedicated issuer benchmark results. They document the initial stage of the benchmarking process.

## Methodology Note

The test was performed in a controlled test environment and measures the execution path covered by the benchmark implementation. The result should therefore be interpreted as a benchmark measurement of the tested Wallet API path rather than as a complete end-to-end production performance measurement.