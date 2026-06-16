-- V25: Performance indexes - fully guarded against partial/missing tables and columns
DO $$
DECLARE
    has_created_at BOOLEAN;
BEGIN
    -- transactions indexes (only if table has created_at)
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'transactions') THEN
        has_created_at := EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='transactions' AND column_name='created_at');
        IF has_created_at THEN
            IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='transactions' AND column_name='from_account_number') THEN
                IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_transactions_from_account_timestamp') THEN
                    EXECUTE 'CREATE INDEX idx_transactions_from_account_timestamp ON transactions(from_account_number, created_at DESC)';
                END IF;
            END IF;
            IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='transactions' AND column_name='to_account_number') THEN
                IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_transactions_to_account_timestamp') THEN
                    EXECUTE 'CREATE INDEX idx_transactions_to_account_timestamp ON transactions(to_account_number, created_at DESC)';
                END IF;
            END IF;
            IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_transactions_status_timestamp') THEN
                EXECUTE 'CREATE INDEX idx_transactions_status_timestamp ON transactions(status, created_at DESC)';
            END IF;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='transactions' AND column_name='idempotency_key') THEN
            IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_transactions_idempotency_key') THEN
                EXECUTE 'CREATE INDEX idx_transactions_idempotency_key ON transactions(idempotency_key) WHERE idempotency_key IS NOT NULL';
            END IF;
        END IF;
        EXECUTE 'ANALYZE transactions';
    END IF;

    -- accounts indexes
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'accounts') THEN
        IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_accounts_account_number') THEN
            EXECUTE 'CREATE INDEX idx_accounts_account_number ON accounts(account_number)';
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='accounts' AND column_name='status') THEN
            IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_accounts_user_id_status') THEN
                EXECUTE 'CREATE INDEX idx_accounts_user_id_status ON accounts(user_id, status)';
            END IF;
        END IF;
        EXECUTE 'ANALYZE accounts';
    END IF;

    -- users indexes
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'users') THEN
        IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_users_username_lower') THEN
            EXECUTE 'CREATE INDEX idx_users_username_lower ON users(LOWER(username))';
        END IF;
        EXECUTE 'ANALYZE users';
    END IF;

    -- outbox_events indexes
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'outbox_events') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='outbox_events' AND column_name='published') THEN
            IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_outbox_events_published_created') THEN
                EXECUTE 'CREATE INDEX idx_outbox_events_published_created ON outbox_events(published, created_at) WHERE published = false';
            END IF;
        END IF;
    END IF;

    -- audit_logs indexes
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'audit_logs') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='audit_logs' AND column_name='timestamp') THEN
            IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_audit_logs_timestamp') THEN
                EXECUTE 'CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC)';
            END IF;
        END IF;
    END IF;

    -- ledger_entries indexes
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'ledger_entries') THEN
        IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_ledger_entries_account_timestamp') THEN
            EXECUTE 'CREATE INDEX idx_ledger_entries_account_timestamp ON ledger_entries(account_id, timestamp DESC)';
        END IF;
    END IF;
END $$;
