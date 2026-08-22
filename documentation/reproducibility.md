# Reproducibility

## Purpose

This document describes the environment, source versions, benchmark structure, execution conditions, measurement methodology, and limitations used during the performance benchmarking work.

The main goal is to make the benchmark experiments reproducible and to provide enough information for another developer to understand how the measurements were produced, what was measured, and under which conditions the measurements were obtained.

This document also distinguishes the dedicated benchmark repository from the original EUDI Wallet source projects in which the benchmark implementations were developed and executed.

---

## Repository and Source Projects

The benchmark implementations, recorded results, comparisons, screenshots, diagrams, and documentation are maintained in:

`eudi-wallet-performance-benchmark`

The benchmark repository is primarily a documentation and experiment repository. It preserves the benchmark-related source files and experimental artefacts, but it is not intended to be a standalone Gradle project.

The original project source and its Gradle/build configuration therefore remain in the corresponding EUDI Wallet source projects.

### Issuer Source Project

The issuer benchmark was developed and executed within:

`eudi-srv-pid-issuer`

The source version used during the benchmark work was:

| Item | Value |
|---|---|
| Project | `eudi-srv-pid-issuer` |
| Commit | `6172fca00fc303286dc27a94f028264ffb929c8c` |

The benchmark-related test implementations were developed and modified in the corresponding test sources of the issuer project and were subsequently preserved in this benchmark repository.

### Benchmark Repository

The dedicated benchmark repository preserves:

- benchmark implementations
- benchmark results
- sequential/parallel comparisons
- reproducibility documentation
- screenshots
- workflow and measurement diagrams
- localization-related documentation and evidence

This separation allows the benchmark work to remain traceable without duplicating the complete original EUDI Wallet source project.

---

## Hardware Environment

The issuer benchmark measurements were performed locally on the following machine:

| Component | Configuration |
|---|---|
| CPU | Intel Core i5-10210U @ 1.60 GHz |
| Physical cores | 4 |
| Logical processors | 8 |
| Memory | Approximately 8 GB RAM |
| Operating System | Windows 11 Home, x64 |

The hardware information is included because CPU resources, available memory, operating-system scheduling, and background processes can affect performance measurements.

---

## Software Environment

The main software environment used during the benchmark work was:

| Component | Version |
|---|---|
| Operating System | Windows 11 Home, x64 |
| JDK | OpenJDK 17.0.19 |
| Gradle | 9.6.1 |
| Kotlin | 2.3.21 |
| Gradle JVM | Java 17.0.19 |
| Docker | 29.5.3 |

The Gradle and Kotlin versions correspond to the development environment of the original EUDI Wallet issuer project.

The JDK version is particularly relevant because JVM execution characteristics can influence benchmark timing.

---

## Docker and Container Environment

Docker was installed and used during the wider EUDI Wallet setup and integration activities.

Docker was used while preparing and accessing the broader EUDI Wallet environment and its related components.

However, Docker containers were **not running during the benchmark measurements themselves**.

Therefore, container execution overhead is outside the reported benchmark measurements.

This distinction is important when interpreting the results. The measurements describe the execution of the benchmark tests under the defined local conditions rather than the performance of the complete containerized EUDI Wallet ecosystem.

---

## Benchmark Structure

The benchmark work contains several related test areas:

- Initial Wallet API testing
- Credential issuance benchmarking
- Verifier processing
- Sequential benchmark execution
- Parallel benchmark execution where applicable

The benchmark repository separates these areas so that the implementation, results, and comparisons can be inspected independently.

The corresponding benchmark implementations are preserved under:

`benchmarks/`

Recorded results are preserved under:

`results/`

Screenshots are preserved under:

`screenshots/`

Workflow and measurement diagrams are preserved under:

`diagrams/`

---

## Credential Issuance Benchmark

The credential issuance benchmark focuses on the execution path involved in credential issuance.

The benchmark initializes the required authorization context and credential request and then performs the credential issuance operation repeatedly.

For each iteration, the benchmark:

1. starts the timer using `System.nanoTime()`,
2. executes the credential issuance operation,
3. stops the timer,
4. calculates the elapsed time,
5. stores the resulting latency,
6. updates the collected statistics.

The benchmark then performs statistical analysis over the collected latency values.

The main calculated metrics include:

- average latency
- median latency
- minimum latency
- maximum latency
- throughput

The benchmark implementation and its execution flow are documented visually in:

`diagrams/benchmark-implementation-flow.png`

The higher-level benchmark process is shown in:

`diagrams/benchmark-overall-flow.png`

---

