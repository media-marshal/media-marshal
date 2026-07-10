package com.mediamarshal.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class SqliteMediaTaskSchemaMigration implements ApplicationRunner {

    private static final String CURRENT_MEDIA_TASK_DDL = """
            create table media_task (
                confirmed_year integer,
                failure_count integer,
                match_confidence float,
                parsed_episode integer,
                parsed_episode_end integer,
                parsed_season integer,
                parsed_year integer,
                corrected_at timestamp,
                corrected_from_task_id bigint,
                corrected_to_task_id bigint,
                created_at timestamp not null,
                id integer,
                last_failed_at timestamp,
                rule_id bigint,
                tmdb_id bigint,
                updated_at timestamp,
                confirmed_country varchar(10),
                confirmation_source varchar(20) check (confirmation_source in ('AUTO_MATCH','MANUAL_SINGLE','MANUAL_BATCH','MANUAL_CORRECTION')),
                asset_type varchar(30) check (asset_type in ('VIDEO_FILE','ISO_IMAGE','BLURAY_DIRECTORY')),
                error_code varchar(50) check (error_code in ('TARGET_CONFLICT','UNSAFE_TARGET_PATH','SOURCE_MISSING','PIPELINE_FAILED')),
                confirmed_genre1 varchar(100),
                confirmed_genre2 varchar(100),
                confirmed_genre3 varchar(100),
                confirmed_genre4 varchar(100),
                confirmed_episode_title varchar(500),
                skip_reason varchar(500),
                confirmed_original_title varchar(255),
                confirmed_title varchar(255),
                error_message TEXT,
                media_type varchar(255) check (media_type in ('MOVIE','TV_SHOW')),
                operation_type varchar(255),
                parsed_codec varchar(255),
                parsed_release_group varchar(255),
                parsed_resolution varchar(255),
                parsed_title varchar(255),
                source_path varchar(255) not null,
                status varchar(255) not null check (status in ('PENDING','PROCESSING','AWAITING_CONFIRMATION','DONE','FAILED','SKIPPED','CORRECTED')),
                target_path varchar(255),
                primary key (id)
            )
            """;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String tableSql = readMediaTaskTableSql();
        if (tableSql == null || isAdr028Schema(tableSql)) {
            return;
        }

        log.info("Migrating media_task CHECK constraints for ADR-028 correction states");
        migrateMediaTaskTable();
    }

    private String readMediaTaskTableSql() {
        return jdbcTemplate.query(
                "select sql from sqlite_master where type='table' and name='media_task'",
                rs -> rs.next() ? rs.getString(1) : null
        );
    }

    private boolean isAdr028Schema(String tableSql) {
        String normalized = tableSql.toUpperCase(Locale.ROOT);
        return normalized.contains("'MANUAL_CORRECTION'") && normalized.contains("'CORRECTED'");
    }

    private void migrateMediaTaskTable() throws SQLException {
        String backupTable = "media_task_backup_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            try (Statement statement = connection.createStatement()) {
                statement.execute("pragma foreign_keys=off");
                connection.setAutoCommit(false);
                statement.execute("alter table media_task rename to " + backupTable);
                statement.execute(CURRENT_MEDIA_TASK_DDL);

                List<String> copyColumns = intersection(
                        tableColumns(connection, backupTable),
                        tableColumns(connection, "media_task")
                );
                if (!copyColumns.isEmpty()) {
                    String columns = String.join(",", copyColumns);
                    statement.execute("insert into media_task (" + columns + ") select " + columns + " from " + backupTable);
                }

                statement.execute("drop table " + backupTable);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("pragma foreign_keys=on");
                }
            }
        }
    }

    private List<String> tableColumns(Connection connection, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("pragma table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        return columns;
    }

    private List<String> intersection(List<String> sourceColumns, List<String> targetColumns) {
        Set<String> targetSet = new HashSet<>(targetColumns);
        return sourceColumns.stream()
                .filter(targetSet::contains)
                .toList();
    }
}
