#!/bin/bash

# postgres-12 container
cd /home/pi/data/sql/health
psql -Upostgres < 10_createdb.sql

