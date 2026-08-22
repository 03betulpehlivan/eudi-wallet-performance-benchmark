# Benchmark and Workflow Diagrams

This folder contains the diagrams I created while analyzing and implementing
the credential issuance and verifier benchmarks.

The main purpose of these diagrams is to make the relationship between the
production workflow, the benchmark implementation, and the actual measurement
scope easier to understand.

During the benchmarking process, I realized that measuring latency values
alone was not enough. I also needed to understand which internal components
were actually executed, where the measurement started and ended, and how the
benchmark was connected to the production workflow.

For this reason, I created the following diagrams.

---

## 1. Benchmark Implementation Flow

**File:** `benchmark-implementation-flow.png`

This diagram shows the implementation-level execution flow of the benchmark.

The benchmark first creates the required authorization context and credential
request and initializes the benchmark statistics.

The benchmark then repeats the credential issuance operation for a predefined
number of iterations. At each iteration, the timer is started using
`System.nanoTime()`, the credential issuance operation is executed, and the
timer is stopped after the operation is completed.

The elapsed time is then calculated and stored as a latency value. The
benchmark updates the total, minimum, and maximum latency values after each
iteration.

After all iterations are completed, the collected measurements are used to
calculate statistical metrics including average, median, minimum, maximum,
and throughput.

I created this diagram to understand the complete benchmark execution from
initialization to the generation of the final performance results.

---

## 2. Benchmark Overall Flow

**File:** `benchmark-overall-flow.png`

This diagram provides a higher-level overview of the benchmark process.

The benchmark starts by initializing the required statistics and then repeats
the credential issuance operation multiple times.

For every iteration, the execution time is measured and the resulting latency
value is stored. After the benchmark loop finishes, the collected values are
used to calculate the main performance metrics.

These metrics include:

- Average latency
- Median latency
- Minimum latency
- Maximum latency
- Throughput

The purpose of this diagram is to provide a simpler overview of how individual
measurements are transformed into benchmark results.

---

## 3. Credential Issuance Production Flow

**File:** `credential-issuance-production-flow.png`

This diagram shows the production-side credential issuance workflow that I
traced while investigating the benchmark.

The flow starts with the Spring application startup and the initialization of
`AppBeans.kt`.

The `CredentialIssuerMetadata` bean provides the available issuer
implementations. The appropriate issuer is then selected through the issuer
factory.

The request continues through components such as
`IssueCredential.fromPlainRequest(...)`, authorization, and
`issueAttestation.invoke(...)`.

Finally, the credential is generated and signed using the production
credential-generation logic, and a `CredentialResponse.Issued(...)` response
is produced.

I created this diagram to understand how the real application components are
connected and how the benchmarked credential issuance logic fits into the
production workflow.

---

## 4. Credential Issuance Benchmark Scope

**File:** `credential-issuance-benchmark-scope.png`

This diagram focuses specifically on the part of the credential issuance
workflow that is measured by the benchmark.

The complete credential issuance workflow contains several stages, including
request processing, issuer business logic, credential generation,
serialization, and response construction.

The diagram identifies the point where the benchmark measurement starts and
shows the execution path followed during the measurement.

This distinction is important because the benchmark does not measure the
complete end-to-end production system.

Instead, the benchmark focuses on selected internal credential issuance
components under controlled conditions.

I created this diagram to clearly separate the actual benchmark measurement
scope from the complete production workflow.

---

## 5. Verifier Internal Request Processing

**File:** `verifier-internal-request-processing.png`

This diagram shows the internal request-processing sequence of the verifier.

An incoming HTTP POST request first reaches the verifier REST endpoint.

The request payload is parsed and validated. After validation, a presentation
transaction is created and an OpenID4VP authorization request is generated.

The transaction identifier is then returned to the wallet, leaving the system
ready for wallet authentication.

I included this diagram to document the verifier-side workflow and to make it
easier to understand which internal stages are involved before the wallet
authentication process.

---

## Why I Created These Diagrams

While implementing the benchmarks, I found that understanding the execution
path was important for interpreting the performance results correctly.

The diagrams helped me answer several questions during the implementation:

- Which methods are actually executed?
- Where does the benchmark timer start?
- Where does the timer stop?
- Which production components are involved?
- Which part of the production workflow is actually measured?
- How are repeated measurements converted into statistical results?
- How does the verifier request-processing flow work?

Therefore, these diagrams document both the benchmark implementation and the
production workflows that I investigated.

They are intended to be read together with the benchmark source code,
benchmark results, and reproducibility documentation in this repository.

---

## Important Measurement Note

The benchmark results should not be interpreted as complete end-to-end
production performance measurements.

The benchmark uses controlled test conditions and focuses on selected internal
components. Network and database communication are not part of the measured
benchmark path.

Therefore, the measured values represent the performance of the selected
components under the defined benchmark conditions rather than the complete
performance of a deployed production system.