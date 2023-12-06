create schema if not exists ${flyway:defaultSchema};
create table if not exists op_user
(
  id_user_chat bigint,
  id_user      varchar,
  constraint uk_id_user unique (id_user),
  constraint pk_id_chat primary key (id_user_chat)
);
COMMENT ON TABLE ${flyway:defaultSchema}.op_user IS 'Пользователи';
COMMENT ON COLUMN ${flyway:defaultSchema}.op_user.id_user_chat IS 'ID чата в телеграмме';
COMMENT ON COLUMN ${flyway:defaultSchema}.op_user.id_user IS 'ID пользователя';

--тип данных для события (не обязателдьно, можно просто заменить этот тип на varchar)
create type free_type as enum ('СоцДень', 'Отгул', 'Больничный','Отпуск');

--таблица где хранятся все заявки
create table if not exists ref_hilodays_requests
(
  id_request              serial,
  id_user                 varchar,
  freeday_type            free_type,
  date_start              date,
  date_end                date,
  date_return_to_work     date,
  doctor_list             integer,
  hours_time_off          integer default null,
  count_business_time_off integer,
  constraint pk_id_holl_list primary key (id_request)
);
alter table ref_hilodays_requests add constraint fk_user_id foreign key (id_user) references op_user(id_user);
COMMENT ON TABLE ${flyway:defaultSchema}.ref_hilodays_requests IS 'Запросы для утверждения';
COMMENT ON COLUMN ${flyway:defaultSchema}.ref_hilodays_requests.id_request IS 'ID запроса на утверждение';
COMMENT ON COLUMN ${flyway:defaultSchema}.ref_hilodays_requests.id_user IS 'ID пользователя';
COMMENT ON COLUMN ${flyway:defaultSchema}.ref_hilodays_requests.freeday_type IS 'Тип запроса';
COMMENT ON COLUMN ${flyway:defaultSchema}.ref_hilodays_requests.date_start IS 'Дата начала';
COMMENT ON COLUMN ${flyway:defaultSchema}.ref_hilodays_requests.date_end IS 'Дата окончания';
COMMENT ON COLUMN ${flyway:defaultSchema}.ref_hilodays_requests.date_return_to_work IS 'Дата выхода на работу';
COMMENT ON COLUMN ${flyway:defaultSchema}.ref_hilodays_requests.doctor_list IS 'Больничный лист';
COMMENT ON COLUMN ${flyway:defaultSchema}.ref_hilodays_requests.hours_time_off IS 'Часы отгула';
COMMENT ON COLUMN ${flyway:defaultSchema}.ref_hilodays_requests.count_business_time_off IS 'Количество рабочих дней для учета';
--таблица в которую будет вписываться номер заявки и пользователи которые должны подтвердить заявку
create table if not exists op_coordination
(
  id                        serial,
  id_request                integer,
  id_user_for_coordination  varchar,
  answer_status             boolean  default null,
  comment                   varchar,
  constraint pk_id_coord primary key (id)
);
alter table op_coordination add constraint fk_id_request foreign key (id_request) references ref_hilodays_requests(id_request);
COMMENT ON TABLE ${flyway:defaultSchema}.op_coordination IS 'Перечень кто и что утверждает';
COMMENT ON COLUMN ${flyway:defaultSchema}.op_coordination.id IS 'ID запроса на утверждение для определенного человек';
COMMENT ON COLUMN ${flyway:defaultSchema}.op_coordination.id_request IS 'ID запроса на утверждение';
COMMENT ON COLUMN ${flyway:defaultSchema}.op_coordination.id_user_for_coordination IS 'ID пользователя который должен утвердить';
COMMENT ON COLUMN ${flyway:defaultSchema}.op_coordination.answer_status IS 'Статус утверждения';
COMMENT ON COLUMN ${flyway:defaultSchema}.op_coordination.comment IS 'Комментарий';
--таблица для учета переработок по часам
create table if not exists op_overtime_work
(
  id            serial,
  id_user       varchar,
  date_overtime date,
  count_hours   integer,
  constraint pk_id_overtime primary key (id)
);
alter table op_overtime_work add constraint fk_overtime_user foreign key (id_user) references op_user(id_user);
COMMENT ON TABLE ${flyway:defaultSchema}.op_overtime_work IS 'Перечень переработок по часам';
COMMENT ON COLUMN ${flyway:defaultSchema}.op_overtime_work.id IS 'ID записи';
COMMENT ON COLUMN ${flyway:defaultSchema}.op_overtime_work.id_user IS 'ID пользователя';
COMMENT ON COLUMN ${flyway:defaultSchema}.op_overtime_work.date_overtime IS 'Дата переработки';
COMMENT ON COLUMN ${flyway:defaultSchema}.op_overtime_work.count_hours IS 'Дата переработки (часы)';
--таблица со всеми праздниками и выходными
create table if not exists d_holidays
(
  holiday_date date,
  comment character varying(1000),
  constraint d_holidays_pk primary key (holiday_date)
);
create unique index d_holidays_pk_idx
  on d_holidays
  using btree
  (holiday_date);
