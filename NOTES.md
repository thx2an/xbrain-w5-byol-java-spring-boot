# NOTES — BYOL Java Spring Boot on Lambda

## 1. Strategy đã chọn

**Strategy A: aws-serverless-java-container-springboot3**

- Sử dụng thư viện `aws-serverless-java-container-springboot3` của AWS Labs.
- Khởi tạo một Lambda Container Handler đóng vai trò như một proxy. Proxy này nhận các HTTP API event từ API Gateway, chuyển đổi chúng thành các Java Servlet request tiêu chuẩn, đưa vào Spring Boot context, lấy kết quả và trả về lại dạng API Gateway response.

## 2. Lý do chọn

1. **Canonical approach (Cách tiếp cận chuẩn):** Đây là phương pháp được AWS chính thức hỗ trợ và khuyến nghị để đưa các ứng dụng Spring Boot lên Lambda.
2. **Không xâm lấn Application Layer (Zero logic changes):**
   - Không cần sửa đổi bất kỳ class nào trong application code gốc (`Application.java` hay `HelloController.java`).
   - Tách biệt hoàn toàn lớp adapter của Lambda ra một package riêng (`dev.byol.lambda`).
3. **Tối thiểu hóa thay đổi:**
   - Chỉ thêm duy nhất 1 file handler mới (`StreamLambdaHandler.java`).
   - Cập nhật `pom.xml` (thêm dependency và cấu hình shade plugin).
   - Cập nhật `template.yaml` để trỏ vào handler mới.
   - Thêm một `Makefile` nhỏ để SAM có thể build đúng định dạng (do một số phiên bản SAM CLI mới bỏ hỗ trợ maven trực tiếp).
4. **Tận dụng tối đa sức mạnh của Spring MVC:** Spring Boot context chỉ cần khởi tạo 1 lần (trong block `static` của handler) để sẵn sàng cho các "warm start". Sau đó, mọi routing, validation, serialization của Spring MVC hoạt động hoàn hảo y như khi chạy trên Tomcat.
5. **Cộng đồng và tài liệu hỗ trợ mạnh mẽ:** Đây là phương pháp phổ biến nhất với đầy đủ tài liệu từ AWS.

### Vì sao không chọn các strategy khác?

| Strategy | Lý do từ chối |
|----------|----------------|
| **B — Lambda Web Adapter** | Mặc dù không cần sửa Java code, nhưng cold start sẽ bị cộng thêm khoảng ~200ms overhead so với cách A. Ít được ưa chuộng cho Java trong các dự án thực tế so với container handler native. |
| **C — Spring Cloud Function** | Yêu cầu phải đập đi xây lại (refactor) toàn bộ controllers thành các bean `Function<Input, Output>`. Quá tốn công, rủi ro cao và vi phạm nghiêm trọng tiêu chí "thay đổi ít nhất có thể". |
| **D — Plain aws-lambda-java-core** | Bắt buộc lập trình viên phải tự parse JSON event từ API Gateway và tự viết logic routing bằng tay. Mã nguồn sẽ phình to ra thêm hàng trăm dòng và không tái sử dụng được ecosystem của Spring MVC. |

## 3. Phân tích Cold Start

Sau khi deploy lên tài khoản AWS Workshop, đây là kết quả đo được:

- **Init Duration (Cold Start):** ~7,794 ms (khoảng 7.8 giây) cho request đầu tiên.
- **Warm Start:** ~3.6 - 3.8 ms cho các request tiếp theo.

**Nguyên nhân gây ra Cold Start chậm (không có SnapStart):**
1. **JVM Startup Time:** Khởi động máy ảo Java bản thân nó đã mất một khoảng thời gian đáng kể.
2. **Spring Boot Framework Tax:**
   - Spring Boot nổi tiếng là nặng nề trong quá trình khởi động.
   - Classpath scanning: Quét toàn bộ project để tìm các annotation (như `@RestController`).
   - Dependency Injection: Khởi tạo hàng loạt các beans và nhúng chúng vào nhau.
   - Auto-configuration: Tự động cấu hình các thành phần dựa trên classpath.
   - Embedded Server (dù không dùng Tomcat thực sự, quá trình setup mock servlet environment vẫn tốn tài nguyên).
   => Đây là chi phí của framework, hoàn toàn **không phải** do logic của ứng dụng chậm.

**Giải pháp khắc phục triệt để:**
- Bật tính năng **AWS Lambda SnapStart**.
- SnapStart hoạt động theo cơ chế: Nó cho phép hàm chạy hết quá trình khởi tạo (Init phase) bao gồm cả việc boot Spring, sau đó chụp một snapshot toàn bộ bộ nhớ và trạng thái của Firecracker microVM, rồi lưu lại thành các chunk được mã hóa.
- Khi có request mới (cold start mới), thay vì chạy lại từ đầu, Lambda chỉ việc "resume" (khôi phục) lại snapshot đó. Quá trình này cực nhanh, có thể giảm thời gian khởi động từ 7.8 giây xuống chỉ còn **~200–500 ms**.

## 4. Bảng tóm tắt các file đã thay đổi/thêm mới

| File | Hành động | Chi tiết |
|------|-----------|----------|
| `pom.xml` | Cập nhật | - Thêm dependency `aws-serverless-java-container-springboot3:2.1.2`<br>- Thay thế `spring-boot-maven-plugin` bằng `maven-shade-plugin` để đóng gói thành một "flat uber-jar" thay vì "nested-JAR" (do Lambda classloader không đọc được nested-JAR). |
| `template.yaml` | Cập nhật | - Chỉnh sửa `Handler: dev.byol.lambda.StreamLambdaHandler::handleRequest`<br>- Đổi `BuildMethod` thành `makefile`. |
| `Makefile` | Tạo mới | Cấu hình cho SAM sử dụng lệnh `mvn` trực tiếp, đi vòng qua lỗi tương thích của các phiên bản SAM CLI mới nhất. |
| `src/main/java/dev/byol/lambda/StreamLambdaHandler.java` | Tạo mới | Lớp Handler implements `RequestStreamHandler`, sử dụng `SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler()` để xử lý HttpApi payload v2. |
| `NOTES.md` | Tạo mới | Chính là tài liệu giải trình này. |
