package front.system.repository;

import front.system.entity.SysSensitiveWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysSensitiveWordRepository extends JpaRepository<SysSensitiveWord, Long> {

    boolean existsByWord(String word);

    List<SysSensitiveWord> findByEnabledOrderByWord(Integer enabled);

    List<SysSensitiveWord> findByCategoryAndEnabledOrderByWord(String category, Integer enabled);

    @Query("SELECT w FROM SysSensitiveWord w WHERE w.enabled = 1 ORDER BY w.word")
    List<SysSensitiveWord> findAllEnabled();

    @Query("SELECT w FROM SysSensitiveWord w WHERE w.word LIKE %:keyword% AND w.enabled = 1 ORDER BY w.word")
    List<SysSensitiveWord> searchByKeyword(@Param("keyword") String keyword);

    void deleteByWord(String word);
}