COMMENT ON TABLE ${flyway:defaultSchema}.d_holidays IS 'Перечень выходных и праздников';
COMMENT ON COLUMN ${flyway:defaultSchema}.d_holidays.holiday_date IS 'Дата';
COMMENT ON COLUMN ${flyway:defaultSchema}.d_holidays.comment IS 'Название';

--представление в котором хранится у какого пользователя сколько осталось соцдней, дней отпуска и часов отгула с учетом того, что они были согласованы
create or replace view vw_vacation_balance as
with sum_vacation as (select sum(req.count_business_time_off)as count_vacation
                           , req.id_user
                        from ref_hilodays_requests req
                        join op_coordination op_coord
                          on op_coord.id_request=req.id_request
                       where not exists (select 1
                                           from op_coordination
                                          where id_request = op_coord.id_request
                                            and (answer_status = false or answer_status is null)
                                        )
                         and req.freeday_type = 'Отпуск'
                       group by req.id_user),
     sum_soc_day as (select sum(req.count_business_time_off)as count_soc_day
                          , req.id_user
                       from ref_hilodays_requests req
                       join op_coordination op_coord
                         on op_coord.id_request=req.id_request
                      where not exists (select 1
                                          from op_coordination
                                         where id_request = op_coord.id_request
                                           and (answer_status = false or answer_status is null)
                                       )
                        and req.freeday_type = 'СоцДень'
                      group by req.id_user),
     sum_time_off as (select sum(req.hours_time_off) as count_hours
                           , req.id_user
                        from ref_hilodays_requests req
                        join op_coordination op_coord
                          on op_coord.id_request=req.id_request
                       where not exists (select 1
                                           from op_coordination
                                          where id_request = op_coord.id_request
                                            and (answer_status = false or answer_status is null)
                                        )
                         and req.freeday_type = 'Отгул'
                       group by req.id_user)
select usr.id_user
    , 28 - COALESCE(sv.count_vacation,0) as vacation_balance
    , 7  - COALESCE(scd.count_soc_day,0) as socdays_balance
    , coalesce((select sum(count_hours)
                  from op_overtime_work
                 where id_user = usr.id_user),0) - coalesce(sto.count_hours,0) as overtime_balance
  from op_user usr
  left join sum_vacation sv
    on sv.id_user = usr.id_user
  left join sum_soc_day scd
    on scd.id_user = usr.id_user
  left join sum_time_off sto
    on sto.id_user = usr.id_user;

COMMENT ON view ${flyway:defaultSchema}.vw_vacation_balance IS 'Банк по дням для отпусков, соцдней и переработок';
COMMENT ON COLUMN ${flyway:defaultSchema}.vw_vacation_balance.id_user IS 'ID пользователя';
COMMENT ON COLUMN ${flyway:defaultSchema}.vw_vacation_balance.vacation_balance IS 'Оставшееся количество дней отпуска';
COMMENT ON COLUMN ${flyway:defaultSchema}.vw_vacation_balance.socdays_balance IS 'Оставшееся количество соцдней';
COMMENT ON COLUMN ${flyway:defaultSchema}.vw_vacation_balance.overtime_balance IS 'Оставшееся количество часов для отгула';

