package com.example.expensetracker.home;

import com.example.expensetracker.transaction.TransactionService;
import com.example.expensetracker.user.CurrentUserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
public class DashboardApiController {

    public static final String CHART_PATH = "/api/dashboard/chart";
    private static final String DEFAULT_PAGE = "0";
    private static final String DEFAULT_PAGE_SIZE = "100";
    private static final String HAS_NEXT_HEADER = "X-Has-Next";

    private final TransactionService transactionService;
    private final CurrentUserService currentUserService;

    public DashboardApiController(TransactionService transactionService, CurrentUserService currentUserService) {
        this.transactionService = transactionService;
        this.currentUserService = currentUserService;
    }

    @GetMapping(value = CHART_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CategoryExpenseDTO>> getChartData(Authentication authentication,
            @Min(0) @RequestParam(defaultValue = DEFAULT_PAGE) int page,
            @Min(1) @Max(TransactionService.MAX_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size) {
        Long userId = currentUserService.getUserId(authentication);
        YearMonth currentMonth = YearMonth.now();
        Pageable pageable = PageRequest.of(page, size);
        Page<CategoryExpenseDTO> expenses = transactionService.getExpensesByCategory(userId, currentMonth, pageable);
        ResponseEntity<List<CategoryExpenseDTO>> response = ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HAS_NEXT_HEADER, Boolean.toString(expenses.hasNext()))
                .body(expenses.getContent());
        return response;
    }
}
