package com.backtoback.reseat.domain.reservation.service.port;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backtoback.reseat.domain.ticket.service.TicketService;

/**
 * TicketCountAdapter가 TicketService에 올바르게 위임하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TicketCountAdapterTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private TicketCountAdapter ticketCountAdapter;

    @Test
    @DisplayName("should_delegateToTicketService_when_countingActiveTickets")
    void should_delegateToTicketService_when_countingActiveTickets() {
        // given
        given(ticketService.countActiveTickets(1L, 10L)).willReturn(2);

        // when
        int result = ticketCountAdapter.countActiveTickets(1L, 10L);

        // then
        assertThat(result).isEqualTo(2);
    }
}
