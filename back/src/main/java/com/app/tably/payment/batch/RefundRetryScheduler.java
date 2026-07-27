package com.app.tably.payment.batch;

import com.app.tably.payment.entity.Payment;
import com.app.tably.payment.entity.PaymentStatus;
import com.app.tably.payment.entity.PaymentType;
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
public class RefundRetryScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    /**
     * FR-09: 환불 실패 재처리 배치 — "취소했는데 돈이 안 들어와요"를 시스템이 스스로 해소한다 (S4).
     * 건별 트랜잭션, 실패 건은 FAILED로 남아 다음 주기에 다시 시도된다.
     */
    @Scheduled(fixedDelay = 300_000)
    public void retryFailedRefunds() {
        List<Payment> failed = paymentRepository.findAllByTypeAndStatus(PaymentType.REFUND, PaymentStatus.FAILED);
        if (failed.isEmpty()) {
            return;
        }
        int succeeded = 0;
        for (Payment refund : failed) {
            try {
                if (paymentService.retryRefund(refund.getId())) {
                    succeeded++;
                }
            } catch (Exception e) {
                log.error("환불 재처리 실패 — payment {}", refund.getId(), e);
            }
        }
        log.info("환불 재처리 배치: 대상 {}건, 성공 {}건", failed.size(), succeeded);
    }
}
