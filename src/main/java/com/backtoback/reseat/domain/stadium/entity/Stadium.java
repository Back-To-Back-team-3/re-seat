package com.backtoback.reseat.domain.stadium.entity;

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

	@Enumerated(EnumType.STRING)
	@Column(
	    name = "status",
	    nullable = false,
	    length = 20
	)
	private StadiumStatus status = StadiumStatus.ACTIVE;

	public static Stadium of(String name, String address, int totalCapacity) {
		Stadium s = new Stadium();
		s.name = name;
		s.address = address;
		s.totalCapacity = totalCapacity;
		s.status = StadiumStatus.ACTIVE;
		return s;
	}
}
