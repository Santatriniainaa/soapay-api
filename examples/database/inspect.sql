\echo '=== Tables in public schema ==='
\dt public.*

\echo '=== Columns in every public table ==='
SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
ORDER BY table_name, ordinal_position;

\echo '=== Payment transactions ==='
TABLE api_transactions;

\echo '=== Refunds ==='
TABLE api_refunds;

\echo '=== Applied Flyway migrations ==='
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;