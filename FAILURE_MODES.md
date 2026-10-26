Failure Modes (what could go wrong & how we fixed it)

The Basics

Double debits / negative wallets: Fixed with row locks (FOR UPDATE) and database checks. Everything lives in Postgres so multi-instance syncing isnt an issue.
Float rounding errors: No floats allowed! We use BIGINT for santims only, any weird decimals just get a 400 error.
Deadlocks: Wallets always lock in strict ID order, so no crossed wires.
Slow bank calls hanging threads: Bank stuff happens via background workers outside of DB transactions, so it wont hog our pool. If the pool fills up, users get a clean 503 instead of hanging forever.
Error leaks: Stack traces are completely hidden. Just clean error codes and short msgs.

Idempotency

Duplicate requests: Keys save right in the tx. If a retry comes in, it waits and replays the exact same answer.
Same key, diff body: Throws a 409 conflict. No funny business allowed.
Stuck keys: Keys expire or rollback properly so things dont get permanently jammed.

Transfers & Reversals

Parallel overdrawing: Checked and debited under the exact same row lock.
Reversing wrong stuff: Cant reverse things that arent completed yet, or anything that drops the receiver below zero. Proper 422 errors are thrown.
Weird amounts: Only values between 1 and 5,000,000 are accepted.

Payouts & Wallets

Paying twice: The exact same payout_id is always used, plus SKIP LOCKED leases so separate instances dont step on each other toes.
Bank timeouts: A timeout means UNKNOWN state, the worker just keeps polling the bank until it gets a final answer.
Callbacks: Honestly, we dont even have callback endpoints built yet, we just poll the bank directly. Random callbacks just get a 404.
Disbursements / Statements: Literally not built yet lol, so no failure modes to worry about there.
