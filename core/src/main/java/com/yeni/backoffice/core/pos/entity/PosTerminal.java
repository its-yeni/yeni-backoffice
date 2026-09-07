package com.yeni.backoffice.core.pos.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pos_terminal", uniqueConstraints =
        @UniqueConstraint(name = "uk_pos_terminal_store_code", columnNames = {"storeId", "terminalCode"}))
public class PosTerminal extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 40)
    private String terminalCode;

    @Column(nullable = false, length = 100)
    private String terminalName;

    @Column(nullable = false, length = 100)
    private String credentialHash;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 30)
    private String appVersion;

    @Column
    private LocalDateTime lastConnectedAt;

    public void connected(String appVersion, LocalDateTime connectedAt) {
        this.appVersion = appVersion;
        this.lastConnectedAt = connectedAt;
    }

    public void changeActive(boolean active) {
        this.active = active;
    }
}
