package front.esign.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 模拟电子签章服务
 * 在 PDF 末尾生成一个红色圆形印章图片
 */
@Service
public class StampService {

    /**
     * 在 PDF 文件末尾添加印章
     * @param pdfStream  原始 PDF 输入流
     * @param signerName 签署人姓名
     * @return 带印章的 PDF 字节数组
     */
    public byte[] stampPdf(InputStream pdfStream, String signerName) {
        try {
            byte[] pdfBytes = toByteArray(pdfStream);
            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                byte[] stampImage = generateStampImage(signerName);

                for (PDPage page : doc.getDocumentCatalog().getPages()) {
                    PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, stampImage, "stamp");

                    float pageWidth = page.getMediaBox().getWidth();
                    float pageHeight = page.getMediaBox().getHeight();

                    float stampWidth = 120;
                    float stampHeight = 120;
                    float x = pageWidth - stampWidth - 50;
                    float y = 50;

                    try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        cs.drawImage(pdImage, x, y, stampWidth, stampHeight);
                    }
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                doc.save(baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("盖章失败：" + e.getMessage(), e);
        }
    }

    private byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = is.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }

    /**
     * 生成红色圆形印章图片（模拟签章）
     * 内容：企业名称 + "已签署" + 签署人 + 日期
     */
    private byte[] generateStampImage(String signerName) throws Exception {
        int size = 300;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();

        // 抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 外圆
        g2d.setColor(new Color(220, 20, 20, 200));
        g2d.setStroke(new BasicStroke(6));
        g2d.draw(new Ellipse2D.Double(6, 6, size - 12, size - 12));

        // 内圆
        g2d.setStroke(new BasicStroke(2));
        g2d.draw(new Ellipse2D.Double(18, 18, size - 36, size - 36));

        // 企业名称（居中）
        Font nameFont = new Font("Microsoft YaHei", Font.BOLD, 20);
        g2d.setFont(nameFont);
        String company = "私有云科技有限公司";
        int nameWidth = g2d.getFontMetrics().stringWidth(company);
        g2d.drawString(company, (size - nameWidth) / 2, size / 2 - 40);

        // 五角星
        g2d.setFont(new Font("Serif", Font.BOLD, 32));
        g2d.drawString("★", size / 2 - 16, size / 2 + 10);

        // "已签署"
        g2d.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
        g2d.drawString("已签署", size / 2 - 42, size / 2 + 48);

        // 签署人
        g2d.setFont(new Font("Microsoft YaHei", Font.PLAIN, 20));
        g2d.drawString(signerName, size / 2 - 30, size / 2 + 80);

        // 日期
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        g2d.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        g2d.drawString(dateStr, size / 2 - 40, size / 2 + 108);

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
