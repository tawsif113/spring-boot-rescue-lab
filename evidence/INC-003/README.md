# INC-003 evidence manifest

| Evidence | Producer | Output |
|---|---|---|
| Forced lost update and protected race | `InventoryConcurrencyIntegrationTest` | `build/evidence/inc-003/inventory-race.json` |

The test does not depend on lucky thread timing. A `CyclicBarrier` forces both fragile transactions to load stock before either can continue. The protected race uses a start gate and distinct idempotency keys so the atomic inventory update—not the retry lock—decides the winner.
