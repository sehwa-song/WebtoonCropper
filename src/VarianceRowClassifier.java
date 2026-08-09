import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;

/**
 * 색상 자체가 아니라 "가로 방향으로 얼마나 단조로운가(표준편차)"로 배경 줄을 판단한다.
 * 흰 배경, 검은 배경, 세로 그라데이션 배경 모두 한 줄 안에서는 색이 거의 일정하다는 공통점을 이용.
 */
public class VarianceRowClassifier implements RowClassifier {

    private final double stdDevThreshold;

    public VarianceRowClassifier(double stdDevThreshold) {
        this.stdDevThreshold = stdDevThreshold;
    }

    @Override
    public boolean isBackgroundRow(Mat row) {
        MatOfDouble mean = new MatOfDouble(); // 평균
        MatOfDouble stddev = new MatOfDouble(); // 표준편차
        Core.meanStdDev(row, mean, stddev);

        double maxChannelStdDev = 0;
        for (double s : stddev.toArray()) {
            maxChannelStdDev = Math.max(maxChannelStdDev, s);
        }
        return maxChannelStdDev < stdDevThreshold; // 커트라인보다 낮으면 배경이니 true.
    }
}
