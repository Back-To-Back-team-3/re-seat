package com.backtoback.reseat.domain.stadium.entity;

import com.backtoback.reseat.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "seat_zones", uniqueConstraints = @UniqueConstraint(name = "uk_seat_zones_stadium_name", columnNames = {
	"stadium_id", "name"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatZone extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stadium_id", nullable = false, foreignKey = @ForeignKey(name = "fk_seat_zones_stadium"))
	private Stadium stadium;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	/**
	 * 좌석 등급. 내야(1루·3루) = INFIELD, 외야 = OUTFIELD.
	 * 요일·시기·연령 할인은 여기서 다루지 않고 C-2 PricePolicy에서 산정한다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "grade", nullable = false, length = 20)
	private SeatGrade grade;

	/**
	 * 구역 기준 성인 정가. 요일·시기 배수 적용 전 원가.
	 * 화~목 기준: INFIELD=18000, OUTFIELD=16000
	 * 실제 game_seats.price는 C-2 PricePolicy.calculate()가 배수를 곱해 산정한다.
	 */
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
