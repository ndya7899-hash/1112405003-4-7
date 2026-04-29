import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class HeightMeasureSimple {

    static final double REF_HEIGHT_CM = 180.0;

    public static void main(String[] args) {
        try {
            // 根據你的 VS Code 截圖修正檔名
            String img1Path = "ca2a26c2-a565-4e14-80ee-17abd28e8a66.jpg";
            String img2Path = "07b1282e-9dde-4b63-8508-a94198adad88.jpg";

            BufferedImage img1 = ImageIO.read(new File(img1Path));
            BufferedImage img2 = ImageIO.read(new File(img2Path));

            if (img1 == null || img2 == null) {
                System.out.println("讀不到圖片，請確認檔名是否與左側清單一致！");
                return;
            }

            // 第一張圖 (左右站立)
            Rect img1RefROI = new Rect(700, 500, 300, 1000); 
            Rect img1TargetROI = new Rect(200, 500, 300, 1000); 
            Result r1 = analyzeOnePhoto(img1, img1RefROI, img1TargetROI, "第一張照片");

            // 第二張圖 (前後站立)
            Rect img2RefROI = new Rect(600, 500, 300, 1000); 
            Rect img2TargetROI = new Rect(150, 500, 350, 1100); 
            Result r2 = analyzeOnePhoto(img2, img2RefROI, img2TargetROI, "第二張照片");

            System.out.println("\n===== 最終計算結果 =====");
            System.out.printf("%s 目標身高：%.2f cm\n", r1.name, r1.targetHeightCm);
            System.out.printf("%s 目標身高：%.2f cm\n", r2.name, r2.targetHeightCm);

        } catch (java.io.IOException e) {
            System.out.println("讀取圖片失敗");
        } catch (Exception e) {
            System.out.println("程式執行出錯: " + e.getMessage());
        }
    }

    static Result analyzeOnePhoto(BufferedImage image, Rect refROI, Rect targetROI, String name) {
        int vanishY = findVanishingLineY(image);
        PersonBox refBox = detectPersonInROI(image, refROI);
        PersonBox targetBox = detectPersonInROI(image, targetROI);

        if (refBox == null || targetBox == null) {
            System.out.println(name + " 偵測人物失敗");
            return new Result(vanishY, 0, name);
        }

        int refPixelHeight = refBox.bottomY - refBox.topY;
        int targetPixelHeight = targetBox.bottomY - targetBox.topY;

        double refCorrected = (double) refPixelHeight / Math.max(1, Math.abs(refBox.bottomY - vanishY));
        double targetCorrected = (double) targetPixelHeight / Math.max(1, Math.abs(targetBox.bottomY - vanishY));

        double targetHeight = (targetCorrected / refCorrected) * REF_HEIGHT_CM;
        return new Result(vanishY, targetHeight, name);
    }

    static int findVanishingLineY(BufferedImage image) {
        int height = image.getHeight();
        return height / 3; // 預設一個大約的消失線位置
    }

    static PersonBox detectPersonInROI(BufferedImage image, Rect roi) {
        int top = -1, bottom = -1;
        for (int y = 0; y < roi.h; y++) {
            int py = roi.y + y;
            if (py >= image.getHeight()) break;
            for (int x = 0; x < roi.w; x++) {
                int px = roi.x + x;
                if (px >= image.getWidth()) break;
                int rgb = image.getRGB(px, py);
                int brightness = (((rgb >> 16) & 0xff) + ((rgb >> 8) & 0xff) + (rgb & 0xff)) / 3;
                if (brightness < 120) { 
                    if (top == -1) top = py;
                    bottom = py;
                }
            }
        }
        if (top == -1) return null;
        return new PersonBox(roi.x, top, roi.x + roi.w, bottom);
    }

    static class Rect {
        int x, y, w, h;
        Rect(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }

    static class PersonBox {
        int leftX, topY, rightX, bottomY;
        PersonBox(int lx, int ty, int rx, int by) { this.leftX = lx; this.topY = ty; this.rightX = rx; this.bottomY = by; }
    }

    static class Result {
        int vanishY; double targetHeightCm; String name;
        Result(int v, double h, String n) { this.vanishY = v; this.targetHeightCm = h; this.name = n; }
    }
}