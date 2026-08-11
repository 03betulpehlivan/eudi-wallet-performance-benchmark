# Issuer Sequential Benchmark

## Overview

This benchmark measures credential issuance performance using a sequential execution model.

Each request is executed individually before the next request begins. This provides a baseline for evaluating the effect of concurrent request processing.

## Configuration

| Parameter | Value |
|---|---:|
| Operation | Credential Issuance |
| Execution Mode | Sequential |
| Iterations | 100 |
| Status | SUCCESS |

## Results

| Metric | Result |
|---|---:|
| Average Latency | 6.89 ms |
| Median Latency | 1.87 ms |
| Minimum Latency | 1.26 ms |
| Maximum Latency | 485.40 ms |
| Throughput | 145.23 req/s |

## Analysis

The benchmark completed successfully for 100 sequential credential issuance operations.

The average latency was 6.89 ms, while the median latency was 1.87 ms. The difference between these values indicates that most requests completed relatively quickly, while a smaller number of requests experienced considerably higher latency.

The maximum observed latency was 485.40 ms, compared with a minimum of 1.26 ms. These occasional higher-latency requests increase the average latency without representing the typical request.

The measured throughput was 145.23 requests per second.

This sequential benchmark provides the baseline for comparison with the parallel issuance benchmarks using 100, 500, and 1000 concurrent requests.
