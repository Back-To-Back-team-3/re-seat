package com.backtoback.reseat.domain.reservation.repository;

import static com.backtoback.reseat.domain.reservation.entity.QReservation.*;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

// 관리자용 예약·선점 상태별 목록 조회
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Reservation> findByGameAndStatus(Long gameId, ReservationStatus status, Pageable pageable) {
        // 컬렉션(reservationSeats) fetch join을 걸지 않는다.
        // offset/limit과 함께 쓰면 Hibernate가 in-memory paging으로 전환돼 페이지 결과가 왜곡된다.
        List<Reservation> content
            = queryFactory
                .selectFrom(reservation)
                .where(reservation.game.id.eq(gameId), statusEq(status))
                .orderBy(getOrderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total
            = queryFactory
                .select(reservation.count())
                .from(reservation)
                .where(reservation.game.id.eq(gameId), statusEq(status))
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression statusEq(ReservationStatus status) {
        // status를 안 보내면(null) 이 조건은 쿼리에서 빠진다 → 전체 상태 조회가 된다
        return status != null ? reservation.status.eq(status) : null;
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();

        for (Sort.Order sortOrder : pageable.getSort()) {
            Order direction = sortOrder.isAscending() ? Order.ASC : Order.DESC;

            switch (sortOrder.getProperty()) {
                case "createdAt" -> orderSpecifiers.add(new OrderSpecifier<>(direction, reservation.createdAt));
                case "holdExpiresAt" -> orderSpecifiers.add(new OrderSpecifier<>(direction, reservation.holdExpiresAt));
                default -> {
                    // 지원하지 않는 정렬 속성은 무시한다.
                }
            }
        }

        if (orderSpecifiers.isEmpty()) {
            orderSpecifiers.add(reservation.createdAt.desc());
        }
        // 생성 시각이 똑같은 행들이 있어도 id로 한 번 더 정렬해서 순서를 고정한다
        // → 페이지네이션 결과가 매번 똑같이 나오도록 보장한다
        orderSpecifiers.add(reservation.id.desc());

        return orderSpecifiers.toArray(new OrderSpecifier[0]);
    }
}
