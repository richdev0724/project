\set ON_ERROR_STOP on

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'marketmono_monitor') THEN
        CREATE ROLE marketmono_monitor LOGIN;
    END IF;
END
$$;

ALTER ROLE marketmono_monitor WITH PASSWORD :'monitor_password';
GRANT pg_monitor TO marketmono_monitor;
GRANT CONNECT ON DATABASE marketmono TO marketmono_monitor;

\connect marketmono

GRANT USAGE ON SCHEMA public TO marketmono_monitor;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO marketmono_monitor;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO marketmono_monitor;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'timescaledb_information') THEN
        GRANT USAGE ON SCHEMA timescaledb_information TO marketmono_monitor;
        GRANT SELECT ON ALL TABLES IN SCHEMA timescaledb_information TO marketmono_monitor;
    END IF;
END
$$;