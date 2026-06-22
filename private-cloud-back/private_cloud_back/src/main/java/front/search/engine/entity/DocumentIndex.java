package front.search.engine.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档索引 POJO（仅作 DTO 使用）
 */
@Data
public class DocumentIndex {

    private Long id;

    /** 文档ID（即 doc_file 表的 id） */
    private Long documentId;

    /** 文档标题（对应 file_name） */
    private String title;

    /** 全文内容（含OCR提取文字） */
    private String fulltextContent;

    /** 上传者姓名 */
    private String uploader;

    /** 文件类型 */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 所属部门ID */
    private Long departmentId;

    /** 相关度评分（非数据库字段，仅查询时使用） */
    private Float relevance;

    /** 上传时间 */
    private LocalDateTime uploadTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
