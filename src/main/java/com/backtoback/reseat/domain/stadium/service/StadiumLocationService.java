package com.backtoback.reseat.domain.stadium.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.stadium.dto.StadiumLocationResponse;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.exception.StadiumNotFoundException;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;

import lombok.RequiredArgsConstructor;

/**
 * 구장 좌표 조회 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StadiumLocationService {

    private final StadiumRepository stadiumRepository;

    /**
     * 구장 좌표를 조회한다.
     *
     * @param stadiumId 구장 ID
     * @return 구장 위치 응답
     */
    public StadiumLocationResponse getLocation(Long stadiumId) {
        Stadium stadium
            = stadiumRepository.findById(stadiumId).orElseThrow(() -> new StadiumNotFoundException(stadiumId));

        return StadiumLocationResponse.from(stadium);
    }
}
