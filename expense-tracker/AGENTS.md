# Expense Tracker – Quy tắc cho AI Agent (Codex)

## Stack (bắt buộc, không tự đổi)
- Java 25, Spring Boot 4.1.x, Maven wrapper, MySQL 8.0 (dùng nội bộ, nâng cấp 8.4 sau), Flyway.
- Các dependency cốt lõi: Spring WebMVC, Thymeleaf, Spring Security, Spring Data JPA, Validation, Flyway Migration, MySQL Driver, DevTools.
- Boot 4 đã tách starter: dùng `spring-boot-starter-webmvc` (KHÔNG dùng `starter-web`), `spring-boot-starter-flyway` + `flyway-mysql`. Boot 4 dùng Jackson 3: nếu không chắc package/API, tra Migration Guide thay vì đoán.
- Spring Security 7: cấu hình bằng SecurityFilterChain bean, lambda DSL.

## Kiến trúc
- Package theo feature; Controller chỉ nhận/trả DTO, logic ở Service, truy vấn ở Repository.
- Không trả Entity ra API. Không dùng field injection.

## Quy tắc viết code (Coding Style)
- Viết code rõ ràng, tường minh, không viết gộp (không dùng one-liner).
- Không lồng ghép nhiều lệnh trên cùng một dòng. Mỗi lệnh nên viết trên một dòng riêng biệt.
- Nếu phải dùng Stream API (`.stream()`), hãy xuống dòng ở mỗi bước (`.filter()`, `.map()`, v.v.) để dễ đọc.
- Tách biến rõ ràng thay vì viết trực tiếp vào câu lệnh `return`.
- Luôn dùng dấu ngoặc nhọn `{}` cho các câu lệnh `if`, `for`, `while` kể cả khi chỉ có 1 dòng bên trong.

## Quy tắc nghiệp vụ (Domain Rules - CỰC KỲ QUAN TRỌNG)
- **Tiền tệ:** Mọi trường liên quan đến tiền (số tiền giao dịch, hạn mức ngân sách) trong Java phải dùng `BigDecimal`. Trong SQL dùng `DECIMAL(15,2)`. Tuyệt đối không dùng `float` hay `double`.
- **Cô lập dữ liệu:** Mọi câu truy vấn (SELECT/UPDATE/DELETE) liên quan đến giao dịch (transactions), danh mục (categories), ngân sách (budgets) BẮT BUỘC phải kèm điều kiện `user_id` của người dùng đang đăng nhập. Người dùng không được phép xem/sửa dữ liệu của người khác.

## Cơ sở dữ liệu
- Mọi thay đổi schema qua Flyway (`V{n}__mota.sql`). Không sửa migration đã áp dụng.
- `spring.jpa.hibernate.ddl-auto=validate`. Không nối chuỗi SQL (tránh SQL Injection).

## Bảo mật
- Mật khẩu chỉ qua DelegatingPasswordEncoder. Không log mật khẩu/token/PII.
- Mặc định từ chối (deny-by-default); mọi endpoint phải khai báo quyền; mọi truy cập bản ghi phải kiểm tra chủ sở hữu.
- Không hardcode secret; đọc từ biến môi trường.

## Definition of Done
- Có test; `./mvnw verify` xanh.
- Không thêm dependency mới nếu chưa hỏi người dùng.