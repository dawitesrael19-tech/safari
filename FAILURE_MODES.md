# FAILURE_MODES.md

Every way each API could lose money, create money, pay twice, block a thread or hang.
Written before the code that addresses it. Each line is marked at the end with how it was
prevented, or "not handled".

## Cross-cutting

- Two instances debit W001 at the same time and it goes negative. — Prevention: TBD
- An in-JVM lock/cache/queue is used for safety and is invisible to the other instance. — Prevention: TBD
- Money stored as float/double/decimal loses or invents santim through rounding. — Prevention: TBD
- A debit commits without its matching credit (partial write) and an observer sees it. — Prevention: TBD
- Two transactions lock the same two wallets in opposite order and deadlock. — Prevention: TBD
- A request holds a DB connection while doing slow work and exhausts the 10-connection pool. — Prevention: TBD
- Pool exhaustion makes requests wait past hikari.connection-timeout and return 5xx. — Prevention: TBD
- All 50 Tomcat threads block on locks or the bank, so unrelated requests hang. — Prevention: TBD
- A lock wait has no timeout and a thread waits forever. — Prevention: TBD
- DB unreachable returns 500 or leaks a driver message/stack trace instead of 503. — Prevention: TBD
- A constraint violation or SQL error leaks a constraint name or SQL text in the response. — Prevention: TBD
- A business outcome (insufficient funds, frozen wallet) surfaces as a 5xx. — Prevention: TBD

## Idempotency (section III)

- Two concurrent requests with the same key on different instances both execute (double debit). — Prevention: TBD
- Key is recorded but the effect commits in a separate transaction, so a crash between them loses or duplicates the effect. — Prevention: TBD
- Same key with a different body executes a second effect instead of 409 IDEMPOTENCY_CONFLICT. — Prevention: TBD
- Replay returns a different status code or body than the original. — Prevention: TBD
- A request waiting on an in-progress key blocks a thread with no bound. — Prevention: TBD
- A failed (rolled-back) original leaves a key stuck "in progress" forever. — Prevention: TBD
- Key longer than 64 chars or missing header is accepted. — Prevention: TBD
- Keys never expire (or expire before 24 h) and a late retry runs twice. — Prevention: TBD

## 5.1 POST /p2p/transfers

- Funds check reads available, then debits in a separate step, so concurrent transfers overdraw (check-then-act race). — Prevention: TBD
- Debit ignores reserved and spends money held for a pending payout. — Prevention: TBD
- Debit commits after a freeze has returned 200. — Prevention: TBD
- Transfer to a FROZEN or missing wallet debits the sender anyway. — Prevention: TBD
- Same-wallet transfer creates or loses money. — Prevention: TBD
- Amount 0, negative or above 5,000,000 accepted. — Prevention: TBD
- Hot wallet (W001 under 200 concurrent requests) holds row locks long enough to exceed the 300 ms p95 or pool timeout. — Prevention: TBD
- W001→W002 and W002→W001 concurrently deadlock (S1.3). — Prevention: TBD

## 5.2 POST /p2p/transfers/{id}/reversal

- Two concurrent reversals of one transfer both succeed (money moved back twice). — Prevention: TBD
- Reversal debits to_wallet below zero / below available. — Prevention: TBD
- Reversal and a concurrent outgoing transfer from to_wallet both pass the funds check (S2.2). — Prevention: TBD
- Reversal rejected because from_wallet is FROZEN (money returning must be accepted). — Prevention: TBD
- Reversal allowed from a FROZEN to_wallet. — Prevention: TBD
- A reversal of a reversal, or of a non-COMPLETED transfer, is allowed. — Prevention: TBD

## 5.3 GET / PUT /wallets/{id}

- GET reads balance and reserved non-atomically and returns available ≠ balance − reserved or negative. — Prevention: TBD
- Freeze waits behind a queue of hot-wallet row locks and takes longer than 2 s. — Prevention: TBD
- Freeze returns 200 while an in-flight debit commits afterwards. — Prevention: TBD
- Freeze cancels or blocks payouts that were already accepted. — Prevention: TBD
- Setting the same status again changes changed_at or returns an error. — Prevention: TBD

## 5.4 POST /payouts

- Reservation and payout row are not written atomically, so money is reserved with no payout (or vice versa). — Prevention: TBD
- Bank is called inside the DB transaction, holding a connection and row lock for up to 60 s. — Prevention: TBD
- Bank is called on the request thread, so 202 misses 500 ms and threads run out. — Prevention: TBD
- Bank timeout (8 s / 504) treated as failure, reservation released, but bank actually paid (money created). — Prevention: TBD
- Unknown outcome retried with a new payout_id, so the bank pays twice. — Prevention: TBD
- Both instances' workers pick up the same pending payout and submit it concurrently. — Prevention: TBD
- Accepting instance is killed; its payouts stay PENDING forever with money reserved. — Prevention: TBD
- Payout marked FAILED while the bank later pays it (paid a payout reported as FAILED). — Prevention: TBD
- Submit result written after the callback already finalised, overwriting SUCCEEDED/FAILED. — Prevention: TBD
- SUCCEEDED lowers balance but does not release reserved (or releases twice). — Prevention: TBD
- Payout worker threads/connections starve 5.1 (p95 > 300 ms, pool timeouts). — Prevention: TBD
- Payout not final within 2 minutes because reconciliation (GET /bank/payouts) never runs or runs too slowly. — Prevention: TBD

## 5.5 POST /callbacks/bank

- Duplicate concurrent callbacks with the same event_id each apply the effect (balance lowered twice). — Prevention: TBD
- Different event_ids for the same payout both apply a final effect. — Prevention: TBD
- Callback arrives before the submit call returns and is rejected or lost. — Prevention: TBD
- Callback contradicting a final status changes it instead of setting conflict: true. — Prevention: TBD
- Callback handler waits on a lock held by the submit path and misses the 2 s deadline, causing retries. — Prevention: TBD
- Unknown payout_id returns 200 and the callback is dropped. — Prevention: TBD
- Callback and reconciler finalise the same payout concurrently and both apply. — Prevention: TBD

## 5.6 POST /b2c/disbursements (optional)

- Same batch_id submitted on both instances pays rows twice. — Prevention: TBD
- Repeated row_id paid more than once. — Prevention: TBD
- Batch holds M001's row lock for the whole run, blocking transfers from M001 (> 300 ms). — Prevention: TBD
- Instance dies mid-batch and rows are never completed or are re-paid on resume. — Prevention: TBD

## 5.7 GET /wallets/{id}/statement (optional)

- Offset pagination skips or repeats entries while new entries are written. — Prevention: TBD
- balance_after written outside the debit/credit transaction, breaking the chain. — Prevention: TBD
- Unindexed query scans 1,000,000 rows and exceeds 150 ms. — Prevention: TBD
