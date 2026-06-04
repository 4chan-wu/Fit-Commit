-- Fitcommit 사용자별 옷장 구조 정리 migration.
-- 기존 테이블을 drop하지 않고, 사용자당 기본 옷장 1개와 사용자 내 옷장명 중복 방지를 보장함.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS public.user_profiles (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    nickname text,
    height_cm numeric,
    body_type text,
    preferred_style text[] NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.user_profiles
    ADD COLUMN IF NOT EXISTS nickname text,
    ADD COLUMN IF NOT EXISTS height_cm numeric,
    ADD COLUMN IF NOT EXISTS body_type text,
    ADD COLUMN IF NOT EXISTS preferred_style text[] NOT NULL DEFAULT '{}',
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
    description text,
    is_default boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.wardrobes
    ADD COLUMN IF NOT EXISTS description text,
    ADD COLUMN IF NOT EXISTS is_default boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE public.wardrobes
    ALTER COLUMN name SET DEFAULT '기본 옷장',
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN is_default SET DEFAULT false,
    ALTER COLUMN is_default SET NOT NULL;

ALTER TABLE public.clothes
    ADD COLUMN IF NOT EXISTS wardrobe_id uuid REFERENCES public.wardrobes(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_wardrobes_user_id ON public.wardrobes(user_id);
CREATE INDEX IF NOT EXISTS idx_clothes_wardrobe_id ON public.clothes(wardrobe_id);

-- 기존 데이터에 기본 옷장이 없으면 사용자별 기본 옷장을 생성함.
INSERT INTO public.wardrobes (user_id, name, description, is_default)
SELECT profile.user_id, '기본 옷장', '자동 생성된 기본 옷장', true
FROM public.user_profiles AS profile
WHERE NOT EXISTS (
    SELECT 1
    FROM public.wardrobes AS wardrobe
    WHERE wardrobe.user_id = profile.user_id
      AND wardrobe.is_default = true
);

-- 같은 사용자에게 기본 옷장이 여러 개 있으면 가장 먼저 생성된 하나만 기본값으로 유지함.
WITH ranked_defaults AS (
    SELECT
        id,
        row_number() OVER (PARTITION BY user_id ORDER BY created_at ASC, id ASC) AS rn
    FROM public.wardrobes
    WHERE is_default = true
)
UPDATE public.wardrobes AS wardrobe
SET is_default = false
FROM ranked_defaults AS ranked
WHERE wardrobe.id = ranked.id
  AND ranked.rn > 1;

-- 같은 사용자 안에서 중복 옷장명이 있으면 뒤쪽 항목에 id 일부를 붙여 unique index 생성 실패를 방지함.
WITH duplicate_names AS (
    SELECT
        id,
        name,
        row_number() OVER (PARTITION BY user_id, name ORDER BY created_at ASC, id ASC) AS rn
    FROM public.wardrobes
)
UPDATE public.wardrobes AS wardrobe
SET name = duplicate_names.name || ' (' || substring(wardrobe.id::text from 1 for 8) || ')'
FROM duplicate_names
WHERE wardrobe.id = duplicate_names.id
  AND duplicate_names.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS wardrobes_one_default_per_user
ON public.wardrobes(user_id)
WHERE is_default = true;

CREATE UNIQUE INDEX IF NOT EXISTS wardrobes_unique_name_per_user
ON public.wardrobes(user_id, name);

DROP TRIGGER IF EXISTS trg_wardrobes_updated_at ON public.wardrobes;
CREATE TRIGGER trg_wardrobes_updated_at
BEFORE UPDATE ON public.wardrobes
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

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

-- 테스트 Auth user가 존재하는 경우에만 기본 프로필/기본 옷장을 seed함.
INSERT INTO public.user_profiles (user_id, nickname, preferred_style)
SELECT '45482f0f-42d0-401e-a18a-77abeb0492cc'::uuid, 'fitcommit-test', '{}'
WHERE EXISTS (
    SELECT 1
    FROM auth.users
    WHERE id = '45482f0f-42d0-401e-a18a-77abeb0492cc'::uuid
)
ON CONFLICT (user_id) DO UPDATE
SET nickname = COALESCE(public.user_profiles.nickname, EXCLUDED.nickname);

INSERT INTO public.wardrobes (user_id, name, description, is_default)
SELECT
    '45482f0f-42d0-401e-a18a-77abeb0492cc'::uuid,
    '기본 옷장',
    '테스트 사용자의 기본 옷장',
    true
WHERE EXISTS (
    SELECT 1
    FROM auth.users
    WHERE id = '45482f0f-42d0-401e-a18a-77abeb0492cc'::uuid
)
AND NOT EXISTS (
    SELECT 1
    FROM public.wardrobes
    WHERE user_id = '45482f0f-42d0-401e-a18a-77abeb0492cc'::uuid
      AND is_default = true
)
ON CONFLICT (user_id, name) DO UPDATE
SET description = COALESCE(public.wardrobes.description, EXCLUDED.description);
