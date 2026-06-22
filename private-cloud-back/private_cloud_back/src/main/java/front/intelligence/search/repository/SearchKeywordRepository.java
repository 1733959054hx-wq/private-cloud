package front.intelligence.search.repository;

import front.intelligence.search.entity.SearchKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchKeywordRepository extends JpaRepository<SearchKeyword, Long> {

    Optional<SearchKeyword> findByKeywordAndDeleted(String keyword, Integer deleted);

    List<SearchKeyword> findByDeletedOrderBySearchCountDesc(Integer deleted);

    List<SearchKeyword> findByIsHotAndDeletedOrderBySearchCountDesc(Integer isHot, Integer deleted);

    List<SearchKeyword> findByUserIdOrderByCreateTimeDesc(Long userId);

    List<SearchKeyword> findTop10ByDeletedOrderBySearchCountDesc(Integer deleted);
}
