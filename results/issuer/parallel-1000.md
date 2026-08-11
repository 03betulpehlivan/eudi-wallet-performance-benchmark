# Issuer Parallel Benchmark — 1000 Concurrent Requests

## Configuration

| Parameter | Value |
|---|---:|
| Operation | Credential Issuance |
| Execution Mode | Parallel |
| Concurrent Requests | 1000 |
| Status | SUCCESS |

## Results

| Metric | Result |
|---|---:|
| Total Time | 1414.87 ms |
| Average per Request | 1.41 ms |
| Throughput | 706.78 req/s |

## Analysis

The benchmark completed successfully with 1000 concurrent credential issuance requests.

The total execution time was 1414.87 ms, with an average of 1.41 ms per request. The measured throughput reached 706.78 requests per second, the highest throughput observed among the tested parallel configurations.

Compared with the 500-concurrent-request benchmark, the total execution time increased slightly, but the measured throughput continued to increase. This indicates that the system was able to process a larger number of concurrent requests efficiently during this test.

This result represents the highest tested concurrency level and is used together with the 100- and 500-request scenarios to evaluate the observed scalability behavior.