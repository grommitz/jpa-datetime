
create table DBTHING (
    id   int auto_increment primary key,
    url  varchar(255),
    time    datetime DEFAULT NULL,
    calTime datetime DEFAULT NULL
) charset = utf8mb4;

insert into DBTHING values (1, "http://www.1pm.com", "2020-01-01 13:00:00", "2020-01-01 13:00:00");
insert into DBTHING values (2, "http://www.summertime.com", "2020-06-01 14:00:00", "2020-06-01 14:00:00");
insert into DBTHING values (3, "http://www.badtime.com", "2018-03-25 01:31:04", "2018-03-25 01:31:04");
insert into DBTHING values (4, "http://www.timechange.com", "2018-03-22 01:30:00", "2018-03-22 01:30:00");

