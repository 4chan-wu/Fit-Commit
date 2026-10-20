-- Fitcommit Supabase Auth 사용자 프로필/옷장 확장 migration.
-- 기존 테이블을 drop하지 않고, 없으면 생성하고 필요한 컬럼만 alter table로 추가함.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    profile_user_id_type text;
    wardrobe_user_id_type text;
BEGIN
    SELECT data_type
      INTO profile_user_id_type
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'user_profiles'
       AND column_name = 'user_id';

    IF profile_user_id_type IS NOT NULL AND profile_user_id_type <> 'uuid' THEN
        RAISE NOTICE 'public.user_profiles.user_id is %, not uuid. Existing table was not dropped; migrate manually before applying Supabase Auth FK.', profile_user_id_type;
    END IF;

    SELECT data_type
      INTO wardrobe_user_id_type
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'wardrobes'
       AND column_name = 'user_id';

    IF wardrobe_user_id_type IS NOT NULL AND wardrobe_user_id_type <> 'uuid' THEN
        RAISE NOTICE 'public.wardrobes.user_id is %, not uuid. Existing table was not dropped; migrate manually before applying Supabase Auth FK.', wardrobe_user_id_type;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.user_profiles (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    nickname text,
    height_cm numeric,
    body_type text,
    preferred_styles text[] NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.user_profiles
    ADD COLUMN IF NOT EXISTS nickname text,
    ADD COLUMN IF NOT EXISTS height_cm numeric,
    ADD COLUMN IF NOT EXISTS body_type text,
    ADD COLUMN IF NOT EXISTS preferred_styles text[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

DROP TRIGGER IF EXISTS trg_user_profiles_updated_at ON public.user_profiles;
CREATE TRIGGER trg_user_profiles_updated_at
BEFORE UPDATE ON public.user_profiles
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS public.wardrobes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name text NOT NULL DEFAULT '기본 옷장',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.wardrobes
    ADD COLUMN IF NOT EXISTS user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS name text NOT NULL DEFAULT '기본 옷장',
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_wardrobes_user_id ON public.wardrobes(user_id);

DROP TRIGGER IF EXISTS trg_wardrobes_updated_at ON public.wardrobes;
CREATE TRIGGER trg_wardrobes_updated_at
BEFORE UPDATE ON public.wardrobes
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.clothes
    ADD COLUMN IF NOT EXISTS wardrobe_id uuid REFERENCES public.wardrobes(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_clothes_wardrobe_id ON public.clothes(wardrobe_id);

ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wardrobes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.clothes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "user_profiles_select_own" ON public.user_profiles;
CREATE POLICY "user_profiles_select_own"
ON public.user_profiles FOR SELECT
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "user_profiles_insert_own" ON public.user_profiles;
CREATE POLICY "user_profiles_insert_own"
ON public.user_profiles FOR INSERT
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "user_profiles_update_own" ON public.user_profiles;
CREATE POLICY "user_profiles_update_own"
ON public.user_profiles FOR UPDATE
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "wardrobes_select_own" ON public.wardrobes;
CREATE POLICY "wardrobes_select_own"
ON public.wardrobes FOR SELECT
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "wardrobes_insert_own" ON public.wardrobes;
CREATE POLICY "wardrobes_insert_own"
ON public.wardrobes FOR INSERT
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "wardrobes_update_own" ON public.wardrobes;
CREATE POLICY "wardrobes_update_own"
ON public.wardrobes FOR UPDATE
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "wardrobes_delete_own" ON public.wardrobes;
CREATE POLICY "wardrobes_delete_own"
ON public.wardrobes FOR DELETE
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "clothes_select_own" ON public.clothes;
CREATE POLICY "clothes_select_own"
ON public.clothes FOR SELECT
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "clothes_insert_own" ON public.clothes;
CREATE POLICY "clothes_insert_own"
ON public.clothes FOR INSERT
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "clothes_update_own" ON public.clothes;
CREATE POLICY "clothes_update_own"
ON public.clothes FOR UPDATE
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "clothes_delete_own" ON public.clothes;
CREATE POLICY "clothes_delete_own"
ON public.clothes FOR DELETE
USING (auth.uid() = user_id);
