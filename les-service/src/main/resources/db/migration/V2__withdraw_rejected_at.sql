-- Track when MECT rejected withdrawal so admins can see edge-case rejections

ALTER TABLE lmr_enrollment ADD COLUMN IF NOT EXISTS withdraw_rejected_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_lmr_enrollment_status ON lmr_enrollment(status);
