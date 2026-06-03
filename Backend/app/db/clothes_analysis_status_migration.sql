-- Supabase SQL Editor에서 기존 public.clothes 테이블에 적용하는 분석 상태 컬럼 migration임.
-- AI 분석 성공이 DB 저장의 필수 조건이 아니도록 pending/completed/failed 상태를 저장함.

ALTER TABLE public.clothes
    ADD COLUMN IF NOT EXISTS analysis_status TEXT NOT NULL DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS analysis_error TEXT,
    ADD COLUMN IF NOT EXISTS analyzed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS needs_analysis BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS sync_status TEXT,
    ADD COLUMN IF NOT EXISTS local_image_path TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'clothes_analysis_status_check'
    ) THEN
        ALTER TABLE public.clothes
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
        ALTER TABLE public.clothes
            ADD CONSTRAINT clothes_sync_status_check
            CHECK (
                sync_status IS NULL
                OR sync_status IN ('local_only', 'pending_sync', 'synced', 'sync_failed')
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_clothes_analysis_status
ON public.clothes(analysis_status);

CREATE INDEX IF NOT EXISTS idx_clothes_sync_status
ON public.clothes(sync_status);
