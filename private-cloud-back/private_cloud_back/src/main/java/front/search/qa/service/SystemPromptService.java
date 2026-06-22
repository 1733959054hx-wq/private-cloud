package front.search.qa.service;

import front.search.qa.entity.SystemPrompt;
import java.util.List;

public interface SystemPromptService {

    List<SystemPrompt> getAvailablePrompts(Long userId);

    SystemPrompt createPrompt(SystemPrompt prompt);

    SystemPrompt updatePrompt(Long id, SystemPrompt prompt, Long userId);

    void deletePrompt(Long id, Long userId);

    void initPresetPrompts();
}
