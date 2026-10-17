-- ═══════════════════════════════════════════════════════════════════
-- Focivo 1.3.0 — Squads (friends competition) backend schema
-- Main Supabase project: dgjseiatlqimjsdhmhxn
-- Status 2026-10-17: Management-API DDL blocked (token has no database_read
-- on the main project) — apply this file manually in the Supabase dashboard
-- SQL editor. The app implements against this schema and shows a graceful
-- "squads are being set up — try again soon" state while the tables are
-- absent (PostgREST 42P01 / 404).
-- ═══════════════════════════════════════════════════════════════════

create table if not exists public.squads (
  id           uuid        primary key default gen_random_uuid(),
  name         text        not null,
  bio          text,
  picture_url  text,            -- remote URL or base64 data URI (owner-supplied picture)
  invite_code  text        not null unique,   -- 6-char code, e.g. "ABU7K2"
  owner_user_id text       not null,          -- Supabase auth uid of the creator
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create table if not exists public.squad_members (
  id           uuid        primary key default gen_random_uuid(),
  squad_id     uuid        not null references public.squads (id) on delete cascade,
  user_id      text        not null,          -- Supabase auth uid
  display_name text        not null default '',
  avatar_url   text,
  joined_at    timestamptz not null default now(),
  unique (squad_id, user_id)
);

create index if not exists idx_squads_invite_code  on public.squads (invite_code);
create index if not exists idx_squad_members_squad on public.squad_members (squad_id);
create index if not exists idx_squad_members_user  on public.squad_members (user_id);

-- Competition = members ranked by REAL study time/points from
-- public.study_leaderboard (study_seconds / weekly_study_seconds), so no
-- per-member stats columns live here — the app joins on user_id at read
-- time. The 8-member cap is enforced app-side (owner spec).

-- ── RLS ──────────────────────────────────────────────────────────────
alter table public.squads        enable row level security;
alter table public.squad_members enable row level security;

-- Anyone (including join-by-link users who haven't signed in yet) can read
-- squads and their member lists.
drop policy if exists "squads_public_read" on public.squads;
create policy "squads_public_read" on public.squads
  for select using (true);

drop policy if exists "squad_members_public_read" on public.squad_members;
create policy "squad_members_public_read" on public.squad_members
  for select using (true);

-- Writes are open for insert (create / join); the app performs the real
-- authorization (owner-only delete, 8-member cap). Tighten here later if the
-- product needs it.
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
