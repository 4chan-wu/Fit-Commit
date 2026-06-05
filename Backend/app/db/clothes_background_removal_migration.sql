-- Fitcommit 의류 배경 제거 이미지 선택 저장 migration.
-- 원본 이미지는 기존 image_path/image_url에 유지하고, 배경 제거 이미지는 별도 컬럼과 Storage 경로로 관리함.

ALTER TABLE public.clothes
    ADD COLUMN IF NOT EXISTS background_removed_image_path TEXT,
    ADD COLUMN IF NOT EXISTS background_removed_image_url TEXT,
    ADD COLUMN IF NOT EXISTS background_removal_status TEXT NOT NULL DEFAULT 'not_requested',
    ADD COLUMN IF NOT EXISTS background_removal_error TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'clothes_background_removal_status_check'
    ) THEN
        ALTER TABLE public.clothes
            ADD CONSTRAINT clothes_background_removal_status_check
            CHECK (background_removal_status IN ('not_requested', 'pending', 'completed', 'failed'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_clothes_background_removal_status
ON public.clothes(background_removal_status);

UPDATE public.clothes
SET background_removal_status = 'not_requested'
WHERE background_removal_status IS NULL;
