-- Add AI for existing accounts without changing applied migration checksums.
INSERT INTO categories(account_id,key,display_name,color,is_default,enabled,created_at,version)
SELECT id,'AI','AI','#0891b2',TRUE,TRUE,CURRENT_TIMESTAMP,0
FROM accounts
ON CONFLICT(account_id,key) DO NOTHING;
