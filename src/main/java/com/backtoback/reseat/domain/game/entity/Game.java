package com.backtoback.reseat.domain.game.entity;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "games", indexes = {
	@Index(name = "idx_games_game_at", columnList = "game_at"),
	@Index(name = "idx_games_home_team_game_at", columnList = "home_team_id, game_at"),
	@Index(name = "idx_games_away_team_game_at", columnList = "away_team_id, game_at"),
	@Index(name = "idx_games_stadium_game_at", columnList = "stadium_id, game_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// home_team_id / away_team_id 둘 다 Team 참조 → JoinColumn name 명시 필수 (안 하면 컬럼명 충돌)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "home_team_id", nullable = false, foreignKey = @ForeignKey(name = "fk_games_home_team"))
	private Team homeTeam;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "away_team_id", nullable = false, foreignKey = @ForeignKey(name = "fk_games_away_team"))
	private Team awayTeam;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "stadium_id", nullable = false, foreignKey = @ForeignKey(name = "fk_games_stadium"))
	private Stadium stadium;

	@Column(name = "game_at", nullable = false)
	private LocalDateTime gameAt;

	@Column(name = "booking_open_at", nullable = false)
	private LocalDateTime bookingOpenAt;

	@Column(name = "booking_close_at", nullable = false)
	private LocalDateTime bookingCloseAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "booking_status", nullable = false, length = 20)
	private BookingStatus bookingStatus;

	// 화면 표시용 라벨 (예: "5월 5일 어린이날 특별 경기"). 없어도 되므로 nullable.
	@Column(name = "title", length = 255)
	private String title;

	@Builder
	private Game(Team homeTeam, Team awayTeam, Stadium stadium,
		LocalDateTime gameAt, LocalDateTime bookingOpenAt, LocalDateTime bookingCloseAt,
		BookingStatus bookingStatus, String title) {
		this.homeTeam = homeTeam;
		this.awayTeam = awayTeam;
		this.stadium = stadium;
		this.gameAt = gameAt;
		this.bookingOpenAt = bookingOpenAt;
		this.bookingCloseAt = bookingCloseAt;
		this.bookingStatus = (bookingStatus != null) ? bookingStatus : BookingStatus.SCHEDULED;
		this.title = title;
	}
}
