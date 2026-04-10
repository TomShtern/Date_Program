--
-- PostgreSQL database dump
--

\restrict 9DXJ3mwjYDFztd6z9KyEu0a33ouiV9mzOiwanT2LepBAes68dEeyqhRD4UYQhxn

-- Dumped from database version 18.3
-- Dumped by pg_dump version 18.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA public;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: blocks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.blocks (
    id uuid NOT NULL,
    blocker_id uuid NOT NULL,
    blocked_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT ck_blocks_distinct_users CHECK ((blocker_id <> blocked_id))
);


--
-- Name: conversations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.conversations (
    id character varying(100) NOT NULL,
    user_a uuid NOT NULL,
    user_b uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    last_message_at timestamp with time zone,
    user_a_last_read_at timestamp with time zone,
    user_b_last_read_at timestamp with time zone,
    archived_at_a timestamp with time zone,
    archive_reason_a character varying(20),
    archived_at_b timestamp with time zone,
    archive_reason_b character varying(20),
    visible_to_user_a boolean DEFAULT true,
    visible_to_user_b boolean DEFAULT true,
    deleted_at timestamp with time zone,
    CONSTRAINT ck_conversations_archive_reason_a_values CHECK (((archive_reason_a IS NULL) OR ((archive_reason_a)::text = ANY ((ARRAY['FRIEND_ZONE'::character varying, 'GRACEFUL_EXIT'::character varying, 'UNMATCH'::character varying, 'BLOCK'::character varying])::text[])))),
    CONSTRAINT ck_conversations_archive_reason_b_values CHECK (((archive_reason_b IS NULL) OR ((archive_reason_b)::text = ANY ((ARRAY['FRIEND_ZONE'::character varying, 'GRACEFUL_EXIT'::character varying, 'UNMATCH'::character varying, 'BLOCK'::character varying])::text[])))),
    CONSTRAINT ck_conversations_distinct_users CHECK ((user_a <> user_b)),
    CONSTRAINT ck_conversations_id_length CHECK ((char_length((id)::text) = 73))
);


--
-- Name: daily_pick_views; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.daily_pick_views (
    user_id uuid NOT NULL,
    viewed_date date NOT NULL,
    viewed_at timestamp with time zone NOT NULL
);


--
-- Name: daily_picks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.daily_picks (
    user_id uuid NOT NULL,
    pick_date date NOT NULL,
    picked_user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL
);


