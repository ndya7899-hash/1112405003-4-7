import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class HeightMeasureSimple {

    static final double REF_HEIGHT_CM = 180.0;

    public static void main(String[] args) {
        try {
            // 兩張照片路徑
            // 把原本的絕對路徑刪除，改成這樣：
String img1Path = "pic1 (1).jpg";
String img2Path = "pic3.jpg";

            BufferedImage img1 = ImageIO.read(new File(img1Path));
            BufferedImage img2 = ImageIO.read(new File(img2Path));

            if (img1 == null || img2 == null) {
                System.out.println("讀不到圖片");
                return;
            }

            // -----------------------------
            // 第一張圖
            // 右邊是 Just do it，左邊是另一位男生
            // -----------------------------
            Rect img1RefROI = new Rect(780, 700, 420, 1200);
            Rect img1TargetROI = new Rect(300, 700, 420, 1200);

            Result r1 = analyzeOnePhoto(img1, img1RefROI, img1TargetROI, "photo1");

            // -----------------------------
            // 第二張圖
            // 後面是 Just do it，前面左邊是另一位男生
            // -----------------------------
            Rect img2RefROI = new Rect(700, 650, 350, 1150);
            Rect img2TargetROI = new Rect(260, 650, 420, 1250);

            Result r2 = analyzeOnePhoto(img2, img2RefROI, img2TargetROI, "photo2");

            System.out.println("===== 計算結果 =====");
            System.out.printf("第一張照片 (消失線Y = %d) 另一位男生估計身高：%.2f cm\n", r1.vanishY, r1.targetHeightCm);
            System.out.printf("第二張照片 (消失線Y = %d) 另一位男生估計身高：%.2f cm\n", r2.vanishY, r2.targetHeightCm);

        } catch (java.io.IOException e) {
            System.out.println("讀取圖片失敗，請檢查路徑");
        } catch (Exception e) {
            System.out.println("程式執行失敗");
        }
    }

    static Result analyzeOnePhoto(BufferedImage image, Rect refROI, Rect targetROI, String name) {
        int vanishY = findVanishingLineY(image);

        PersonBox refBox = detectPersonInROI(image, refROI);
        PersonBox targetBox = detectPersonInROI(image, targetROI);

        if (refBox == null || targetBox == null) {
            System.out.println(name + " 偵測人物失敗");
            return new Result(vanishY, 0);
        }

        int refPixelHeight = refBox.bottomY - refBox.topY;
        int targetPixelHeight = targetBox.bottomY - targetBox.topY;

        double refCorrected = (double) refPixelHeight / Math.abs(refBox.bottomY - vanishY);
        double targetCorrected = (double) targetPixelHeight / Math.abs(targetBox.bottomY - vanishY);

        double targetHeight = (targetCorrected / refCorrected) * REF_HEIGHT_CM;

        System.out.println("----- " + name + " -----");
        System.out.println("vanishing line y = " + vanishY);
        
        // 變數要在這裡印出才對喔！
        System.out.println("參考人物 left = " + refBox.leftX + ", right = " + refBox.rightX + ", top = " + refBox.topY + ", bottom = " + refBox.bottomY);
        System.out.println("目標人物 left = " + targetBox.leftX + ", right = " + targetBox.rightX + ", top = " + targetBox.topY + ", bottom = " + targetBox.bottomY);
        System.out.printf("估計身高 = %.2f cm\n", targetHeight);

        return new Result(vanishY, targetHeight);
    }

    // ===============================
    // 用建築物水平線估計 vanishing line
    // ===============================
    static int findVanishingLineY(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int[][] gray = new int[height][width];

        // 灰階化
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                gray[y][x] = (r + g + b) / 3;
            }
        }

        int[] rowScore = new int[height];

        // 水平邊緣分數
        for (int y = 0; y < height - 1; y++) {
            int sum = 0;
            for (int x = 0; x < width; x++) {
                sum += Math.abs(gray[y + 1][x] - gray[y][x]);
            }
            rowScore[y] = sum;
        }

        int[] smooth = smooth(rowScore, 21);

        // 只在建築主要區域找水平線
        int startY = height / 8;
        int endY = height * 3 / 5;

        int peak1 = findBestPeak(smooth, startY, endY, -1, 120);
        int peak2 = findBestPeak(smooth, startY, endY, peak1, 120);

        if (peak1 > peak2) {
            int t = peak1;
            peak1 = peak2;
            peak2 = t;
        }

        return (peak1 + peak2) / 2;
    }

    // ===============================
    // 在指定區域內偵測人物
    // 不用手動輸入頭腳座標
    // ===============================
    static PersonBox detectPersonInROI(BufferedImage image, Rect roi) {
        int[][] mask = new int[roi.h][roi.w];

        // 先做人物暗色衣物遮罩
        for (int y = 0; y < roi.h; y++) {
            for (int x = 0; x < roi.w; x++) {
                int px = roi.x + x;
                int py = roi.y + y;

                if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) {
                    continue;
                }

                int rgb = image.getRGB(px, py);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                int brightness = (r + g + b) / 3;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int sat = max - min;

                // 黑衣、深色短褲、深色人體輪廓
                boolean isDarkBody =
                        brightness < 95 ||
                        (brightness < 120 && sat < 45) ||
                        (r < 90 && g < 90 && b < 90);

                if (isDarkBody) {
                    mask[y][x] = 1;
                }
            }
        }

        // 每列統計
        int[] rowCount = new int[roi.h];
        for (int y = 0; y < roi.h; y++) {
            int cnt = 0;
            for (int x = 0; x < roi.w; x++) {
                if (mask[y][x] == 1) cnt++;
            }
            rowCount[y] = cnt;
        }

        // 每行統計
        int[] colCount = new int[roi.w];
        for (int x = 0; x < roi.w; x++) {
            int cnt = 0;
            for (int y = 0; y < roi.h; y++) {
                if (mask[y][x] == 1) cnt++;
            }
            colCount[x] = cnt;
        }

        int top = -1;
        int bottom = -1;
        int left = -1;
        int right = -1;

        int rowThreshold = Math.max(8, roi.w / 20);
        int colThreshold = Math.max(12, roi.h / 18);

        // 找頭頂
        for (int y = 0; y < roi.h; y++) {
            if (rowCount[y] > rowThreshold) {
                top = y;
                break;
            }
        }

        // 找腳底
        for (int y = roi.h - 1; y >= 0; y--) {
            if (rowCount[y] > rowThreshold) {
                bottom = y;
                break;
            }
        }

        // 找左右邊界
        for (int x = 0; x < roi.w; x++) {
            if (colCount[x] > colThreshold) {
                left = x;
                break;
            }
        }

        for (int x = roi.w - 1; x >= 0; x--) {
            if (colCount[x] > colThreshold) {
                right = x;
                break;
            }
        }

        if (top == -1 || bottom == -1 || left == -1 || right == -1) {
            return null;
        }

        return new PersonBox(
                roi.x + left,
                roi.y + top,
                roi.x + right,
                roi.y + bottom
        );
    }

    static int[] smooth(int[] data, int windowSize) {
        int n = data.length;
        int[] result = new int[n];
        int half = windowSize / 2;

        for (int i = 0; i < n; i++) {
            int sum = 0;
            int count = 0;

            for (int j = i - half; j <= i + half; j++) {
                if (j >= 0 && j < n) {
                    sum += data[j];
                    count++;
                }
            }

            result[i] = (count == 0) ? 0 : (sum / count);
        }

        return result;
    }

    static int findBestPeak(int[] data, int start, int end, int avoidIndex, int minDistance) {
        int bestIndex = start;
        int bestValue = -1;

        for (int i = start + 1; i < end - 1; i++) {
            boolean isPeak = data[i] >= data[i - 1] && data[i] >= data[i + 1];

            if (!isPeak) continue;

            if (avoidIndex != -1 && Math.abs(i - avoidIndex) < minDistance) {
                continue;
            }

            if (data[i] > bestValue) {
                bestValue = data[i];
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    static class Rect {
        int x, y, w, h;

        Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    static class PersonBox {
        int leftX, topY, rightX, bottomY;

        PersonBox(int leftX, int topY, int rightX, int bottomY) {
            this.leftX = leftX;
            this.topY = topY;
            this.rightX = rightX;
            this.bottomY = bottomY;
        }
    }

    static class Result {
        int vanishY;
        double targetHeightCm;

        Result(int vanishY, double targetHeightCm) {
            this.vanishY = vanishY;
            this.targetHeightCm = targetHeightCm;
        }
    }
}