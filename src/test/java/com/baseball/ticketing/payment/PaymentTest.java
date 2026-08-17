package com.baseball.ticketing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baseball.ticketing.game.Game;
import com.baseball.ticketing.game.Stadium;
import com.baseball.ticketing.member.Member;
import com.baseball.ticketing.reservation.Reservation;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private Payment newPendingPayment() {
        Member member = Member.builder().email("user@example.com").password("pw").name("홍길동").build();
        Stadium stadium = Stadium.builder().name("잠실야구장").address("서울").build();
        Game game = Game.builder()
                .stadium(stadium)
                .homeTeam("LG")
                .awayTeam("KIA")
                .gameDatetime(LocalDateTime.now().plusDays(1))
                .build();
        Reservation reservation = Reservation.builder().member(member).game(game).totalPrice(30000).build();
        return Payment.builder().reservation(reservation).amount(30000).method(PaymentMethod.MOCK).build();
    }

    @Test
    void builder_defaultsStatusToPending() {
        assertThat(newPendingPayment().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void complete_fromPending_movesToCompletedAndStampsPaidAt() {
        Payment payment = newPendingPayment();

        payment.complete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    void complete_whenNotPending_throws() {
        Payment payment = newPendingPayment();
        payment.complete();

        assertThatThrownBy(payment::complete).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fail_fromPending_movesToFailed() {
        Payment payment = newPendingPayment();

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void fail_whenNotPending_throws() {
        Payment payment = newPendingPayment();
        payment.complete();

        assertThatThrownBy(payment::fail).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refund_fromCompleted_movesToRefunded() {
        Payment payment = newPendingPayment();
        payment.complete();

        payment.refund();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refund_whenNotCompleted_throws() {
        Payment payment = newPendingPayment();

        assertThatThrownBy(payment::refund).isInstanceOf(IllegalStateException.class);
    }
}
