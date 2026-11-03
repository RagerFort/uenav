package com.uenav.server.admin;

public final class Admin {

    private Admin() { }

    /** One row of admin_account. */
    public record Account(int id, String username, String passwordHash, String fullName, String role) { }

    /** Who is logged in. Kept in memory with the login token. */
    public record Session(int accountId, String username, String fullName, String role) { }

    public record LoginResult(String token, Session session, long expiresInSeconds) { }
}
