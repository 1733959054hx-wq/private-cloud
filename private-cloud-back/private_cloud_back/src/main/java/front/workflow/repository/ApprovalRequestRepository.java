package front.workflow.repository;

import front.workflow.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByApplicantIdOrderByCreateTimeDesc(Long applicantId);

    List<ApprovalRequest> findByStatusOrderByCreateTimeDesc(Integer status);

    long countByStatus(Integer status);
}
