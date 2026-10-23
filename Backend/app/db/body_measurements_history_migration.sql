-- Fitcommit 신체 측정 기록 테이블.
-- Supabase Dashboard > SQL Editor에서 한 번 실행합니다.
-- 각 기록은 로그인한 사용자만 조회/추가/수정/삭제할 수 있습니다.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.body_measurements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    measured_at timestamptz NOT NULL DEFAULT now(),

    -- 현재 Android 측정 결과. DB에서는 표시 단위와 동일한 cm로 저장합니다.
    height_cm numeric(5, 2) NOT NULL,
    shoulder_width_cm numeric(5, 2) NOT NULL,
    hip_width_cm numeric(5, 2) NOT NULL,
    arm_length_cm numeric(5, 2) NOT NULL,
    leg_length_cm numeric(5, 2) NOT NULL,

    -- 세그멘테이션 기반 실루엣 측정을 추가할 때 채울 선택 컬럼입니다.
    chest_width_cm numeric(5, 2),
    waist_width_cm numeric(5, 2),
    thigh_width_cm numeric(5, 2),

    -- 번역 문장 대신 언어에 독립적인 코드를 저장합니다.
    body_shape_code text NOT NULL,
    measurement_method text NOT NULL,
    confidence numeric(4, 3),
    algorithm_version text NOT NULL DEFAULT 'android-v1',

    -- 원본 신체 사진은 저장하지 않습니다. 추후 얼굴을 제외한 비공개
    -- 실루엣 이미지를 도입할 때 Storage 객체 경로만 기록합니다.
    silhouette_path text,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT body_measurements_height_range
        CHECK (height_cm BETWEEN 100 AND 230),
    CONSTRAINT body_measurements_shoulder_range
        CHECK (shoulder_width_cm BETWEEN 10 AND 100),
    CONSTRAINT body_measurements_hip_range
        CHECK (hip_width_cm BETWEEN 10 AND 100),
    CONSTRAINT body_measurements_arm_range
        CHECK (arm_length_cm BETWEEN 10 AND 150),
    CONSTRAINT body_measurements_leg_range
        CHECK (leg_length_cm BETWEEN 20 AND 180),
    CONSTRAINT body_measurements_chest_range
        CHECK (chest_width_cm IS NULL OR chest_width_cm BETWEEN 10 AND 100),
    CONSTRAINT body_measurements_waist_range
        CHECK (waist_width_cm IS NULL OR waist_width_cm BETWEEN 10 AND 100),
    CONSTRAINT body_measurements_thigh_range
        CHECK (thigh_width_cm IS NULL OR thigh_width_cm BETWEEN 5 AND 70),
    CONSTRAINT body_measurements_shape_code
        CHECK (body_shape_code IN ('inverted_triangle', 'triangle', 'rectangle', 'unknown')),
    CONSTRAINT body_measurements_method
        CHECK (measurement_method IN ('ar_raw_depth', 'manual_height')),
    CONSTRAINT body_measurements_confidence_range
        CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1)
);

CREATE INDEX IF NOT EXISTS idx_body_measurements_user_measured_at
    ON public.body_measurements (user_id, measured_at DESC);

ALTER TABLE public.body_measurements ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS body_measurements_select_own ON public.body_measurements;
CREATE POLICY body_measurements_select_own
ON public.body_measurements
FOR SELECT TO authenticated
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS body_measurements_insert_own ON public.body_measurements;
CREATE POLICY body_measurements_insert_own
ON public.body_measurements
FOR INSERT TO authenticated
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS body_measurements_update_own ON public.body_measurements;
CREATE POLICY body_measurements_update_own
ON public.body_measurements
FOR UPDATE TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS body_measurements_delete_own ON public.body_measurements;
CREATE POLICY body_measurements_delete_own
ON public.body_measurements
FOR DELETE TO authenticated
USING (auth.uid() = user_id);

REVOKE ALL ON TABLE public.body_measurements FROM anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.body_measurements TO authenticated;

COMMENT ON TABLE public.body_measurements IS
    '사용자별 신체 비율 측정 이력. 원본 신체 사진은 저장하지 않는다.';
COMMENT ON COLUMN public.body_measurements.algorithm_version IS
    '알고리즘 변경 전후의 측정값을 구분하기 위한 버전.';
COMMENT ON COLUMN public.body_measurements.silhouette_path IS
    '선택적인 비공개 실루엣 Storage 객체 경로. 공개 URL을 저장하지 않는다.';

COMMIT;