--функция дял подсчета сколько рабочих дней нужно засчитать
create or replace function f_count_business_days(p_from_date date, p_to_date   date)
returns bigint
as $fbd$
  select count(d::date) as d
    from generate_series(p_from_date, p_to_date, '1 day'::interval) d
   where d not in (select holiday_date from d_holidays)
$fbd$ language sql;

create or replace function f_find_return_to_work_date(p_date_start date)
 returns date
 language plpgsql
as $$
declare
  actual_date date := p_date_start;
begin
  loop
    actual_date := actual_date + 1;
      if not exists (select 1 from d_holidays where holiday_date = actual_date) then
        return actual_date;
      end if;
  end loop;
end;
$$;

--триггер для изменения даты выхода на работу после отдыха и пересчета количества дней
create or replace function f_edit_return_date_and_countbuisness_time_off()
 returns trigger
 language plpgsql
as $$
begin
  new.date_return_to_work = f_find_return_to_work_date(new.date_end);
  case
    when new.freeday_type = 'Отгул' and new.hours_time_off < 8 then
      new.count_business_time_off = 0;
    else
      new.count_business_time_off = f_count_business_days(new.date_start, new.date_end);
  end case;
  return new;
end;
$$;;

create trigger update_business_days_and_insert_return_date
  before insert or update
  on ref_hilodays_requests
  for each row
  execute function f_edit_return_date_and_countbuisness_time_off();

--функция для подсчета количества пересечений интересующего отдыха с другими пользователями, указанными в массиве
create or replace function f_check_vacation_intersection(p_id_request         integer
                                                       , p_users_id_for_check varchar[])
 returns table(id_request_intersection integer
             , user_id                 varchar
             , type_holliday           free_type
             , date_start              date
             , date_end                date
             , date_return_to_work     date
             )
 language plpgsql
 security definer
as $$
begin
  return query
  select ref_hol_for_check.id_request
       , ref_hol_for_check.id_user
       , ref_hol_for_check.freeday_type
       , ref_hol_for_check.date_start
       , ref_hol_for_check.date_end
       , ref_hol_for_check.date_return_to_work
    from ref_hilodays_requests ref_hol_for_id
    join ref_hilodays_requests ref_hol_for_check
      on ref_hol_for_id.id_request != ref_hol_for_check.id_request
   where ref_hol_for_id.id_request = p_id_request
     and tsrange(ref_hol_for_id.date_start, ref_hol_for_id.date_end) && tsrange(ref_hol_for_check.date_start, ref_hol_for_check.date_end) = true
     and ref_hol_for_check.id_user = any(p_users_id_for_check);
end;
$$;

--функция для добавления отдыха в таблицу
create or replace function f_add_event_day_off(p_id_user        varchar
                                             , p_freeday_type   free_type
                                             , p_date_start     date
                                             , p_date_end       date    default null::date
                                             , p_doc_list       integer default null::integer
                                             , p_hours_time_off integer default null::integer)
 returns void
 language plpgsql
 security definer
as $$
declare
    p_calc_end_date date;
begin
  case
    when p_hours_time_off is not null then
      p_calc_end_date := f_find_return_to_work_date(p_date_start + p_hours_time_off/8);
    when p_date_end is null then
      p_calc_end_date := f_find_return_to_work_date(p_date_start);
    else
      p_calc_end_date := f_find_return_to_work_date(p_date_end);
  end case;
  insert into ref_hilodays_requests(id_user
                                  , freeday_type
                                  , date_start
                                  , date_end
                                  , date_return_to_work
                                  , doctor_list
                                  , hours_time_off)
  select p_id_user
       , p_freeday_type
       , p_date_start
       , case
           when p_hours_time_off is not null then
             p_date_start + p_hours_time_off/8
           else coalesce(p_date_start, p_date_end)
         end
       , p_calc_end_date
       , p_doc_list
       , p_hours_time_off;
end;
$$;

--функция для добавления записей в таблицу с согласованиями по id запроса и с передачей в нее массива с id полтзователей
create or replace function f_insert_data_op_coordination(p_id_request               integer
                                                       , p_id_user_for_coordination varchar[])
returns void
as $$
begin
  for i in array_lower(p_id_user_for_coordination, 1) .. array_upper(p_id_user_for_coordination, 1)
  loop
    insert into op_coordination ( id_request
                                , id_user_for_coordination)
    values (p_id_request, p_id_user_for_coordination[i]);
  end loop;
