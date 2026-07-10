package com.mediamarshal.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteMediaTaskSchemaMigrationTest {

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbcTemplate;
    private SqliteMediaTaskSchemaMigration migration;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("media-marshal.db").toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(dataSource);
        migration = new SqliteMediaTaskSchemaMigration(dataSource, jdbcTemplate);
    }

    @Test
    void migratesOldMediaTaskCheckConstraintsAndKeepsRows() throws Exception {
        createOldMediaTaskTable("""
                confirmation_source varchar(20) check (confirmation_source in ('AUTO_MATCH','MANUAL_SINGLE','MANUAL_BATCH')),
                status varchar(255) not null check (status in ('PENDING','PROCESSING','AWAITING_CONFIRMATION','DONE','FAILED','SKIPPED')),
                """);
        jdbcTemplate.update("""
                insert into media_task (id, source_path, status, confirmation_source, created_at)
                values (1, '/media/old.mkv', 'DONE', 'AUTO_MATCH', current_timestamp)
                """);

        migration.run(null);

        assertThat(tableSql()).contains("'MANUAL_CORRECTION'", "'CORRECTED'");
        assertThat(jdbcTemplate.queryForObject("select source_path from media_task where id = 1", String.class))
                .isEqualTo("/media/old.mkv");

        jdbcTemplate.update("""
                insert into media_task (id, source_path, status, confirmation_source, created_at)
                values (2, '/media/corrected.mkv', 'DONE', 'MANUAL_CORRECTION', current_timestamp)
                """);
        jdbcTemplate.update("""
                insert into media_task (id, source_path, status, created_at)
                values (3, '/media/history.mkv', 'CORRECTED', current_timestamp)
                """);

        assertThat(jdbcTemplate.queryForObject("select count(*) from media_task", Integer.class)).isEqualTo(3);
    }

    @Test
    void migratesWhenOnlyCorrectedStatusIsMissing() throws Exception {
        createOldMediaTaskTable("""
                confirmation_source varchar(20) check (confirmation_source in ('AUTO_MATCH','MANUAL_SINGLE','MANUAL_BATCH','MANUAL_CORRECTION')),
                corrected_at timestamp,
                status varchar(255) not null check (status in ('PENDING','PROCESSING','AWAITING_CONFIRMATION','DONE','FAILED','SKIPPED')),
                """);

        migration.run(null);

        assertThat(tableSql()).contains("'MANUAL_CORRECTION'", "'CORRECTED'");
        jdbcTemplate.update("""
                insert into media_task (id, source_path, status, confirmation_source, created_at)
                values (1, '/media/corrected.mkv', 'CORRECTED', 'MANUAL_CORRECTION', current_timestamp)
                """);
    }

    private void createOldMediaTaskTable(String checkedColumns) {
        jdbcTemplate.execute("""
                create table media_task (
                    id integer,
                    source_path varchar(255) not null,
                """ + checkedColumns + """
                    created_at timestamp not null,
                    primary key (id)
                )
                """);
    }

    private String tableSql() {
        return jdbcTemplate.queryForObject(
                "select sql from sqlite_master where type='table' and name='media_task'",
                String.class
        );
    }
}
