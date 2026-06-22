package front.workspace.documentspace.service;

import front.storage.service.StorageRouter;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 视频封面生成服务
 * 优先使用 JCodec（轻量、纯 Java、支持 MP4）抽取第一帧；
 * JCodec 失败时尝试 FFmpeg；都失败则返回默认播放图标封面。
 */
@Service
public class VideoCoverService {

    private static final Logger log = LoggerFactory.getLogger(VideoCoverService.class);

    private static final List<String> VIDEO_TYPES = Arrays.asList("mp4", "webm", "ogg", "mov", "avi", "mkv", "flv", "m4v", "3gp");

    @Autowired
    private DocFileRepository docFileRepository;

    @Autowired
    private StorageRouter storageRouter;

    public boolean isVideo(String fileType) {
        return fileType != null && VIDEO_TYPES.contains(fileType.toLowerCase());
    }

    /**
     * 生成视频封面图片字节（JPEG）
     * @param fileId 文件ID
     * @return 封面图片字节，生成失败返回 null
     */
    public byte[] generateCover(Long fileId) {
        Optional<DocFile> optional = docFileRepository.findById(fileId);
        if (optional.isEmpty()) {
            log.warn("[视频封面] 文件不存在: fileId={}", fileId);
            return null;
        }
        DocFile file = optional.get();
        if (!isVideo(file.getFileType())) {
            log.warn("[视频封面] 非视频文件: fileId={}, fileType={}", fileId, file.getFileType());
            return null;
        }

        Path tempVideo = null;
        Path tempCover = null;
        try {
            String ext = file.getFileType().toLowerCase();
            tempVideo = Files.createTempFile("video_", "." + ext);

            log.info("[视频封面] 开始生成: fileId={}, fileName={}, storageType={}, filePath={}",
                    fileId, file.getFileName(), file.getStorageType(), file.getFilePath());

            // 下载视频到临时文件
            String storageType = storageRouter.resolveStorageType(file.getStorageType(), file.getFilePath());
            long downloaded;
            try (InputStream is = storageRouter.getStorage(storageType).download(file.getFilePath())) {
                downloaded = Files.copy(is, tempVideo, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[视频封面] 视频下载完成: fileId={}, storageType={}, size={}", fileId, storageType, downloaded);

            // 1. 优先使用 JCodec 抽帧（轻量、纯 Java，对 MP4 最友好）
            byte[] jcodecCover = generateCoverByJCodec(tempVideo.toFile());
            if (jcodecCover != null && jcodecCover.length > 0) {
                log.info("[视频封面] JCodec 生成成功: fileId={}, coverSize={}", fileId, jcodecCover.length);
                return jcodecCover;
            }

            // 2. JCodec 失败时尝试 FFmpeg（服务器需安装）
            tempCover = Files.createTempFile("cover_", ".jpg");
            byte[] ffmpegCover = generateCoverByFfmpeg(tempVideo, tempCover);
            if (ffmpegCover != null && ffmpegCover.length > 0) {
                log.info("[视频封面] FFmpeg 生成成功: fileId={}, coverSize={}", fileId, ffmpegCover.length);
                return ffmpegCover;
            }

            // 3. 降级默认封面
            log.warn("[视频封面] 抽帧全部失败，返回默认封面: fileId={}", fileId);
            return generateDefaultCover();
        } catch (Exception e) {
            log.warn("[视频封面] 生成失败: fileId={}, error={}", fileId, e.getMessage(), e);
            return generateDefaultCover();
        } finally {
            try {
                if (tempVideo != null) Files.deleteIfExists(tempVideo);
            } catch (Exception ignored) {}
            try {
                if (tempCover != null) Files.deleteIfExists(tempCover);
            } catch (Exception ignored) {}
        }
    }

    private byte[] generateCoverByJCodec(File videoFile) {
        try {
            // 抽取第 0 帧（关键帧）
            Picture picture = FrameGrab.getFrameFromFile(videoFile, 0);
            BufferedImage bufferedImage = AWTUtil.toBufferedImage(picture);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(bufferedImage, "jpg", baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.warn("[视频封面] JCodec 抽帧失败: error={}", e.getMessage());
            return null;
        }
    }

    private byte[] generateCoverByFfmpeg(Path tempVideo, Path tempCover) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i", tempVideo.toString(),
                    "-ss", "00:00:01",
                    "-vframes", "1",
                    "-q:v", "2",
                    "-f", "image2",
                    tempCover.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("[视频封面] FFmpeg 执行超时");
                return null;
            }
            if (process.exitValue() != 0 || !Files.exists(tempCover) || Files.size(tempCover) == 0) {
                log.warn("[视频封面] FFmpeg 执行失败, exitValue={}", process.exitValue());
                return null;
            }
            return Files.readAllBytes(tempCover);
        } catch (Exception e) {
            log.warn("[视频封面] FFmpeg 异常: error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 生成默认视频封面（带播放三角的图标）
     */
    private byte[] generateDefaultCover() {
        int w = 320;
        int h = 240;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, w, h);

        // 播放圆圈
        g.setColor(new Color(64, 158, 255));
        g.setStroke(new BasicStroke(4));
        int cx = w / 2;
        int cy = h / 2;
        int r = 40;
        g.drawOval(cx - r, cy - r, r * 2, r * 2);

        // 播放三角
        int[] xPoints = {cx - 15, cx - 15, cx + 22};
        int[] yPoints = {cy - 20, cy + 20, cy};
        g.fillPolygon(xPoints, yPoints, 3);
        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
