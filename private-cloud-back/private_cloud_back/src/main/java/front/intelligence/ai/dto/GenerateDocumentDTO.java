package front.intelligence.ai.dto;

import java.util.Map;

public class GenerateDocumentDTO {

    private String templateId;
    private Map<String, String> params;
    private String model;
    private String referenceContent;

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) { this.params = params; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getReferenceContent() { return referenceContent; }
    public void setReferenceContent(String referenceContent) { this.referenceContent = referenceContent; }
}
