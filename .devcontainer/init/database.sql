-- Create databases
CREATE DATABASE admin;
CREATE DATABASE powerjob_product;

-- Grant privileges (if necessary, usually the default user 'admin' owns them if created by it,
-- but here we are running as the superuser usually in initdb)
GRANT ALL PRIVILEGES ON DATABASE admin TO admin;
GRANT ALL PRIVILEGES ON DATABASE powerjob_product TO admin;
