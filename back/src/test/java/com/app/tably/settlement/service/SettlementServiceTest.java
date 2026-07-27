package com.app.tably.settlement.service;

import com.app.tably.common.exception.BusinessException;
import com.app.tably.common.exception.ErrorCode;
import com.app.tably.member.entity.Member;
import com.app.tably.member.entity.Role;
import com.app.tably.member.repository.MemberRepository;
import com.app.tably.payment.entity.Payment;
import com.app.tably.payment.entity.PaymentStatus;
import com.app.tably.payment.entity.PaymentType;
import com.app.tably.payment.repository.PaymentRepository;
import com.app.tably.reservation.entity.Reservation;
import com.app.tably.reservation.entity.ReservationStatus;
import com.app.tably.restaurant.entity.Restaurant;
import com.app.tably.restaurant.repository.RestaurantRepository;
import com.app.tably.settlement.entity.Settlement;
import com.app.tably.settlement.entity.SettlementItem;
import com.app.tably.settlement.entity.SettlementItemType;
import com.app.tably.settlement.repository.SettlementItemRepository;
import com.app.tably.settlement.repository.SettlementRepository;
import com.app.tably.slot.entity.Slot;
import com.app.tably.slot.entity.SlotStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    private static final YearMonth AUGUST = YearMonth.of(2026, 8);

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementItemRepository settlementItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private SettlementService settlementService;

    private Member owner() {
        return Member.builder().id(99L).email("owner@tably.com").password("pw").name("박성호").role(Role.OWNER).build();
    }

    private Restaurant restaurant() {
        return Restaurant.builder().id(10L).owner(owner()).name("스시 준").build();
    }

    private Reservation reservation(Long id, ReservationStatus status, LocalDate slotDate) {
        Member guest = Member.builder().id(1L).email("guest@tably.com").password("pw").name("김지현").role(Role.GUEST).build();
        Slot slot = Slot.builder().id(id + 1000).restaurant(restaurant())
                .slotDate(slotDate).slotTime(LocalTime.of(20, 30)).tableNo(1).status(SlotStatus.OPEN).build();
        return Reservation.builder().id(id).slot(slot).member(guest)
                .partySize(2).status(status).heldAt(LocalDateTime.now()).build();
    }

    private Payment approvedPay(Reservation reservation, int amount) {
        return Payment.builder().id(reservation.getId() + 500).reservation(reservation)
                .type(PaymentType.PAY).amount(amount).status(PaymentStatus.APPROVED)
                .idempotencyKey("key-" + reservation.getId()).pgTxId("tx").createdAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("월 정산 — 몰수 2건 + 철회 차감 1건: 수수료 10%, 마이너스 이월 반영 (S7)")
    void settleMonth_forfeitsAndRevoke() {
        Reservation noShow1 = reservation(1L, ReservationStatus.NO_SHOW, LocalDate.of(2026, 8, 15));
        Reservation noShow2 = reservation(2L, ReservationStatus.NO_SHOW, LocalDate.of(2026, 8, 20));
        Reservation revoked = reservation(3L, ReservationStatus.NO_SHOW_REVOKED, LocalDate.of(2026, 7, 10));

        given(settlementRepository.findByRestaurantIdAndSettlementMonth(10L, "2026-08"))
                .willReturn(Optional.empty());
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant()));
        given(settlementItemRepository.findForfeitCandidates(10L, AUGUST.atDay(1), AUGUST.atEndOfMonth(),
                ReservationStatus.NO_SHOW, SettlementItemType.FORFEIT))
                .willReturn(List.of(noShow1, noShow2));
        given(settlementItemRepository.findRevokeCandidates(10L, ReservationStatus.NO_SHOW_REVOKED,
                SettlementItemType.FORFEIT, SettlementItemType.REVOKE_ADJUSTMENT))
                .willReturn(List.of(revoked));
        given(paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                1L, PaymentType.PAY, PaymentStatus.APPROVED)).willReturn(Optional.of(approvedPay(noShow1, 40000)));
        given(paymentRepository.findFirstByReservationIdAndTypeAndStatusOrderByIdDesc(
                2L, PaymentType.PAY, PaymentStatus.APPROVED)).willReturn(Optional.of(approvedPay(noShow2, 20000)));
        // 7월 정산에서 몰수됐던 건 — 차감액은 그때 지급된 몫(40000 - 4000)
        given(settlementItemRepository.findByReservationIdAndType(3L, SettlementItemType.FORFEIT))
                .willReturn(Optional.of(SettlementItem.builder()
                        .reservation(revoked).type(SettlementItemType.FORFEIT).amount(40000).build()));
        given(settlementRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(settlementItemRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        Settlement settlement = settlementService.settleMonth(10L, AUGUST).orElseThrow();

        assertThat(settlement.getTotalForfeit()).isEqualTo(60000);
        assertThat(settlement.getTotalCommission()).isEqualTo(6000);
        assertThat(settlement.getTotalAdjustment()).isEqualTo(-36000);
        assertThat(settlement.getPayout()).isEqualTo(18000);
        assertThat(settlement.getSettlementMonth()).isEqualTo("2026-08");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SettlementItem>> captor = ArgumentCaptor.forClass(List.class);
        then(settlementItemRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
        assertThat(captor.getValue()).extracting(SettlementItem::getType)
                .containsExactly(SettlementItemType.FORFEIT, SettlementItemType.FORFEIT,
                        SettlementItemType.REVOKE_ADJUSTMENT);
    }

    @Test
    @DisplayName("이미 정산된 달은 그대로 반환 — 재실행해도 중복 지급 없음 (FR-17)")
    void settleMonth_rerunIsIdempotent() {
        Settlement done = Settlement.builder().id(5L).restaurant(restaurant()).settlementMonth("2026-08")
                .totalForfeit(60000).totalCommission(6000).totalAdjustment(0).payout(54000)
                .createdAt(LocalDateTime.now()).build();
        given(settlementRepository.findByRestaurantIdAndSettlementMonth(10L, "2026-08"))
                .willReturn(Optional.of(done));

        Settlement result = settlementService.settleMonth(10L, AUGUST).orElseThrow();

        assertThat(result).isSameAs(done);
        then(settlementRepository).should(never()).save(any());
        then(settlementItemRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("대상 건이 없으면 정산서를 만들지 않는다")
    void settleMonth_nothingToSettle() {
        given(settlementRepository.findByRestaurantIdAndSettlementMonth(10L, "2026-08"))
                .willReturn(Optional.empty());
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant()));
        given(settlementItemRepository.findForfeitCandidates(any(), any(), any(), any(), any()))
                .willReturn(List.of());
        given(settlementItemRepository.findRevokeCandidates(any(), any(), any(), any()))
                .willReturn(List.of());

        assertThat(settlementService.settleMonth(10L, AUGUST)).isEmpty();
        then(settlementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("정산서 조회 — 사장 본인이 아니면 NOT_RESTAURANT_OWNER")
    void getStatement_notOwner() {
        Member stranger = Member.builder().id(77L).email("x@tably.com").password("pw").name("타인").role(Role.OWNER).build();
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant()));
        given(memberRepository.findById(77L)).willReturn(Optional.of(stranger));

        assertThatThrownBy(() -> settlementService.getStatement(77L, 10L, "2026-08"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_RESTAURANT_OWNER);
    }

    @Test
    @DisplayName("월 형식이 틀리면 INVALID_MONTH")
    void getStatement_invalidMonth() {
        given(restaurantRepository.findById(10L)).willReturn(Optional.of(restaurant()));
        given(memberRepository.findById(99L)).willReturn(Optional.of(owner()));

        assertThatThrownBy(() -> settlementService.getStatement(99L, 10L, "2026/08"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_MONTH);
    }
}
