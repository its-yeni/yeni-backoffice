package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.common.exception.BusinessException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.pos.entity.PosTerminal;
import com.yeni.backoffice.core.pos.repository.PosTerminalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class PosTerminalAuthenticationService {
    private final PosTerminalRepository terminals;

    public PosTerminalAuthenticationService(PosTerminalRepository terminals) {
        this.terminals = terminals;
    }

    @Transactional
    public PosTerminal authenticate(Long storeId, String terminalCode, String credential, String appVersion) {
        if (storeId == null || !StringUtils.hasText(terminalCode) || !StringUtils.hasText(credential)) {
            throw unauthorized();
        }
        PosTerminal terminal = terminals.findByStoreIdAndTerminalCode(storeId, terminalCode.trim())
                .filter(PosTerminal::isActive)
                .orElseThrow(this::unauthorized);
        byte[] expected = terminal.getCredentialHash().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = sha256(credential).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) throw unauthorized();
        terminal.connected(normalizeVersion(appVersion), LocalDateTime.now());
        return terminal;
    }

    public static String hashCredential(String credential) {
        if (!StringUtils.hasText(credential)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "POS 자격증명은 비어 있을 수 없습니다.");
        }
        return sha256(credential);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", impossible);
        }
    }

    private String normalizeVersion(String appVersion) {
        if (!StringUtils.hasText(appVersion)) return null;
        String normalized = appVersion.trim();
        return normalized.length() <= 30 ? normalized : normalized.substring(0, 30);
    }

    private BusinessException unauthorized() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "POS 단말 인증에 실패했습니다.");
    }
}
