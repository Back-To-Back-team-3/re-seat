package com.backtoback.reseat.stadium.entity;

import com.backtoback.reseat.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
    name = "seats",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_seats_location",
        columnNames = {"stadium_id", "zone_id", "seat_block", "seat_row", "seat_number"}
    ),
    indexes = @Index(name = "idx_seats_zone_status", columnList = "zone_id, status")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stadium_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_seats_stadium"))
    private Stadium stadium;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_seats_zone"))
    private SeatZone zone;

    @Column(name = "seat_block", nullable = false, length = 20)
    private String seatBlock;

    @Column(name = "seat_row", nullable = false, length = 20)
    private String seatRow;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Column(name = "x_position")
    private Integer xPosition;

    @Column(name = "y_position")
    private Integer yPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeatStatus status = SeatStatus.ACTIVE;

    public static Seat of(Stadium stadium, SeatZone zone, String seatBlock, String seatRow, String seatNumber) {
        Seat s = new Seat();
        s.stadium = stadium;
        s.zone = zone;
        s.seatBlock = seatBlock;
        s.seatRow = seatRow;
        s.seatNumber = seatNumber;
        s.status = SeatStatus.ACTIVE;
        return s;
    }
}
