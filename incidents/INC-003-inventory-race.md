# INC-003 — Inventory overselling

**Status:** Planned

## Scenario

Two transactions read the same available stock, both pass validation, and both reserve units. The fragile baseline has no version column, database lock, or atomic conditional update.

The investigation will reproduce the race before comparing optimistic locking, pessimistic locking, and atomic SQL updates.

