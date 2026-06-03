-- =========================================================
-- Fit Commit - Supabase PostgreSQL Schema
-- =========================================================
-- Run this file in Supabase Dashboard > SQL Editor.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =========================================================
-- 1. auth / users
-- =========================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL DEFAULT '',
    nickname VARCHAR(100) NOT NULL,
    phone_number VARCHAR(30) UNIQUE,
    password VARCHAR(255) NOT NULL DEFAULT '',
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number VARCHAR(30) UNIQUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_count INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;

CREATE TABLE IF NOT EXISTS user_credentials (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    token_expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_accounts_user_id ON auth_accounts(user_id);

-- =========================================================
-- 2. profile
-- =========================================================

CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    profile_image_url TEXT,
    country_code VARCHAR(10) DEFAULT 'KR',
    gender VARCHAR(20),
    birth_year INT,
    body_type VARCHAR(50),
    skin_tone VARCHAR(50),
    height_cm INT,
    preferred_styles TEXT,
    preferred_brands TEXT,
    bio TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    allow_ai_recommendation BOOLEAN NOT NULL DEFAULT TRUE,
    allow_marketing_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    allow_push_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    preferred_language VARCHAR(20) NOT NULL DEFAULT 'ko',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 3. wardrobe
-- =========================================================

CREATE TABLE IF NOT EXISTS wardrobes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL DEFAULT '기본 옷장',
    description TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wardrobes_user_id ON wardrobes(user_id);

CREATE TABLE IF NOT EXISTS clothes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wardrobe_id BIGINT NOT NULL REFERENCES wardrobes(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    subcategory VARCHAR(50),
    color VARCHAR(50),
    brand VARCHAR(100),
    size VARCHAR(50),
    season VARCHAR(50),
    purchase_date DATE,
    memo TEXT,
    analysis_status TEXT NOT NULL DEFAULT 'pending',
    analysis_error TEXT,
    analyzed_at TIMESTAMPTZ,
    needs_analysis BOOLEAN NOT NULL DEFAULT TRUE,
    sync_status TEXT DEFAULT 'synced',
    local_image_path TEXT,
    wear_count INT NOT NULL DEFAULT 0,
    last_worn_at TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_clothes_user_id ON clothes(user_id);
CREATE INDEX IF NOT EXISTS idx_clothes_wardrobe_id ON clothes(wardrobe_id);
CREATE INDEX IF NOT EXISTS idx_clothes_category ON clothes(category);
CREATE INDEX IF NOT EXISTS idx_clothes_status ON clothes(status);
CREATE INDEX IF NOT EXISTS idx_clothes_analysis_status ON clothes(analysis_status);
CREATE INDEX IF NOT EXISTS idx_clothes_sync_status ON clothes(sync_status);

ALTER TABLE clothes
    ADD COLUMN IF NOT EXISTS analysis_status TEXT NOT NULL DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS analysis_error TEXT,
    ADD COLUMN IF NOT EXISTS analyzed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS needs_analysis BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS sync_status TEXT DEFAULT 'synced',
    ADD COLUMN IF NOT EXISTS local_image_path TEXT;

ALTER TABLE clothes
    ALTER COLUMN sync_status SET DEFAULT 'synced';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'clothes_analysis_status_check'
    ) THEN
        ALTER TABLE clothes
            ADD CONSTRAINT clothes_analysis_status_check
            CHECK (analysis_status IN ('pending', 'completed', 'failed'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'clothes_sync_status_check'
    ) THEN
        ALTER TABLE clothes
            ADD CONSTRAINT clothes_sync_status_check
            CHECK (
                sync_status IS NULL
                OR sync_status IN ('local_only', 'pending_sync', 'synced', 'sync_failed')
            );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS clothing_images (
    id BIGSERIAL PRIMARY KEY,
    clothing_id BIGINT NOT NULL REFERENCES clothes(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_clothing_images_clothing_id ON clothing_images(clothing_id);

CREATE TABLE IF NOT EXISTS wear_logs (
    id BIGSERIAL PRIMARY KEY,
    clothing_id BIGINT NOT NULL REFERENCES clothes(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    worn_date DATE NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wear_logs_clothing_id ON wear_logs(clothing_id);
CREATE INDEX IF NOT EXISTS idx_wear_logs_user_id ON wear_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_wear_logs_worn_date ON wear_logs(worn_date);

CREATE TABLE IF NOT EXISTS outfits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    memo TEXT,
    source_type VARCHAR(30) NOT NULL DEFAULT 'user',
    score NUMERIC(5,2),
    reason_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outfits_user_id ON outfits(user_id);
CREATE INDEX IF NOT EXISTS idx_outfits_source_type ON outfits(source_type);

CREATE TABLE IF NOT EXISTS outfit_items (
    id BIGSERIAL PRIMARY KEY,
    outfit_id BIGINT NOT NULL REFERENCES outfits(id) ON DELETE CASCADE,
    clothing_id BIGINT NOT NULL REFERENCES clothes(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (outfit_id, clothing_id)
);

CREATE INDEX IF NOT EXISTS idx_outfit_items_outfit_id ON outfit_items(outfit_id);
CREATE INDEX IF NOT EXISTS idx_outfit_items_clothing_id ON outfit_items(clothing_id);

-- =========================================================
-- 4. ai / chat
-- =========================================================

CREATE TABLE IF NOT EXISTS chat_rooms (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255),
    room_type VARCHAR(50) NOT NULL DEFAULT 'general',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_rooms_user_id ON chat_rooms(user_id);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    chat_room_id BIGINT NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_type VARCHAR(20) NOT NULL,
    message_type VARCHAR(30) NOT NULL DEFAULT 'text',
    content TEXT NOT NULL,
    model_name VARCHAR(100),
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    status VARCHAR(30) NOT NULL DEFAULT 'sent',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_chat_room_id ON chat_messages(chat_room_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_sender_type ON chat_messages(sender_type);
CREATE INDEX IF NOT EXISTS idx_chat_messages_created_at ON chat_messages(created_at);

CREATE TABLE IF NOT EXISTS ai_request_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    chat_room_id BIGINT REFERENCES chat_rooms(id) ON DELETE SET NULL,
    request_type VARCHAR(50) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    request_payload JSONB,
    response_payload JSONB,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_request_logs_user_id ON ai_request_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_request_logs_chat_room_id ON ai_request_logs(chat_room_id);
CREATE INDEX IF NOT EXISTS idx_ai_request_logs_requested_at ON ai_request_logs(requested_at);

-- =========================================================
-- 5. billing
-- =========================================================

CREATE TABLE IF NOT EXISTS plans (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    price INT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES plans(id),
    order_id VARCHAR(100) NOT NULL UNIQUE,
    amount INT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    status VARCHAR(30) NOT NULL DEFAULT 'ready',
    provider VARCHAR(30) NOT NULL DEFAULT 'toss',
    provider_payment_key VARCHAR(255),
    fail_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_payment_id VARCHAR(255),
    amount INT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    status VARCHAR(30) NOT NULL,
    payment_type VARCHAR(30) NOT NULL,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments(user_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);

CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    payment_id BIGINT REFERENCES payments(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status);

CREATE TABLE IF NOT EXISTS payment_events (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_payload JSONB NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_events_provider ON payment_events(provider);
CREATE INDEX IF NOT EXISTS idx_payment_events_processed ON payment_events(processed);

-- =========================================================
-- 6. support
-- =========================================================

CREATE TABLE IF NOT EXISTS faq_items (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notices (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS feedbacks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(255),
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'received',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_feedbacks_user_id ON feedbacks(user_id);
CREATE INDEX IF NOT EXISTS idx_feedbacks_status ON feedbacks(status);

CREATE TABLE IF NOT EXISTS inquiries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'open',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_inquiries_user_id ON inquiries(user_id);
CREATE INDEX IF NOT EXISTS idx_inquiries_status ON inquiries(status);

-- =========================================================
-- seed data
-- =========================================================

INSERT INTO users (email, name, nickname, phone_number, password, status)
VALUES ('test@test.com', '테스트', 'testuser', '01000000000', 'change-me', 'active')
ON CONFLICT (email) DO NOTHING;

INSERT INTO plans (code, name, price, currency, is_active)
VALUES
    ('ai_ticket_1', 'AI 스타일 분석 1회권', 4900, 'KRW', TRUE),
    ('premium_ticket_1', '프리미엄 코디 추천 1회권', 9900, 'KRW', TRUE)
ON CONFLICT (code) DO NOTHING;

-- =========================================================
-- triggers
-- Drop existing triggers first so this file can be re-run safely.
-- =========================================================

DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_user_credentials_updated_at ON user_credentials;
CREATE TRIGGER trg_user_credentials_updated_at BEFORE UPDATE ON user_credentials FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_auth_accounts_updated_at ON auth_accounts;
CREATE TRIGGER trg_auth_accounts_updated_at BEFORE UPDATE ON auth_accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_user_profiles_updated_at ON user_profiles;
CREATE TRIGGER trg_user_profiles_updated_at BEFORE UPDATE ON user_profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_user_preferences_updated_at ON user_preferences;
CREATE TRIGGER trg_user_preferences_updated_at BEFORE UPDATE ON user_preferences FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_wardrobes_updated_at ON wardrobes;
CREATE TRIGGER trg_wardrobes_updated_at BEFORE UPDATE ON wardrobes FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_clothes_updated_at ON clothes;
CREATE TRIGGER trg_clothes_updated_at BEFORE UPDATE ON clothes FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_outfits_updated_at ON outfits;
CREATE TRIGGER trg_outfits_updated_at BEFORE UPDATE ON outfits FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_chat_rooms_updated_at ON chat_rooms;
CREATE TRIGGER trg_chat_rooms_updated_at BEFORE UPDATE ON chat_rooms FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_plans_updated_at ON plans;
CREATE TRIGGER trg_plans_updated_at BEFORE UPDATE ON plans FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_orders_updated_at ON orders;
CREATE TRIGGER trg_orders_updated_at BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_payments_updated_at ON payments;
CREATE TRIGGER trg_payments_updated_at BEFORE UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_subscriptions_updated_at ON subscriptions;
CREATE TRIGGER trg_subscriptions_updated_at BEFORE UPDATE ON subscriptions FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_faq_items_updated_at ON faq_items;
CREATE TRIGGER trg_faq_items_updated_at BEFORE UPDATE ON faq_items FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_notices_updated_at ON notices;
CREATE TRIGGER trg_notices_updated_at BEFORE UPDATE ON notices FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_feedbacks_updated_at ON feedbacks;
CREATE TRIGGER trg_feedbacks_updated_at BEFORE UPDATE ON feedbacks FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_inquiries_updated_at ON inquiries;
CREATE TRIGGER trg_inquiries_updated_at BEFORE UPDATE ON inquiries FOR EACH ROW EXECUTE FUNCTION set_updated_at();
