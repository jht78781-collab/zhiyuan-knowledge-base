package cn.enterprise.kb.knowledge;

import cn.enterprise.kb.common.PageView;
import cn.enterprise.kb.user.AccountService.Identity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class KnowledgeController {
    record Create(@NotBlank(message = "请填写知识库名称。") @Size(max = 100) String name,
                  @NotNull @Size(max = 1000) String description,
                  @NotBlank(message = "请填写 Dify Dataset ID。") @Size(max = 40) String datasetId,
                  @NotBlank(message = "请填写 Dify 应用别名。") @Size(max = 64) String appAlias) {}
    record Edit(@NotBlank(message = "请填写知识库名称。") @Size(max = 100) String name,
                @NotNull @Size(max = 1000) String description) {}
    record Grant(@NotBlank(message = "请填写成员账号。") @Size(max = 64) String username,
                 @NotNull(message = "请选择权限。") KnowledgeService.Permission permission) {}

    private final KnowledgeService service;
    KnowledgeController(KnowledgeService service) { this.service = service; }

    @GetMapping("/knowledge-bases")
    PageView<KnowledgeService.View> list(@AuthenticationPrincipal Identity actor, @RequestParam(defaultValue = "0") int page) {
        return service.list(actor, page);
    }

    @GetMapping("/knowledge-bases/{id}")
    KnowledgeService.View detail(@AuthenticationPrincipal Identity actor, @PathVariable long id) {
        return service.detail(actor, id);
    }

    @PostMapping("/admin/knowledge-bases")
    @ResponseStatus(HttpStatus.CREATED)
    KnowledgeService.View create(@RequestBody @Valid Create input) {
        return service.create(input.name(), input.description(), input.datasetId(), input.appAlias());
    }

    @PutMapping("/knowledge-bases/{id}")
    KnowledgeService.View edit(@AuthenticationPrincipal Identity actor, @PathVariable long id, @RequestBody @Valid Edit input) {
        return service.update(actor, id, input.name(), input.description());
    }

    @GetMapping("/knowledge-bases/{id}/members")
    PageView<KnowledgeService.Member> members(@AuthenticationPrincipal Identity actor, @PathVariable long id,
                                              @RequestParam(defaultValue = "0") int page) {
        return service.members(actor, id, page);
    }

    @PostMapping("/knowledge-bases/{id}/members")
    KnowledgeService.Member grant(@AuthenticationPrincipal Identity actor, @PathVariable long id, @RequestBody @Valid Grant input) {
        return service.grant(actor, id, input.username(), input.permission());
    }

    @DeleteMapping("/knowledge-bases/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal Identity actor, @PathVariable long id, @PathVariable long userId) {
        service.revoke(actor, id, userId);
    }
}
