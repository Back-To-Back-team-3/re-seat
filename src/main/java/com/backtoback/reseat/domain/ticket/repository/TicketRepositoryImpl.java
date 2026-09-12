package com.backtoback.reseat.domain.ticket.repository;

import static com.backtoback.reseat.domain.ticket.entity.QTicket.ticket;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.backtoback.reseat.domain.admin.ticket.dto.request.TicketSearchCondition;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TicketRepositoryImpl implements TicketRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Ticket> searchTickets(TicketSearchCondition condition, Pageable pageable) {
        List<Ticket> content
            = queryFactory
                .selectFrom(ticket)
                .join(ticket.user)
                .fetchJoin()
                .join(ticket.game)
                .fetchJoin()
                .join(ticket.gameSeat)
                .fetchJoin()
                .where(
                    userIdEq(condition.userId()),
                    statusEq(condition.status()),
                    gameDateGoe(condition.gameDateFrom()),
                    gameDateLt(condition.gameDateTo())
                )
                .orderBy(getOrderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total
            = queryFactory
                .select(ticket.count())
                .from(ticket)
                .where(
                    userIdEq(condition.userId()),
                    statusEq(condition.status()),
                    gameDateGoe(condition.gameDateFrom()),
                    gameDateLt(condition.gameDateTo())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression userIdEq(Long userId) {
        return userId == null ? null : ticket.user.id.eq(userId);
    }

    private BooleanExpression statusEq(TicketStatus status) {
        return status == null ? null : ticket.status.eq(status);
    }

    private BooleanExpression gameDateGoe(LocalDate from) {
        return from == null ? null : ticket.game.gameAt.goe(from.atStartOfDay());
    }

    private BooleanExpression gameDateLt(LocalDate to) {
        return to == null ? null : ticket.game.gameAt.lt(to.plusDays(1).atStartOfDay());
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();

        for (Sort.Order sortOrder : pageable.getSort()) {
            Order direction = sortOrder.isAscending() ? Order.ASC : Order.DESC;

            switch (sortOrder.getProperty()) {
                case "issuedAt" -> orderSpecifiers.add(new OrderSpecifier<>(direction, ticket.issuedAt));
                case "gameAt" -> orderSpecifiers.add(new OrderSpecifier<>(direction, ticket.game.gameAt));
                default -> {
                    // 지원하지 않는 정렬 속성은 무시한다.
                }
            }
        }

        if (orderSpecifiers.isEmpty()) {
            orderSpecifiers.add(ticket.issuedAt.desc());
        }
        orderSpecifiers.add(ticket.id.desc());

        return orderSpecifiers.toArray(new OrderSpecifier[0]);
    }
}
