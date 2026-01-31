package com.shiftbot.repository.sqlite;

import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteDatabase {
    private final SQLiteDataSource dataSource;

    public SqliteDatabase(String jdbcUrl) {
        this.dataSource = new SQLiteDataSource();
        this.dataSource.setUrl(jdbcUrl);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void initialize() {
        migrate();
        applyPragmas();
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void applyPragmas() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL;");
            statement.execute("PRAGMA synchronous=NORMAL;");
            statement.execute("PRAGMA busy_timeout=5000;");
            statement.execute("PRAGMA foreign_keys=ON;");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to apply SQLite pragmas", e);
        }
    }
}
