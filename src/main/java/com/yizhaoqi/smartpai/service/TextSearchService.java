package com.yizhaoqi.smartpai.service;

import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.tokenizer.StandardTokenizer;
import com.yizhaoqi.smartpai.entity.KnowledgeChunkDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 基于 MySQL 的文本召回服务。
 * 优先走 FULLTEXT，失败时回退到 LIKE/LOCATE。
 */
@Service
public class TextSearchService {

    private static final Logger logger = LoggerFactory.getLogger(TextSearchService.class);
    private static final int MAX_TERMS = 8;
    private static final int FALLBACK_MULTIPLIER = 4;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TextSearchService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<KnowledgeChunkDocument> searchAccessible(String query, String userId, List<String> orgTags, int limit) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(userId)) {
            return Collections.emptyList();
        }
        SearchContext context = buildSearchContext(query, limit);
        if (context.terms().isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgeChunkDocument> fullTextHits = searchAccessibleByFullText(context, userId, orgTags);
        if (!fullTextHits.isEmpty()) {
            return fullTextHits;
        }
        return searchAccessibleByLike(context, userId, orgTags);
    }

    public List<KnowledgeChunkDocument> searchPublic(String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        SearchContext context = buildSearchContext(query, limit);
        if (context.terms().isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgeChunkDocument> fullTextHits = searchPublicByFullText(context);
        if (!fullTextHits.isEmpty()) {
            return fullTextHits;
        }
        return searchPublicByLike(context);
    }

    private List<KnowledgeChunkDocument> searchAccessibleByFullText(SearchContext context, String userId, List<String> orgTags) {
        StringBuilder sql = new StringBuilder("""
                SELECT vector_id, file_md5, chunk_id, text_content, model_version, user_id, org_tag, is_public,
                       MATCH(text_content) AGAINST (:naturalQuery IN NATURAL LANGUAGE MODE) AS text_score
                FROM document_vectors
                WHERE MATCH(text_content) AGAINST (:booleanQuery IN BOOLEAN MODE)
                  AND (
                        user_id = :userId
                        OR is_public = true
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("naturalQuery", context.naturalQuery())
                .addValue("booleanQuery", context.booleanQuery())
                .addValue("userId", userId)
                .addValue("limit", context.limit());

        if (!CollectionUtils.isEmpty(orgTags)) {
            sql.append(" OR org_tag IN (:orgTags)");
            params.addValue("orgTags", orgTags);
        }
        sql.append(") ORDER BY text_score DESC, chunk_id ASC LIMIT :limit");

        try {
            return jdbcTemplate.query(sql.toString(), params, this::mapRow);
        } catch (Exception e) {
            logger.debug("FULLTEXT 可访问搜索失败，准备回退 LIKE：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<KnowledgeChunkDocument> searchPublicByFullText(SearchContext context) {
        String sql = """
                SELECT vector_id, file_md5, chunk_id, text_content, model_version, user_id, org_tag, is_public,
                       MATCH(text_content) AGAINST (:naturalQuery IN NATURAL LANGUAGE MODE) AS text_score
                FROM document_vectors
                WHERE MATCH(text_content) AGAINST (:booleanQuery IN BOOLEAN MODE)
                  AND is_public = true
                ORDER BY text_score DESC, chunk_id ASC
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("naturalQuery", context.naturalQuery())
                .addValue("booleanQuery", context.booleanQuery())
                .addValue("limit", context.limit());

        try {
            return jdbcTemplate.query(sql, params, this::mapRow);
        } catch (Exception e) {
            logger.debug("FULLTEXT 公开搜索失败，准备回退 LIKE：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<KnowledgeChunkDocument> searchAccessibleByLike(SearchContext context, String userId, List<String> orgTags) {
        StringBuilder sql = new StringBuilder("""
                SELECT vector_id, file_md5, chunk_id, text_content, model_version, user_id, org_tag, is_public
                FROM document_vectors
                WHERE (
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", context.fallbackLimit());

        appendLikeTerms(sql, params, context.terms());
        sql.append(") AND (user_id = :userId OR is_public = true");
        if (!CollectionUtils.isEmpty(orgTags)) {
            sql.append(" OR org_tag IN (:orgTags)");
            params.addValue("orgTags", orgTags);
        }
        sql.append(") ORDER BY chunk_id ASC LIMIT :limit");

        return jdbcTemplate.query(sql.toString(), params, this::mapRow);
    }

    private List<KnowledgeChunkDocument> searchPublicByLike(SearchContext context) {
        StringBuilder sql = new StringBuilder("""
                SELECT vector_id, file_md5, chunk_id, text_content, model_version, user_id, org_tag, is_public
                FROM document_vectors
                WHERE (
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", context.fallbackLimit());

        appendLikeTerms(sql, params, context.terms());
        sql.append(") AND is_public = true ORDER BY chunk_id ASC LIMIT :limit");

        return jdbcTemplate.query(sql.toString(), params, this::mapRow);
    }

    private void appendLikeTerms(StringBuilder sql, MapSqlParameterSource params, List<String> terms) {
        for (int i = 0; i < terms.size(); i++) {
            String paramName = "term" + i;
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("LOWER(text_content) LIKE :").append(paramName);
            params.addValue(paramName, "%" + terms.get(i).toLowerCase(Locale.ROOT) + "%");
        }
    }

    private SearchContext buildSearchContext(String rawQuery, int limit) {
        String normalized = rawQuery == null ? "" : rawQuery.trim();
        if (!StringUtils.hasText(normalized)) {
            return new SearchContext("", "", Collections.emptyList(), Math.max(limit, 1), Math.max(limit, 1));
        }

        Set<String> terms = new LinkedHashSet<>();
        terms.add(normalized);
        try {
            List<Term> segments = StandardTokenizer.segment(normalized);
            for (Term segment : segments) {
                if (segment == null || !StringUtils.hasText(segment.word)) {
                    continue;
                }
                String token = segment.word.trim();
                if (token.length() >= 2 || token.matches("[A-Za-z0-9]{2,}")) {
                    terms.add(token);
                }
                if (terms.size() >= MAX_TERMS) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.debug("HanLP 分词失败，直接使用原始查询词：{}", e.getMessage());
        }

        List<String> orderedTerms = new ArrayList<>(terms);
        String naturalQuery = String.join(" ", orderedTerms);
        String booleanQuery = buildBooleanQuery(orderedTerms);
        int safeLimit = Math.max(limit, 1);
        return new SearchContext(naturalQuery, booleanQuery, orderedTerms, safeLimit, safeLimit * FALLBACK_MULTIPLIER);
    }

    private String buildBooleanQuery(List<String> terms) {
        List<String> fragments = new ArrayList<>();
        for (String term : terms) {
            String escaped = term.replace("\"", "\\\"");
            if (term.contains(" ")) {
                fragments.add("+\"" + escaped + "\"");
            } else {
                fragments.add("+" + escaped);
            }
        }
        return String.join(" ", fragments);
    }

    private KnowledgeChunkDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
        KnowledgeChunkDocument document = new KnowledgeChunkDocument();
        document.setId(KnowledgeChunkDocument.buildId(
                rs.getString("file_md5"),
                rs.getInt("chunk_id")
        ));
        document.setFileMd5(rs.getString("file_md5"));
        document.setChunkId(rs.getInt("chunk_id"));
        document.setTextContent(rs.getString("text_content"));
        document.setModelVersion(rs.getString("model_version"));
        document.setUserId(rs.getString("user_id"));
        document.setOrgTag(rs.getString("org_tag"));
        document.setPublic(rs.getBoolean("is_public"));

        try {
            float score = rs.getFloat("text_score");
            if (!rs.wasNull()) {
                document.setScore(score);
            }
        } catch (SQLException ignored) {
            // LIKE 回退查询没有 text_score 字段，忽略即可
        }
        return document;
    }

    private record SearchContext(
            String naturalQuery,
            String booleanQuery,
            List<String> terms,
            int limit,
            int fallbackLimit
    ) {
    }
}
