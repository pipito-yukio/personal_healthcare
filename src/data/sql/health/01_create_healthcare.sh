#!/bin/bash
psql -Upostgres -f 10_createdb.sql
sleep 3
psql -Upostgres -d healthcare_db -f 11_createtable.sql

