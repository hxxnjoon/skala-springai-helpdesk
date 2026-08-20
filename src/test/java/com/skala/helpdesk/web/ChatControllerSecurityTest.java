package com.skala.helpdesk.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.skala.helpdesk.advisor.AuditLog;
import com.skala.helpdesk.chat.AnswerDto;
import com.skala.helpdesk.chat.HelpDeskService;
import com.skala.helpdesk.config.SecurityConfig;
import com.skala.helpdesk.rag.IngestService;
import com.skala.helpdesk.repository.TicketRepository;

/**
 * 완료 기준 — /api/**는 인증이 필요하고 /api/admin/**는 ADMIN 롤만 접근할 수 있다. 모델을
 * 부르지 않는 웹 슬라이스라 무료로 돈다.
 */
@WebMvcTest(controllers = {ChatController.class, AdminController.class})
@Import(SecurityConfig.class)
class ChatControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HelpDeskService helpDeskService;
    @MockitoBean
    private VectorStore vectorStore;
    @MockitoBean
    private IngestService ingestService;
    @MockitoBean
    private TicketRepository tickets;
    @MockitoBean
    private AuditLog auditLog;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("인증 없이 채팅을 요청하면 401")
    void 인증_없이_요청하면_401() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new Body("s1", "안녕"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    @DisplayName("인증된 사용자는 채팅할 수 있다")
    void 인증된_사용자는_채팅_가능() throws Exception {
        when(helpDeskService.ask(any(), any(), any())).thenReturn(new AnswerDto("답변", List.of(), false));

        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new Body("s1", "안녕"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    @DisplayName("일반 사용자는 관리자 엔드포인트에 접근할 수 없다")
    void 일반_사용자는_관리자_엔드포인트_차단() throws Exception {
        mockMvc.perform(get("/api/admin/chunks").param("q", "반품"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin1", roles = {"USER", "ADMIN"})
    @DisplayName("관리자는 관리자 엔드포인트에 접근할 수 있다")
    void 관리자는_관리자_엔드포인트_접근_가능() throws Exception {
        when(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.<org.springframework.ai.vectorstore.SearchRequest>any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/chunks").param("q", "반품"))
                .andExpect(status().isOk());
    }

    private record Body(String sessionId, String message) {
    }
}
