package front.intelligence.ai.dto;

import java.util.List;
import java.util.Map;

public class ChatRequestDTO {

    private List<Map<String, String>> messages;
    private String model;
    private Long fileId;
    private String systemPrompt;
    private String modeLabel;

    public List<Map<String, String>> getMessages() { return messages; }
    public void setMessages(List<Map<String, String>> messages) { this.messages = messages; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getModeLabel() { return modeLabel; }
    public void setModeLabel(String modeLabel) { this.modeLabel = modeLabel; }
}