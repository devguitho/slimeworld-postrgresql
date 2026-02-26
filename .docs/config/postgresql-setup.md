# PostgreSQL Setup Guide

This guide will help you set up PostgreSQL as a data source for Slime World Manager.

## Prerequisites

- PostgreSQL 9.5 or higher installed and running
- A PostgreSQL database created for SWM
- A PostgreSQL user with appropriate permissions

## Creating the Database

Connect to your PostgreSQL server and create a database:

```sql
CREATE DATABASE slimeworldmanager;
```

## Creating a User

Create a user with a password:

```sql
CREATE USER slimeworldmanager WITH PASSWORD 'your_secure_password';
```

Grant the necessary permissions:

```sql
GRANT ALL PRIVILEGES ON DATABASE slimeworldmanager TO slimeworldmanager;
```

## Configuring SWM

Edit the `sources.yml` file in your SWM plugin folder:

```yaml
postgresql:
  enabled: true
  host: 127.0.0.1
  port: 5432
  username: slimeworldmanager
  password: your_secure_password
  database: slimeworldmanager
```

## Using PostgreSQL

Once configured, you can use PostgreSQL as a data source when loading worlds:

```
/swm load <world-name> postgresql
```

## Advantages of PostgreSQL

- **Performance**: PostgreSQL offers excellent performance for read and write operations
- **Reliability**: ACID compliance ensures data integrity
- **Scalability**: Handles large amounts of data efficiently
- **Open Source**: Free and open-source with a strong community
- **Advanced Features**: Support for JSON, full-text search, and more

## Troubleshooting

### Connection Issues

If you're having trouble connecting to PostgreSQL:

1. Check that PostgreSQL is running: `systemctl status postgresql`
2. Verify the host and port in `sources.yml`
3. Ensure the user has the correct permissions
4. Check PostgreSQL's `pg_hba.conf` for connection rules

### Performance Optimization

For better performance with large worlds:

1. Increase `shared_buffers` in `postgresql.conf`
2. Adjust `work_mem` for complex queries
3. Enable connection pooling (already handled by HikariCP)
4. Consider using SSDs for PostgreSQL data directory

## Migration from MySQL

If you're migrating from MySQL to PostgreSQL, you can use the SWM migration command:

```
/swm migrate <world-name> mysql postgresql
```

This will copy the world from MySQL to PostgreSQL without any data loss.
