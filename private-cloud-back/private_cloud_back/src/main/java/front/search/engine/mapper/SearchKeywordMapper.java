package front.search.engine.mapper;

import front.intelligence.search.entity.SearchKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 搜索关键词 JPA 仓库
 */
@Repository
public interface SearchKeywordMapper extends JpaRepository<SearchKeyword, Long> {

    @Query(value = "SELECT keyword FROM search_keyword WHERE keyword LIKE :prefix% AND deleted = 0 ORDER BY search_count DESC LIMIT :limit", nativeQuery = true)
    List<String> findByPrefix(@Param("prefix") String prefix, @Param("limit") int limit);

    @Query(value = "SELECT keyword FROM search_keyword WHERE is_hot = 1 AND deleted = 0 ORDER BY search_count DESC LIMIT :limit", nativeQuery = true)
    List<String> findHotKeywords(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE SearchKeyword s SET s.searchCount = s.searchCount + 1 WHERE s.keyword = :keyword AND s.deleted = 0")
    int incrementCount(@Param("keyword") String keyword);
}
