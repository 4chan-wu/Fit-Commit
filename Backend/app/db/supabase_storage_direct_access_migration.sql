-- Allow authenticated mobile clients to upload only inside their own folder.
-- Object names must follow: {auth.uid()}/{category}/{uuid}.jpg

BEGIN;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'clothing-images',
    'clothing-images',
    false,
    2097152,
    ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO UPDATE SET
    public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS clothing_images_insert_own_folder ON storage.objects;
CREATE POLICY clothing_images_insert_own_folder
ON storage.objects FOR INSERT TO authenticated
WITH CHECK (
    bucket_id = 'clothing-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
);

DROP POLICY IF EXISTS clothing_images_select_own_folder ON storage.objects;
CREATE POLICY clothing_images_select_own_folder
ON storage.objects FOR SELECT TO authenticated
USING (
    bucket_id = 'clothing-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
);

DROP POLICY IF EXISTS clothing_images_update_own_folder ON storage.objects;
CREATE POLICY clothing_images_update_own_folder
ON storage.objects FOR UPDATE TO authenticated
USING (
    bucket_id = 'clothing-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
)
WITH CHECK (
    bucket_id = 'clothing-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
);

DROP POLICY IF EXISTS clothing_images_delete_own_folder ON storage.objects;
CREATE POLICY clothing_images_delete_own_folder
ON storage.objects FOR DELETE TO authenticated
USING (
    bucket_id = 'clothing-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
);

COMMIT;
