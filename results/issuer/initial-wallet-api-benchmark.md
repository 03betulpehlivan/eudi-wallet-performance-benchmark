# Initial Wallet API Benchmark

## Overview

This benchmark represents the initial performance measurement approach used during the credential issuance evaluation.

The benchmark measures credential issuance through the Wallet API using a sequential request model. It was implemented as an initial baseline before the benchmark methodology was refined and the dedicated issuer benchmark was introduced.

## Configuration

| Parameter | Value |
|---|---:|
| Operation | Credential Issuance |
| Execution Mode | Sequential |
| Request Count | 1000 |
| Status | SUCCESS |

## Results

| Metric | Result |
|---|---:|
| Total Time | 33.093 s |
| Average Latency | 33.09 ms |
| Median Latency | 24.98 ms |
| Minimum Latency | 17.38 ms |
| Maximum Latency | 4229.26 ms |
| Throughput | 30.22 req/s |

## Analysis

The benchmark completed successfully for 1000 sequential credential issuance requests.

The average latency was 33.09 ms, while the median latency was lower at 24.98 ms. This difference indicates that a smaller number of requests experienced substantially higher latency.

The minimum observed latency was 17.38 ms, whereas the maximum reached 4229.26 ms. The large difference between the minimum, median, and maximum values suggests the presence of occasional high-latency requests during the benchmark execution.

The measured throughput was 30.22 requests per second.

## Methodological Evolution

This implementation was retained as the initial benchmark approach used during the performance evaluation.

During the subsequent analysis, the benchmark methodology was refined by introducing a dedicated issuer benchmark and separate sequential and parallel execution scenarios. This allowed credential issuance performance to be evaluated more systematically under different concurrency levels.

The initial benchmark is therefore preserved as part of the development and methodological history of the performance evaluation.