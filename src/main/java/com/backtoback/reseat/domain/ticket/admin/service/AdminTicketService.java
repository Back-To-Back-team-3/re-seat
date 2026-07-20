package com.backtoback.reseat.domain.ticket.admin.service;

import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminUserTicketResponse;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminTicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<AdminUserTicketResponse> getUserTickets(Long userId, TicketStatus status, Pageable pageable) {
        // 회원 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다. userId: " + userId);
        }

        return ticketRepository.findAllByUserIdAndStatusWithDetails(userId, status, pageable)
                .map(AdminUserTicketResponse::from);
    }
}
