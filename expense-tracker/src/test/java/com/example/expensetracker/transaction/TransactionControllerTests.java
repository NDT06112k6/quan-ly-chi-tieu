package com.example.expensetracker.transaction;

import com.example.expensetracker.category.CategoryDTO;
import com.example.expensetracker.category.CategoryType;
import com.example.expensetracker.common.util.CategoryViewHelper;
import com.example.expensetracker.config.SecurityConfig;
import com.example.expensetracker.user.CurrentUserService;
import com.example.expensetracker.user.UserProfileDTO;
import com.example.expensetracker.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
@MockitoBean(types = UserDetailsService.class)
class TransactionControllerTests {

    private static final Long USER_ID = 42L;
    private static final Long TRANSACTION_ID = 13L;
    private static final Long CATEGORY_ID = 7L;
    private static final String USERNAME = "transaction-view@example.invalid";
    private static final String DETAIL_PATH = "/transactions/13";
    private static final String EDIT_PATH = "/transactions/13/edit";
    private static final String DELETE_PATH = "/transactions/13/delete";
    private static final BigDecimal AMOUNT = new BigDecimal("123.45");
    private static final LocalDate OCCURRED_ON = LocalDate.of(2026, 10, 25);
    private final MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private CategoryViewHelper categoryViewHelper;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private UserService userService;

    @Autowired
    TransactionControllerTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @BeforeEach
    void configureAuthenticatedUser() {
        when(currentUserService.getUserId(any(Authentication.class))).thenReturn(USER_ID);
        when(currentUserService.getUserId()).thenReturn(USER_ID);
        when(userService.getProfile(USERNAME)).thenReturn(new UserProfileDTO(USERNAME, "Minh"));
    }

    @ParameterizedTest
    @ValueSource(strings = {DETAIL_PATH, EDIT_PATH})
    void requiresAuthenticationToReadTransactionPages(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(transactionService, categoryViewHelper);
    }

