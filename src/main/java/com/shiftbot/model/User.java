package com.shiftbot.model;

import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;

import java.time.Instant;
import java.util.Objects;

public class User {
    private long userId;
    private String username;
    private String fullName;
    private String phone;
    private Role role;
    private UserStatus status;
    private Instant createdAt;
    private Long createdBy;

    public User() {
    }

    public User(long userId, String username, String fullName, String phone, Role role, UserStatus status, Instant createdAt, Long createdBy) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return userId == user.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
