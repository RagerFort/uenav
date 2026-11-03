package com.uenav.server.admin;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminAccountRepository implements AdminAccountRepository {

    private final JdbcTemplate jdbc;

    public JdbcAdminAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Admin.Account> findByUsername(String username) {
        return jdbc.query(
                "SELECT account_id, username, password_hash, full_name, role "
                        + "FROM admin_account WHERE username = ?",
                (rs, i) -> new Admin.Account(rs.getInt("account_id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("full_name"), rs.getString("role")),
                username).stream().findFirst();
    }

    @Override
    public int count() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM admin_account", Integer.class);
        return n == null ? 0 : n;
    }

    @Override
    public void insert(String username, String passwordHash, String fullName, String role) {
        jdbc.update("INSERT INTO admin_account (username, password_hash, full_name, role) VALUES (?, ?, ?, ?)",
                username, passwordHash, fullName, role);
    }
}
