package com.shiftbot.service;

import com.shiftbot.model.AccessRequest;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.AccessRequestStatus;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.AccessRequestsRepository;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class AccessRequestService {
    private static final Logger log = LoggerFactory.getLogger(AccessRequestService.class);

    private final AccessRequestsRepository accessRequestsRepository;
    private final UsersRepository usersRepository;
    private final ZoneId zoneId;

    public AccessRequestService(AccessRequestsRepository accessRequestsRepository, UsersRepository usersRepository, ZoneId zoneId) {
        this.accessRequestsRepository = accessRequestsRepository;
        this.usersRepository = usersRepository;
        this.zoneId = zoneId;
    }

    public AccessRequest createRequest(User user, String comment) {
        AccessRequest request = new AccessRequest();
        request.setTelegramUserId(user.getUserId());
        request.setUsername(user.getUsername());
        request.setFullName(user.getFullName());
        request.setComment(comment);
        request.setStatus(AccessRequestStatus.PENDING);
        request.setCreatedAt(TimeUtils.nowInstant(zoneId));
        accessRequestsRepository.save(request);
        return request;
    }

    public Optional<AccessRequest> getPendingByTelegramUserId(long telegramUserId) {
        return accessRequestsRepository.findAll().stream()
                .filter(r -> r.getTelegramUserId() == telegramUserId)
                .filter(r -> r.getStatus() == AccessRequestStatus.PENDING)
                .findFirst();
    }

    public AccessRequest createPendingIfAbsent(User user, String comment) {
        Optional<AccessRequest> existing = getPendingByTelegramUserId(user.getUserId());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (user.getStatus() != UserStatus.PENDING) {
            updateUserStatus(user.getUserId(), UserStatus.PENDING);
        }
        return createRequest(user, comment);
    }

    public List<AccessRequest> listPendingRequests() {
        return accessRequestsRepository.findAll().stream()
                .filter(r -> r.getStatus() == AccessRequestStatus.PENDING)
                .sorted(Comparator.comparing(AccessRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public AccessRequest approveRequest(String requestId, long seniorUserId) {
        AccessRequest request = load(requestId);
        request.setStatus(AccessRequestStatus.APPROVED);
        request.setProcessedBy(seniorUserId);
        request.setProcessedAt(TimeUtils.nowInstant(zoneId));
        accessRequestsRepository.update(request);
        updateUserStatus(request.getTelegramUserId(), UserStatus.APPROVED);
        log.info("Access request {} approved by senior {}", requestId, seniorUserId);
        return request;
    }

    public AccessRequest approve(String requestId, long seniorUserId) {
        return approveRequest(requestId, seniorUserId);
    }

    public AccessRequest rejectRequest(String requestId, long seniorUserId) {
        AccessRequest request = load(requestId);
        request.setStatus(AccessRequestStatus.REJECTED);
        request.setProcessedBy(seniorUserId);
        request.setProcessedAt(TimeUtils.nowInstant(zoneId));
        accessRequestsRepository.update(request);
        updateUserStatus(request.getTelegramUserId(), UserStatus.REJECTED);
        log.info("Access request {} rejected by senior {}", requestId, seniorUserId);
        return request;
    }

    public AccessRequest reject(String requestId, long seniorUserId) {
        return rejectRequest(requestId, seniorUserId);
    }

    private void updateUserStatus(long userId, UserStatus status) {
        User user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Role role = user.getRole() == null ? Role.SELLER : user.getRole();
        User updated = new User(user.getUserId(), user.getUsername(), user.getFullName(), user.getLocationId(),
                user.getPhone(), role, status, user.getCreatedAt(), user.getCreatedBy());
        usersRepository.updateRow(user.getUserId(), updated);
    }

    private AccessRequest load(String requestId) {
        return accessRequestsRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found: " + requestId));
    }
}
