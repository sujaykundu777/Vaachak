begin;

create table if not exists public.vaachak_users (
  id uuid primary key default gen_random_uuid(),
  display_name text not null check (length(btrim(display_name)) between 1 and 120),
  created_at timestamptz not null default now()
);

create table if not exists public.vaachak_preferences (
  user_id uuid primary key references public.vaachak_users(id) on delete cascade,
  preferred_language text not null default 'en' check (length(preferred_language) between 2 and 20),
  speech_rate double precision not null default 1 check (speech_rate between 0.5 and 2),
  voice text not null default '' check (length(voice) <= 100),
  theme text not null default 'system' check (theme in ('system','light','dark')),
  font_size integer not null default 18 check (font_size between 14 and 48),
  auto_read boolean not null default false,
  extra jsonb not null default '{}'::jsonb check (jsonb_typeof(extra) = 'object'),
  updated_at timestamptz not null default now()
);

create table if not exists public.vaachak_conversations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.vaachak_users(id) on delete cascade,
  title text not null check (length(btrim(title)) between 1 and 200),
  provider text not null default '' check (length(provider) <= 100),
  model text not null default '' check (length(model) <= 200),
  metadata jsonb not null default '{}'::jsonb check (jsonb_typeof(metadata) = 'object'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (id,user_id)
);

create table if not exists public.vaachak_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null,
  user_id uuid not null,
  message_index bigint generated always as identity,
  role text not null check (role in ('system','user','assistant','tool')),
  content text not null check (length(btrim(content)) between 1 and 100000),
  metadata jsonb not null default '{}'::jsonb check (jsonb_typeof(metadata) = 'object'),
  created_at timestamptz not null default now(),
  foreign key (conversation_id,user_id) references public.vaachak_conversations(id,user_id) on delete cascade
);
create index if not exists vaachak_conversations_user_updated_idx on public.vaachak_conversations(user_id,updated_at desc,id);
create index if not exists vaachak_messages_conversation_order_idx on public.vaachak_messages(user_id,conversation_id,message_index);

create or replace function public.vaachak_set_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at = now();
  return new;
end;
$$;
create or replace function public.vaachak_touch_conversation() returns trigger
language plpgsql set search_path = '' as $$
begin
  update public.vaachak_conversations set updated_at = now()
  where id = new.conversation_id and user_id = new.user_id;
  return new;
end;
$$;
drop trigger if exists vaachak_preferences_updated on public.vaachak_preferences;
create trigger vaachak_preferences_updated before update on public.vaachak_preferences
for each row execute function public.vaachak_set_updated_at();
drop trigger if exists vaachak_conversations_updated on public.vaachak_conversations;
create trigger vaachak_conversations_updated before update on public.vaachak_conversations
for each row execute function public.vaachak_set_updated_at();
drop trigger if exists vaachak_messages_touch on public.vaachak_messages;
create trigger vaachak_messages_touch after insert on public.vaachak_messages
for each row execute function public.vaachak_touch_conversation();

-- Backend-only access. No anonymous/authenticated browser policies are created.
alter table public.vaachak_users enable row level security;
alter table public.vaachak_preferences enable row level security;
alter table public.vaachak_conversations enable row level security;
alter table public.vaachak_messages enable row level security;
revoke all on public.vaachak_users,public.vaachak_preferences,public.vaachak_conversations,public.vaachak_messages from public,anon,authenticated;
grant usage on schema public to service_role;
grant select,insert,update on public.vaachak_users,public.vaachak_preferences,public.vaachak_conversations,public.vaachak_messages to service_role;
grant usage,select on sequence public.vaachak_messages_message_index_seq to service_role;
revoke all on function public.vaachak_set_updated_at() from public,anon,authenticated;
revoke all on function public.vaachak_touch_conversation() from public,anon,authenticated;
grant execute on function public.vaachak_set_updated_at(),public.vaachak_touch_conversation() to service_role;

notify pgrst, 'reload schema';
commit;
