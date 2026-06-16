-- Insert default admin user (only if users table exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'users') THEN
        ALTER TABLE users ADD COLUMN IF NOT EXISTS account_non_locked boolean NOT NULL DEFAULT true;
        ALTER TABLE users ADD COLUMN IF NOT EXISTS tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

        INSERT INTO users (username, password, pin, role, first_name, last_name, email, tier, account_non_locked, created_at, updated_at)
        SELECT 'admin',
               '$2a$10$N.zmdr9k7uOIW8sGz/Wn4.DQNaU7VtYZdSfN6S7.qjKqKqZ8QJ8O6',
               '$2a$10$N.zmdr9k7uOIW8sGz/Wn4.DQNaU7VtYZdSfN6S7.qjKqKqZ8QJ8O6',
               'ADMIN', 'Admin', 'User', 'admin@titan.com', 'PREMIUM', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');
    END IF;
END $$;
