import org.opencv.core.Mat;

/**
 * Strategy: "이 한 줄(row)이 배경(여백)인가?"를 판단하는 기준.
 * 흰색/검은색/그라데이션 등 색상이 달라도 교체 가능하도록 인터페이스로 분리.
 */
public interface RowClassifier {
    boolean isBackgroundRow(Mat row);
}
