\set ON_ERROR_STOP on
begin;
set local role service_role;
insert into public.vaachak_users(id,display_name) values
('123e4567-e89b-12d3-a456-426655440000','Schema test reader'),
('123e4567-e89b-12d3-a456-426655440009','Another reader');
insert into public.vaachak_preferences(user_id,preferred_language,updated_at)
values ('123e4567-e89b-12d3-a456-426655440000','en','2000-01-01');
insert into public.vaachak_preferences(user_id,preferred_language)
values ('123e4567-e89b-12d3-a456-426655440000','hi')
on conflict(user_id) do update set preferred_language=excluded.preferred_language;
insert into public.vaachak_conversations(id,user_id,title)
values ('123e4567-e89b-12d3-a456-426655440001','123e4567-e89b-12d3-a456-426655440000','Schema test');
insert into public.vaachak_messages(conversation_id,user_id,role,content)
values ('123e4567-e89b-12d3-a456-426655440001','123e4567-e89b-12d3-a456-426655440000','user','First'),
('123e4567-e89b-12d3-a456-426655440001','123e4567-e89b-12d3-a456-426655440000','assistant','Second');
do $$
begin
  if (select count(*) from public.vaachak_preferences where user_id='123e4567-e89b-12d3-a456-426655440000') <> 1 then raise exception 'Preference upsert created duplicate'; end if;
  if (select updated_at from public.vaachak_preferences where user_id='123e4567-e89b-12d3-a456-426655440000') < '2020-01-01'::timestamptz then raise exception 'Timestamp trigger failed'; end if;
  if (select array_agg(content order by message_index) from public.vaachak_messages where conversation_id='123e4567-e89b-12d3-a456-426655440001') <> array['First','Second'] then raise exception 'Message order failed'; end if;
  begin
    insert into public.vaachak_messages(conversation_id,user_id,role,content)
    values ('123e4567-e89b-12d3-a456-426655440001','123e4567-e89b-12d3-a456-426655440009','user','Wrong user');
    raise exception 'Cross-user message was allowed';
  exception when foreign_key_violation then null;
  end;
  if has_table_privilege('anon','public.vaachak_users','SELECT') then raise exception 'Anonymous users can read app users'; end if;
  if has_table_privilege('authenticated','public.vaachak_messages','INSERT') then raise exception 'Browser users can insert messages'; end if;
  if not has_table_privilege('service_role','public.vaachak_preferences','INSERT') then raise exception 'Backend grant missing'; end if;
  if (select count(*) from pg_class where relname in ('vaachak_users','vaachak_preferences','vaachak_conversations','vaachak_messages') and relrowsecurity) <> 4 then raise exception 'RLS missing'; end if;
end;
$$;
rollback;
