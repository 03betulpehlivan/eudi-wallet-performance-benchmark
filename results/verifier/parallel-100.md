# Verifier Parallel Benchmark — 100 Concurrent Requests

## Configuration

| Parameter | Value |
|---|---:|
| Operation | Wallet Response and Verification |
| Execution Mode | Parallel |
| Concurrent Requests | 100 |
| Status | SUCCESS |

## Results

### Wallet Response

| Metric | Result |
|---|---:|
| Average | 52.89 ms |
| Minimum | 19.65 ms |
| Maximum | 560.74 ms |

### Verification

| Metric | Result |
|---|---:|
| Average | 30.62 ms |
| Minimum | 11.58 ms |
| Maximum | 259.42 ms |

### Overall

| Metric | Result |
|---|---:|
| Total Time | 27440.39 ms |
| Throughput | 3.64 req/s |

## Analysis

The benchmark completed successfully with 100 concurrent verifier requests.

The average Wallet Response latency was 52.89 ms, while the average Verification latency was 30.62 ms. Verification therefore showed a lower average latency than the Wallet Response stage in this execution.

The maximum latency was 560.74 ms for Wallet Response and 259.42 ms for Verification, showing that occasional higher-latency requests occurred during the parallel execution.

The total execution time was 27440.39 ms and the measured throughput was 3.64 requests per second.

These results provide the first parallel verifier measurement and can be compared with the sequential verifier benchmark to evaluate the effect of concurrent execution.