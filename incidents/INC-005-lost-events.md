# INC-005 — Lost order events

**Status:** Planned

## Scenario

Order persistence and message publication cannot participate in one atomic transaction. A process failure between them can leave a committed order without its integration event.

The investigation will reproduce the failure and implement a transactional outbox with retry and duplicate-delivery handling.

