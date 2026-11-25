package cn.enterprise.kb.chat;

import cn.enterprise.kb.common.ApiProblem;
import cn.enterprise.kb.common.SchemaReady;
import cn.enterprise.kb.dify.DifyChatClient;
import cn.enterprise.kb.dify.DifyFailure;
import cn.enterprise.kb.user.AccountService.Identity;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final ChatStore store;
    private final DifyChatClient dify;
    private final SchemaReady schema;

    ChatService(ChatStore store, DifyChatClient dify, SchemaReady schema) {
        this.store = store;
        this.dify = dify;
        this.schema = schema;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    void recover() { if (schema.chat()) store.recoverInterrupted(); }

    ChatStore.TurnView ask(Identity user, long kbId, long conversationId, UUID requestId, String question) {
        var started = store.begin(user, kbId, conversationId, requestId, question);
        if (started.existing() != null) return started.existing();
        var request = started.remote();
        long start = System.nanoTime();
        DifyChatClient.Answer answer = null;
        String code = null;
        String message = null;
        try {
            answer = dify.ask(request.alias(), user.id(), request.remoteConversation(), request.question());
        } catch (DifyFailure e) {
            code = e.code();
            message = e.getMessage();
        } catch (RuntimeException e) {
            log.error("Chat request {} failed ({})", request.turnId(), e.getClass().getSimpleName());
            code = "DIFY_FAILURE";
            message = "问答服务暂不可用，请稍后重试。";
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        var result = store.complete(request, answer, elapsed, code, message);
        if (result == null) throw ApiProblem.missing("会话不存在或访问权限已变更。");
        return result;
    }
}
