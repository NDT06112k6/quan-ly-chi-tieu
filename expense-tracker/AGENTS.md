# Quy tắc dự án Expense Tracker (Dành cho AI Agent)

## 1. Bối cảnh và điểm vào (Context & Entry Points)
- Dự án này là một ứng dụng Web Spring Boot tên là **Expense Tracker** (Quản lý chi tiêu cá nhân).
- Công nghệ: Java 25, Spring Boot 4.1.x, Thymeleaf, MySQL 8.0.
- File khởi chạy chính (Entry point): `src/main/java/com/example/expensetracker/ExpenseTrackerApplication.java`.
- File cấu hình cốt lõi: `src/main/resources/application.properties` (chứa cấu hình DB, JPA, Flyway).
- Các file chứa logic cốt lõi:
    - `src/main/resources/db/migration/V1__init_schema.sql`: Chứa toàn bộ cấu trúc bảng cơ sở dữ liệu.
    - Thư mục `src/main/java/com/example/expensetracker/`: Chứa code chia theo feature (user, category, transaction, budget).
- Mọi thay đổi về nghiệp vụ hay cơ sở dữ liệu phải bắt đầu từ việc đọc hiểu các file trên.

## 2. Stack & Kiến trúc (Bắt buộc)
- **Stack:** Java 25, Spring Boot 4.1.x, Maven, MySQL 8.0, Flyway.
- **Dependencies cốt lõi:** WebMVC, Thymeleaf, Security, JPA, Validation, Flyway, MySQL, DevTools. Không tự ý thêm thư viện khác nếu chưa hỏi.
- **Kiến trúc:** Chia package theo feature (user, category, transaction, budget). Controller chỉ nhận/trả DTO, logic ở Service, truy vấn ở Repository. Không trả Entity ra API.
- **Khởi tạo:** Luôn dùng Constructor Injection. Không dùng Field Injection (`@Autowired` trên biến).

## 3. Nghiệp vụ cốt lõi (Cực kỳ quan trọng)
- **Tiền tệ:** Mọi trường lưu tiền (amount, limit...) trong Java **bắt buộc dùng `BigDecimal`**. Trong SQL dùng `DECIMAL(15,2)`. Tuyệt đối không dùng `float` hay `double`.
- **Cô lập dữ liệu:** Mọi câu truy vấn (SELECT/UPDATE/DELETE) liên quan đến transactions, categories, budgets **bắt buộc phải có điều kiện `user_id`** của người đang đăng nhập.
- **Database:** Mọi thay đổi schema phải qua Flyway (`V{n}__mota.sql`). Cấu hình `spring.jpa.hibernate.ddl-auto=validate`. Không nối chuỗi SQL.

## 4. Coding Style (Viết code rõ ràng)
- Viết code rõ ràng, tường minh, **không viết gộp (không dùng one-liner)**.
- Không lồng ghép nhiều lệnh trên cùng một dòng. Mỗi lệnh nên viết trên một dòng riêng biệt.
- Nếu phải dùng Stream API (`.stream()`), hãy xuống dòng ở mỗi bước (`.filter()`, `.map()`, v.v.) để dễ đọc. (Ưu tiên dùng vòng lặp `for` thông thường nếu thấy dễ hiểu hơn).
- Tách biến rõ ràng thay vì viết trực tiếp vào câu lệnh `return`.
- Luôn dùng dấu ngoặc nhọn `{}` cho các câu lệnh `if`, `for`, `while` kể cả khi chỉ có 1 dòng bên trong.

## 5. Cách sửa lỗi (Error Handling & Debug)
- Khi sửa lỗi, xác định rõ đầu vào, cấu hình, trạng thái cache và bước đầu tiên bị sai.
- Sửa nguyên nhân gốc rễ (root cause) ở module sở hữu hành vi đó, tránh dùng fallback UI để che giấu lỗi dữ liệu.
- Khi một bước bắt buộc thất bại, không tiếp tục chạy với dữ liệu không hợp lệ. Giữ nguyên lỗi; không âm thầm biến lỗi API thành kết quả rỗng (không fake success).
- Khi thay đổi mặc định hoặc hành vi công khai, phải cập nhật tài liệu và nêu rõ ảnh hưởng đến cấu hình hoặc cache.

## 6. Bảo mật
- Mật khẩu chỉ qua `DelegatingPasswordEncoder`. Không log mật khẩu/token/PII.
- Mặc định từ chối (deny-by-default); mọi endpoint phải khai báo quyền.
- Không hardcode secret; đọc từ biến môi trường.

## 7. Definition of Done
- Có test; `./mvnw verify` xanh.
- Không thêm dependency mới nếu chưa hỏi người dùng.

## 8. Quy chuẩn Doanh nghiệp (Enterprise Standards)
- **Xử lý lỗi tập trung (Global Exception Handling):**
  - Tuyệt đối không dùng khối `try-catch` bừa bãi trong Controller để bắt lỗi nghiệp vụ.
  - Sử dụng `@ControllerAdvice` và `@ExceptionHandler` để bắt lỗi toàn cục.
  - Trả về một cấu trúc lỗi (Error Response) thống nhất cho toàn bộ API (ví dụ: mã lỗi, thông điệp, thời gian xảy ra).
- **Phân trang (Pagination):**
  - Tuyệt đối không dùng lệnh `findAll()` không có tham số để lấy toàn bộ danh sách (tránh sập server khi dữ liệu lớn).
  - Mọi API trả về danh sách (giao dịch, danh mục, ngân sách) bắt buộc phải sử dụng `Pageable` của Spring Data JPA để phân trang.
- **Validation (Kiểm tra dữ liệu đầu vào):**
  - Mọi DTO nhận dữ liệu từ người dùng (Request DTO) phải được đánh dấu `@Valid` ở tầng Controller.
  - Sử dụng các Annotation ràng buộc như `@NotNull`, `@NotBlank`, `@Size`, `@Positive` (cho số tiền) để chặn dữ liệu rác ngay từ cửa ngõ.

## 9. Clean Code (Code sạch)
- **Đặt tên (Naming):** Tên biến, tên hàm, tên class phải rõ nghĩa, dùng tiếng Anh, không viết tắt tùy tiện (ví dụ: dùng `transactionAmount` thay vì `tAmt`). Tên Class dùng danh từ, tên hàm dùng động từ.
- **Hàm ngắn gọn (Single Responsibility):** Mỗi hàm chỉ nên làm đúng 1 việc. Nếu hàm quá dài (trên 30 dòng) hoặc có quá 3 tham số truyền vào, phải tách nhỏ hàm hoặc dùng DTO.
- **Không Hardcode (Magic Numbers/Strings):** Tuyệt đối không viết thẳng các con số hoặc chuỗi vào logic (ví dụ: `if (type == "CHI")`). Phải sử dụng `Enum` hoặc hằng số (`public static final String TYPE_EXPENSE = "CHI"`).
- **Comment (Bình luận):** Code phải tự giải thích ý nghĩa của nó. Comment chỉ dùng để giải thích **"Tại sao" (Why)** làm như vậy (ví dụ: tại sao phải dùng thuật toán này), không comment giải thích **"Cái gì" (What)**.
- **Xóa code rác:** Không để lại các đoạn code đã comment out (ví dụ: `// int a = 5;`) hay các biến/hàm không dùng đến trong dự án.
- **Nguyên tắc DRY (Don't Repeat Yourself):** Nếu thấy một đoạn logic bị lặp lại từ 2 lần trở lên ở các class khác nhau, phải tách nó ra thành hàm dùng chung (trong thư mục `common/util`).