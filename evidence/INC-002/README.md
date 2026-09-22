# INC-002 evidence manifest

| Evidence | Producer | Output |
|---|---|---|
| Eight concurrent retry results | `OrderIdempotencyIntegrationTest` | `build/evidence/inc-002/concurrent-retries.json` |
| Payload mismatch behavior | `OrderIdempotencyIntegrationTest` | CI test report |
| Fingerprint determinism | `IdempotencyFingerprintTest` | CI test report |

The evidence test uses Java 25 virtual threads and a `CountDownLatch` start gate so the requests compete for the same key instead of running sequentially by accident.
