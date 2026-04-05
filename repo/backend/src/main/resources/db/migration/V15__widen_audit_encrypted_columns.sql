-- Encrypted values (Base64 of AES-GCM ciphertext) are much larger than raw values.
-- Widen all columns that use EncryptedStringConverter to TEXT.

-- audit_log
ALTER TABLE audit_log ALTER COLUMN ip_address TYPE TEXT;
ALTER TABLE audit_log ALTER COLUMN device_fingerprint TYPE TEXT;

-- check_ins
ALTER TABLE check_ins ALTER COLUMN device_fingerprint TYPE TEXT;

-- users (address and device_id already TEXT, but device_id is VARCHAR(512))
ALTER TABLE users ALTER COLUMN device_id TYPE TEXT;

-- addresses (city, state, zip_code are now encrypted)
ALTER TABLE addresses ALTER COLUMN city TYPE TEXT;
ALTER TABLE addresses ALTER COLUMN state TYPE TEXT;
ALTER TABLE addresses ALTER COLUMN zip_code TYPE TEXT;
