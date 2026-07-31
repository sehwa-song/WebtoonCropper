import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

public class App {
    public static void main(String[] args) {
        // 1. OpenCV 핵심 라이브러리 로드 (필수)
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        System.out.println("✅ OpenCV 라이브러리 로드 성공!");

        // 2. 테스트할 이미지 경로 설정
        // 앞서 만드신 폴더 구조에 맞춰서 이미지 하나를 지정해 줍니다.
        String imagePath = "src/images/001/005.jpg"; 
        
        // 3. 이미지를 다차원 배열(Matrix) 형태로 읽어오기
        Mat image = Imgcodecs.imread(imagePath);

        // 4. 결과 출력
        if (image.empty()) {
            System.out.println("❌ 이미지를 찾을 수 없습니다. 경로를 확인하세요.");
        } else {
            System.out.println("✅ 이미지 읽기 성공!");
            System.out.println("가로 해상도: " + image.width() + " px");
            System.out.println("세로 해상도: " + image.height() + " px");
        }
    }
}