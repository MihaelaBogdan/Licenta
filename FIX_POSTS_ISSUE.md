# Fix: Posts Not Saving Issue

Problem: Posts from users are not being saved to the database. Only seeded posts appear.

## Root Cause

The `feed_posts` table likely has RLS (Row Level Security) policies that are blocking INSERT operations for users.

## Solution

### Step 1: Run SQL Script in Supabase

1. Go to **https://app.supabase.com** → CityScape project
2. Click **SQL Editor** → **New Query**
3. Copy the content from: `/Users/mihaela/Desktop/Licenta/backend/fix_feed_posts_rls.sql`
4. Paste it in the SQL editor
5. Click **Run**

This will:
- ✅ Enable RLS on feed_posts
- ✅ Drop old blocking policies
- ✅ Create new policies allowing INSERT/SELECT/UPDATE/DELETE
- ✅ Grant permissions to authenticated and anon users

### Step 2: Verify It Works

Test posting from the app:
1. Open app → Feed → "+" button
2. Create a new post
3. Submit it
4. Check if it appears in the feed immediately

### Step 3: Check Backend Logs

If posts still don't work, check backend logs:
```bash
curl https://flask-serve.vercel.app/feed -X GET
```

Should return posts including newly created ones.

## Debugging Checklist

If posts still don't save:

- [ ] Run the SQL script above
- [ ] Check Supabase table schema: does `feed_posts` have all required columns?
- [ ] Check RLS policies: go to **feed_posts** → **RLS** tab
- [ ] Verify permissions: GRANT statements were executed
- [ ] Test endpoint directly: `curl -X POST https://flask-serve.vercel.app/feed -d '{"caption":"test",...}'`
- [ ] Check browser console for network errors
- [ ] Check Vercel logs for API errors

## Quick Fix Commands

Run these in Supabase SQL editor if basic fix doesn't work:

```sql
-- Disable RLS completely (not recommended for production)
ALTER TABLE feed_posts DISABLE ROW LEVEL SECURITY;

-- OR re-enable with permissive policies
ALTER TABLE feed_posts ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Enable access for all users" ON feed_posts;
CREATE POLICY "Enable access for all users" ON feed_posts
FOR ALL USING (true);
```

## Expected Result

After fix:
- ✅ New posts save instantly
- ✅ All users can see all posts
- ✅ Posts appear in feed within seconds
- ✅ Seeded posts still visible
