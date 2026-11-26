-- The BE-1 UUID and institution IDs keep their original public names.
-- BE-2's legacy tables use numeric user_id and institution_id foreign keys.
ALTER TABLE users ADD COLUMN user_id SERIAL;
ALTER TABLE users ADD CONSTRAINT users_user_id_key UNIQUE (user_id);
