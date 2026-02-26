package com.grinderwolf.swm.plugin.loaders.postgres;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.exceptions.WorldInUseException;
import com.grinderwolf.swm.plugin.config.DatasourcesConfig;
import com.grinderwolf.swm.plugin.loaders.LoaderUtils;
import com.grinderwolf.swm.plugin.loaders.UpdatableLoader;
import com.grinderwolf.swm.plugin.log.Logging;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PostgresLoader extends UpdatableLoader {

    // World locking executor service
    private static final ScheduledExecutorService SERVICE = Executors.newScheduledThreadPool(2, new ThreadFactoryBuilder()
            .setNameFormat("SWM PostgreSQL Lock Pool Thread #%1$d").build());

    private static final int CURRENT_DB_VERSION = 1;

    // Database version handling queries
    private static final String CREATE_VERSIONING_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS database_version (" +
            "id SERIAL PRIMARY KEY, version INTEGER);";
    private static final String INSERT_VERSION_QUERY = "INSERT INTO database_version (id, version) VALUES (1, ?) " +
            "ON CONFLICT (id) DO UPDATE SET version = EXCLUDED.version;";
    private static final String GET_VERSION_QUERY = "SELECT version FROM database_version WHERE id = 1;";

    // v1 update query
    private static final String ALTER_LOCKED_COLUMN_QUERY = "ALTER TABLE worlds ALTER COLUMN locked TYPE BIGINT;";

    // World handling queries
    private static final String CREATE_WORLDS_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS worlds (" +
            "id SERIAL PRIMARY KEY, name VARCHAR(255) UNIQUE, world BYTEA, locked BIGINT NOT NULL DEFAULT 0);";
    private static final String SELECT_WORLD_QUERY = "SELECT world, locked FROM worlds WHERE name = ?;";
    private static final String UPDATE_WORLD_QUERY = "INSERT INTO worlds (name, world, locked) VALUES (?, ?, 1) " +
            "ON CONFLICT (name) DO UPDATE SET world = EXCLUDED.world;";
    private static final String UPDATE_LOCK_QUERY = "UPDATE worlds SET locked = ? WHERE name = ?;";
    private static final String DELETE_WORLD_QUERY = "DELETE FROM worlds WHERE name = ?;";
    private static final String LIST_WORLDS_QUERY = "SELECT name FROM worlds;";

    private final Map<String, ScheduledFuture> lockedWorlds = new HashMap<>();
    private final HikariDataSource source;

    public PostgresLoader(DatasourcesConfig.PostgresConfig config) throws SQLException {
        // Carregar o driver PostgreSQL explicitamente
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC Driver not found", e);
        }
        
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl("jdbc:postgresql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName("org.postgresql.Driver");

        // Pool settings
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000); // 30 seconds
        hikariConfig.setIdleTimeout(600000); // 10 minutes
        hikariConfig.setMaxLifetime(1800000); // 30 minutes
        hikariConfig.setLeakDetectionThreshold(60000); // 1 minute

        // PostgreSQL specific settings
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("socketTimeout", "30");
        hikariConfig.addDataSourceProperty("tcpKeepAlive", "true");

        source = new HikariDataSource(hikariConfig);

        try (Connection con = source.getConnection()) {
            // Create worlds table
            try (PreparedStatement statement = con.prepareStatement(CREATE_WORLDS_TABLE_QUERY)) {
                statement.execute();
            }

            // Create versioning table
            try (PreparedStatement statement = con.prepareStatement(CREATE_VERSIONING_TABLE_QUERY)) {
                statement.execute();
            }
        }
    }

    @Override
    public void update() throws IOException, NewerDatabaseException {
        try (Connection con = source.getConnection()) {
            int version;

            try (PreparedStatement statement = con.prepareStatement(GET_VERSION_QUERY);
                 ResultSet set = statement.executeQuery()) {
                version = set.next() ? set.getInt(1) : -1;
            }

            if (version > CURRENT_DB_VERSION) {
                throw new NewerDatabaseException(CURRENT_DB_VERSION, version);
            }

            if (version < CURRENT_DB_VERSION) {
                Logging.warning("Your SWM PostgreSQL database is outdated. The update process will start in 10 seconds.");
                Logging.warning("Note that this update might make your database incompatible with older SWM versions.");
                Logging.warning("Make sure no other servers with older SWM versions are using this database.");
                Logging.warning("Shut down the server to prevent your database from being updated.");

                try {
                    Thread.sleep(10000L);
                } catch (InterruptedException ignored) {

                }

                // Update to v1: alter locked column to store a long
                try (PreparedStatement statement = con.prepareStatement(ALTER_LOCKED_COLUMN_QUERY)) {
                    statement.executeUpdate();
                }

                // Insert/update database version table
                try (PreparedStatement statement = con.prepareStatement(INSERT_VERSION_QUERY)) {
                    statement.setInt(1, CURRENT_DB_VERSION);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public byte[] loadWorld(String worldName, boolean readOnly) throws UnknownWorldException, IOException, WorldInUseException {
        Logging.info("loadWorld() called for world: " + worldName + ", readOnly: " + readOnly);
        
        int retries = 3;
        SQLException lastException = null;
        
        for (int i = 0; i < retries; i++) {
            try (Connection con = source.getConnection();
                PreparedStatement statement = con.prepareStatement(SELECT_WORLD_QUERY)) {
                statement.setString(1, worldName);
                ResultSet set = statement.executeQuery();

                if (!set.next()) {
                    Logging.warning("World " + worldName + " not found in PostgreSQL database");
                    throw new UnknownWorldException(worldName);
                }

                if (!readOnly) {
                    long lockedMillis = set.getLong("locked");

                    if (System.currentTimeMillis() - lockedMillis <= LoaderUtils.MAX_LOCK_TIME) {
                        throw new WorldInUseException(worldName);
                    }

                    Logging.info("Acquiring lock for world: " + worldName);
                    updateLock(worldName, true);
                } else {
                    Logging.info("Loading world " + worldName + " in READ-ONLY mode (no lock acquired)");
                }

                byte[] worldData = set.getBytes("world");
                Logging.info("Successfully loaded world " + worldName + " from PostgreSQL (" + worldData.length + " bytes)");
                return worldData;
            } catch (SQLException ex) {
                lastException = ex;
                if (i < retries - 1) {
                    Logging.warning("Failed to load world " + worldName + " from PostgreSQL (attempt " + (i + 1) + "/" + retries + "), retrying...");
                    try {
                        Thread.sleep(1000); // Wait 1 second before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        
        throw new IOException("Failed to load world after " + retries + " attempts", lastException);
    }

    private void updateLock(String worldName, boolean forceSchedule) {
        try (Connection con = source.getConnection();
             PreparedStatement statement = con.prepareStatement(UPDATE_LOCK_QUERY)) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, worldName);

            statement.executeUpdate();
        } catch (SQLException ex) {
            Logging.error("Failed to update the lock for world " + worldName + ":");
            ex.printStackTrace();
        }

        if (forceSchedule || lockedWorlds.containsKey(worldName)) {
            lockedWorlds.put(worldName, SERVICE.schedule(() -> updateLock(worldName, false), LoaderUtils.LOCK_INTERVAL, TimeUnit.MILLISECONDS));
        }
    }

    @Override
    public boolean worldExists(String worldName) throws IOException {
        try (Connection con = source.getConnection();
             PreparedStatement statement = con.prepareStatement(SELECT_WORLD_QUERY)) {
            statement.setString(1, worldName);
            ResultSet set = statement.executeQuery();

            return set.next();
        } catch (SQLException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public List<String> listWorlds() throws IOException {
        List<String> worldList = new ArrayList<>();

        try (Connection con = source.getConnection();
             PreparedStatement statement = con.prepareStatement(LIST_WORLDS_QUERY)) {
            ResultSet set = statement.executeQuery();

            while (set.next()) {
                worldList.add(set.getString("name"));
            }
        } catch (SQLException ex) {
            throw new IOException(ex);
        }

        return worldList;
    }

    @Override
    public void saveWorld(String worldName, byte[] serializedWorld, boolean lock) throws IOException {
        try (Connection con = source.getConnection();
             PreparedStatement statement = con.prepareStatement(UPDATE_WORLD_QUERY)) {
            statement.setString(1, worldName);
            statement.setBytes(2, serializedWorld);
            statement.executeUpdate();

            if (lock) {
                updateLock(worldName, true);
            }
        } catch (SQLException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void unlockWorld(String worldName) throws IOException, UnknownWorldException {
        Logging.info("unlockWorld() called for world: " + worldName);
        
        ScheduledFuture future = lockedWorlds.remove(worldName);

        if (future != null) {
            future.cancel(false);
            Logging.info("Cancelled scheduled lock update for world: " + worldName);
        }

        try (Connection con = source.getConnection();
             PreparedStatement statement = con.prepareStatement(UPDATE_LOCK_QUERY)) {
            statement.setLong(1, 0L);
            statement.setString(2, worldName);

            int rowsUpdated = statement.executeUpdate();
            if (rowsUpdated == 0) {
                Logging.warning("unlockWorld() failed - world not found in database: " + worldName);
                throw new UnknownWorldException(worldName);
            }
            
            Logging.info("World " + worldName + " successfully unlocked in PostgreSQL database");
        } catch (SQLException ex) {
            Logging.error("SQLException while unlocking world " + worldName + ": " + ex.getMessage());
            throw new IOException(ex);
        }
    }

    @Override
    public boolean isWorldLocked(String worldName) throws IOException, UnknownWorldException {
        if (lockedWorlds.containsKey(worldName)) {
            return true;
        }

        try (Connection con = source.getConnection();
             PreparedStatement statement = con.prepareStatement(SELECT_WORLD_QUERY)) {
            statement.setString(1, worldName);
            ResultSet set = statement.executeQuery();

            if (!set.next()) {
                throw new UnknownWorldException(worldName);
            }

            return System.currentTimeMillis() - set.getLong("locked") <= LoaderUtils.MAX_LOCK_TIME;
        } catch (SQLException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void deleteWorld(String worldName) throws IOException, UnknownWorldException {
        // Log the deletion with stack trace to track who's calling this
        Logging.warning("deleteWorld() called for world: " + worldName);
        Logging.warning("Stack trace:");
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            Logging.warning("  at " + element.toString());
        }
        
        ScheduledFuture future = lockedWorlds.remove(worldName);

        if (future != null) {
            future.cancel(false);
        }

        try (Connection con = source.getConnection();
             PreparedStatement statement = con.prepareStatement(DELETE_WORLD_QUERY)) {
            statement.setString(1, worldName);

            if (statement.executeUpdate() == 0) {
                throw new UnknownWorldException(worldName);
            }
            
            Logging.warning("World " + worldName + " successfully deleted from PostgreSQL database");
        } catch (SQLException ex) {
            throw new IOException(ex);
        }
    }
}
