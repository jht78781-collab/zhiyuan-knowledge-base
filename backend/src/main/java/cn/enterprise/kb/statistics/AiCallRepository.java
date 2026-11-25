package cn.enterprise.kb.statistics;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AiCallRepository extends JpaRepository<AiCall, Long> {
    @Query("""
            select count(c) as callCount, coalesce(sum(case when c.status = :success then 1 else 0 end), 0) as successes,
            coalesce(sum(case when c.status = :failed then 1 else 0 end), 0) as failures,
            avg(c.latencyMs) as averageLatency, count(c.latencyMs) as knownLatencies,
            sum(c.inputTokens) as inputTokens, sum(c.outputTokens) as outputTokens, sum(c.totalTokens) as totalTokens,
            count(c.totalTokens) as knownTokens
            from AiCall c where c.calledAt >= :from and c.calledAt < :until
            """)
    Rollup aggregate(Instant from, Instant until, UsageService.Status success, UsageService.Status failed);

    interface Rollup {
        long getCallCount();
        long getSuccesses();
        long getFailures();
        Double getAverageLatency();
        long getKnownLatencies();
        Long getInputTokens();
        Long getOutputTokens();
        Long getTotalTokens();
        long getKnownTokens();
    }
}
