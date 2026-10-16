-- Run once in the Supabase SQL editor before the production release.
-- Existing object ownership policies remain unchanged; public reads are disabled.
UPDATE storage.buckets
SET public = false
WHERE id = 'clothing-images';
