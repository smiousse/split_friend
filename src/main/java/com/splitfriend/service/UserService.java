package com.splitfriend.service;

import com.splitfriend.model.User;
import com.splitfriend.model.enums.Role;
import com.splitfriend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.default-email:admin@splitfriend.local}")
    private String adminEmail;

    @Value("${app.admin.default-password:admin123}")
    private String adminPassword;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void initDefaultAdmin() {
        log.info("Checking for default admin user with email: {}", adminEmail);
        log.debug("Admin password configured (length={})", adminPassword != null ? adminPassword.length() : 0);

        if (!userRepository.existsByEmail(adminEmail)) {
            log.info("Default admin user not found, creating new admin account: {}", adminEmail);
            User admin = User.builder()
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .name("Administrator")
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.info("Default admin user created successfully: {}", adminEmail);
        } else {
            log.info("Default admin user already exists: {}", adminEmail);
            // Log existing admin details for debugging
            userRepository.findByEmail(adminEmail).ifPresent(existingAdmin -> {
                log.info("Existing admin - email: {}, enabled: {}, role: {}",
                        existingAdmin.getEmail(), existingAdmin.getEnabled(), existingAdmin.getRole());
            });
        }
    }

    public User registerUser(String email, String password, String name) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .name(name)
                .role(Role.USER)
                .enabled(true)
                .build();

        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public List<User> searchUsers(String search) {
        return userRepository.searchUsers(search);
    }

    public User updateUser(User user) {
        return userRepository.save(user);
    }

    public void updatePassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void enableUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setEnabled(true);
            userRepository.save(user);
        });
    }

    public void disableUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setEnabled(false);
            userRepository.save(user);
        });
    }

    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    public long countActiveUsers() {
        return userRepository.countActiveUsers();
    }

    public long countTotalUsers() {
        return userRepository.count();
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public void setTotpSecret(User user, String secret) {
        user.setTotpSecret(secret);
        userRepository.save(user);
    }

    public void enableTotp(User user) {
        user.setTotpEnabled(true);
        userRepository.save(user);
    }

    public void disableTotp(User user) {
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
    }
}
