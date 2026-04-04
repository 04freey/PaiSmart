package com.yizhaoqi.smartpai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时为 document_vectors 补齐常用索引。
 */
@Component
public class DocumentVectorIndexInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DocumentVectorIndexInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public DocumentVectorIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            if (!tableExists("document_vectors")) {
                logger.warn("表 document_vectors 尚不存在，跳过索引初始化");
                return;
            }

            createIndexIfAbsent(
                    "document_vectors",
                    "idx_document_vectors_file_chunk",
                    "CREATE INDEX idx_document_vectors_file_chunk ON document_vectors (file_md5, chunk_id)"
            );
            createIndexIfAbsent(
                    "document_vectors",
                    "idx_document_vectors_acl_user",
                    "CREATE INDEX idx_document_vectors_acl_user ON document_vectors (user_id)"
            );
            createIndexIfAbsent(
                    "document_vectors",
                    "idx_document_vectors_acl_org_public",
                    "CREATE INDEX idx_document_vectors_acl_org_public ON document_vectors (org_tag, is_public)"
            );
            createIndexIfAbsent(
                    "document_vectors",
                    "idx_document_vectors_acl_public",
                    "CREATE INDEX idx_document_vectors_acl_public ON document_vectors (is_public)"
            );
            ensureFullTextIndex();
        } catch (Exception e) {
            logger.warn("初始化 document_vectors 索引时出现异常，将继续启动: {}", e.getMessage());
        }
    }

    private void ensureFullTextIndex() {
        if (indexExists("document_vectors", "ft_document_vectors_text_ngram")
                || indexExists("document_vectors", "ft_document_vectors_text")) {
            return;
        }

        try {
            jdbcTemplate.execute("CREATE FULLTEXT INDEX ft_document_vectors_text_ngram ON document_vectors (text_content) WITH PARSER ngram");
            logger.info("已创建全文索引 ft_document_vectors_text_ngram（ngram）");
        } catch (Exception firstEx) {
            logger.warn("创建 ngram 全文索引失败，将尝试普通 FULLTEXT: {}", firstEx.getMessage());
            try {
                jdbcTemplate.execute("CREATE FULLTEXT INDEX ft_document_vectors_text ON document_vectors (text_content)");
                logger.info("已创建全文索引 ft_document_vectors_text");
            } catch (Exception secondEx) {
                logger.warn("创建普通 FULLTEXT 也失败，文本搜索将回退到 LIKE 方案: {}", secondEx.getMessage());
            }
        }
    }

    private void createIndexIfAbsent(String tableName, String indexName, String ddl) {
        if (indexExists(tableName, indexName)) {
            return;
        }
        jdbcTemplate.execute(ddl);
        logger.info("已创建索引 {} on {}", indexName, tableName);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class,
                tableName,
                indexName
        );
        return count != null && count > 0;
    }
}
