package com.baseball.ticketing.payment;

import com.baseball.ticketing.reservation.Reservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Builder
    public Payment(Reservation reservation, int amount, PaymentMethod method) {
        this.reservation = reservation;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.PENDING;
    }

    /** PENDING → COMPLETED. 모의 결제 승인 성공 시 호출. */
    public void complete() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태가 아닌 결제는 완료 처리할 수 없습니다: paymentId=" + id);
        }
        this.status = PaymentStatus.COMPLETED;
        this.paidAt = LocalDateTime.now();
    }

    /** PENDING → FAILED. */
    public void fail() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태가 아닌 결제는 실패 처리할 수 없습니다: paymentId=" + id);
        }
        this.status = PaymentStatus.FAILED;
    }

    /** COMPLETED → REFUNDED. 예매 취소(CONFIRMED 상태) 시 호출. */
    public void refund() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("COMPLETED 상태가 아닌 결제는 환불 처리할 수 없습니다: paymentId=" + id);
        }
        this.status = PaymentStatus.REFUNDED;
    }
}
