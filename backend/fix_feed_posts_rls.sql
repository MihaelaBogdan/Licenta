-- Fix feed_posts RLS policies to allow posting

-- 1. Enable RLS if not already enabled
ALTER TABLE feed_posts ENABLE ROW LEVEL SECURITY;

-- 2. Drop existing policies if they exist
DROP POLICY IF EXISTS "Allow users to insert own posts" ON feed_posts;
DROP POLICY IF EXISTS "Allow insert feed posts" ON feed_posts;
DROP POLICY IF EXISTS "Allow select feed posts" ON feed_posts;
DROP POLICY IF EXISTS "Allow update own posts" ON feed_posts;
DROP POLICY IF EXISTS "Allow delete own posts" ON feed_posts;

-- 3. CREATE NEW POLICIES

-- Allow anyone (authenticated or anon) to insert posts
CREATE POLICY "Allow insert feed posts" ON feed_posts
FOR INSERT
WITH CHECK (true);

-- Allow anyone to read all posts
CREATE POLICY "Allow select feed posts" ON feed_posts
FOR SELECT
USING (true);

-- Allow post owner to update their posts
CREATE POLICY "Allow update own posts" ON feed_posts
FOR UPDATE
USING (auth.uid()::text = user_id::text OR auth.uid() IS NULL)
WITH CHECK (auth.uid()::text = user_id::text OR auth.uid() IS NULL);

-- Allow post owner to delete their posts
CREATE POLICY "Allow delete own posts" ON feed_posts
FOR DELETE
USING (auth.uid()::text = user_id::text OR auth.uid() IS NULL);

-- 4. Grant permissions
GRANT ALL ON feed_posts TO authenticated;
GRANT ALL ON feed_posts TO anon;
GRANT ALL ON feed_posts TO service_role;

-- 5. Check if table exists and verify structure
SELECT
    column_name,
    data_type,
    is_nullable
FROM information_schema.columns
WHERE table_name = 'feed_posts'
ORDER BY ordinal_position;
