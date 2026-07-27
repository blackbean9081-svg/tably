package com.app.tably.payment.batch;

import com.app.tably.payment.entity.Payment;
import com.app.tably.payment.entity.PaymentStatus;
import com.app.tably.payment.repository.PaymentRepository;
import com.app.tably.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconcileScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    /**
     * FR-07: UNKNOWN 결제 재확인 배치. 건별 트랜잭션 — 한 건의 실패가 나머지를 막지 않는다.
     * 확정 못 한 건은 UNKNOWN으로 남아 다음 주기에 다시 시도된다 (멱등).
     */
    @Scheduled(fixedDelay = 300_000)
    public void reconcileUnknownPayments() {
        List<Payment> unknowns = paymentRepository.findAllByStatus(PaymentStatus.UNKNOWN);
        if (unknowns.isEmpty()) {
            return;
        }
        int resolved = 0;
        for (Payment payment : unknowns) {
            try {
                if (paymentService.reconcile(payment.getId())) {
                    resolved++;
                }
            } catch (Exception e) {
                log.error("UNKNOWN 재확인 실패 — payment {}", payment.getId(), e);
            }
        }
        log.info("결제 재확인 배치: 대상 {}건, 확정 {}건", unknowns.size(), resolved);
    }
}
