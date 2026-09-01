package com.backtoback.reseat.domain.stadium.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backtoback.reseat.domain.stadium.dto.StadiumLocationResponse;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.exception.StadiumNotFoundException;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;

@ExtendWith(MockitoExtension.class)
class StadiumLocationServiceTest {

    @Mock
    private StadiumRepository stadiumRepository;

    private StadiumLocationService stadiumLocationService;

    @Test
    @DisplayName("존재하는 구장 ID로 조회하면 이름과 좌표를 함께 반환한다")
    void getLocation_returnsCoordinates_whenStadiumExists() {
        stadiumLocationService = new StadiumLocationService(stadiumRepository);
        Stadium stadium = Stadium.of("서울종합운동장 야구장", "서울 송파구", 23750);
        BigDecimal latitude = new BigDecimal("37.5121676");
        BigDecimal longitude = new BigDecimal("127.0719084");
        stadium.registerCoordinates(latitude, longitude);
        when(stadiumRepository.findById(1L)).thenReturn(Optional.of(stadium));

        StadiumLocationResponse response = stadiumLocationService.getLocation(1L);

        assertThat(response.name()).isEqualTo("서울종합운동장 야구장");
        assertThat(response.latitude()).isEqualByComparingTo(latitude);
        assertThat(response.longitude()).isEqualByComparingTo(longitude);
    }

    @Test
    @DisplayName("존재하지 않는 구장 ID로 조회하면 StadiumNotFoundException이 발생한다")
    void getLocation_throws_whenStadiumNotFound() {
        stadiumLocationService = new StadiumLocationService(stadiumRepository);
        when(stadiumRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stadiumLocationService.getLocation(999L)).isInstanceOf(StadiumNotFoundException.class);
    }
}
