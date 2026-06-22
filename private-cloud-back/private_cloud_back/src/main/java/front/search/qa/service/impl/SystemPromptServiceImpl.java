package front.search.qa.service.impl;

import front.search.qa.entity.SystemPrompt;
import front.search.qa.repository.SystemPromptRepository;
import front.search.qa.service.SystemPromptService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class SystemPromptServiceImpl implements SystemPromptService {

    @Resource
    private SystemPromptRepository systemPromptRepository;

    @Override
    public List<SystemPrompt> getAvailablePrompts(Long userId) {
        return systemPromptRepository.findByIsPresetTrueOrUserIdOrderBySortOrderAsc(userId);
    }

    @Override
    @Transactional
    public SystemPrompt createPrompt(SystemPrompt prompt) {
        prompt.setIsPreset(false);
        return systemPromptRepository.save(prompt);
    }

    @Override
    @Transactional
    public SystemPrompt updatePrompt(Long id, SystemPrompt prompt, Long userId) {
        SystemPrompt existing = systemPromptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("提示词模板不存在"));
        // 预设模板不允许修改
        if (Boolean.TRUE.equals(existing.getIsPreset())) {
            throw new RuntimeException("预设模板不允许修改");
        }
        // 只能修改自己创建的
        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此模板");
        }
        existing.setName(prompt.getName());
        existing.setLabel(prompt.getLabel());
        existing.setPromptContent(prompt.getPromptContent());
        existing.setDescription(prompt.getDescription());
        existing.setSortOrder(prompt.getSortOrder());
        return systemPromptRepository.save(existing);
    }

    @Override
    @Transactional
    public void deletePrompt(Long id, Long userId) {
        SystemPrompt existing = systemPromptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("提示词模板不存在"));
        if (Boolean.TRUE.equals(existing.getIsPreset())) {
            throw new RuntimeException("预设模板不允许删除");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此模板");
        }
        systemPromptRepository.delete(existing);
    }

    @Override
    @PostConstruct
    @Transactional
    public void initPresetPrompts() {
        // 仅在表为空时初始化预设模板
        if (systemPromptRepository.count() > 0) return;

        // 精准提取模式
        SystemPrompt precise = new SystemPrompt();
        precise.setName("精准提取");
        precise.setLabel("精准提取模式");
        precise.setPromptContent("你是一个严谨的文档分析专家。请严格基于提供的文档内容回答问题，不得自行编造或推测。" +
                "引用文档信息时必须标注来源（如【来源：文档名称】）。如果文档中没有相关内容，请直接回复'文档未提及'。" +
                "回答要精确、客观，不改变原文含义，严禁产生幻觉。");
        precise.setDescription("适合财务、合同类文档。强调不改变原意，严禁幻觉。");
        precise.setIsPreset(true);
        precise.setSortOrder(1);
        systemPromptRepository.save(precise);

        // 总结概括模式
        SystemPrompt summary = new SystemPrompt();
        summary.setName("总结概括");
        summary.setLabel("总结概括模式");
        summary.setPromptContent("你是一个高效的信息提炼专家。请对提供的文档内容进行总结概括，" +
                "提炼出核心要点和大纲结构。要求：1. 提炼出3-5个核心要点；2. 标注关键时间节点和数据；" +
                "3. 使用清晰的层级结构组织内容；4. 用简洁的语言概括每部分的主旨。");
        summary.setDescription("适合长篇报告。要求提炼大纲、提取关键时间节点。");
        summary.setIsPreset(true);
        summary.setSortOrder(2);
        systemPromptRepository.save(summary);

        // 代码解析模式
        SystemPrompt code = new SystemPrompt();
        code.setName("代码解析");
        code.setLabel("代码解析模式");
        code.setPromptContent("你是一个资深的技术文档解读专家。请对提供的技术文档进行深度解析，" +
                "要求：1. 对代码片段使用Markdown代码块高亮展示；2. 逐步解释实现原理和设计思路；" +
                "3. 指出关键的API接口和参数说明；4. 如有必要，提供使用示例；5. 分析潜在的技术风险和优化建议。");
        code.setDescription("适合技术文档。要求高亮代码块，解释实现原理。");
        code.setIsPreset(true);
        code.setSortOrder(3);
        systemPromptRepository.save(code);
    }
}
