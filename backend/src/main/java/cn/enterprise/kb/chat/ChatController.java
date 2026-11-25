package cn.enterprise.kb.chat;

import cn.enterprise.kb.common.PageView;
import cn.enterprise.kb.user.AccountService.Identity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/conversations")
class ChatController {
    record Question(@NotNull(message = "缺少请求标识。") UUID requestId,
                    @NotBlank(message = "请填写问题。") @Size(max = 2000, message = "问题最多 2000 个字符。") String question) {}

    private final ChatStore history;
    private final ChatService chat;
    ChatController(ChatStore history, ChatService chat) { this.history = history; this.chat = chat; }

    @GetMapping
    PageView<ChatStore.ConversationView> list(@AuthenticationPrincipal Identity actor, @PathVariable long kbId,
                                             @RequestParam(defaultValue = "0") int page) {
        return history.list(actor, kbId, page);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ChatStore.ConversationView create(@AuthenticationPrincipal Identity actor, @PathVariable long kbId) {
        return history.create(actor, kbId);
    }

    @GetMapping("/{id}")
    ChatStore.History history(@AuthenticationPrincipal Identity actor, @PathVariable long kbId, @PathVariable long id,
                              @RequestParam(defaultValue = "0") int page) {
        return history.history(actor, kbId, id, page);
    }

    @PostMapping("/{id}/messages")
    ChatStore.TurnView ask(@AuthenticationPrincipal Identity actor, @PathVariable long kbId, @PathVariable long id,
                          @RequestBody @Valid Question input) {
        return chat.ask(actor, kbId, id, input.requestId(), input.question());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Identity actor, @PathVariable long kbId, @PathVariable long id) {
        history.delete(actor, kbId, id);
    }
}