## Credential Issuance Measurement Scope

The benchmark does **not** represent the complete end-to-end production credential issuance system.

The production workflow contains several stages, including request processing, issuer selection/business logic, credential generation, serialization, and response construction.

The benchmark instead focuses on a selected internal execution path under controlled benchmark conditions.

The measurement scope is documented separately in:

`diagrams/credential-issuance-benchmark-scope.png`

The production-side workflow investigated during the implementation is documented in:

`diagrams/credential-issuance-production-flow.png`

This distinction is important because the benchmark values should not be interpreted as complete production-system latency measurements.

---

## Verifier Benchmark

The repository also contains benchmark-related work for verifier processing.

The verifier-side documentation focuses on the internal request-processing path.

The investigated flow begins with an HTTP POST request reaching the verifier REST endpoint. The request is parsed and validated, a presentation transaction is created, and an OpenID4VP authorization request is generated before the transaction identifier is returned to the wallet.

The corresponding workflow is documented in:

`diagrams/verifier-internal-request-processing.png`

The verifier benchmark results and related artefacts are preserved under the corresponding `results/verifier/` and `benchmarks/verifier/` directories.

---

## Sequential and Parallel Execution

The benchmark work includes sequential execution and parallel execution where applicable.

Sequential execution measures repeated operations one after another.

Parallel execution is used to investigate the behavior of the benchmarked issuer path under concurrent request loads.

The issuer parallel benchmark includes separate measurements for:

- 100 concurrent requests
- 500 concurrent requests
- 1000 concurrent requests

Sequential versus parallel results are documented under:

`results/comparisons/`

The corresponding benchmark implementations are preserved under:

`benchmarks/`

The exact recorded values are preserved in the result files rather than being recreated in this document.

---

## Measurement Methodology

The benchmark measures elapsed execution time using:

`System.nanoTime()`

For each measured operation, a start timestamp is recorded before the benchmarked operation and a stop timestamp is recorded after the operation.

The elapsed time is then calculated from the difference between the two timestamps.

The collected latency values are stored and used to calculate the statistical performance metrics.

The benchmark therefore separates:

1. benchmark initialization,
2. measured execution,
3. latency collection,
4. statistical analysis,
5. final result generation.

This structure was also used when creating the benchmark workflow diagrams.

---

## Statistical Metrics

The benchmark collects latency measurements across multiple iterations.

The recorded measurements are used to calculate:

### Average

The average latency represents the mean execution time across the collected measurements.

### Median

The median represents the middle value of the latency distribution after ordering the measurements.

### Minimum

The minimum represents the fastest recorded execution.

### Maximum

The maximum represents the slowest recorded execution.

### Throughput

Throughput represents the number of completed requests or operations per unit of time and is used as an additional performance indicator.

These metrics are preserved in the recorded benchmark results under:

`results/`

---

## Benchmark Configurations

The repository contains separate result sets for the different benchmark scenarios.

For issuer benchmarking, the repository includes sequential and parallel measurements.

The parallel measurements include the following concurrency levels:

| Configuration | Concurrent requests |
|---|---:|
| Parallel | 100 |
| Parallel | 500 |
| Parallel | 1000 |

The corresponding sequential and parallel comparisons are stored under:

`results/comparisons/`

The exact recorded benchmark outputs should be used when comparing executions rather than assuming that every repeated execution will produce identical numerical values.

---

## Execution Conditions

The benchmark measurements were performed locally under the hardware and software conditions described in this document.

The benchmark tests were executed within the original EUDI Wallet project environment.

The dedicated benchmark repository contains the preserved benchmark-related implementations and recorded experimental artefacts.

During measurement:

- the benchmark was executed locally;
- Docker containers were not running;
- the benchmark used the defined test configuration;
- the benchmark measured the selected internal execution path;
- repeated measurements were collected before statistical analysis.

Background system activity and JVM runtime behavior can still introduce small differences between executions.

---

## Result Variability

Benchmark results may vary between individual executions.

The reported latency and throughput values should therefore not be interpreted as values that must be reproduced exactly to the last decimal place.

Possible sources of variation include:

- JVM execution behavior
- thread scheduling
- coroutine scheduling
- operating-system scheduling
- background system activity
- available CPU resources
- memory usage
- runtime conditions

Reproducibility in this work therefore means that the same benchmark implementation, source version, configuration, measurement methodology, and comparable execution environment can be used again.

The objective is to reproduce comparable performance characteristics rather than identical numerical values.

Small differences between repeated executions do not necessarily indicate an error in the benchmark implementation.

---

