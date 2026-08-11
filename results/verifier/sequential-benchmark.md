# Verifier Sequential Benchmark

## Configuration

| Parameter | Value |
|---|---:|
| Operation | Wallet Response and Verification |
| Execution Mode | Sequential |
| Iterations | 100 |
| Status | SUCCESS |

## Results

### Init Transaction

| Metric | Result |
|---|---:|
| Average | 32.27 ms |
| Minimum | 15.40 ms |
| Maximum | 132.44 ms |

### Wallet Response

| Metric | Result |
|---|---:|
| Average | 29.59 ms |
| Minimum | 14.99 ms |
| Maximum | 76.33 ms |

### Verification

| Metric | Result |
|---|---:|
| Average | 20.61 ms |
| Minimum | 8.14 ms |
| Maximum | 75.93 ms |

## Analysis

The benchmark was executed sequentially for 100 iterations.

The measurements separate the main stages of the verifier-side processing into Init Transaction, Wallet Response, and Verification. This separation makes it possible to observe the latency contribution of each stage independently.

The average latency was 32.27 ms for Init Transaction, 29.59 ms for Wallet Response, and 20.61 ms for Verification.

Among the three stages, Verification had the lowest average latency, while Init Transaction had the highest average latency. The maximum observed latency was also highest for Init Transaction at 132.44 ms.

The results show that the individual verification operation itself was relatively lightweight compared with the transaction initialization and wallet response stages in this sequential execution.

These measurements provide the sequential baseline for comparison with the parallel verifier benchmark.