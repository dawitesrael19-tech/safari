INSERT INTO wallets (wallet_id, status, merchant, balance)
SELECT 'W' || lpad(n::text, 3, '0'), CASE WHEN n <= 100 THEN 'ACTIVE' ELSE 'FROZEN' END, FALSE, 1000000
FROM generate_series(1, 105) AS n
ON CONFLICT (wallet_id) DO NOTHING;

INSERT INTO wallets (wallet_id, status, merchant, balance)
VALUES ('M001', 'ACTIVE', TRUE, 50000000)
ON CONFLICT (wallet_id) DO NOTHING;
