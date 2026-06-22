package front.search.qa.repository;

import front.search.qa.entity.SystemPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemPromptRepository extends JpaRepository<SystemPrompt, Long> {

    List<SystemPrompt> findByIsPresetTrueOrderBySortOrderAsc();

    List<SystemPrompt> findByUserIdOrderBySortOrderAsc(Long userId);

    List<SystemPrompt> findByIsPresetTrueOrUserIdOrderBySortOrderAsc(Long userId);
}
