-- One-time in-database snapshot taken immediately before the unified Auth migration.
-- The backup schema is not exposed by the project's PostgREST configuration.

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.schemata
        WHERE schema_name = 'fitcommit_backup_20261014'
    ) THEN
        RAISE EXCEPTION 'Backup schema fitcommit_backup_20261014 already exists';
    END IF;
END $$;

CREATE SCHEMA fitcommit_backup_20261014;
REVOKE ALL ON SCHEMA fitcommit_backup_20261014 FROM PUBLIC, anon, authenticated;

DO $$
DECLARE
    source_table record;
BEGIN
    FOR source_table IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
        ORDER BY tablename
    LOOP
        EXECUTE format(
            'CREATE TABLE fitcommit_backup_20261014.%I AS TABLE public.%I',
            source_table.tablename,
            source_table.tablename
        );
        EXECUTE format(
            'REVOKE ALL ON TABLE fitcommit_backup_20261014.%I FROM PUBLIC, anon, authenticated',
            source_table.tablename
        );
    END LOOP;
END $$;

COMMENT ON SCHEMA fitcommit_backup_20261014 IS
    'Fitcommit snapshot before Supabase Auth UUID migration on 2026-10-14 Asia/Seoul.';

COMMIT;
