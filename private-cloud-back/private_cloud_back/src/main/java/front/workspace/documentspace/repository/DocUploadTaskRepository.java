package front.workspace.documentspace.repository;

import front.workspace.documentspace.entity.DocUploadTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocUploadTaskRepository extends JpaRepository<DocUploadTask, Long> {

    Optional<DocUploadTask> findByFileIdAndStatus(String fileId, Integer status);

    Optional<DocUploadTask> findByFileIdAndUploaderIdAndStatus(String fileId, Long uploaderId, Integer status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE DocUploadTask t SET t.receivedChunks = t.receivedChunks + 1 WHERE t.id = :taskId")
    int incrementReceivedChunks(@Param("taskId") Long taskId);
}
