create database testdb;
use testdb;

create table MARIARESULT (
    id   int auto_increment primary key,
    url  varchar(255),
    date timestamp
) charset = utf8mb4;

insert into MARIARESULT values (1, "http://www.1pm.com", "2020-01-01 13:00:00");
insert into MARIARESULT values (2, "http://www.summertime.com", "2020-06-01 14:00:00");
insert into MARIARESULT values (3, "http://www.badtime.com", "2018-03-25 02:31:04");