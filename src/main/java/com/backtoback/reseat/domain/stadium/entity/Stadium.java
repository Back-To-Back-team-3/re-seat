package com.backtoback.reseat.domain.stadium.entity;

import java.math.BigDecimal;

import com.backtoback.reseat.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "stadiums")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stadium extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        name = "name",
        nullable = false,
        length = 100,
        unique = true
    )
    private String name;

    @Column(
        name = "address",
        nullable = false,
        length = 255
    )
    private String address;

    @Column(
        name = "total_capacity",
        nullable = false
    )
    private int totalCapacity;

    // 구장 위치 위도.
    @Column(
        name = "latitude",
        precision = 10,
        scale = 7
    )
    private BigDecimal latitude;

    // 구장 위치 경도.
    @Column(
        name = "longitude",
        precision = 11,
        scale = 7
    )
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private StadiumStatus status = StadiumStatus.ACTIVE;

    public static Stadium of(String name, String address, int totalCapacity) {
        Stadium stadium = new Stadium();
        stadium.name = name;
        stadium.address = address;
        stadium.totalCapacity = totalCapacity;
        stadium.status = StadiumStatus.ACTIVE;
        return stadium;
    }

    /**
     * 구장 좌표를 등록한다.
     */
    public void registerCoordinates(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
