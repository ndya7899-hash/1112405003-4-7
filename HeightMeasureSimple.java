import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class HeightMeasureSimple {


    static final double REF_HEIGHT_CM = 180.0; 

    public static void main(String[] args) {
        try {
            String[] imgPaths = {"pic1.jpg", "pic2.jpg", "pic3.jpg", "pic4.jpg"};
            
            for (int i = 0; i < imgPaths.length; i++) {
                File f = new File(imgPaths[i]);
                if (!f.exists()) {
                    System.out.println("找不到檔案: " + imgPaths[i]);
                    continue;
                }
                BufferedImage img = ImageIO.read(f);
                
                // 設定搜尋範圍 (ROI): 需要根據每張圖黑衣人的位置微調
                // Rect 格式: (x, y, 寬度, 高度)
                Rect refROI;    // Just Do It 同學的位置
                Rect targetROI; // 目標同學的位置

                if (i == 0) { // 照片 1
                    refROI = new Rect(550, 500, 300, 1000); 
                    targetROI = new Rect(300, 500, 250, 1000);
                } else if (i == 1) { // 照片 2
                    refROI = new Rect(600, 550, 250, 950);
                    targetROI = new Rect(400, 550, 200, 950);
                } else { // 照片 3 & 4 (大約值，可微調)
                    refROI = new Rect(500, 500, 300, 1000);
                    targetROI = new Rect(100, 500, 300, 1000);
                }

                analyzeOnePhoto(img, refROI, targetROI, "照片 " + (i + 1));
            }

        } catch (Exception e) {
            System.out.println("執行失敗: " + e.getMessage());
        }
    }

    static void analyzeOnePhoto(BufferedImage image, Rect refROI, Rect targetROI, String label) {
        // 1. 估計消失線 (根據天花板結構)
        int vanishY = image.getHeight() / 3; 

        // 2. 偵測人物 Y 軸邊界
        PersonBox refBox = detectPerson(image, refROI);
        PersonBox targetBox = detectPerson(image, targetROI);

        if (refBox == null || targetBox == null) {
            System.out.println(label + ": 偵測失敗");
            return;
        }

        // 3. 計算像素高度
        int hRef = refBox.bottom - refBox.top;
        int hTarget = targetBox.bottom - targetBox.top;

        // 4. 根據消失點距離進行補償 (Pinhole Camera Model)
        // 距離消失線越遠，代表在現實中越靠近相機
        double distRef = Math.abs(refBox.bottom - vanishY);
        double distTarget = Math.abs(targetBox.bottom - vanishY);

        double ratioRef = (double) hRef / distRef;
        double ratioTarget = (double) hTarget / distTarget;

        // 5. 換算身高
        double finalHeight = (ratioTarget / ratioRef) * REF_HEIGHT_CM;

        System.out.printf("%s -> 基準(180cm)像素:%d, 目標像素:%d, 計算身高:%.2f cm\n", 
                          label, hRef, hTarget, finalHeight);
    }

    static PersonBox detectPerson(BufferedImage img, Rect r) {
        int t = -1, b = -1;
        for (int y = r.y; y < r.y + r.h && y < img.getHeight(); y++) {
            for (int x = r.x; x < r.x + r.w && x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int brightness = (((rgb >> 16) & 0xff) + ((rgb >> 8) & 0xff) + (rgb & 0xff)) / 3;
                if (brightness < 110) { // 偵測深色衣物/頭髮
                    if (t == -1) t = y;
                    b = y;
                }
            }
        }
        return (t == -1) ? null : new PersonBox(t, b);
    }

    static class Rect {
        int x, y, w, h;
        Rect(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }

    static class PersonBox {
        int top, bottom;
        PersonBox(int t, int b) { this.top = t; this.bottom = b; }
    }
}