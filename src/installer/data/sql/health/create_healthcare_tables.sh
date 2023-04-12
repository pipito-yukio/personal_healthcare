#!/bin/bash

# postgres-12
cd /home/pi/data/sql/health
psql -Udeveloper healthcare_db < 11_createtable.sql
sleep 1
# Deafult person into bodyhealth.person  
psql -Udeveloper healthcare_db < 20_insert_person.sql

