import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.opencv.core.Core;

public class App {
    public static void main(String[] args) throws IOException {
        // 1. OpenCV 핵심 라이브러리 로드 (필수)
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        System.out.println("OpenCV 라이브러리 로드 성공!");

        // 2. 처리할 에피소드 폴더 목록: 인자로 특정 에피소드를 주면 그것만, 없으면 전체(001~013) 처리
        Path imagesRoot = Paths.get("src/images");
        List<Path> episodeDirs;
        if (args.length > 0) {
            episodeDirs = List.of(imagesRoot.resolve(args[0]));
        } else {
            try (var stream = Files.list(imagesRoot)) {
                episodeDirs = stream.filter(Files::isDirectory).sorted().toList();
            }
        }

        double stdDevThreshold = 6.0; // 여백 판정 민감도 (낮을수록 엄격)
        int minGapHeight = 120;       // 이 높이 이상 이어져야 진짜 컷 경계로 인정 (캡션 박스 주변의 좁은 여백은 무시)
        int minPanelHeight = 80;      // 컷 하나의 최소 높이 (이보다 작은 조각은 이웃과 병합)

        RowClassifier classifier = new VarianceRowClassifier(stdDevThreshold);
        GapFinder gapFinder = new GapFinder(minGapHeight, minPanelHeight);
        WebtoonCropper cropper = new WebtoonCropper(classifier, gapFinder, minPanelHeight);

        for (Path episodeDir : episodeDirs) {
            String episode = episodeDir.getFileName().toString();
            Path outputDir = Paths.get("output_", episode);

            List<Path> imageFiles; 
            try (var stream = Files.list(episodeDir)) { // 이미지 파일들만 고르기.
                imageFiles = stream.filter(p -> p.toString().toLowerCase().endsWith(".jpg"))
                        .sorted()
                        .toList();
            }

            int totalPanels = cropper.cropEpisode(imageFiles, outputDir);
            System.out.println(episode + " 완료: 총 " + totalPanels + "개 컷 -> " + outputDir);
        }
    }
}
