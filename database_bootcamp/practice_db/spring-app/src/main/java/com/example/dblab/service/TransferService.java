package com.example.dblab.service;

import com.example.dblab.domain.Account;
import com.example.dblab.domain.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 부트캠프 Week 3 시연용 송금 서비스.
 *  - 비관적 잠금 (잠금 순서 일관성)
 *  - 낙관적 잠금 (@Version)
 *  - 재시도
 *  - rollbackFor = Exception.class
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AccountRepository repo;

    /**
     * 비관적 잠금 + 순서 일관성으로 데드락 회피.
     */
    @Transactional(rollbackFor = Exception.class)
    public void transferPessimistic(Integer fromId, Integer toId, BigDecimal amount) {
        // 잠금 순서: 항상 작은 ID 먼저
        Integer firstId = Math.min(fromId, toId);
        Integer secondId = Math.max(fromId, toId);

        Account first = repo.findByIdForUpdate(firstId);
        Account second = repo.findByIdForUpdate(secondId);

        Account from = first.getId().equals(fromId) ? first : second;
        Account to   = first.getId().equals(toId)   ? first : second;

        from.withdraw(amount);
        to.deposit(amount);
        log.info("Pessimistic transfer {} -> {} ({})", fromId, toId, amount);
    }

    /**
     * 낙관적 잠금 + 자동 재시도.
     */
    @Retryable(
        retryFor = {
            OptimisticLockingFailureException.class,
            DeadlockLoserDataAccessException.class,
            CannotAcquireLockException.class
        },
        maxAttempts = 5,
        backoff = @Backoff(delay = 50, multiplier = 2)
    )
    @Transactional(rollbackFor = Exception.class)
    public void transferOptimistic(Integer fromId, Integer toId, BigDecimal amount) {
        Account from = repo.findById(fromId).orElseThrow();
        Account to   = repo.findById(toId).orElseThrow();
        from.withdraw(amount);
        to.deposit(amount);
        log.info("Optimistic transfer {} -> {} ({})", fromId, toId, amount);
    }
}
