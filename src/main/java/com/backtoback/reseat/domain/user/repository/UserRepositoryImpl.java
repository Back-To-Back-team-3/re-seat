package com.backtoback.reseat.domain.user.repository;

import static com.backtoback.reseat.domain.user.entity.QUser.*;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.backtoback.reseat.domain.user.admin.dto.request.UserSearchCondition;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<User> searchUsers(UserSearchCondition condition, Pageable pageable) {
        List<User> content = queryFactory
            .selectFrom(user)
            .where(
                emailContains(condition.email()),
                nameContains(condition.name()),
                nicknameContains(condition.nickname()),
                phoneContains(condition.phone()),
                roleEq(condition.role()),
                statusEq(condition.status()))
            .orderBy(getOrderSpecifiers(pageable))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory
            .select(user.count())
            .from(user)
            .where(
                emailContains(condition.email()),
                nameContains(condition.name()),
                nicknameContains(condition.nickname()),
                phoneContains(condition.phone()),
                roleEq(condition.role()),
                statusEq(condition.status()))
            .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanExpression emailContains(String email) {
        return (email == null || email.isBlank()) ? null : user.email.contains(email);
    }

    private BooleanExpression nameContains(String name) {
        return (name == null || name.isBlank()) ? null : user.name.contains(name);
    }

    private BooleanExpression nicknameContains(String nickname) {
        return (nickname == null || nickname.isBlank()) ? null : user.nickname.contains(nickname);
    }

    private BooleanExpression phoneContains(String phone) {
        return (phone == null || phone.isBlank()) ? null : user.phone.contains(phone);
    }

    private BooleanExpression roleEq(UserRole role) {
        return role == null ? null : user.role.eq(role);
    }

    private BooleanExpression statusEq(UserStatus status) {
        return status == null ? null : user.status.eq(status);
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();

        for (Sort.Order sortOrder : pageable.getSort()) {
            Order direction = sortOrder.isAscending() ? Order.ASC : Order.DESC;

            switch (sortOrder.getProperty()) {
                case "id" -> orderSpecifiers.add(new OrderSpecifier<>(direction, user.id));
                case "email" -> orderSpecifiers.add(new OrderSpecifier<>(direction, user.email));
                case "name" -> orderSpecifiers.add(new OrderSpecifier<>(direction, user.name));
                case "createdAt" -> orderSpecifiers.add(new OrderSpecifier<>(direction, user.createdAt));
                case "updatedAt" -> orderSpecifiers.add(new OrderSpecifier<>(direction, user.updatedAt));
                default -> {
                    // Ignore unsupported properties
                }
            }
        }

        if (orderSpecifiers.isEmpty()) {
            orderSpecifiers.add(user.createdAt.desc());
        }

        orderSpecifiers.add(user.id.desc());

        return orderSpecifiers.toArray(new OrderSpecifier[0]);
    }
}
