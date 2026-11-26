-- V101__create_platform_tables.sql
-- Migration file for BE-1 Platform Core tables

CREATE TABLE IF NOT EXISTS "roles" (
    "id" SERIAL PRIMARY KEY,
    "name" VARCHAR(50) NOT NULL UNIQUE,
    "created_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed default roles
INSERT INTO "roles" ("id", "name") VALUES (1, 'super_admin');
INSERT INTO "roles" ("id", "name") VALUES (2, 'institution_admin');
INSERT INTO "roles" ("id", "name") VALUES (3, 'user');

CREATE TABLE IF NOT EXISTS "institutions" (
    "id" SERIAL PRIMARY KEY,
    "name" VARCHAR(255) NOT NULL,
    "slug" VARCHAR(100) NOT NULL UNIQUE,
    "status" VARCHAR(50) NOT NULL DEFAULT 'active',
    "created_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "users" (
    "id" VARCHAR(100) PRIMARY KEY, -- Supabase Auth UUID
    "email" VARCHAR(255) NOT NULL UNIQUE,
    "role_id" INT NOT NULL REFERENCES "roles"("id"),
    "institution_id" INT NULL REFERENCES "institutions"("id") ON DELETE SET NULL,
    "full_name" VARCHAR(255),
    "status" VARCHAR(50) NOT NULL DEFAULT 'active',
    "created_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "admin_invitations" (
    "id" SERIAL PRIMARY KEY,
    "email" VARCHAR(255) NOT NULL,
    "role_id" INT NOT NULL REFERENCES "roles"("id"),
    "institution_id" INT NULL REFERENCES "institutions"("id") ON DELETE CASCADE,
    "token" VARCHAR(255) NOT NULL UNIQUE,
    "status" VARCHAR(50) NOT NULL DEFAULT 'pending',
    "expires_at" TIMESTAMP WITH TIME ZONE NOT NULL,
    "created_by" VARCHAR(100) NULL REFERENCES "users"("id") ON DELETE SET NULL,
    "created_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "user_sync_log" (
    "id" SERIAL PRIMARY KEY,
    "user_id" VARCHAR(100) NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "event_type" VARCHAR(50) NOT NULL,
    "status" VARCHAR(50) NOT NULL,
    "synced_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "app_settings" (
    "key" VARCHAR(100) PRIMARY KEY,
    "value" TEXT NOT NULL,
    "description" TEXT,
    "updated_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed default global service_mode
INSERT INTO "app_settings" ("key", "value", "description") VALUES
    ('service_mode', 'full_llm_bot', 'Default AI mode: full_llm_bot or bot_with_admin_validation');

CREATE TABLE IF NOT EXISTS "institution_settings" (
    "id" SERIAL PRIMARY KEY,
    "institution_id" INT NOT NULL REFERENCES "institutions"("id") ON DELETE CASCADE,
    "key" VARCHAR(100) NOT NULL,
    "value" TEXT NOT NULL,
    "updated_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_institution_setting_key UNIQUE ("institution_id", "key")
);

CREATE TABLE IF NOT EXISTS "rag_files" (
    "id" SERIAL PRIMARY KEY,
    "institution_id" INT NOT NULL REFERENCES "institutions"("id") ON DELETE CASCADE,
    "file_name" VARCHAR(255) NOT NULL,
    "file_path" TEXT NOT NULL,
    "file_size" BIGINT NOT NULL,
    "processing_status" VARCHAR(50) NOT NULL DEFAULT 'pending',
    "error_message" TEXT,
    "uploaded_by" VARCHAR(100) NULL REFERENCES "users"("id") ON DELETE SET NULL,
    "created_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
