# Issuer Parallel Benchmark — 500 Concurrent Requests

## Configuration

| Parameter | Value |
|---|---:|
| Operation | Credential Issuance |
| Execution Mode | Parallel |
| Concurrent Requests | 500 |
| Status | SUCCESS |

## Results

| Metric | Result |
|---|---:|
| Total Time | 1151.98 ms |
| Average per Request | 2.30 ms |
| Throughput | 434.04 req/s |

## Analysis

The benchmark completed successfully with 500 concurrent credential issuance requests.

The total execution time was 1151.98 ms, with an average of 2.30 ms per request. The measured throughput increased substantially to 434.04 requests per second.

Compared with the 100-concurrent-request benchmark, the system processed a much larger number of requests within a shorter total execution time. This demonstrates significantly higher measured throughput under this concurrency level.

The result should be interpreted together with the other concurrency scenarios rather than as a standalone indication of unlimited scalability.
