package front.search.engine.dto;

import lombok.Data;
import java.util.List;

/**
 * 搜索结果DTO - 含高亮片段
 */
@Data
public class SearchResult {

    /** 文档ID */
    private Long documentId;

    /** 文档标题（含高亮标记） */
    private String title;

    /** 匹配内容摘要 */
    private String summary;

    /** 上传者 */
    private String uploader;

    /** 文件类型 */
    private String fileType;

    /** 文件大小 */
    private Long fileSize;

    /** 上传时间 */
    private String uploadTime;

    /** 相关度得分 */
    private Float score;

    /** 关键词高亮片段列表 */
    private List<String> highlights;

    /** 文档标签列表 */
    private List<String> tags;

    /** 空间类型：0=个人 1=部门 2=企业 */
    private Integer spaceType;
}
