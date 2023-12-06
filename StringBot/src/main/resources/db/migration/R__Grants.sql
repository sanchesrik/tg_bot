-- ${flyway:timestamp}
GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${service_user};
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA ${flyway:defaultSchema} TO ${service_user};
