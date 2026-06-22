package front.intelligence.ai.repository;

import front.intelligence.ai.entity.DocTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocTagRepository extends JpaRepository<DocTag, Long> {

    List<DocTag> findByFileId(Long fileId);

    List<DocTag> findByTagName(String tagName);

    List<DocTag> findByTagSource(String tagSource);

    @Modifying
    void deleteByFileId(Long fileId);

    List<DocTag> findByFileIdAndTagSource(Long fileId, String tagSource);

    /** 获取所有不重复的标签名及其出现次数（用于标签云） */
    @Query("SELECT d.tagName, COUNT(d) FROM DocTag d GROUP BY d.tagName ORDER BY COUNT(d) DESC")
    List<Object[]> findTagCloud();

    /** 按标签名查找对应的所有文件ID */
    @Query("SELECT d.fileId FROM DocTag d WHERE d.tagName = :tagName")
    List<Long> findFileIdsByTagName(String tagName);
}
