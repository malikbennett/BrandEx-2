package com.brandex.service;

import java.util.TreeMap;
import java.util.function.Consumer;

import com.brandex.models.User;
import com.brandex.models.enums.UserStatus;
import com.brandex.repository.UserRepository;
import com.brandex.utilities.EmailSender;
import com.brandex.utilities.OTPGenerator;
import com.brandex.utilities.PasswordHasher;

// The service class for managing users.
public class UserService {
    private static UserService instance;
    private final UserRepository userRepo = UserRepository.getInstance();
    private final TreeMap<String, User> userTree = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private boolean loaded = false;

    // Returns the instance of the UserService
    public static UserService getInstance() {
        if (instance == null)
            instance = new UserService();
        return instance;
    }

    // Load all users from database
    public void loadUsers() {
        this.userRepo.listUsers().forEach(user -> this.setInternalUser(user));
        this.loaded = true;
    }

    // Reloads the users
    public void reloadUsers() {
        this.userTree.clear();
        this.loadUsers();
    }

    // Returns true if the users are loaded
    public boolean isLoaded() {
        return this.loaded;
    }

    // Iterates over all users
    public void forEachUser(Consumer<User> action) {
        this.userTree.values().forEach(action);
    }

    // Searches for a user by ID
    public User searchById(String id) {
        final User[] found = new User[1];
        this.userTree.values().forEach(user -> {
            if (user.getId().equals(id)) {
                found[0] = user;
            }
        });
        return found[0];
    }

    // Searches for a user by username
    public User searchByUsername(String username) {
        final User[] found = new User[1];
        this.userTree.values().forEach(user -> {
            if (user.getUsername().equalsIgnoreCase(username)) {
                found[0] = user;
            }
        });
        return found[0];
    }

    // Creates a new user
    public void createUser(User user) throws Exception {
        if (user == null)
            throw new Exception("User cannot be null.");
        String generatedId = this.userRepo.createUser(user);
        if (generatedId == null)
            throw new Exception("Failed to save user to database.");
        user.setId(generatedId);
        this.userTree.put(user.getUsername(), user);
    }

    // Updates a user
    public void updateUser(User user, UserStatus status, String role) throws Exception {
        boolean ok = this.userRepo.updateUserStatusAndRole(user.getId(), status, role);
        if (!ok)
            throw new Exception("Failed to update user in database.");
        user.setStatus(status);
        user.setRole(role);
    }

    // Deletes a user
    public void deleteUser(User user) throws Exception {
        if (user == null)
            throw new Exception("User cannot be null.");
        boolean ok = this.userRepo.deleteUser(user.getId());
        if (!ok)
            throw new Exception("Failed to delete user from database.");
        this.userTree.remove(user.getUsername());
    }

    // Resets a user's password
    public void resetPassword(User user) throws Exception {
        String tempPassword = OTPGenerator.generate();
        String hash = PasswordHasher.hash(tempPassword);

        boolean ok = this.userRepo.resetPassword(user.getId(), hash);
        if (!ok)
            throw new Exception("Failed to reset password in database.");

        user.setPasswordHash(hash);
        user.setForcePwChange(true);

        // Send reset email asynchronously with retries
        EmailSender.sendAsync(
                user.getEmail(),
                "Your BrandEx Password Reset",
                buildPasswordResetEmail(user, tempPassword),
                () -> System.out.println("Password reset email sent to " + user.getEmail()),
                error -> System.err.println("Password reset email PERMANENT failure: " + error));
    }

    // Builds the password reset email
    private String buildPasswordResetEmail(User user, String tempPassword) {
        return String.format("""
                Hello %s,

                An administrator has reset your BrandEx account password.

                ─────────────────────────────────────
                NEW ACCESS CREDENTIALS
                ─────────────────────────────────────
                Temporary PW  :  %s

                Please log in immediately using this code.
                You will be required to set a permanent password upon entry.

                ─────────────────────────────────────
                SECURITY ADVISORY
                ─────────────────────────────────────
                If you did not request this change, please contact
                BrandEx support immediately.

                Thank you,

                Regards,
                The BrandEx Team
                """,
                user.getFirstName(),
                tempPassword);
    }

    // Clears the users
    public void clearUsers() {
        this.userTree.clear();
        this.loaded = false;
    }

    // Returns the users tree
    public TreeMap<String, User> getUsersTree() {
        return this.userTree;
    }

    // Sets the internal user
    private void setInternalUser(User user) {
        this.userTree.put(user.getUsername(), user);
    }
}
