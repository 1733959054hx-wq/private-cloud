package front.workspace.documentspace.repository;

import front.workspace.documentspace.entity.DocFile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface DocFileRepository extends JpaRepository<DocFile, Long> {

    @Query(value = "SELECT id, file_name AS fileName, file_type AS fileType, uploader_name AS uploaderName, file_size AS fileSize, create_time AS createTime FROM doc_file WHERE deleted = 0 ORDER BY create_time DESC", nativeQuery = true)
    List<Map<String, Object>> listAllDocuments();

    List<DocFile> findByDirectoryIdAndDeletedAndStatusOrderByCreateTimeDesc(Long directoryId, Integer deleted, Integer status);

    org.springframework.data.domain.Page<DocFile> findByDirectoryIdAndDeletedAndStatusOrderByCreateTimeDesc(Long directoryId, Integer deleted, Integer status, Pageable pageable);

    List<DocFile> findByDirectoryIdIsNullAndDeletedAndStatusOrderByCreateTimeDesc(Integer deleted, Integer status);

    org.springframework.data.domain.Page<DocFile> findByDirectoryIdIsNullAndDeletedAndStatusOrderByCreateTimeDesc(Integer deleted, Integer status, Pageable pageable);

    List<DocFile> findByUploaderIdAndDeletedAndStatusOrderByCreateTimeDesc(Long uploaderId, Integer deleted, Integer status);

    List<DocFile> findByDeletedAndStatus(Integer deleted, Integer status);

    long countByDeletedAndStatus(Integer deleted, Integer status);

    @Query("SELECT COUNT(f) FROM DocFile f WHERE f.deleted = 0 AND f.status = 1 AND f.uploaderId = :uploaderId AND f.createTime >= :since")
    long countMonthlyUploads(@Param("uploaderId") Long uploaderId, @Param("since") LocalDateTime since);

    List<DocFile> findByMd5AndDeleted(String md5, Integer deleted);

    @Query("SELECT f FROM DocFile f WHERE f.deleted = 0 AND f.status = 1 AND " +
            "(f.fileName LIKE %:keyword% OR f.fulltextContent LIKE %:keyword%) " +
            "ORDER BY f.updateTime DESC")
    List<DocFile> searchByKeyword(@Param("keyword") String keyword);

    @Query(value = "SELECT * FROM doc_file WHERE deleted = 0 AND status = 1 AND " +
            "MATCH(file_name, fulltext_content) AGAINST(:keyword IN NATURAL LANGUAGE MODE) " +
            "ORDER BY update_time DESC", nativeQuery = true)
    List<DocFile> fulltextSearch(@Param("keyword") String keyword);

    @Modifying
    @Query("UPDATE DocFile f SET f.viewCount = f.viewCount + 1 WHERE f.id = :fileId")
    void incrementViewCount(@Param("fileId") Long fileId);

    @Modifying
    @Query("UPDATE DocFile f SET f.viewCount = f.viewCount + :delta WHERE f.id = :fileId")
    void incrementViewCountBy(@Param("fileId") Long fileId, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE DocFile f SET f.downloadCount = f.downloadCount + 1 WHERE f.id = :fileId")
    void incrementDownloadCount(@Param("fileId") Long fileId);

    @Modifying
    @Query("UPDATE DocFile f SET f.downloadCount = f.downloadCount + :delta WHERE f.id = :fileId")
    void incrementDownloadCountBy(@Param("fileId") Long fileId, @Param("delta") long delta);

    List<DocFile> findByUploaderIdAndDeletedOrderByCreateTimeDesc(Long uploaderId, Integer deleted);

    long countByDeletedAndStatusAndUploaderId(Integer deleted, Integer status, Long uploaderId);

    boolean existsByFileNameAndDirectoryIdAndDeleted(String fileName, Long directoryId, Integer deleted);

    boolean existsByFileNameAndDirectoryIdIsNullAndDeleted(String fileName, Integer deleted);

    @Query("SELECT f FROM DocFile f WHERE f.deleted = 0 AND f.status = 1 AND f.directoryId = :directoryId AND (f.departmentId = :departmentId OR f.departmentId IS NULL) ORDER BY f.createTime DESC")
    List<DocFile> findByDirectoryIdAndDepartmentAccessible(@Param("directoryId") Long directoryId, @Param("departmentId") Long departmentId);

    @Query("SELECT f FROM DocFile f WHERE f.deleted = 0 AND f.status = 1 AND f.directoryId IS NULL AND (f.departmentId = :departmentId OR f.departmentId IS NULL) ORDER BY f.createTime DESC")
    List<DocFile> findByRootAndDepartmentAccessible(@Param("departmentId") Long departmentId);

    @Query("SELECT f FROM DocFile f WHERE f.deleted = 0 AND f.status = 1 AND (f.departmentId = :departmentId OR f.departmentId IS NULL) AND " +
            "(f.fileName LIKE %:keyword% OR f.fulltextContent LIKE %:keyword%) " +
            "ORDER BY f.updateTime DESC")
    List<DocFile> searchByKeywordAndDepartment(@Param("keyword") String keyword, @Param("departmentId") Long departmentId);

    @Query("SELECT f FROM DocFile f WHERE f.deleted = 0 AND f.status = 1 AND f.spaceType = :spaceType AND (f.spaceId = :spaceId OR f.spaceId IS NULL) AND f.directoryId = :directoryId ORDER BY f.createTime DESC")
    List<DocFile> findBySpaceAndDirectory(@Param("spaceType") Integer spaceType, @Param("spaceId") Long spaceId, @Param("directoryId") Long directoryId);

    @Query("SELECT f FROM DocFile f WHERE f.deleted = 0 AND f.status = 1 AND f.spaceType = :spaceType AND (f.spaceId = :spaceId OR f.spaceId IS NULL) AND f.directoryId IS NULL ORDER BY f.createTime DESC")
    List<DocFile> findBySpaceAndRootDirectory(@Param("spaceType") Integer spaceType, @Param("spaceId") Long spaceId);

    boolean existsByFileNameAndDirectoryIdAndSpaceTypeAndSpaceIdAndDeleted(String fileName, Long directoryId, Integer spaceType, Long spaceId, Integer deleted);

    boolean existsByFileNameAndDirectoryIdIsNullAndSpaceTypeAndSpaceIdAndDeleted(String fileName, Integer spaceType, Long spaceId, Integer deleted);

    @Query("SELECT f FROM DocFile f WHERE f.deleted = 0 AND f.status = 1 AND f.spaceType = :spaceType AND (f.spaceId = :spaceId OR f.spaceId IS NULL) AND " +
            "(f.fileName LIKE %:keyword% OR f.fulltextContent LIKE %:keyword%) " +
            "ORDER BY f.updateTime DESC")
    List<DocFile> searchByKeywordAndSpace(@Param("keyword") String keyword, @Param("spaceType") Integer spaceType, @Param("spaceId") Long spaceId);

    List<DocFile> findByDirectoryIdInAndDeletedAndStatus(List<Long> directoryIds, Integer deleted, Integer status);

    @Query("SELECT f.fileType AS fileType, SUM(f.fileSize) AS totalSize FROM DocFile f WHERE f.uploaderId = :uploaderId AND f.deleted = 0 AND f.status = 1 GROUP BY f.fileType")
    List<Map<String, Object>> sumFileSizeByTypeForUser(@Param("uploaderId") Long uploaderId);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM DocFile f WHERE f.uploaderId = :uploaderId AND f.deleted = 0 AND f.status = 1")
    Long sumFileSizeByUser(@Param("uploaderId") Long uploaderId);

    List<DocFile> findByDepartmentIdAndDeletedAndStatusOrderByCreateTimeDesc(Long departmentId, Integer deleted, Integer status, Pageable pageable);

    // ===== 管理后台仪表盘查询 =====

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM DocFile f WHERE f.deleted = 0 AND f.status = 1")
    Long sumFileSize();

    @Query("SELECT COUNT(f) FROM DocFile f WHERE f.deleted = 0 AND f.status = 1 AND f.createTime >= :since")
    long countUploadsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM DocFile f WHERE f.deleted = 0 AND f.status = 1 AND YEAR(f.createTime) = :year AND MONTH(f.createTime) = :month")
    Long sumSizeByMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT f FROM DocFile f WHERE f.deleted = 0 AND f.status = 1 ORDER BY f.viewCount DESC")
    List<DocFile> findTopDocsByViewCount(Pageable pageable);
}
