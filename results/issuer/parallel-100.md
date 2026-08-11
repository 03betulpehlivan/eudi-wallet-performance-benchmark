# Issuer Parallel Benchmark — 100 Concurrent Requests

## Configuration

| Parameter | Value |
|---|---:|
| Operation | Credential Issuance |
| Execution Mode | Parallel |
| Concurrent Requests | 100 |
| Status | SUCCESS |

## Results

| Metric | Result |
|---|---:|
| Total Time | 3371.22 ms |
| Average per Request | 33.71 ms |
| Throughput | 29.66 req/s |

## Analysis

The benchmark completed successfully with 100 concurrent credential issuance requests.

The total execution time was 3371.22 ms, with an average of 33.71 ms per request. The measured throughput was 29.66 requests per second.

At this concurrency level, the measured throughput is close to the sequential baseline. This indicates that increasing concurrency to 100 requests did not provide a significant throughput improvement in this particular execution.

This result serves as the first point in the parallel scalability evaluation and is compared with the 500- and 1000-concurrent-request scenarios.