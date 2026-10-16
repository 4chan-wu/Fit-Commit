-- Supabase SQL Editor에서 한 번 실행하세요.
-- Android 옷장 화면의 이름 저장과 안전한 옷장 삭제/이동을 지원합니다.

ALTER TABLE public.clothes
    ADD COLUMN IF NOT EXISTS display_name text NOT NULL DEFAULT '';

UPDATE public.clothes
   SET display_name = COALESCE(NULLIF(subcategory, ''), NULLIF(main_category, ''), '의류')
 WHERE display_name = '';

CREATE OR REPLACE FUNCTION public.delete_wardrobe_and_move_clothes(p_wardrobe_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
    target_wardrobe public.wardrobes%ROWTYPE;
    default_wardrobe_id uuid;
BEGIN
    SELECT *
      INTO target_wardrobe
      FROM public.wardrobes
     WHERE id = p_wardrobe_id
       AND user_id = auth.uid();

    IF NOT FOUND THEN
        RAISE EXCEPTION '옷장을 찾을 수 없습니다.';
    END IF;

    IF target_wardrobe.is_default THEN
        RAISE EXCEPTION '기본 옷장은 삭제할 수 없습니다.';
    END IF;

    SELECT id
      INTO default_wardrobe_id
      FROM public.wardrobes
     WHERE user_id = auth.uid()
       AND is_default = true
     ORDER BY created_at ASC
     LIMIT 1;

    IF default_wardrobe_id IS NULL THEN
        RAISE EXCEPTION '기본 옷장이 없습니다.';
    END IF;

    UPDATE public.clothes
       SET wardrobe_id = default_wardrobe_id
     WHERE user_id = auth.uid()
       AND wardrobe_id = p_wardrobe_id;

    DELETE FROM public.wardrobes
     WHERE id = p_wardrobe_id
       AND user_id = auth.uid();
END;
$$;

GRANT EXECUTE ON FUNCTION public.delete_wardrobe_and_move_clothes(uuid) TO authenticated;
