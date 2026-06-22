package front.workspace.documentspace.repository;

import front.workspace.documentspace.entity.DocDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocDirectoryRepository extends JpaRepository<DocDirectory, Long> {

    List<DocDirectory> findByParentIdAndDeletedOrderBySortOrder(Long parentId, Integer deleted);

    List<DocDirectory> findByDepartmentIdAndDeletedOrderBySortOrder(Long departmentId, Integer deleted);

    List<DocDirectory> findByDeletedOrderBySortOrder(Integer deleted);

    @Query("SELECT d FROM DocDirectory d WHERE d.deleted = 0 AND (d.departmentId = :departmentId OR d.departmentId IS NULL) ORDER BY d.sortOrder")
    List<DocDirectory> findByDepartmentIdOrPublic(@Param("departmentId") Long departmentId);

    long countByParentIdAndDeleted(Long parentId, Integer deleted);

    boolean existsByDirNameAndParentIdAndDeleted(String dirName, Long parentId, Integer deleted);

    @Query("SELECT d FROM DocDirectory d WHERE d.deleted = 0 AND d.spaceType = :spaceType AND (d.spaceId = :spaceId OR d.spaceId IS NULL) ORDER BY d.sortOrder")
    List<DocDirectory> findBySpaceTypeAndSpaceId(@Param("spaceType") Integer spaceType, @Param("spaceId") Long spaceId);

    boolean existsByDirNameAndParentIdAndSpaceTypeAndSpaceIdAndDeleted(String dirName, Long parentId, Integer spaceType, Long spaceId, Integer deleted);

    @Query("SELECT d FROM DocDirectory d WHERE d.deleted = 0 AND d.spaceType = :spaceType AND (d.spaceId = :spaceId OR d.spaceId IS NULL) AND d.parentId = :parentId ORDER BY d.sortOrder")
    List<DocDirectory> findBySpaceAndParentId(@Param("spaceType") Integer spaceType, @Param("spaceId") Long spaceId, @Param("parentId") Long parentId);
}