end;
$$ language plpgsql;


do
$$
declare
    calc_year integer:=2023; --Указанный год
    begin_date date; --переменная для начальной даты года
    end_date date; --Переменная для конечной даты годы
    dow_value integer; --номер дня недели. Пн-1,...,Сб-6,Вс-0
begin
    --Проверка года на коорректность
    if calc_year between 1988 and 2099 then
        begin_date :=to_date('01.01.'||calc_year, 'dd.mm.yyyy');
        end_date :=to_date('31.12.'||calc_year, 'dd.mm.yyyy');

        while begin_date<=end_date
        loop
            raise info '%',to_char(begin_date,'dd.mm.yyyy');
            begin_date:=begin_date+interval '1 day';
            dow_value:=extract(dow from begin_date);
            if dow_value in(0,6) then
                insert into d_holidays(holiday_date,comment)
                        values(begin_date,
                            case when dow_value=6 then 'Суббота' else 'Воскресенье' end);
            end if;
        end loop;
        /*Блок праздников и переносов*/
        insert into d_holidays(holiday_date,comment)
            values(to_date('01.01.2023','dd.mm.yyyy'),'Новогодние каникулы')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
        insert into d_holidays(holiday_date,comment)
            values(to_date('02.01.2023','dd.mm.yyyy'),'Новогодние каникулы')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
        insert into d_holidays(holiday_date,comment)
            values(to_date('03.01.2023','dd.mm.yyyy'),'Новогодние каникулы')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
        insert into d_holidays(holiday_date,comment)
            values(to_date('04.01.2023','dd.mm.yyyy'),'Новогодние каникулы')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
        insert into d_holidays(holiday_date,comment)
            values(to_date('05.01.2023','dd.mm.yyyy'),'Новогодние каникулы')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
        insert into d_holidays(holiday_date,comment)
            values(to_date('06.01.2023','dd.mm.yyyy'),'Новогодние каникулы')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
        insert into d_holidays(holiday_date,comment)
            values(to_date('07.01.2023','dd.mm.yyyy'),'Новогодние каникулы')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
        insert into d_holidays(holiday_date,comment)
            values(to_date('08.01.2023','dd.mm.yyyy'),'Новогодние каникулы')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;


        insert into d_holidays(holiday_date,comment)
            values(to_date('23.02.2023','dd.mm.yyyy'),'День защитника отечества')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;


        insert into d_holidays(holiday_date,comment)
            values(to_date('08.03.2023','dd.mm.yyyy'),'Международный женский день')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;

        delete from d_holidays where holiday_date=to_date('27.04.2014','dd.mm.yyyy');
        insert into d_holidays(holiday_date,comment)
            values(to_date('29.04.2023','dd.mm.yyyy'),'Перенос с 27.04.2023')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
        insert into d_holidays(holiday_date,comment)
            values(to_date('30.04.2023','dd.mm.yyyy'),'Перенос с 02.11.2023')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;

        insert into d_holidays(holiday_date,comment)
            values(to_date('01.05.2023','dd.mm.yyyy'),'Праздник весны и труда')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
        insert into d_holidays(holiday_date,comment)
            values(to_date('09.05.2023','dd.mm.yyyy'),'Праздник победы')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
        insert into d_holidays(holiday_date,comment)
            values(to_date('10.05.2023','dd.mm.yyyy'),'Перенос с 06.01.2023')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;

        insert into d_holidays(holiday_date,comment)
            values(to_date('12.06.2023','dd.mm.yyyy'),'День России')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;

        insert into d_holidays(holiday_date,comment)
            values(to_date('04.11.2023','dd.mm.yyyy'),'День народного единства')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;


        insert into d_holidays(holiday_date,comment)
            values(to_date('30.12.2023','dd.mm.yyyy'),'Перенос с 28.12.2023')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
        insert into d_holidays(holiday_date,comment)
            values(to_date('31.12.2023','dd.mm.yyyy'),'Перенос с 07.01.2023')
            on conflict(holiday_date) do update set comment=EXCLUDED.comment;
       /*Блок праздников и переносов*/


    end if;
end$$;