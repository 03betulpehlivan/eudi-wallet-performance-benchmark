# Reproducibility

## Purpose

This document describes the environment, project versions, hardware, and execution conditions used for the benchmark measurements.

The goal is to make the benchmark experiments reproducible and to clearly distinguish the benchmark repository from the original EUDI Wallet / Issuer project in which the tests were executed.

---

## Repository and Source Project

The benchmark implementations and their recorded results are maintained in the:

`eudi-wallet-performance-benchmark`

repository.

The issuer benchmarks were executed within the corresponding:

`eudi-srv-pid-issuer`

project.

The benchmark repository is used primarily to preserve the benchmark implementations, results, comparisons, screenshots, and documentation. It is not intended to be a standalone Gradle project.

Therefore, the Gradle wrapper and build configuration remain in the original EUDI project rather than being duplicated in the benchmark repository.

### Issuer Source Project

| Item | Value |
|---|---|
| Project | `eudi-srv-pid-issuer` |
| Commit used for the benchmark work | `6172fca00fc303286dc27a94f028264ffb929c8c` |

The benchmark-related test implementations were developed and modified within the corresponding test sources of the issuer project and were subsequently preserved in the dedicated benchmark repository.

---

## Hardware Environment

The issuer benchmark measurements were performed on the following machine:

| Component | Configuration |
|---|---|
| CPU | Intel Core i5-10210U @ 1.60 GHz |
| Physical cores | 4 |
| Logical processors | 8 |
| Memory | Approximately 8 GB RAM |
| Operating System | Windows 11 Home, x64 |

---

## Software Environment

| Component | Version |
|---|---|
| Operating System | Windows 11 Home, x64 |
| JDK | OpenJDK 17.0.19 |
| Gradle | 9.6.1 |
| Kotlin | 2.3.21 |
| Gradle JVM | Java 17.0.19 |
| Docker | 29.5.3 |

The Gradle version and Kotlin version were obtained from the Gradle environment of the original `eudi-srv-pid-issuer` project.

---

## Docker and Container Environment

Docker was installed and used during the setup and integration of the EUDI Wallet environment.

Docker was used while setting up and accessing the wider EUDI Wallet environment, including the components required for integration and development activities.

However, Docker containers were **not running during the benchmark measurements themselves**.

Therefore, Docker container execution overhead is outside the reported benchmark measurements.

This distinction is important when interpreting the benchmark results. The measurements represent the execution conditions of the benchmark tests rather than the complete containerized deployment of the EUDI Wallet ecosystem.

---

## Benchmark Execution Conditions

The benchmark measurements were performed locally on the hardware and software environment described above.

The benchmark tests were executed within the original EUDI project environment rather than as an independently built Gradle project inside the benchmark repository.

The benchmark repository preserves the corresponding benchmark implementations and recorded results so that the experimental methodology and results can be inspected independently from the original project.

---

## Result Variability

Benchmark results may vary between individual executions.

The reported latency and throughput values should therefore not be interpreted as values that must be reproduced exactly to the last decimal place.

Runtime factors such as JVM behavior, thread scheduling, coroutine scheduling, system load, and other execution-time effects can influence individual measurements.

Reproducibility in this study therefore means that the same benchmark implementation, configuration, source version, and execution methodology can be reproduced and that the resulting performance characteristics should remain comparable.

For this reason, small differences between repeated executions do not necessarily indicate an error in the benchmark implementation.

---

## Benchmark Scope

The benchmark measurements are controlled performance measurements of specific execution paths.

They should not be interpreted as complete end-to-end measurements of the production EUDI Wallet credential issuance or verification workflow.

In particular, the benchmark results should be interpreted together with the measurement boundaries and limitations documented in the corresponding result and comparison documents.

---

## Benchmark Configurations

The benchmark repository contains separate benchmark implementations for:

- Initial Wallet API testing
- Issuer credential issuance
- Verifier processing

Issuer and verifier benchmarks are further evaluated using sequential and parallel execution where applicable.

The issuer parallel benchmark includes separate measurements for:

- 100 concurrent requests
- 500 concurrent requests
- 1000 concurrent requests

The exact benchmark parameters and recorded results are documented separately under the `results/` directory.

---

## Reproduction Notes

To reproduce the benchmark experiments, the following should be kept consistent where possible:

1. The corresponding source project and benchmark-related source version should be used.
2. The required JDK version should be used.
3. The corresponding Gradle version should be used.
4. The benchmark implementation and configuration should remain unchanged.
5. The same sequential or parallel execution mode should be selected.
6. The same request or concurrency level should be used.
7. The benchmark should be executed under comparable hardware and system conditions.
8. Docker should not be running during the benchmark measurements if reproducing the measurements described in this document.
9. Individual numerical results should be expected to vary between executions.

The recorded results in this repository represent the actual benchmark executions used during the study and provide reference points for comparison.

---

## Related Documentation

Detailed benchmark results are available under:

`results/`

Sequential versus parallel comparisons are available under:

`results/comparisons/`

Benchmark implementations are available under:

`benchmarks/`

Screenshots of selected benchmark executions are stored under:

`screenshots/`