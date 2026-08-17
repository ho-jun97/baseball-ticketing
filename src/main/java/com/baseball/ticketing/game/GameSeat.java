package com.baseball.ticketing.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경기별 좌석 상태 — 동적, 동시성 제어 핵심 테이블 (CLAUDE.md 핵심 설계 원칙 1).
 * 락은 항상 이 엔티티의 row 단위로 건다 — "좌석 자체"가 아니라 "이번 경기의 이 좌석 상태"를 잠근다.
 * {@link #version}이 낙관적 락(@Version)이며, 동시에 들어온 hold 요청 중 하나만 성공하고
 * 나머지는 {@link jakarta.persistence.OptimisticLockException}으로 실패해야 한다 — 이 실패를
 * 서비스 레이어에서 409 SEAT_ALREADY_TAKEN으로 변환한다.
 */
@Entity
@Table(
        name = "game_seat",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_game_seat_game_seat_id",
                columnNames = {"game_id", "seat_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameSeatStatus status;

    @Column(name = "hold_expire_at")
    private LocalDateTime holdExpireAt;

    @Version
    @Column(nullable = false)
    private int version;

    @Builder
    public GameSeat(Game game, Seat seat) {
        this.game = game;
        this.seat = seat;
        this.status = GameSeatStatus.AVAILABLE;
    }

    /** AVAILABLE → HOLD. 이미 HOLD/SOLD면 예외 — 호출부(서비스)에서 409 SEAT_ALREADY_TAKEN으로 변환한다. */
    public void hold(LocalDateTime holdExpireAt) {
        if (this.status != GameSeatStatus.AVAILABLE) {
            throw new IllegalStateException("이미 선점되었거나 판매된 좌석입니다: gameSeatId=" + id);
        }
        this.status = GameSeatStatus.HOLD;
        this.holdExpireAt = holdExpireAt;
    }

    /** HOLD → SOLD. HOLD TTL 만료 여부는 호출부에서 확인해 410 HOLD_EXPIRED로 변환한다. */
    public void sell() {
        if (this.status != GameSeatStatus.HOLD) {
            throw new IllegalStateException("HOLD 상태가 아닌 좌석은 판매 확정할 수 없습니다: gameSeatId=" + id);
        }
        this.status = GameSeatStatus.SOLD;
        this.holdExpireAt = null;
    }

    /** HOLD/SOLD → AVAILABLE. 예매 취소, HOLD TTL 만료 스케줄러(4주차 구현)에서 사용한다. */
    public void release() {
        this.status = GameSeatStatus.AVAILABLE;
        this.holdExpireAt = null;
    }

    public boolean isHoldExpired(LocalDateTime now) {
        return this.status == GameSeatStatus.HOLD
                && this.holdExpireAt != null
                && this.holdExpireAt.isBefore(now);
    }
}
