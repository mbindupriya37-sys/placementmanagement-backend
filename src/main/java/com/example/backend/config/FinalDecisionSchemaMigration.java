package com.example.backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/** Removes a stale unique index left by an older one-to-one decider mapping. */
@Component
public class FinalDecisionSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public FinalDecisionSchemaMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!metadata.getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
            Set<String> nonUniqueIndexes = new HashSet<>();
            Set<String> staleUniqueIndexes = new HashSet<>();
            try (ResultSet indexes = metadata.getIndexInfo(null, connection.getSchema(), "final_decision_records", false, false)) {
                while (indexes.next()) {
                    String indexName = indexes.getString("INDEX_NAME");
                    String columnName = indexes.getString("COLUMN_NAME");
                    boolean nonUnique = indexes.getBoolean("NON_UNIQUE");
                    if (indexName != null && columnName != null
                            && columnName.equalsIgnoreCase("decided_by_user_id")
                            && !indexName.equalsIgnoreCase("PRIMARY")) {
                        if (nonUnique) {
                            nonUniqueIndexes.add(indexName);
                        } else {
                            staleUniqueIndexes.add(indexName);
                        }
                    }
                }
            }
            if (!staleUniqueIndexes.isEmpty() && nonUniqueIndexes.isEmpty()) {
                jdbcTemplate.execute("ALTER TABLE final_decision_records "
                        + "ADD INDEX idx_final_decision_decider (decided_by_user_id)");
            }
            for (String indexName : staleUniqueIndexes) {
                jdbcTemplate.execute("ALTER TABLE final_decision_records DROP INDEX `"
                        + indexName.replace("`", "``") + "`");
            }
        }
    }
}
