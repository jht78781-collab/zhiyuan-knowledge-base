package cn.enterprise.kb.user;

import cn.enterprise.kb.common.PageView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
class UserAdminController {
    record Create(@NotBlank(message = "请填写账号。") @Size(max = 64) String username,
                  @NotBlank(message = "请填写姓名。") @Size(max = 80) String displayName,
                  @NotBlank(message = "请填写初始密码。") @Size(max = 72) String password,
                  @NotNull(message = "请选择角色。") AccountService.Role role) {}
    record Change(@NotNull(message = "请选择角色。") AccountService.Role role,
                  @NotNull(message = "请选择账号状态。") Boolean enabled, @Size(max = 72) String password) {}

    private final AccountService users;
    UserAdminController(AccountService users) { this.users = users; }

    @GetMapping
    PageView<AccountService.UserView> list(@RequestParam(defaultValue = "0") int page) { return users.list(page); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountService.UserView create(@Valid @RequestBody Create input) {
        return users.create(input.username(), input.displayName(), input.password(), input.role());
    }

    @PutMapping("/{id}")
    AccountService.UserView update(@AuthenticationPrincipal AccountService.Identity actor, @PathVariable long id,
                                   @Valid @RequestBody Change input) {
        return users.change(actor.id(), id, input.role(), input.enabled(), input.password());
    }
}
