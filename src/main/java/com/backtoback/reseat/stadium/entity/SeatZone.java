package com.backtoback.reseat.stadium.entity;

import com.backtoback.reseat.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
    name = "seat_zones",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_seat_zones_stadium_name",
        columnNames = {"stadium_id", "name"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatZone extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stadium_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_seat_zones_stadium"))
    private Stadium stadium;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 20)
    private SeatGrade grade;

    @Column(name = "base_price", nullable = false)
    private int basePrice;

    public static SeatZone of(Stadium stadium, String name, SeatGrade grade, int basePrice) {
        SeatZone z = new SeatZone();
        z.stadium = stadium;
        z.name = name;
        z.grade = grade;
        z.basePrice = basePrice;
        return z;
    }
}
