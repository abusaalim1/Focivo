-- ═══════════════════════════════════════════════════════════════════
-- Focivo — one-shot backend setup (paste ONCE in the Supabase dashboard
-- SQL editor on the MAIN project "Focivo" / dgjseiatlqimjsdhmhxn).
--
-- Verified live 2026-10-17 via REST (custom.supabase-main connector):
--   * public.study_leaderboard columns: user_id, display_name,
--     study_seconds, streak, subject_tag, last_updated,
--     weekly_study_seconds, current_week_start — avatar_url ABSENT (42703).
--   * public.squads / public.squad_members ABSENT (PGRST205).
-- DDL is not possible through any API route (Management API 403s), hence
-- this file. The app implements against this schema and degrades gracefully
-- ("squads are being set up — try again soon") while the tables are absent.
-- ═══════════════════════════════════════════════════════════════════

-- ── (a) Squads (friends competition, v1.3.0) ─────────────────────────

create table if not exists public.squads (
  id            uuid        primary key default gen_random_uuid(),
  name          text        not null,
  bio           text,
  picture_url   text,              -- remote URL or base64 data URI (owner-supplied picture)
  invite_code   text        not null unique,   -- 6-char code, e.g. "ABU7K2"
  owner_user_id text        not null,          -- Supabase auth uid of the creator
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

create table if not exists public.squad_members (
  id           uuid        primary key default gen_random_uuid(),
  squad_id     uuid        not null references public.squads (id) on delete cascade,
  user_id      text        not null,          -- Supabase auth uid
  display_name text        not null default '',
  avatar_url   text,                          -- remote URL or base64 data URI
  joined_at    timestamptz not null default now(),
  unique (squad_id, user_id)
);

create index if not exists idx_squads_invite_code  on public.squads (invite_code);
create index if not exists idx_squad_members_squad on public.squad_members (squad_id);
create index if not exists idx_squad_members_user  on public.squad_members (user_id);

-- Competition = members ranked by REAL study time from
-- public.study_leaderboard (study_seconds / weekly_study_seconds) joined on
-- user_id at read time — no per-member stats columns here.
-- The 8-member cap is enforced app-side (owner spec).

-- ── RLS ──────────────────────────────────────────────────────────────
alter table public.squads        enable row level security;
alter table public.squad_members enable row level security;

-- Anyone (including join-by-link users who have not signed in yet) can read
-- squads and their member lists.
drop policy if exists "squads_public_read" on public.squads;
create policy "squads_public_read" on public.squads
  for select using (true);

drop policy if exists "squad_members_public_read" on public.squad_members;
create policy "squad_members_public_read" on public.squad_members
  for select using (true);

-- Writes are open for insert (create / join); the app performs the real
-- authorization (owner-only delete, 8-member cap). Tighten here later if
-- the product needs it.
drop policy if exists "squads_insert" on public.squads;
create policy "squads_insert" on public.squads
  for insert with check (true);

drop policy if exists "squads_update" on public.squads;
create policy "squads_update" on public.squads
  for update using (true) with check (true);

drop policy if exists "squads_delete" on public.squads;
create policy "squads_delete" on public.squads
  for delete using (true);

drop policy if exists "squad_members_insert" on public.squad_members;
create policy "squad_members_insert" on public.squad_members
  for insert with check (true);

drop policy if exists "squad_members_delete" on public.squad_members;
create policy "squad_members_delete" on public.squad_members
  for delete using (true);

-- ── (b) Leaderboard avatar_url (v1.2.2 avatar persistence) ────────────
-- The app already writes this column best-effort (PostgREST 42703 tolerated);
-- adding it makes leaderboard + squad avatars persist for real.
alter table public.study_leaderboard
  add column if not exists avatar_url text;

-- ── (c) Leaderboard is_public (v1.3.0 profile privacy) ───────────────
-- The app writes this column best-effort (PostgREST 42703 tolerated) and
-- enforces privacy locally until it exists: private users are hidden from
-- other users' leaderboard surfaces on-device. DEFAULT true keeps every
-- legacy row public, matching the app's read-side null-means-public rule.
alter table public.study_leaderboard
  add column if not exists is_public boolean not null default true;
