package front.intelligence.ai.dto;

/**
 * AI 助手快捷指令卡片 DTO
 * 由后端动态下发，前端渲染为轮播卡片
 */
public class QuickActionDTO {

    /** 唯一标识 */
    private String id;

    /** 卡片标题 */
    private String title;

    /** 卡片副标题/描述 */
    private String description;

    /** Element Plus 图标名称（如 Document、Search、Edit 等） */
    private String icon;

    /**
     * 动作类型
     * chat  - 点击后将提示词填入输入框，发送给大模型对话
     * route - 点击后直接使用 Vue Router 跳转到系统对应页面
     */
    private String actionType;

    /** 动作值：chat 类型时为提示词文本，route 类型时为路由路径 */
    private String actionValue;

    /** 排序序号（越小越靠前） */
    private Integer sortOrder;

    /** 卡片主题色（可选，CSS 颜色值） */
    private String color;

    public QuickActionDTO() {}

    public QuickActionDTO(String id, String title, String description, String icon,
                          String actionType, String actionValue, Integer sortOrder, String color) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.actionType = actionType;
        this.actionValue = actionValue;
        this.sortOrder = sortOrder;
        this.color = color;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getActionValue() { return actionValue; }
    public void setActionValue(String actionValue) { this.actionValue = actionValue; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}