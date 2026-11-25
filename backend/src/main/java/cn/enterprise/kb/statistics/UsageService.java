package cn.enterprise.kb.statistics;

import cn.enterprise.kb.common.ApiProblem;
import cn.enterprise.kb.dify.DifyChatClient.Usage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageService {
    enum Status { RUNNING, SUCCESS, FAILED }
    public record Summary(LocalDate from, LocalDate to, String zone, long calls, long succeeded, long failed, long running,
                          Long averageMs, long timed, Long inputTokens, Long outputTokens, Long totalTokens, long missingTotalTokens) {}

    private final AiCallRepository calls;
    private final ZoneId zone;

    UsageService(AiCallRepository calls, @Value("${APP_STATS_TIME_ZONE:Asia/Shanghai}") String zone) {
        this.calls = calls;
        this.zone = ZoneId.of(zone);
    }

    @Transactional
    public void begin(long messageId, Instant at) {
        var call = new AiCall();
        call.messageId = messageId;
        call.status = Status.RUNNING;
        call.calledAt = at;
        calls.save(call);
    }

    @Transactional
    public void end(long messageId, Long elapsedMs, Usage usage, String errorCode) {
        var call = calls.findById(messageId).orElseThrow();
        call.status = errorCode == null ? Status.SUCCESS : Status.FAILED;
        call.latencyMs = elapsedMs;
        call.errorCode = errorCode;
        if (usage != null) {
            call.inputTokens = usage.input();
            call.outputTokens = usage.output();
            call.totalTokens = usage.total();
        }
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to) {
        if (to == null) to = LocalDate.now(zone);
        if (from == null) from = to.minusDays(13);
        if (from.getYear() < 1970 || to.getYear() > 9998 || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw ApiProblem.bad("请选择有效的日期范围，最多 366 天。");
        }
        var result = calls.aggregate(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant(),
                Status.SUCCESS, Status.FAILED);
        long terminal = result.getSuccesses() + result.getFailures();
        return new Summary(from, to, zone.getId(), result.getCallCount(), result.getSuccesses(), result.getFailures(),
                result.getCallCount() - terminal, result.getAverageLatency() == null ? null : Math.round(result.getAverageLatency()),
                result.getKnownLatencies(), result.getInputTokens(), result.getOutputTokens(), result.getTotalTokens(),
                terminal - result.getKnownTokens());
    }
}
