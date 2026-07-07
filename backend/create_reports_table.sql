-- Create content_reports table for storing user reports
CREATE TABLE IF NOT EXISTS content_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL,
    post_id UUID,
    comment_id UUID,
    reason TEXT NOT NULL DEFAULT 'Conținut neadecvat',
    status TEXT NOT NULL DEFAULT 'pending', -- pending, reviewed, resolved, dismissed
    admin_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT check_post_or_comment CHECK (post_id IS NOT NULL OR comment_id IS NOT NULL)
);

-- Create index for faster queries
CREATE INDEX IF NOT EXISTS idx_content_reports_status ON content_reports(status);
CREATE INDEX IF NOT EXISTS idx_content_reports_reporter_id ON content_reports(reporter_id);
CREATE INDEX IF NOT EXISTS idx_content_reports_created_at ON content_reports(created_at DESC);

-- Enable RLS
ALTER TABLE content_reports ENABLE ROW LEVEL SECURITY;

-- Allow anyone to insert reports
CREATE POLICY "Allow insert reports" ON content_reports FOR INSERT WITH CHECK (true);

-- Allow admins to view all reports
CREATE POLICY "Allow admins to view reports" ON content_reports FOR SELECT USING (
    auth.uid()::text IN (SELECT user_id FROM user_profiles WHERE role = 'admin')
);

-- Allow users to view their own reports
CREATE POLICY "Allow users to view own reports" ON content_reports FOR SELECT USING (
    reporter_id = auth.uid()
);

-- GRANTS
GRANT ALL ON content_reports TO authenticated;
GRANT ALL ON content_reports TO anon;
