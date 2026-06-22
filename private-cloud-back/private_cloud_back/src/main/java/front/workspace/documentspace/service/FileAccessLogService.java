package front.workspace.documentspace.service;

import front.workspace.documentspace.entity.FileAccessLog;
import front.workspace.documentspace.repository.FileAccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Service
public class FileAccessLogService {

    @Autowired
    private FileAccessLogRepository accessLogRepository;

    @Async("ocrExecutor")
    @Transactional
    public void logAccessAsync(Long userId, Long fileId, String accessType) {
        FileAccessLog log = new FileAccessLog();
        log.setUserId(userId);
        log.setFileId(fileId);
        log.setAccessType(accessType);
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) {
                    ip = request.getRemoteAddr();
                }
                log.setIp(ip);
            }
        } catch (Exception e) {
            log.setIp("unknown");
        }
        accessLogRepository.save(log);
    }

    public List<FileAccessLog> getRecentAccessLogs(Long userId, int limit) {
        return accessLogRepository.findRecentByUserId(userId, limit);
    }
}