    @ParameterizedTest
    @CsvSource({"CHI, text-danger", "THU, text-success"})
    void rendersDetailUsingActualCategoryTypeAndAuthenticatedOwner(CategoryType categoryType, String color)
            throws Exception {
        TransactionDetailDTO detail = new TransactionDetailDTO(
                TRANSACTION_ID, AMOUNT, OCCURRED_ON, "Food", categoryType, "Lunch");
        when(transactionService.getTransactionDetail(TRANSACTION_ID, USER_ID)).thenReturn(detail);

        MvcResult result = mockMvc.perform(get(DETAIL_PATH).with(user(USERNAME)).param("userId", "99"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction/detail"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("Chi tiết giao dịch", "123.45", "2026-10-25", "Food", "Lunch", "id=\"app-sidebar\"");
        assertThat(html).containsPattern("class=\"[^\"]*fw-bold[^\"]*" + color + "[^\"]*\" id=\"transaction-amount\"");
        assertThat(html).contains("href=\"/transactions\"", "href=\"" + EDIT_PATH + "\"",
                "method=\"post\" action=\"" + DELETE_PATH + "\"", "name=\"_csrf\"");
        assertThat(html).containsOnlyOnce("aria-current=\"page\"");
        verify(transactionService).getTransactionDetail(TRANSACTION_ID, USER_ID);
    }

    @Test
    void escapesCategoryAndNoteContent() throws Exception {
        TransactionDetailDTO detail = new TransactionDetailDTO(
                TRANSACTION_ID, AMOUNT, OCCURRED_ON, "<b>Food</b>", CategoryType.CHI, "<script>alert(1)</script>");
        when(transactionService.getTransactionDetail(TRANSACTION_ID, USER_ID)).thenReturn(detail);

        MvcResult result = mockMvc.perform(get(DETAIL_PATH).with(user(USERNAME)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("&lt;b&gt;Food&lt;/b&gt;", "&lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("<b>Food</b>", "<script>alert(1)</script>");
    }

    @Test
    void rendersAnAbsentNoteWithoutLosingTransactionDetails() throws Exception {
        TransactionDetailDTO detail = new TransactionDetailDTO(
                TRANSACTION_ID, AMOUNT, OCCURRED_ON, "Food", CategoryType.CHI, null);
        when(transactionService.getTransactionDetail(TRANSACTION_ID, USER_ID)).thenReturn(detail);

        MvcResult result = mockMvc.perform(get(DETAIL_PATH).with(user(USERNAME)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("Không có ghi chú.", "123.45");
    }

    @ParameterizedTest
    @ValueSource(longs = {13L, 999L})
    void rendersForbiddenForMissingOrForeignTransaction(Long transactionId) throws Exception {
        when(transactionService.getTransactionDetail(transactionId, USER_ID))
                .thenThrow(new AccessDeniedException("Private transaction details"));

        MvcResult result = mockMvc.perform(get("/transactions/{id}", transactionId).with(user(USERNAME)))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/error"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("Private transaction details");
        verify(transactionService).getTransactionDetail(transactionId, USER_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number"})
    void rejectsInvalidDetailIds(String transactionId) throws Exception {
        mockMvc.perform(get("/transactions/{id}", transactionId).with(user(USERNAME)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void includesAnEyeIconAndDetailLinkOnEachListRow() throws Exception {
        TransactionDTO dto = createTransactionDTO();
        when(transactionService.getTransactionsByUserId(eq(USER_ID), any(TransactionFilterDTO.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto)));
        doAnswer(invocation -> {
            Model model = invocation.getArgument(0);
            model.addAttribute("categoryNames", Map.of(CATEGORY_ID, "Food"));
            model.addAttribute("categoryTypes", Map.of(CATEGORY_ID, CategoryType.CHI.name()));
            return null;
        }).when(categoryViewHelper).populateCategoryDetails(any(Model.class), eq(USER_ID), eq(Set.of(CATEGORY_ID)));

        MvcResult result = mockMvc.perform(get("/transactions").with(user(USERNAME)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("href=\"" + DETAIL_PATH + "\"",
                "Xem chi tiết", "aria-label=\"Xem chi tiết giao dịch 13\"", "<circle cx=\"12\" cy=\"12\" r=\"3\"");
    }

    @Test
    void rendersTheOwnedTransactionInTheReusableEditForm() throws Exception {
        when(transactionService.getTransactionForEdit(TRANSACTION_ID, USER_ID)).thenReturn(createTransactionDTO());
        stubCategoryOptions();

        MvcResult result = mockMvc.perform(get(EDIT_PATH).with(user(USERNAME)))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction/form"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("Chỉnh sửa giao dịch", "value=\"123.45\"",
                "action=\"" + EDIT_PATH + "\"", "name=\"_csrf\"", "Quay lại chi tiết");
        verify(transactionService).getTransactionForEdit(TRANSACTION_ID, USER_ID);
    }

    @Test
    void rejectsReadingAnotherUsersEditForm() throws Exception {
        when(transactionService.getTransactionForEdit(TRANSACTION_ID, USER_ID))
                .thenThrow(new AccessDeniedException("Denied"));

        mockMvc.perform(get(EDIT_PATH).with(user(USERNAME)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryViewHelper);
    }

    @Test
    void updatesUsingThePathIdAndCurrentUserRatherThanSubmittedOwnership() throws Exception {
        mockMvc.perform(validEditRequest().param("id", "999").param("userId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETAIL_PATH));

        ArgumentCaptor<TransactionDTO> dtoCaptor = ArgumentCaptor.forClass(TransactionDTO.class);
        verify(transactionService).updateTransaction(eq(TRANSACTION_ID), dtoCaptor.capture(), eq(USER_ID));
        assertThat(dtoCaptor.getValue().getId()).isNull();
        assertThat(dtoCaptor.getValue().getAmount()).isEqualTo(AMOUNT);
    }

    @Test
    void retainsTheEditActionAndFieldErrorsWhenValidationFails() throws Exception {
        when(transactionService.getTransactionForEdit(TRANSACTION_ID, USER_ID)).thenReturn(createTransactionDTO());
        stubCategoryOptions();

        MvcResult result = mockMvc.perform(validEditRequest("0"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("transaction/form"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("Số tiền phải lớn hơn 0.",
                "action=\"" + EDIT_PATH + "\"", "name=\"_csrf\"");
        verify(transactionService).getTransactionForEdit(TRANSACTION_ID, USER_ID);
    }

    @Test
    void rejectsAnUnauthorizedUpdate() throws Exception {
        doThrow(new AccessDeniedException("Denied"))
                .when(transactionService).updateTransaction(eq(TRANSACTION_ID), any(TransactionDTO.class), eq(USER_ID));

        mockMvc.perform(validEditRequest())
                .andExpect(status().isForbidden());
    }

    @Test
    void deletesWithCsrfAndRedirectsToTheList() throws Exception {
        mockMvc.perform(post(DELETE_PATH).with(user(USERNAME)).with(csrf()).param("userId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"));

        verify(transactionService).deleteTransaction(TRANSACTION_ID, USER_ID);
    }

    @Test
    void rejectsDeletingAnotherUsersTransaction() throws Exception {
        doThrow(new AccessDeniedException("Denied"))
                .when(transactionService).deleteTransaction(TRANSACTION_ID, USER_ID);

        mockMvc.perform(post(DELETE_PATH).with(user(USERNAME)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {EDIT_PATH, DELETE_PATH})
    void rejectsWritesWithoutCsrf(String path) throws Exception {
        mockMvc.perform(post(path).with(user(USERNAME)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(transactionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {EDIT_PATH, DELETE_PATH})
    void rejectsAnonymousWritesWithAValidCsrfToken(String path) throws Exception {
        mockMvc.perform(post(path).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void neverDeletesThroughGet() throws Exception {
        mockMvc.perform(get(DELETE_PATH).with(user(USERNAME)))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(transactionService);
    }

    private TransactionDTO createTransactionDTO() {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(TRANSACTION_ID);
        dto.setAmount(AMOUNT);
        dto.setCategoryId(CATEGORY_ID);
        dto.setOccurredOn(OCCURRED_ON);
        dto.setNote("Lunch");
        return dto;
    }

    private MockHttpServletRequestBuilder validEditRequest() {
        MockHttpServletRequestBuilder request = validEditRequest(AMOUNT.toPlainString());
        return request;
    }

    private MockHttpServletRequestBuilder validEditRequest(String amount) {
        MockHttpServletRequestBuilder request = post(EDIT_PATH).with(user(USERNAME)).with(csrf())
                .param("amount", amount)
                .param("occurredOn", OCCURRED_ON.toString())
                .param("categoryId", CATEGORY_ID.toString())
                .param("note", "Lunch");
        return request;
    }

    private void stubCategoryOptions() {
        CategoryDTO category = new CategoryDTO();
        category.setId(CATEGORY_ID);
        category.setName("Food");
        category.setType(CategoryType.CHI.name());
        doAnswer(invocation -> {
            Model model = invocation.getArgument(0);
            model.addAttribute("categories", List.of(category));
            model.addAttribute("hasMoreCategories", false);
            return null;
        }).when(categoryViewHelper).populateCategoryOptions(any(Model.class), eq(USER_ID), eq(CATEGORY_ID));
    }
}
