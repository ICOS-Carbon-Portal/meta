-- Ensure we're in a clean transaction state
ROLLBACK;
SELECT 'Transaction state cleaned' as status;
