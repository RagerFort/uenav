package com.uenav.server.admin;

import java.util.Optional;

public interface AdminAccountRepository {
    Optional<Admin.Account> findByUsername(String username);

    int count();

    void insert(String username, String passwordHash, String fullName, String role);
}
