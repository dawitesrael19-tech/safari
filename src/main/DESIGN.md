quick design brain dump (dont look at my code lmao)

the basics: postgres + FOR UPDATE row locks handle concurrency. no floats cuz math is weird (bigint santims only). wallets lock in strict id order so we never deadlock.
idempotency: hashed req/resp saved in same tx. dupes wait 1.5s max then get a 409. keys last 24h globally.
payouts & workers: requests just reserve cash & spit out a 202. background workers grab 'em with SKIP LOCKED, talk to the bank, and poll if it times out. never pay twice cause the bank remembers the payout_id.
timeouts: 1.5s lock limit, 2s connection limit. everything drops a clean 503 instead of hanging forever.
db crash? state is safe on disk. worker lease expires, picks it back up, retries the same id. money stays locked, nothing leaks.
what we skipped: callbacks (we just poll), disbursements, statements, and the 1-click test suite (did it by hand lol). 200 transfers on one wallet are a bit slow (~670ms p95) cuz of row contention, but it works.
