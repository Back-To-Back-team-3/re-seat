package com.backtoback.reseat.domain.game.repository;

import static com.backtoback.reseat.domain.game.entity.QGame.game;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.service.GameSearchCondition;
import com.backtoback.reseat.domain.stadium.entity.QStadium;
import com.backtoback.reseat.domain.team.entity.QTeam;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 경기 조회 QueryDSL 구현체.
 *
 * <p>목록 조회에서 homeTeam, awayTeam, stadium을 fetch join하여
 * 카드 UI 렌더링에 필요한 정보를 한 번의 쿼리로 가져온다.</p>
 */
@RequiredArgsConstructor
public class GameRepositoryImpl implements GameRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QTeam homeTeam = new QTeam("homeTeam");
    private static final QTeam awayTeam = new QTeam("awayTeam");
    private static final QStadium stadium = new QStadium("stadium");

    @Override
    public Page<Game> searchGames(GameSearchCondition condition, Pageable pageable) {
        List<Game> content = queryFactory
            .selectFrom(game)
            .join(game.homeTeam, homeTeam).fetchJoin()
            .join(game.awayTeam, awayTeam).fetchJoin()
            .join(game.stadium, stadium).fetchJoin()
            .where(
                homeTeamIdEq(condition.homeTeamId()),
                awayTeamIdEq(condition.awayTeamId()),
                gameAtGoe(condition.from()),
                gameAtLt(condition.to()),
                bookingStatusEq(condition.bookingStatus())
            )
            .orderBy(getOrderSpecifiers(pageable))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory
            .select(game.count())
            .from(game)
            .where(
                homeTeamIdEq(condition.homeTeamId()),
                awayTeamIdEq(condition.awayTeamId()),
                gameAtGoe(condition.from()),
                gameAtLt(condition.to()),
                bookingStatusEq(condition.bookingStatus())
            )
            .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /**
     * 홈팀 ID가 일치하는 경기를 조회한다.
     */
    private BooleanExpression homeTeamIdEq(Long homeTeamId) {
        if (homeTeamId == null) {
            return null;
        }

        return game.homeTeam.id.eq(homeTeamId);
    }

    /**
     * from 날짜 이상인 경기를 조회한다.
     *
     * <p>LocalDate를 받기 때문에 해당 날짜의 00:00:00부터 조회한다.</p>
     */
    private BooleanExpression gameAtGoe(LocalDate from) {
        if (from == null) {
            return null;
        }

        return game.gameAt.goe(from.atStartOfDay());
    }

    /**
     * 원정팀 ID가 일치하는 경기를 조회한다.
     */
    private BooleanExpression awayTeamIdEq(Long awayTeamId) {
        if (awayTeamId == null) {
            return null;
        }

        return game.awayTeam.id.eq(awayTeamId);
    }

    /**
     * to 날짜 이하인 경기를 조회한다.
     *
     * <p>LocalDate 기준으로는 다음 날 00:00:00 미만 조건을 사용해야
     * to 날짜 전체가 포함된다.</p>
     */
    private BooleanExpression gameAtLt(LocalDate to) {
        if (to == null) {
            return null;
        }

        LocalDateTime nextDayStart = to.plusDays(1).atStartOfDay();
        return game.gameAt.lt(nextDayStart);
    }

    private BooleanExpression bookingStatusEq(BookingStatus bookingStatus) {
        if (bookingStatus == null) {
            return null;
        }

        return game.bookingStatus.eq(bookingStatus);
    }

    /**
     * Pageable의 sort 조건을 QueryDSL OrderSpecifier로 변환한다.
     *
     * <p>허용하지 않는 정렬 필드는 무시한다.
     * 기본 정렬은 gameAt ASC, id ASC이다.</p>
     */
    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();

        for (Sort.Order sortOrder : pageable.getSort()) {
            Order direction = sortOrder.isAscending() ? Order.ASC : Order.DESC;

            switch (sortOrder.getProperty()) {
                case "gameAt" -> orderSpecifiers.add(new OrderSpecifier<>(direction, game.gameAt));
                case "bookingOpenAt" -> orderSpecifiers.add(new OrderSpecifier<>(direction, game.bookingOpenAt));
                case "bookingCloseAt" -> orderSpecifiers.add(new OrderSpecifier<>(direction, game.bookingCloseAt));
                case "id", "gameId" -> orderSpecifiers.add(new OrderSpecifier<>(direction, game.id));
                default -> {
                    // 허용하지 않는 정렬 필드는 API 안정성을 위해 무시한다.
                }
            }
        }

        if (orderSpecifiers.isEmpty()) {
            orderSpecifiers.add(game.gameAt.asc());
        }

        // 같은 경기 시간이 있을 수 있으므로 id 기준 보조 정렬을 추가한다.
        orderSpecifiers.add(game.id.asc());

        return orderSpecifiers.toArray(new OrderSpecifier[0]);
    }
}
