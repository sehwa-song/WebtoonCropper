import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

/**
 * 전체 흐름 오케스트레이터:
 * 에피소드의 여러 이미지를 이어붙여 -> GapFinder로 컷 경계 찾기 -> 작은 조각 병합
 * -> 컷 내부 여백 트리밍 -> 원본 이미지 번호 기준으로 이름 붙여 저장.
 */
public class WebtoonCropper {

    private static final double MOSTLY_BACKGROUND_RATIO = 0.97;
    private static final int CONTENT_PADDING = 20; // 그림 주변에 남겨둘 최소 여백

    private final RowClassifier classifier;
    private final GapFinder gapFinder;
    private final int minPanelHeight;

    public WebtoonCropper(RowClassifier classifier, GapFinder gapFinder, int minPanelHeight) {
        this.classifier = classifier;
        this.gapFinder = gapFinder;
        this.minPanelHeight = minPanelHeight;
    }

    /** 에피소드를 구성하는 이미지 파일들을 순서대로 이어붙여 컷 단위로 잘라 저장하고, 저장된 컷 수를 반환한다. */
    public int cropEpisode(List<Path> imageFiles, Path outputDir) throws IOException {
        List<Mat> pages = new ArrayList<>();
        List<FileSpan> fileSpans = new ArrayList<>();
        int cumulativeRows = 0;

        for (Path file : imageFiles) {
            Mat page = Imgcodecs.imread(file.toString());
            if (page.empty()) {
                System.out.println("스킵(읽기 실패): " + file);
                continue;
            }
            int fileNumber = parseFileNumber(file.getFileName().toString());
            fileSpans.add(new FileSpan(fileNumber, cumulativeRows, cumulativeRows + page.rows()));
            cumulativeRows += page.rows();
            pages.add(page);
        }
        if (pages.isEmpty()) {
            return 0;
        }

        Mat fullStrip = new Mat();
        Core.vconcat(pages, fullStrip);

        List<Integer> cuts = gapFinder.findCutPoints(fullStrip, classifier);

        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);
        boundaries.addAll(cuts);
        boundaries.add(fullStrip.rows());
        boundaries = mergeSmallSegments(boundaries);

        Files.createDirectories(outputDir);

        Map<Integer, Integer> panelCountPerFile = new LinkedHashMap<>();
        int savedCount = 0;

        for (int i = 0; i < boundaries.size() - 1; i++) {
            int top = boundaries.get(i);
            int bottom = boundaries.get(i + 1);
            if (bottom <= top) {
                continue;
            }

            Mat rawPanel = fullStrip.submat(top, bottom, 0, fullStrip.cols());
            if (isMostlyBackground(rawPanel)) {
                continue; // 내용 없이 여백뿐인 컷은 저장하지 않음
            }

            TrimResult trimmed = trimToContent(rawPanel, top);

            int fileNumber = findOwningFile(fileSpans, trimmed.absoluteTop());
            int indexInFile = panelCountPerFile.merge(fileNumber, 1, Integer::sum);

            String outName = String.format("%d_%d.jpg", fileNumber, indexInFile);
            Imgcodecs.imwrite(outputDir.resolve(outName).toString(), trimmed.mat());
            savedCount++;
        }

        return savedCount;
    }

    /** minPanelHeight보다 작은 세그먼트를 이웃과 합친다 (맨 끝에 남는 자투리 컷 포함). */
    private List<Integer> mergeSmallSegments(List<Integer> boundaries) {
        List<Integer> result = new ArrayList<>(boundaries);
        int i = 0;
        while (i < result.size() - 1 && result.size() > 2) {
            int height = result.get(i + 1) - result.get(i);
            if (height >= minPanelHeight) {
                i++;
                continue;
            }
            if (i == result.size() - 2) {
                result.remove(i); // 마지막 자투리는 바로 앞 컷에 흡수
                i = Math.max(i - 1, 0);
            } else {
                result.remove(i + 1); // 다음 컷과 합쳐서 흡수
            }
        }
        return result;
    }

    /**
     * 컷 위/아래의 배경 줄을 걷어내고, 실제 그림 영역 주변에 약간의 패딩만 남긴다.
     * segmentTop은 panel이 fullStrip에서 시작하는 절대 row로, 트리밍 후 실제 내용이
     * 어느 원본 파일에 속하는지(findOwningFile) 정확히 판단하기 위해 필요하다.
     */
    private TrimResult trimToContent(Mat panel, int segmentTop) {
        int rows = panel.rows();

        int top = 0;
        while (top < rows && classifier.isBackgroundRow(panel.row(top))) {
            top++;
        }
        int bottom = rows - 1;
        while (bottom >= 0 && classifier.isBackgroundRow(panel.row(bottom))) {
            bottom--;
        }

        if (top > bottom) {
            return new TrimResult(panel, segmentTop); // 이론상 isMostlyBackground에서 이미 걸러짐
        }

        int paddedTop = Math.max(0, top - CONTENT_PADDING);
        int paddedBottom = Math.min(rows, bottom + 1 + CONTENT_PADDING);
        Mat trimmed = panel.submat(paddedTop, paddedBottom, 0, panel.cols());
        return new TrimResult(trimmed, segmentTop + top);
    }

    private boolean isMostlyBackground(Mat panel) {
        int backgroundRows = 0;
        int totalRows = panel.rows();
        for (int y = 0; y < totalRows; y++) {
            if (classifier.isBackgroundRow(panel.row(y))) {
                backgroundRows++;
            }
        }
        return backgroundRows / (double) totalRows > MOSTLY_BACKGROUND_RATIO;
    }

    private static int findOwningFile(List<FileSpan> fileSpans, int row) {
        for (FileSpan span : fileSpans) {
            if (row >= span.startRow && row < span.endRow) {
                return span.fileNumber;
            }
        }
        return fileSpans.get(fileSpans.size() - 1).fileNumber;
    }

    private static int parseFileNumber(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot == -1 ? fileName : fileName.substring(0, dot);
        return Integer.parseInt(base);
    }

    private record FileSpan(int fileNumber, int startRow, int endRow) {
    }

    private record TrimResult(Mat mat, int absoluteTop) {
    }
}
