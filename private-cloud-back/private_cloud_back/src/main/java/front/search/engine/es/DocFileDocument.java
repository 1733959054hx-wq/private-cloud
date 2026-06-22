package front.search.engine.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import java.util.Date;

/**
 * ES 文档索引（映射 doc_file 表，使用 IK 中文分词）
 */
@Data
@Document(indexName = "doc_file")
@Setting(replicas = 0)
public class DocFileDocument {

    @Id
    private Long id;

    /** 文档标题（IK 中文分词） */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String fileName;

    /** 全文内容（IK 中文分词） */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String fulltextContent;

    /** 上传者 */
    @Field(type = FieldType.Keyword)
    private String uploaderName;

    /** 文件类型 */
    @Field(type = FieldType.Keyword)
    private String fileType;

    /** 文件大小 */
    @Field(type = FieldType.Long)
    private Long fileSize;

    /** 所属部门ID */
    @Field(type = FieldType.Long)
    private Long departmentId;

    /** 空间类型：0=个人空间 1=部门空间 2=企业空间 */
    @Field(type = FieldType.Integer)
    private Integer spaceType;

    /** 空间ID（个人空间=用户ID，部门空间=部门ID，企业空间=公司ID） */
    @Field(type = FieldType.Long)
    private Long spaceId;

    /** 上传者ID */
    @Field(type = FieldType.Long)
    private Long uploaderId;

    /** 上传时间 */
    @Field(type = FieldType.Date)
    private Date createTime;
}