## What Is Included in the Measurement

The benchmark measurements represent the execution time of the selected internal benchmark path under the defined test conditions.

The measurement scope is intentionally narrower than the complete deployed EUDI Wallet ecosystem.

The benchmark results should therefore be understood as measurements of the selected components rather than complete end-to-end user-perceived performance.

---

## What Is Not Included

The following should not be interpreted as part of the reported benchmark measurement unless explicitly included by a particular benchmark configuration:

- complete end-to-end wallet interaction
- complete deployed EUDI Wallet ecosystem performance
- Docker container execution overhead
- real-world network conditions
- production deployment infrastructure
- user-device interaction time

This limitation is important when comparing the benchmark results with real-world system performance.

---

## Reproduction Procedure

To reproduce the benchmark experiments, the following conditions should be kept consistent where possible.

### 1. Use the corresponding source project

Use the relevant original EUDI Wallet source project and source version used for the benchmark work.

For the issuer benchmark:

`eudi-srv-pid-issuer`

Source commit:

`6172fca00fc303286dc27a94f028264ffb929c8c`

### 2. Use the required software environment

The benchmark environment used:

- Windows 11 Home, x64
- OpenJDK 17.0.19
- Gradle 9.6.1
- Kotlin 2.3.21

### 3. Preserve the benchmark implementation

The benchmark implementation and its configuration should remain unchanged when reproducing a recorded experiment.

### 4. Select the same benchmark mode

Use the same sequential or parallel execution mode as the experiment being reproduced.

### 5. Use the same concurrency configuration

For parallel issuer measurements, use the corresponding concurrency level:

- 100
- 500
- 1000

### 6. Use comparable execution conditions

Where possible, reproduce the measurements on comparable hardware and with similar system load.

### 7. Keep Docker outside the measurement

Docker containers should not be running during the benchmark measurement if reproducing the conditions described in this document.

### 8. Repeat the benchmark

Multiple executions should be expected to produce slightly different numerical results.

The recorded results in this repository should therefore be used as reference points rather than exact target values.

---

## Repository Artefacts

The benchmark repository contains the following main artefact groups:

| Directory | Purpose |
|---|---|
| `benchmarks/` | Preserved benchmark implementations |
| `diagrams/` | Benchmark and workflow diagrams |
| `documentation/` | Reproducibility and supporting documentation |
| `localization/` | Localization-related documentation and evidence |
| `results/` | Recorded benchmark results and comparisons |
| `screenshots/` | Selected screenshots and execution evidence |

The repository is intended to preserve the experimental work as a traceable collection of implementations, measurements, documentation, and supporting evidence.

---

## Interpreting the Recorded Results

The results stored in this repository represent the actual benchmark executions performed during the study.

They should be interpreted together with:

- the benchmark implementation,
- the benchmark configuration,
- the measurement scope,
- the execution environment,
- the workflow diagrams,
- the result comparisons,
- and the limitations described in this document.

A higher or lower value should not be interpreted independently from the execution mode and configuration under which it was obtained.

For example, sequential and parallel measurements represent different execution conditions and should be compared according to their corresponding configuration.

---

## Limitations

The benchmark has several limitations that should be considered when interpreting the results.

First, the measurements were performed on a single local machine rather than across a representative range of hardware.

Second, runtime behavior can introduce variability between executions.

Third, the benchmark focuses on selected internal execution paths rather than the complete end-to-end EUDI Wallet ecosystem.

Fourth, the measurements were obtained under controlled local conditions and therefore do not represent production deployment conditions.

Finally, the benchmark results should be considered performance observations for the defined experimental setup rather than universal performance characteristics of the EUDI Wallet system.

---

## Related Documentation

Detailed benchmark results are available under:

`results/`

Sequential versus parallel comparisons are available under:

`results/comparisons/`

Benchmark implementations are available under:

`benchmarks/`

Benchmark and workflow diagrams are available under:

`diagrams/`

Selected screenshots are available under:

`screenshots/`

Localization-related documentation and evidence are available under:

`localization/`

The benchmark diagrams also provide a visual explanation of the relationship between the production workflow, benchmark implementation, and actual measurement scope.

---

## Final Reproducibility Note

The purpose of this documentation is not to guarantee identical benchmark numbers across different executions.

Instead, it provides the source version, execution environment, benchmark structure, measurement methodology, configurations, scope, limitations, and repository artefacts required to understand and repeat the experiments.

A reproduction should therefore aim to use the same benchmark implementation and configuration under comparable execution conditions and then compare the resulting performance characteristics with the recorded measurements in this repository.