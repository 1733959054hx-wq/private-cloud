package front.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 点选文字验证码服务
 * - 后端使用 Java 原生 BufferedImage 生成带随机汉字的背景图并记录坐标
 * - 要求用户按顺序点击指定汉字
 * - 验证时比对容差范围（±18px），一次性使用
 * - 全程无需第三方依赖，纯 JDK 实现
 */
@Service
public class CaptchaService {

    private static final String CAPTCHA_KEY_PREFIX = "captcha:text:";
    private static final String CAPTCHA_FAIL_KEY_PREFIX = "captcha:fail:";
    private static final long CAPTCHA_TTL_SECONDS = 120;
    private static final int MAX_VERIFY_FAIL_COUNT = 3;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 画布尺寸 */
    private static final int CANVAS_WIDTH = 320;
    private static final int CANVAS_HEIGHT = 160;

    /** 汉字字号范围 */
    private static final int FONT_SIZE_MIN = 28;
    private static final int FONT_SIZE_MAX = 36;

    /** 验证容差（像素） */
    private static final int TOLERANCE = 18;

    /** 画布上显示的汉字数量（含干扰字） */
    private static final int TOTAL_CHARS = 5;

    /** 需要按顺序点击的汉字数量 */
    private static final int TARGET_CHARS = 3;

    /** 常用汉字池（笔画适中，辨识度好） */
    private static final String CHAR_POOL =
            "天地人和风云雷雨雪山水石田林花草木鸟鱼虫龙马牛羊虎鹿" +
            "春夏秋冬东南西北日月星辰金银铜铁玉石珠宝刀剑弓矛" +
            "心手耳目口足首身肩背胸腹指掌腕肘膝踝颈额眉唇齿舌" +
            "门窗桌椅书笔墨纸砚琴棋画诗酒茶饭菜肉蛋奶糖盐醋";

    private static final Font[] FONTS = {
            new Font("Microsoft YaHei", Font.BOLD, 30),
            new Font("SimHei", Font.BOLD, 30),
            new Font("KaiTi", Font.BOLD, 30),
            new Font("SimSun", Font.BOLD, 30),
    };

