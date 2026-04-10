-- Places (Migrated from backend JSON)
CREATE TABLE IF NOT EXISTS public.places (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    rating DOUBLE PRECISION,
    image_url TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    type TEXT,
    address TEXT
);

-- Visited Places (Recorded manually or through activity completion)
CREATE TABLE IF NOT EXISTS public.visited_places (
    id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    place_id INTEGER, -- If it's a static place from Supabase
    google_place_id TEXT, -- If it's a real-time place from Google
    place_name TEXT NOT NULL,
    place_type TEXT,
    visited_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- User Profiles (Extends Supabase Auth Auth.users)
CREATE TABLE IF NOT EXISTS public.user_profiles (
    id UUID REFERENCES auth.users(id) ON DELETE CASCADE PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    level INTEGER DEFAULT 1,
    current_xp INTEGER DEFAULT 0,
    total_xp INTEGER DEFAULT 0,
    places_visited INTEGER DEFAULT 0,
    badges_earned INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- User Badges
CREATE TABLE IF NOT EXISTS public.user_badges (
    id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    badge_id TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    icon_name TEXT,
    is_unlocked BOOLEAN DEFAULT FALSE,
    unlocked_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(user_id, badge_id)
);

-- User Achievements
CREATE TABLE IF NOT EXISTS public.user_achievements (
    id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    xp_reward INTEGER DEFAULT 0,
    earned_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Planned Activities
CREATE TABLE IF NOT EXISTS public.planned_activities (
    id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    place_id INTEGER,
    place_name TEXT NOT NULL,
    place_type TEXT,
    place_image_url TEXT,
    scheduled_date BIGINT, 
    scheduled_time TEXT,
    is_completed BOOLEAN DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Activity Groups
CREATE TABLE IF NOT EXISTS public.activity_groups (
    id SERIAL PRIMARY KEY,
    activity_id INTEGER REFERENCES public.planned_activities(id) ON DELETE SET NULL,
    creator_id UUID REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    group_name TEXT NOT NULL,
    group_code TEXT UNIQUE NOT NULL,
    max_members INTEGER DEFAULT 10,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Group Members
CREATE TABLE IF NOT EXISTS public.group_members (
    id SERIAL PRIMARY KEY,
    group_id INTEGER REFERENCES public.activity_groups(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    user_name TEXT,
    status TEXT DEFAULT 'pending', 
    is_creator BOOLEAN DEFAULT FALSE,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(group_id, user_id)
);

-- Invitations
CREATE TABLE IF NOT EXISTS public.invitations (
    id SERIAL PRIMARY KEY,
    from_user_id UUID REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    from_user_name TEXT,
    to_user_id UUID REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    group_id INTEGER REFERENCES public.activity_groups(id) ON DELETE CASCADE,
    group_name TEXT,
    activity_name TEXT,
    activity_date TEXT,
    activity_time TEXT,
    status TEXT DEFAULT 'pending',
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Member Schedules
CREATE TABLE IF NOT EXISTS public.member_schedules (
    id SERIAL PRIMARY KEY,
    group_id INTEGER REFERENCES public.activity_groups(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    user_name TEXT,
    date BIGINT, 
    start_time TEXT,
    end_time TEXT,
    is_available BOOLEAN DEFAULT TRUE,
    note TEXT
);

-- Enable Row Level Security (RLS) for all tables
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_badges ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_achievements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.planned_activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activity_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.member_schedules ENABLE ROW LEVEL SECURITY;

-- Note: Policies cannot use IF NOT EXISTS directly without PL/pgSQL
-- SO we use a safe block format:
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Users can view own profile') THEN
        CREATE POLICY "Users can view own profile" ON public.user_profiles FOR SELECT USING (auth.uid() = id);
        CREATE POLICY "Users can update own profile" ON public.user_profiles FOR UPDATE USING (auth.uid() = id);
        CREATE POLICY "Users can view any profile" ON public.user_profiles FOR SELECT USING (true);
        CREATE POLICY "Users manage own badges" ON public.user_badges USING (auth.uid() = user_id);
        CREATE POLICY "Users manage own achievements" ON public.user_achievements USING (auth.uid() = user_id);
        CREATE POLICY "Users manage own planned activities" ON public.planned_activities USING (auth.uid() = user_id);
        CREATE POLICY "Anyone can view groups" ON public.activity_groups FOR SELECT USING (true);
        CREATE POLICY "Creators manage activity groups" ON public.activity_groups USING (auth.uid() = creator_id);
        CREATE POLICY "Anyone can read group members" ON public.group_members FOR SELECT USING (true);
        CREATE POLICY "Users insert members" ON public.group_members FOR INSERT WITH CHECK (true);
        CREATE POLICY "Users manage their own member status" ON public.group_members FOR UPDATE USING (auth.uid() = user_id);
        CREATE POLICY "Users manage their own schedules" ON public.member_schedules USING (auth.uid() = user_id);
    END IF;
END
$$;

-- CREATE A TRIGGER TO AUTOMATICALLY CREATE A USER PROFILE ON SIGNUP
CREATE OR REPLACE FUNCTION public.handle_new_user() 
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.user_profiles (id, email, name)
  VALUES (new.id, new.email, COALESCE(new.raw_user_meta_data->>'display_name', new.raw_user_meta_data->>'name', 'User'));
  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'on_auth_user_created') THEN
        CREATE TRIGGER on_auth_user_created
          AFTER INSERT ON auth.users
          FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();
    END IF;
END $$;
