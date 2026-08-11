import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Mat;

/**
 * "row별로 배경 여부를 훑어서 -> 충분히 긴 여백 구간을 찾아 -> 컷 라인을 뽑는다"
 * 는 흐름은 고정하고, "이 row가 배경이냐"라는 판단만 RowClassifier(Strategy)에 위임한다.
 */
public class GapFinder {

    private final int minGapHeight;   // 이 높이 이상 연속된 여백만 컷 경계 후보로 인정
    private final int minPanelHeight; // 컷 하나의 최소 높이 (너무 촘촘하게 잘리는 것 방지). 
                                      // 텍스트를 듬성듬성 놓을 때가 있으니 방지 목적.

    public GapFinder(int minGapHeight, int minPanelHeight) {
        this.minGapHeight = minGapHeight;
        this.minPanelHeight = minPanelHeight;
    }

    /** 이미지 안에서 컷을 나눌 y좌표(각 여백 구간의 중앙) 목록을 반환한다. */
    public List<Integer> findCutPoints(Mat image, RowClassifier classifier) {
        int height = image.rows();
        List<Integer> cutPoints = new ArrayList<>();

        int runStart = -1; // 배경 구간이 시작된 y좌표
        int lastCut = 0;  // 가장 최근에 확정된 컷 좌표.

        for (int y = 0; y < height; y++) {
            boolean isBackground = classifier.isBackgroundRow(image.row(y));

            if (isBackground) {
                if (runStart == -1) {
                    runStart = y;
                }
                continue; // 배경이면 다음 y로 넘어감.
            }

            if (runStart != -1) { // 직전까진 배경이었는데 이번 y부턴 그림으로 바뀜.
                registerRunIfValid(cutPoints, runStart, y, lastCut); // 방금 끝난 배경구간을 컷 후보로 등록 시도.
                if (!cutPoints.isEmpty()) { // 조건을 만족할시 cutpoints에 추가해서 lastcut을 그 새 값으로 갱신.
                    lastCut = cutPoints.get(cutPoints.size() - 1);
                }
                runStart = -1; // 배경 구간 안에 있다는 표시 지우기 (리셋). 새 시작점을 기록하기 위함.
            }
        }

        // 이미지가 여백으로 끝나는 경우도 처리
        if (runStart != -1) {
            registerRunIfValid(cutPoints, runStart, height, lastCut);
        }

        return cutPoints; // 가위질할 설계도를 다음 작업자에게 넘기기 위해 리스트로 반환.
    }

    // runEnd : 그 배경 구간이 끝난 y 좌표 = height (그림이 다시 시작된 지점)
    // lastCut: 가장 최근에 확정된 컷 좌표.
    private void registerRunIfValid(List<Integer> cutPoints, int runStart, int runEnd, int lastCut) {
        int runLength = runEnd - runStart;
        if (runLength < minGapHeight) {
            return; // 너무 짧은 여백(그림 속 흰 셔츠 한 줄 등)은 컷 경계로 보지 않음
        }
        int mid = (runStart + runEnd) / 2; // 컷 사이 간격이 최소를 넘겼으면 정중앙으로 선 긋기.
        if (mid - lastCut < minPanelHeight) {
            return; // 직전 컷과 너무 가까우면 과도한 분할이므로 무시
        }
        cutPoints.add(mid);
    }
}
