-- Creates the audit service's database on first container start.
--
-- The postgres image runs every .sql file in /docker-entrypoint-initdb.d, but only
-- when the data directory is empty. So this runs on a fresh `docker compose up` and
-- is skipped on every later start, which is what you want: it is not idempotent and
-- would fail on a second run.
--
-- If the finpulse-pgdata volume already exists from before this file was added, it
-- will NOT run. `docker compose down -v` removes the volume and lets it fire.
CREATE DATABASE finpulse_audit OWNER finpulse;
