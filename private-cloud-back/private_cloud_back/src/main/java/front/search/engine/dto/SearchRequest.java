package front.search.engine.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 搜索引擎请求DTO - 多条件交叉过滤
 */
@Data
public class SearchRequest {

    /** 搜索关键词 */
    private String keyword;

    /** 上传者 */
    private String uploader;

    /** 开始日期 */
    private LocalDateTime startDate;

    /** 结束日期 */
    private LocalDateTime endDate;

    /** 文件类型筛选（如：pdf,docx,xlsx） */
    private List<String> fileTypes;

    /** 标签筛选（按标签名过滤文档） */
    private String tag;

    /** 页码 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 20;

    /** 排序字段：score/uploadTime/fileSize */
    private String orderBy = "score";

    /** 排序方向：asc/desc */
    private String orderDir = "desc";

    // ===== 权限过滤字段（由 Controller 注入，不由前端传入） =====

    /** 当前用户ID */
    private Long currentUserId;

    /** 当前用户部门ID（null 表示未分配部门） */
    private Long currentDepartmentId;

    /** 是否为管理员 */
    private Boolean currentIsAdmin;
}