--
-- Name: friend_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.friend_requests (
    id uuid NOT NULL,
    from_user_id uuid NOT NULL,
    to_user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    status character varying(20) NOT NULL,
    responded_at timestamp with time zone,
    pair_key character varying(73),
    pending_marker character varying(10),
    CONSTRAINT ck_friend_requests_distinct_users CHECK ((from_user_id <> to_user_id)),
    CONSTRAINT ck_friend_requests_status_values CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: likes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.likes (
    id uuid NOT NULL,
    who_likes uuid NOT NULL,
    who_got_liked uuid NOT NULL,
    direction character varying(10) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT ck_likes_direction_values CHECK (((direction)::text = ANY ((ARRAY['LIKE'::character varying, 'SUPER_LIKE'::character varying, 'PASS'::character varying])::text[]))),
    CONSTRAINT ck_likes_distinct_users CHECK ((who_likes <> who_got_liked))
);


--
-- Name: matches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.matches (
    id character varying(100) NOT NULL,
    user_a uuid NOT NULL,
    user_b uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    state character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    ended_at timestamp with time zone,
    ended_by uuid,
    end_reason character varying(30),
    deleted_at timestamp with time zone,
    CONSTRAINT ck_matches_distinct_users CHECK ((user_a <> user_b)),
    CONSTRAINT ck_matches_end_reason_values CHECK (((end_reason IS NULL) OR ((end_reason)::text = ANY ((ARRAY['FRIEND_ZONE'::character varying, 'GRACEFUL_EXIT'::character varying, 'UNMATCH'::character varying, 'BLOCK'::character varying])::text[])))),
    CONSTRAINT ck_matches_ended_by_participant CHECK (((ended_by IS NULL) OR (ended_by = user_a) OR (ended_by = user_b))),
    CONSTRAINT ck_matches_id_length CHECK ((char_length((id)::text) = 73)),
    CONSTRAINT ck_matches_state_values CHECK (((state)::text = ANY ((ARRAY['ACTIVE'::character varying, 'FRIENDS'::character varying, 'UNMATCHED'::character varying, 'GRACEFUL_EXIT'::character varying, 'BLOCKED'::character varying])::text[])))
);


--
-- Name: messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.messages (
    id uuid NOT NULL,
    conversation_id character varying(100) NOT NULL,
    sender_id uuid NOT NULL,
    content character varying(1000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT ck_messages_content_nonblank CHECK ((char_length(TRIM(BOTH FROM content)) > 0)),
    CONSTRAINT ck_messages_conversation_id_length CHECK ((char_length((conversation_id)::text) = 73))
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    type character varying(30) NOT NULL,
    title character varying(200) NOT NULL,
    message text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    is_read boolean DEFAULT false,
    data_json text,
    CONSTRAINT ck_notifications_message_nonblank CHECK ((char_length(TRIM(BOTH FROM message)) > 0)),
    CONSTRAINT ck_notifications_title_nonblank CHECK ((char_length(TRIM(BOTH FROM title)) > 0)),
    CONSTRAINT ck_notifications_type_values CHECK (((type)::text = ANY ((ARRAY['MATCH_FOUND'::character varying, 'NEW_MESSAGE'::character varying, 'FRIEND_REQUEST'::character varying, 'FRIEND_REQUEST_ACCEPTED'::character varying, 'GRACEFUL_EXIT'::character varying])::text[])))
);


--
-- Name: platform_stats; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_stats (
    id uuid NOT NULL,
    computed_at timestamp with time zone NOT NULL,
    total_active_users integer DEFAULT 0 NOT NULL,
    avg_likes_received double precision DEFAULT 0.0 NOT NULL,
    avg_likes_given double precision DEFAULT 0.0 NOT NULL,
    avg_match_rate double precision DEFAULT 0.0 NOT NULL,
    avg_like_ratio double precision DEFAULT 0.5 NOT NULL
);


--
-- Name: profile_notes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.profile_notes (
    author_id uuid NOT NULL,
    subject_id uuid NOT NULL,
    content character varying(500) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT ck_profile_notes_content_nonblank CHECK ((char_length(TRIM(BOTH FROM content)) > 0)),
    CONSTRAINT ck_profile_notes_distinct_users CHECK ((author_id <> subject_id))
);


--
-- Name: profile_views; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.profile_views (
    viewer_id uuid NOT NULL,
    viewed_id uuid NOT NULL,
    viewed_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_profile_views_distinct_users CHECK ((viewer_id <> viewed_id))
);


--
-- Name: reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reports (
    id uuid NOT NULL,
    reporter_id uuid NOT NULL,
    reported_user_id uuid NOT NULL,
    reason character varying(50) NOT NULL,
    description character varying(500),
    created_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT ck_reports_distinct_users CHECK ((reporter_id <> reported_user_id)),
    CONSTRAINT ck_reports_reason_values CHECK (((reason)::text = ANY ((ARRAY['SPAM'::character varying, 'INAPPROPRIATE_CONTENT'::character varying, 'HARASSMENT'::character varying, 'FAKE_PROFILE'::character varying, 'UNDERAGE'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: schema_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schema_version (
    version integer NOT NULL,
    applied_at timestamp with time zone NOT NULL,
    description character varying(255)
);


--
-- Name: standouts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.standouts (
    id uuid NOT NULL,
    seeker_id uuid NOT NULL,
    standout_user_id uuid NOT NULL,
    featured_date date NOT NULL,
    rank integer NOT NULL,
    score integer NOT NULL,
    reason character varying(200) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    interacted_at timestamp with time zone
);


--
-- Name: swipe_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.swipe_sessions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    started_at timestamp with time zone NOT NULL,
    last_activity_at timestamp with time zone NOT NULL,
    ended_at timestamp with time zone,
    state character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    swipe_count integer DEFAULT 0 NOT NULL,
    like_count integer DEFAULT 0 NOT NULL,
    pass_count integer DEFAULT 0 NOT NULL,
    match_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_swipe_sessions_state_values CHECK (((state)::text = ANY ((ARRAY['ACTIVE'::character varying, 'COMPLETED'::character varying])::text[])))
);


--
-- Name: undo_states; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.undo_states (
    user_id uuid NOT NULL,
    like_id uuid NOT NULL,
    who_likes uuid NOT NULL,
    who_got_liked uuid NOT NULL,
    direction character varying(10) NOT NULL,
    like_created_at timestamp with time zone NOT NULL,
    match_id character varying(100),
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_undo_states_direction_values CHECK (((direction)::text = ANY ((ARRAY['LIKE'::character varying, 'SUPER_LIKE'::character varying, 'PASS'::character varying])::text[]))),
    CONSTRAINT ck_undo_states_match_id_length CHECK (((match_id IS NULL) OR (char_length((match_id)::text) = 73)))
);


--
-- Name: user_achievements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_achievements (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    achievement character varying(50) NOT NULL,
    unlocked_at timestamp with time zone NOT NULL
);


--
-- Name: user_db_drinking; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_db_drinking (
    user_id uuid NOT NULL,
    value character varying(50) NOT NULL,
    CONSTRAINT ck_user_db_drinking_value_values CHECK (((value)::text = ANY ((ARRAY['NEVER'::character varying, 'SOCIALLY'::character varying, 'REGULARLY'::character varying])::text[])))
);


--
-- Name: user_db_education; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_db_education (
    user_id uuid NOT NULL,
    value character varying(50) NOT NULL,
    CONSTRAINT ck_user_db_education_value_values CHECK (((value)::text = ANY ((ARRAY['HIGH_SCHOOL'::character varying, 'SOME_COLLEGE'::character varying, 'BACHELORS'::character varying, 'MASTERS'::character varying, 'PHD'::character varying, 'TRADE_SCHOOL'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: user_db_looking_for; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_db_looking_for (
    user_id uuid NOT NULL,
    value character varying(50) NOT NULL,
    CONSTRAINT ck_user_db_looking_for_value_values CHECK (((value)::text = ANY ((ARRAY['CASUAL'::character varying, 'SHORT_TERM'::character varying, 'LONG_TERM'::character varying, 'MARRIAGE'::character varying, 'UNSURE'::character varying])::text[])))
);


--
-- Name: user_db_smoking; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_db_smoking (
    user_id uuid NOT NULL,
    value character varying(50) NOT NULL,
    CONSTRAINT ck_user_db_smoking_value_values CHECK (((value)::text = ANY ((ARRAY['NEVER'::character varying, 'SOMETIMES'::character varying, 'REGULARLY'::character varying])::text[])))
);


--
-- Name: user_db_wants_kids; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_db_wants_kids (
    user_id uuid NOT NULL,
    value character varying(50) NOT NULL,
    CONSTRAINT ck_user_db_wants_kids_value_values CHECK (((value)::text = ANY ((ARRAY['NO'::character varying, 'OPEN'::character varying, 'SOMEDAY'::character varying, 'HAS_KIDS'::character varying])::text[])))
);


--
-- Name: user_interested_in; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_interested_in (
    user_id uuid NOT NULL,
    gender character varying(30) NOT NULL,
    CONSTRAINT ck_user_interested_in_gender_values CHECK (((gender)::text = ANY ((ARRAY['MALE'::character varying, 'FEMALE'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: user_interests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_interests (
    user_id uuid NOT NULL,
    interest character varying(50) NOT NULL
);


--
-- Name: user_photos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_photos (
    user_id uuid NOT NULL,
    "position" integer NOT NULL,
    url character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: user_stats; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_stats (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    computed_at timestamp with time zone NOT NULL,
    total_swipes_given integer DEFAULT 0 NOT NULL,
    likes_given integer DEFAULT 0 NOT NULL,
    passes_given integer DEFAULT 0 NOT NULL,
    like_ratio double precision DEFAULT 0.0 NOT NULL,
    total_swipes_received integer DEFAULT 0 NOT NULL,
    likes_received integer DEFAULT 0 NOT NULL,
    passes_received integer DEFAULT 0 NOT NULL,
    incoming_like_ratio double precision DEFAULT 0.0 NOT NULL,
    total_matches integer DEFAULT 0 NOT NULL,
    active_matches integer DEFAULT 0 NOT NULL,
    match_rate double precision DEFAULT 0.0 NOT NULL,
    blocks_given integer DEFAULT 0 NOT NULL,
    blocks_received integer DEFAULT 0 NOT NULL,
    reports_given integer DEFAULT 0 NOT NULL,
    reports_received integer DEFAULT 0 NOT NULL,
    reciprocity_score double precision DEFAULT 0.0 NOT NULL,
    selectiveness_score double precision DEFAULT 0.5 NOT NULL,
    attractiveness_score double precision DEFAULT 0.5 NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    name character varying(100) NOT NULL,
    bio character varying(500),
    birth_date date,
    gender character varying(20),
    lat double precision,
    lon double precision,
    has_location_set boolean DEFAULT false,
    max_distance_km integer DEFAULT 50,
    min_age integer DEFAULT 18,
    max_age integer DEFAULT 99,
    state character varying(20) DEFAULT 'INCOMPLETE'::character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    smoking character varying(20),
    drinking character varying(20),
    wants_kids character varying(20),
    looking_for character varying(20),
    education character varying(20),
    height_cm integer,
    db_min_height_cm integer,
    db_max_height_cm integer,
    db_max_age_diff integer,
    email character varying(200),
    phone character varying(50),
    is_verified boolean,
    verification_method character varying(10),
    verification_code character varying(10),
    verification_sent_at timestamp with time zone,
    verified_at timestamp with time zone,
    pace_messaging_frequency character varying(30),
    pace_time_to_first_date character varying(30),
    pace_communication_style character varying(30),
    pace_depth_preference character varying(30),
    deleted_at timestamp with time zone,
    CONSTRAINT ck_users_age_bounds CHECK ((min_age <= max_age)),
    CONSTRAINT ck_users_drinking_values CHECK (((drinking IS NULL) OR ((drinking)::text = ANY ((ARRAY['NEVER'::character varying, 'SOCIALLY'::character varying, 'REGULARLY'::character varying])::text[])))),
    CONSTRAINT ck_users_education_values CHECK (((education IS NULL) OR ((education)::text = ANY ((ARRAY['HIGH_SCHOOL'::character varying, 'SOME_COLLEGE'::character varying, 'BACHELORS'::character varying, 'MASTERS'::character varying, 'PHD'::character varying, 'TRADE_SCHOOL'::character varying, 'OTHER'::character varying])::text[])))),
    CONSTRAINT ck_users_email_trimmed CHECK (((email IS NULL) OR ((email)::text = TRIM(BOTH FROM email)))),
    CONSTRAINT ck_users_gender_values CHECK (((gender IS NULL) OR ((gender)::text = ANY ((ARRAY['MALE'::character varying, 'FEMALE'::character varying, 'OTHER'::character varying])::text[])))),
    CONSTRAINT ck_users_height_bounds CHECK (((db_min_height_cm IS NULL) OR (db_max_height_cm IS NULL) OR (db_min_height_cm <= db_max_height_cm))),
    CONSTRAINT ck_users_looking_for_values CHECK (((looking_for IS NULL) OR ((looking_for)::text = ANY ((ARRAY['CASUAL'::character varying, 'SHORT_TERM'::character varying, 'LONG_TERM'::character varying, 'MARRIAGE'::character varying, 'UNSURE'::character varying])::text[])))),
    CONSTRAINT ck_users_max_age_diff_nonnegative CHECK (((db_max_age_diff IS NULL) OR (db_max_age_diff >= 0))),
    CONSTRAINT ck_users_pace_comm_style_values CHECK (((pace_communication_style IS NULL) OR ((pace_communication_style)::text = ANY ((ARRAY['TEXT_ONLY'::character varying, 'VOICE_NOTES'::character varying, 'VIDEO_CALLS'::character varying, 'IN_PERSON_ONLY'::character varying, 'MIX_OF_EVERYTHING'::character varying])::text[])))),
    CONSTRAINT ck_users_pace_depth_values CHECK (((pace_depth_preference IS NULL) OR ((pace_depth_preference)::text = ANY ((ARRAY['SMALL_TALK'::character varying, 'DEEP_CHAT'::character varying, 'EXISTENTIAL'::character varying, 'DEPENDS_ON_VIBE'::character varying])::text[])))),
    CONSTRAINT ck_users_pace_first_date_values CHECK (((pace_time_to_first_date IS NULL) OR ((pace_time_to_first_date)::text = ANY ((ARRAY['QUICKLY'::character varying, 'FEW_DAYS'::character varying, 'WEEKS'::character varying, 'MONTHS'::character varying, 'WILDCARD'::character varying])::text[])))),
    CONSTRAINT ck_users_pace_msg_freq_values CHECK (((pace_messaging_frequency IS NULL) OR ((pace_messaging_frequency)::text = ANY ((ARRAY['RARELY'::character varying, 'OFTEN'::character varying, 'CONSTANTLY'::character varying, 'WILDCARD'::character varying])::text[])))),
    CONSTRAINT ck_users_phone_trimmed CHECK (((phone IS NULL) OR ((phone)::text = TRIM(BOTH FROM phone)))),
    CONSTRAINT ck_users_smoking_values CHECK (((smoking IS NULL) OR ((smoking)::text = ANY ((ARRAY['NEVER'::character varying, 'SOMETIMES'::character varying, 'REGULARLY'::character varying])::text[])))),
    CONSTRAINT ck_users_state_values CHECK (((state)::text = ANY ((ARRAY['INCOMPLETE'::character varying, 'ACTIVE'::character varying, 'PAUSED'::character varying, 'BANNED'::character varying])::text[]))),
    CONSTRAINT ck_users_verification_method_values CHECK (((verification_method IS NULL) OR ((verification_method)::text = ANY ((ARRAY['EMAIL'::character varying, 'PHONE'::character varying])::text[])))),
    CONSTRAINT ck_users_wants_kids_values CHECK (((wants_kids IS NULL) OR ((wants_kids)::text = ANY ((ARRAY['NO'::character varying, 'OPEN'::character varying, 'SOMEDAY'::character varying, 'HAS_KIDS'::character varying])::text[]))))
);


--
-- Name: blocks blocks_blocker_id_blocked_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT blocks_blocker_id_blocked_id_key UNIQUE (blocker_id, blocked_id);


--
-- Name: blocks blocks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT blocks_pkey PRIMARY KEY (id);


--
-- Name: conversations conversations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_pkey PRIMARY KEY (id);


--
-- Name: daily_pick_views daily_pick_views_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.daily_pick_views
    ADD CONSTRAINT daily_pick_views_pkey PRIMARY KEY (user_id, viewed_date);


--
-- Name: daily_picks daily_picks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.daily_picks
    ADD CONSTRAINT daily_picks_pkey PRIMARY KEY (user_id, pick_date);


--
-- Name: friend_requests friend_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_requests
    ADD CONSTRAINT friend_requests_pkey PRIMARY KEY (id);


--
-- Name: likes likes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.likes
    ADD CONSTRAINT likes_pkey PRIMARY KEY (id);


--
-- Name: matches matches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT matches_pkey PRIMARY KEY (id);


--
-- Name: messages messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: platform_stats platform_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_stats
    ADD CONSTRAINT platform_stats_pkey PRIMARY KEY (id);


--
-- Name: profile_notes profile_notes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_notes
    ADD CONSTRAINT profile_notes_pkey PRIMARY KEY (author_id, subject_id);


--
-- Name: profile_views profile_views_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_views
    ADD CONSTRAINT profile_views_pkey PRIMARY KEY (viewer_id, viewed_id, viewed_at);


--
-- Name: reports reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_pkey PRIMARY KEY (id);


--
-- Name: reports reports_reporter_id_reported_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_reporter_id_reported_user_id_key UNIQUE (reporter_id, reported_user_id);


--
-- Name: schema_version schema_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schema_version
    ADD CONSTRAINT schema_version_pkey PRIMARY KEY (version);


--
-- Name: standouts standouts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.standouts
    ADD CONSTRAINT standouts_pkey PRIMARY KEY (id);


--
-- Name: swipe_sessions swipe_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.swipe_sessions
    ADD CONSTRAINT swipe_sessions_pkey PRIMARY KEY (id);


--
-- Name: conversations uk_conversation_users; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT uk_conversation_users UNIQUE (user_a, user_b);


--
-- Name: likes uk_likes; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.likes
    ADD CONSTRAINT uk_likes UNIQUE (who_likes, who_got_liked);


--
-- Name: matches uk_matches; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT uk_matches UNIQUE (user_a, user_b);


--
-- Name: standouts uk_standouts_daily; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.standouts
    ADD CONSTRAINT uk_standouts_daily UNIQUE (seeker_id, standout_user_id, featured_date);


--
-- Name: users uk_users_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_email UNIQUE (email);


--
-- Name: users uk_users_phone; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_phone UNIQUE (phone);


--
-- Name: undo_states undo_states_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.undo_states
    ADD CONSTRAINT undo_states_pkey PRIMARY KEY (user_id);


--
-- Name: user_achievements user_achievements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_achievements
    ADD CONSTRAINT user_achievements_pkey PRIMARY KEY (id);


--
-- Name: user_achievements user_achievements_user_id_achievement_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_achievements
    ADD CONSTRAINT user_achievements_user_id_achievement_key UNIQUE (user_id, achievement);


--
-- Name: user_db_drinking user_db_drinking_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_db_drinking
    ADD CONSTRAINT user_db_drinking_pkey PRIMARY KEY (user_id, value);


--
-- Name: user_db_education user_db_education_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_db_education
    ADD CONSTRAINT user_db_education_pkey PRIMARY KEY (user_id, value);


--
-- Name: user_db_looking_for user_db_looking_for_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_db_looking_for
    ADD CONSTRAINT user_db_looking_for_pkey PRIMARY KEY (user_id, value);


--
-- Name: user_db_smoking user_db_smoking_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_db_smoking
    ADD CONSTRAINT user_db_smoking_pkey PRIMARY KEY (user_id, value);


--
-- Name: user_db_wants_kids user_db_wants_kids_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_db_wants_kids
    ADD CONSTRAINT user_db_wants_kids_pkey PRIMARY KEY (user_id, value);


--
-- Name: user_interested_in user_interested_in_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_interested_in
    ADD CONSTRAINT user_interested_in_pkey PRIMARY KEY (user_id, gender);


--
-- Name: user_interests user_interests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_interests
    ADD CONSTRAINT user_interests_pkey PRIMARY KEY (user_id, interest);


--
-- Name: user_photos user_photos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_photos
    ADD CONSTRAINT user_photos_pkey PRIMARY KEY (user_id, "position");


--
-- Name: user_stats user_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_stats
    ADD CONSTRAINT user_stats_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_achievements_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_achievements_user_id ON public.user_achievements USING btree (user_id);


--
-- Name: idx_blocks_blocked; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_blocks_blocked ON public.blocks USING btree (blocked_id);


--
-- Name: idx_blocks_blocker; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_blocks_blocker ON public.blocks USING btree (blocker_id);


--
-- Name: idx_conversations_last_msg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversations_last_msg ON public.conversations USING btree (last_message_at DESC);


--
-- Name: idx_conversations_user_a; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversations_user_a ON public.conversations USING btree (user_a);


--
-- Name: idx_conversations_user_a_last_msg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversations_user_a_last_msg ON public.conversations USING btree (user_a, last_message_at DESC, created_at DESC, id DESC) WHERE ((deleted_at IS NULL) AND (visible_to_user_a = true));


--
-- Name: idx_conversations_user_b; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversations_user_b ON public.conversations USING btree (user_b);


--
-- Name: idx_conversations_user_b_last_msg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversations_user_b_last_msg ON public.conversations USING btree (user_b, last_message_at DESC, created_at DESC, id DESC) WHERE ((deleted_at IS NULL) AND (visible_to_user_b = true));


--
-- Name: idx_daily_pick_views_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_daily_pick_views_date ON public.daily_pick_views USING btree (viewed_date);


--
-- Name: idx_daily_pick_views_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_daily_pick_views_user ON public.daily_pick_views USING btree (user_id);


--
-- Name: idx_daily_picks_pick_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_daily_picks_pick_date ON public.daily_picks USING btree (pick_date);


--
-- Name: idx_friend_req_to_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_friend_req_to_status ON public.friend_requests USING btree (to_user_id, status);


--
-- Name: idx_friend_req_to_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_friend_req_to_user ON public.friend_requests USING btree (to_user_id);


--
-- Name: idx_friend_req_users; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_friend_req_users ON public.friend_requests USING btree (from_user_id, to_user_id, status);


--
-- Name: idx_likes_direction_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_likes_direction_created ON public.likes USING btree (direction, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_likes_received_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_likes_received_created ON public.likes USING btree (who_got_liked, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_likes_who_got_liked; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_likes_who_got_liked ON public.likes USING btree (who_got_liked);


--
-- Name: idx_likes_who_likes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_likes_who_likes ON public.likes USING btree (who_likes);


--
-- Name: idx_matches_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_matches_state ON public.matches USING btree (state);


--
-- Name: idx_matches_user_a; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_matches_user_a ON public.matches USING btree (user_a);


--
-- Name: idx_matches_user_b; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_matches_user_b ON public.matches USING btree (user_b);


--
-- Name: idx_messages_conversation_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_messages_conversation_created ON public.messages USING btree (conversation_id, created_at);


--
-- Name: idx_messages_sender_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_messages_sender_created ON public.messages USING btree (sender_id, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_messages_sender_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_messages_sender_id ON public.messages USING btree (sender_id);


--
-- Name: idx_notifications_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_created ON public.notifications USING btree (created_at DESC);


--
-- Name: idx_notifications_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_user ON public.notifications USING btree (user_id, is_read);


--
-- Name: idx_platform_stats_computed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_stats_computed_at ON public.platform_stats USING btree (computed_at DESC);


--
-- Name: idx_profile_notes_author; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_notes_author ON public.profile_notes USING btree (author_id);


--
-- Name: idx_profile_views_viewed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_views_viewed_at ON public.profile_views USING btree (viewed_at DESC);


--
-- Name: idx_profile_views_viewed_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_views_viewed_id ON public.profile_views USING btree (viewed_id);


--
-- Name: idx_profile_views_viewer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_views_viewer ON public.profile_views USING btree (viewer_id);


--
-- Name: idx_reports_reported; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_reported ON public.reports USING btree (reported_user_id);


--
-- Name: idx_sessions_started_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sessions_started_at ON public.swipe_sessions USING btree (user_id, started_at);


--
-- Name: idx_sessions_started_at_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sessions_started_at_desc ON public.swipe_sessions USING btree (started_at DESC) WHERE ((state)::text = 'ACTIVE'::text);


--
-- Name: idx_sessions_user_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sessions_user_active ON public.swipe_sessions USING btree (user_id, state);


--
-- Name: idx_sessions_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sessions_user_id ON public.swipe_sessions USING btree (user_id);


--
-- Name: idx_standouts_interacted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_standouts_interacted_at ON public.standouts USING btree (seeker_id, interacted_at DESC) WHERE (interacted_at IS NOT NULL);


--
-- Name: idx_standouts_seeker_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_standouts_seeker_date ON public.standouts USING btree (seeker_id, featured_date DESC);


--
-- Name: idx_undo_states_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_undo_states_expires ON public.undo_states USING btree (expires_at);


--
-- Name: idx_user_interested_in_gender; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_interested_in_gender ON public.user_interested_in USING btree (gender);


--
-- Name: idx_user_interests_interest; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_interests_interest ON public.user_interests USING btree (interest);


--
-- Name: idx_user_stats_computed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_stats_computed ON public.user_stats USING btree (user_id, computed_at DESC);


--
-- Name: idx_user_stats_computed_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_stats_computed_desc ON public.user_stats USING btree (computed_at DESC);


--
-- Name: idx_user_stats_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_stats_user_id ON public.user_stats USING btree (user_id);


--
-- Name: idx_users_gender_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_gender_state ON public.users USING btree (gender, state);


--
-- Name: idx_users_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_state ON public.users USING btree (state);


--
-- Name: uk_friend_requests_pending_pair; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_friend_requests_pending_pair ON public.friend_requests USING btree (pair_key, pending_marker);


--
-- Name: blocks blocks_blocked_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT blocks_blocked_id_fkey FOREIGN KEY (blocked_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: blocks blocks_blocker_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT blocks_blocker_id_fkey FOREIGN KEY (blocker_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: conversations conversations_user_a_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_user_a_fkey FOREIGN KEY (user_a) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: conversations conversations_user_b_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_user_b_fkey FOREIGN KEY (user_b) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: daily_pick_views fk_daily_pick_views_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.daily_pick_views
    ADD CONSTRAINT fk_daily_pick_views_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: daily_picks fk_daily_picks_picked_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.daily_picks
    ADD CONSTRAINT fk_daily_picks_picked_user FOREIGN KEY (picked_user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: daily_picks fk_daily_picks_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.daily_picks
    ADD CONSTRAINT fk_daily_picks_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: likes fk_likes_who_got_liked; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.likes
    ADD CONSTRAINT fk_likes_who_got_liked FOREIGN KEY (who_got_liked) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: likes fk_likes_who_likes; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.likes
    ADD CONSTRAINT fk_likes_who_likes FOREIGN KEY (who_likes) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: matches fk_matches_ended_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fk_matches_ended_by FOREIGN KEY (ended_by) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: matches fk_matches_user_a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fk_matches_user_a FOREIGN KEY (user_a) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: matches fk_matches_user_b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fk_matches_user_b FOREIGN KEY (user_b) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: swipe_sessions fk_sessions_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.swipe_sessions
    ADD CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_achievements fk_user_achievements_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_achievements
    ADD CONSTRAINT fk_user_achievements_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_stats fk_user_stats_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_stats
    ADD CONSTRAINT fk_user_stats_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: friend_requests friend_requests_from_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_requests
    ADD CONSTRAINT friend_requests_from_user_id_fkey FOREIGN KEY (from_user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: friend_requests friend_requests_to_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_requests
    ADD CONSTRAINT friend_requests_to_user_id_fkey FOREIGN KEY (to_user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: messages messages_conversation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES public.conversations(id) ON DELETE CASCADE;


--
-- Name: messages messages_sender_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: notifications notifications_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: profile_notes profile_notes_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_notes
    ADD CONSTRAINT profile_notes_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: profile_notes profile_notes_subject_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_notes
    ADD CONSTRAINT profile_notes_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: profile_views profile_views_viewed_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_views
    ADD CONSTRAINT profile_views_viewed_id_fkey FOREIGN KEY (viewed_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: profile_views profile_views_viewer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_views
    ADD CONSTRAINT profile_views_viewer_id_fkey FOREIGN KEY (viewer_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: reports reports_reported_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_reported_user_id_fkey FOREIGN KEY (reported_user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: reports reports_reporter_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: standouts standouts_seeker_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.standouts
    ADD CONSTRAINT standouts_seeker_id_fkey FOREIGN KEY (seeker_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: standouts standouts_standout_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.standouts
    ADD CONSTRAINT standouts_standout_user_id_fkey FOREIGN KEY (standout_user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_db_drinking user_db_drinking_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_db_drinking
    ADD CONSTRAINT user_db_drinking_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_db_education user_db_education_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_db_education
    ADD CONSTRAINT user_db_education_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_db_looking_for user_db_looking_for_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_db_looking_for
    ADD CONSTRAINT user_db_looking_for_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_db_smoking user_db_smoking_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_db_smoking
    ADD CONSTRAINT user_db_smoking_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_db_wants_kids user_db_wants_kids_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_db_wants_kids
    ADD CONSTRAINT user_db_wants_kids_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_interested_in user_interested_in_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_interested_in
    ADD CONSTRAINT user_interested_in_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_interests user_interests_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_interests
    ADD CONSTRAINT user_interests_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_photos user_photos_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_photos
    ADD CONSTRAINT user_photos_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict 9DXJ3mwjYDFztd6z9KyEu0a33ouiV9mzOiwanT2LepBAes68dEeyqhRD4UYQhxn
