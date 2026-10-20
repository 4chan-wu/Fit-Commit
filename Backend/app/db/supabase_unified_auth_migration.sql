-- Fitcommit unified Supabase migration
--
-- Run in Supabase SQL Editor after taking a database backup. This migration is
-- deliberately fail-fast: legacy user_id tables must still be empty. The live
-- project was checked on 2026-10-13 and all tables listed below had zero rows.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Stop exposing the legacy password tables through PostgREST immediately.
ALTER TABLE IF EXISTS public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.user_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.auth_accounts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.users FROM anon, authenticated;
REVOKE ALL ON TABLE public.user_credentials FROM anon, authenticated;
REVOKE ALL ON TABLE public.auth_accounts FROM anon, authenticated;

-- Convert every empty legacy foreign key from public.users(bigint) to
-- auth.users(uuid). Abort instead of guessing an account mapping if data was
-- inserted after the preflight check.
DO $$
DECLARE
    current_table text;
    data_type_name text;
    constraint_name text;
    has_rows boolean;
    target_tables text[] := ARRAY[
        'ai_request_logs',
        'auth_accounts',
        'chat_rooms',
        'feedbacks',
        'inquiries',
        'orders',
        'outfits',
        'payments',
        'subscriptions',
        'user_credentials',
        'user_preferences',
        'wear_logs'
    ];
BEGIN
    FOREACH current_table IN ARRAY target_tables LOOP
        IF to_regclass(format('public.%I', current_table)) IS NULL THEN
            CONTINUE;
        END IF;

        SELECT columns.data_type
          INTO data_type_name
          FROM information_schema.columns
         WHERE columns.table_schema = 'public'
           AND columns.table_name = current_table
           AND columns.column_name = 'user_id';

        IF data_type_name IS NULL OR data_type_name = 'uuid' THEN
            CONTINUE;
        END IF;

        EXECUTE format('SELECT EXISTS (SELECT 1 FROM public.%I LIMIT 1)', current_table)
           INTO has_rows;
        IF has_rows THEN
            RAISE EXCEPTION
                'public.%.user_id is % and the table is not empty; map legacy users before migration',
                current_table,
                data_type_name;
        END IF;

        FOR constraint_name IN
            SELECT constraints.conname
              FROM pg_constraint AS constraints
              JOIN pg_class AS relations
                ON relations.oid = constraints.conrelid
              JOIN pg_namespace AS namespaces
                ON namespaces.oid = relations.relnamespace
              JOIN LATERAL unnest(constraints.conkey) AS key(attnum)
                ON true
              JOIN pg_attribute AS attributes
                ON attributes.attrelid = relations.oid
               AND attributes.attnum = key.attnum
             WHERE constraints.contype = 'f'
               AND namespaces.nspname = 'public'
               AND relations.relname = current_table
               AND attributes.attname = 'user_id'
        LOOP
            EXECUTE format(
                'ALTER TABLE public.%I DROP CONSTRAINT %I',
                current_table,
                constraint_name
            );
        END LOOP;

        EXECUTE format(
            'ALTER TABLE public.%I ALTER COLUMN user_id TYPE uuid USING user_id::text::uuid',
            current_table
        );
        EXECUTE format(
            'ALTER TABLE public.%I ADD CONSTRAINT %I FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE',
            current_table,
            current_table || '_user_id_auth_fkey'
        );
    END LOOP;
END $$;

ALTER TABLE public.user_profiles
    ADD COLUMN IF NOT EXISTS full_name text,
    ADD COLUMN IF NOT EXISTS phone_number text;

CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.user_profiles (
        user_id,
        nickname,
        full_name,
        phone_number,
        preferred_styles
    ) VALUES (
        NEW.id,
        NEW.raw_user_meta_data ->> 'nickname',
        NEW.raw_user_meta_data ->> 'name',
        NEW.raw_user_meta_data ->> 'phone_number',
        '{}'
    )
    ON CONFLICT (user_id) DO UPDATE SET
        nickname = COALESCE(EXCLUDED.nickname, public.user_profiles.nickname),
        full_name = COALESCE(EXCLUDED.full_name, public.user_profiles.full_name),
        phone_number = COALESCE(EXCLUDED.phone_number, public.user_profiles.phone_number);

    INSERT INTO public.wardrobes (user_id, name, description, is_default)
    SELECT NEW.id, '기본 옷장', '자동 생성된 기본 옷장', true
    WHERE NOT EXISTS (
        SELECT 1 FROM public.wardrobes
        WHERE wardrobes.user_id = NEW.id
          AND wardrobes.is_default = true
    );

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
AFTER INSERT OR UPDATE OF raw_user_meta_data ON auth.users
FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();

-- Protect user-owned data even if a mobile client calls PostgREST directly.
DO $$
DECLARE
    table_name text;
    owned_tables text[] := ARRAY[
        'user_profiles', 'wardrobes', 'clothes', 'chat_rooms',
        'ai_request_logs', 'feedbacks', 'inquiries', 'orders', 'outfits',
        'payments', 'subscriptions', 'user_preferences', 'wear_logs'
    ];
BEGIN
    FOREACH table_name IN ARRAY owned_tables LOOP
        IF to_regclass(format('public.%I', table_name)) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_name);
        END IF;
    END LOOP;
END $$;

-- Tables used directly by authenticated clients.
DROP POLICY IF EXISTS chat_rooms_own_all ON public.chat_rooms;
CREATE POLICY chat_rooms_own_all ON public.chat_rooms
FOR ALL TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS chat_messages_own_all ON public.chat_messages;
CREATE POLICY chat_messages_own_all ON public.chat_messages
FOR ALL TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.chat_rooms
        WHERE chat_rooms.id = chat_messages.chat_room_id
          AND chat_rooms.user_id = auth.uid()
    )
)
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.chat_rooms
        WHERE chat_rooms.id = chat_messages.chat_room_id
          AND chat_rooms.user_id = auth.uid()
    )
);

ALTER TABLE public.plans ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS plans_read_active ON public.plans;
CREATE POLICY plans_read_active ON public.plans
FOR SELECT TO anon, authenticated
USING (is_active = true);

-- The three legacy rows are retained for audit/rollback, but only the database
-- owner/service role can read them. Drop these tables after account recovery.
COMMENT ON TABLE public.users IS
    'Legacy password accounts. Locked from clients; migrate via password reset, then drop.';

COMMIT;