    /** 字符颜色池（深色系，确保在浅色背景上可读） */
    private static final Color[] CHAR_COLORS = {
            new Color(30, 30, 120),
            new Color(120, 30, 30),
            new Color(30, 100, 30),
            new Color(100, 30, 100),
            new Color(20, 80, 120),
            new Color(120, 80, 20),
            new Color(50, 50, 50),
    };

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 本地内存兜底：Redis 不可用时临时存储验证码状态（120秒过期）
    private final java.util.concurrent.ConcurrentHashMap<String, String> captchaLocalFallback =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Integer> captchaFailLocalFallback =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 生成点选文字验证码
     * @return { captchaKey, image(Base64 PNG), prompt }
     */
    public Map<String, Object> generateCaptcha() {
        String captchaKey = UUID.randomUUID().toString();
        Random random = ThreadLocalRandom.current();

        // ========== 1. 从汉字池中随机选取不重复的汉字 ==========
        List<Character> pool = new ArrayList<>();
        for (char c : CHAR_POOL.toCharArray()) {
            pool.add(c);
        }
        Collections.shuffle(pool, random);
        List<Character> selectedChars = pool.subList(0, TOTAL_CHARS);

        // 前 TARGET_CHARS 个为目标字（按此顺序点击），后面的是干扰字
        List<Character> targetChars = selectedChars.subList(0, TARGET_CHARS);

        // ========== 2. 生成 BufferedImage ==========
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 绘制浅色背景
        drawBackground(g2d, random);

        // ========== 3. 计算字符位置（避免重叠） ==========
        List<int[]> positions = new ArrayList<>(); // [{x, y}, ...] 每个字的中心坐标
        List<Integer> fontSizes = new ArrayList<>();

        for (int i = 0; i < TOTAL_CHARS; i++) {
            int fontSize = random.nextInt(FONT_SIZE_MIN, FONT_SIZE_MAX + 1);
            fontSizes.add(fontSize);

            int attempts = 0;
            int cx, cy;
            boolean valid;
            do {
                cx = random.nextInt(40, CANVAS_WIDTH - 40);
                cy = random.nextInt(40, CANVAS_HEIGHT - 20);
                valid = true;
                for (int[] existing : positions) {
                    if (Math.abs(cx - existing[0]) < 45 && Math.abs(cy - existing[1]) < 40) {
                        valid = false;
                        break;
                    }
                }
                attempts++;
            } while (!valid && attempts < 50);

            positions.add(new int[]{cx, cy});
        }

        // ========== 4. 绘制字符 ==========
        for (int i = 0; i < TOTAL_CHARS; i++) {
            drawCharacter(g2d, random, selectedChars.get(i),
                    positions.get(i)[0], positions.get(i)[1], fontSizes.get(i));
        }

        g2d.dispose();

        // ========== 5. 存储目标字符坐标到 Redis ==========
        // 格式：JSON 数组 [{x, y, c}, ...]，只存目标字
        List<Map<String, Object>> targetPositions = new ArrayList<>();
        for (int i = 0; i < TARGET_CHARS; i++) {
            Map<String, Object> pos = new HashMap<>();
            pos.put("x", positions.get(i)[0]);
            pos.put("y", positions.get(i)[1]);
            pos.put("c", String.valueOf(targetChars.get(i)));
            targetPositions.add(pos);
        }

        String redisKey = CAPTCHA_KEY_PREFIX + captchaKey;
        String targetJson;
        try {
            targetJson = objectMapper.writeValueAsString(targetPositions);
        } catch (Exception e) {
            throw new RuntimeException("验证码坐标序列化失败", e);
        }
        // 优先写入 Redis；Redis 不可用时降级到本地内存兜底
        try {
            redisTemplate.opsForValue().set(redisKey, targetJson, CAPTCHA_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[Captcha] Redis 写入失败，启用本地内存兜底: " + e.getMessage());
            captchaLocalFallback.put(redisKey, targetJson);
            scheduleLocalFallbackExpire(redisKey);
        }

        // ========== 6. 构建提示信息 ==========
        StringBuilder promptBuilder = new StringBuilder("请依次点击：");
        for (int i = 0; i < TARGET_CHARS; i++) {
            if (i > 0) promptBuilder.append("、");
            promptBuilder.append(targetChars.get(i));
        }

        // ========== 7. 图片转 Base64 ==========
        String base64Image;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("验证码图片生成失败", e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("captchaKey", captchaKey);
        result.put("image", base64Image);
        result.put("prompt", promptBuilder.toString());
        return result;
    }

    /**
     * 验证点选文字
     * @param captchaKey 验证码标识
     * @param clicks 用户点击坐标列表 [{x, y}, ...]
     * @return 是否验证通过
     */
    @SuppressWarnings("unchecked")
    public boolean verifyCaptcha(String captchaKey, List<Map<String, Object>> clicks) {
        if (captchaKey == null || captchaKey.isBlank() || clicks == null) {
            return false;
        }
        String redisKey = CAPTCHA_KEY_PREFIX + captchaKey;
        String stored = null;
        boolean usingLocalFallback = false;
        try {
            Object redisValue = redisTemplate.opsForValue().get(redisKey);
            if (redisValue != null) stored = redisValue.toString();
        } catch (Exception e) {
            System.err.println("[Captcha] Redis 读取失败，尝试本地内存兜底: " + e.getMessage());
        }
        if (stored == null) {
            stored = captchaLocalFallback.get(redisKey);
            if (stored != null) usingLocalFallback = true;
        }
        if (stored == null) {
            return false;
        }

        // 校验失败次数限制（防暴力枚举）
        String failKey = CAPTCHA_FAIL_KEY_PREFIX + captchaKey;
        Object failCountObj = null;
        try {
            failCountObj = redisTemplate.opsForValue().get(failKey);
        } catch (Exception ignored) {}
        int failCount = 0;
        if (failCountObj != null) {
            failCount = failCountObj instanceof Integer ? (Integer) failCountObj : Integer.parseInt(failCountObj.toString());
            if (failCount >= MAX_VERIFY_FAIL_COUNT) {
                try { redisTemplate.delete(redisKey); } catch (Exception ignored) {}
                try { redisTemplate.delete(failKey); } catch (Exception ignored) {}
                captchaLocalFallback.remove(redisKey);
                captchaFailLocalFallback.remove(failKey);
                return false;
            }
        } else {
            Integer localFail = captchaFailLocalFallback.get(failKey);
            if (localFail != null && localFail >= MAX_VERIFY_FAIL_COUNT) {
                captchaLocalFallback.remove(redisKey);
                captchaFailLocalFallback.remove(failKey);
                return false;
            }
            if (localFail != null) failCount = localFail;
        }

        // 解析存储的目标位置（标准 JSON）
        List<Map<String, Object>> targets;
        try {
            targets = objectMapper.readValue(stored, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            try { redisTemplate.delete(redisKey); } catch (Exception ignored) {}
            captchaLocalFallback.remove(redisKey);
            return false;
        }

        if (clicks.size() != targets.size()) {
            recordCaptchaFail(failKey, usingLocalFallback);
            return false;
        }

        // 逐个比对点击坐标
        for (int i = 0; i < targets.size(); i++) {
            Map<String, Object> target = targets.get(i);
            Map<String, Object> click = clicks.get(i);
            int targetX = toInt(target.get("x"));
            int targetY = toInt(target.get("y"));
            int clickX = toInt(click.get("x"));
            int clickY = toInt(click.get("y"));

            if (Math.abs(targetX - clickX) > TOLERANCE || Math.abs(targetY - clickY) > TOLERANCE) {
                recordCaptchaFail(failKey, usingLocalFallback);
                return false;
            }
        }

        // 验证通过：清除验证码和失败计数
        try { redisTemplate.delete(redisKey); } catch (Exception ignored) {}
        try { redisTemplate.delete(failKey); } catch (Exception ignored) {}
        captchaLocalFallback.remove(redisKey);
        captchaFailLocalFallback.remove(failKey);
        return true;
    }

    private void recordCaptchaFail(String failKey, boolean usingLocalFallback) {
        Object current = null;
        try {
            current = redisTemplate.opsForValue().get(failKey);
        } catch (Exception ignored) {}
        int count = current == null ? 0 : (current instanceof Integer ? (Integer) current : Integer.parseInt(current.toString()));
        if (usingLocalFallback || current == null) {
            Integer localCount = captchaFailLocalFallback.get(failKey);
            count = localCount == null ? count : localCount;
        }
        int next = count + 1;
        try {
            redisTemplate.opsForValue().set(failKey, next, CAPTCHA_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            captchaFailLocalFallback.put(failKey, next);
            scheduleLocalFallbackExpire(failKey);
        }
    }

    private void scheduleLocalFallbackExpire(String key) {
        new Thread(() -> {
            try {
                Thread.sleep(CAPTCHA_TTL_SECONDS * 1000);
            } catch (InterruptedException ignored) {}
            captchaLocalFallback.remove(key);
            captchaFailLocalFallback.remove(key);
        }).start();
    }

    // ======================== 绘图辅助方法 ========================

    /** 绘制带噪声的背景 */
    private void drawBackground(Graphics2D g2d, Random random) {
        // 浅色渐变底色
        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(230 + random.nextInt(20), 230 + random.nextInt(20), 235 + random.nextInt(15)),
                CANVAS_WIDTH, CANVAS_HEIGHT, new Color(220 + random.nextInt(20), 225 + random.nextInt(20), 230 + random.nextInt(15))
        );
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // 随机干扰线
        for (int i = 0; i < 8; i++) {
            g2d.setColor(new Color(
                    random.nextInt(150, 220),
                    random.nextInt(150, 220),
                    random.nextInt(150, 220),
                    60 + random.nextInt(60)
            ));
            g2d.setStroke(new BasicStroke(1 + random.nextFloat()));
            g2d.drawLine(
                    random.nextInt(CANVAS_WIDTH), random.nextInt(CANVAS_HEIGHT),
                    random.nextInt(CANVAS_WIDTH), random.nextInt(CANVAS_HEIGHT)
            );
        }

        // 随机干扰点
        for (int i = 0; i < 40; i++) {
            g2d.setColor(new Color(
                    random.nextInt(120, 220),
                    random.nextInt(120, 220),
                    random.nextInt(120, 220),
                    80 + random.nextInt(80)
            ));
            int size = 2 + random.nextInt(4);
            g2d.fillOval(random.nextInt(CANVAS_WIDTH), random.nextInt(CANVAS_HEIGHT), size, size);
        }

        // 随机干扰小圆
        for (int i = 0; i < 5; i++) {
            g2d.setColor(new Color(
                    random.nextInt(180, 230),
                    random.nextInt(180, 230),
                    random.nextInt(180, 230),
                    40 + random.nextInt(40)
            ));
            int r = 8 + random.nextInt(15);
            g2d.drawOval(random.nextInt(CANVAS_WIDTH - r), random.nextInt(CANVAS_HEIGHT - r), r, r);
        }
    }

    /** 绘制单个汉字（带随机旋转和颜色） */
    private void drawCharacter(Graphics2D g2d, Random random, char ch, int cx, int cy, int fontSize) {
        Font font = FONTS[random.nextInt(FONTS.length)].deriveFont((float) fontSize);
        Color color = CHAR_COLORS[random.nextInt(CHAR_COLORS.length)];

        // 随机旋转角度（-20° ~ 20°）
        double angle = (random.nextDouble() - 0.5) * Math.toRadians(40);

        AffineTransform original = g2d.getTransform();
        g2d.rotate(angle, cx, cy);
        g2d.setFont(font);
        g2d.setColor(color);

        // 计算文字居中偏移
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(String.valueOf(ch));
        int textHeight = fm.getHeight();
        g2d.drawString(String.valueOf(ch), cx - textWidth / 2, cy + textHeight / 4);

        // 加描边增强辨识度
        g2d.setColor(new Color(255, 255, 255, 80));
        g2d.setStroke(new BasicStroke(0.5f));
        g2d.drawString(String.valueOf(ch), cx - textWidth / 2 + 1, cy + textHeight / 4 + 1);

        g2d.setTransform(original);
    }

    private int toInt(Object obj) {
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj != null) {
            try { return Integer.parseInt(obj.toString().trim()); } catch (NumberFormatException ignored) { }
        }
        return 0;
    }
}
