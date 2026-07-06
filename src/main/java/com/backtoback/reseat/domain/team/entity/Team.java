package com.backtoback.reseat.domain.team.entity;

import com.backtoback.reseat.global.common.BaseEntity;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "teams",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_teams_name", columnNames = "name") // 팀명 중복 방지
        },
        indexes = {
                @Index(name = "idx_teams_home_stadium", columnList = "home_stadium_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "home_stadium_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_teams_home_stadium")
    )
    private Stadium homeStadium;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamStatus status;

    public static Team of(String name, Stadium homeStadium) {
        Team team = new Team();
        team.name = name;
        team.homeStadium = homeStadium;
        team.status = TeamStatus.ACTIVE;
        return team;
    }
}
