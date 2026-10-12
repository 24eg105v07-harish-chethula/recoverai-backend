package com.recoverai.backend;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.recoverai.backend.entity.*;
import com.recoverai.backend.repository.*;
import com.recoverai.backend.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AuthUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired RecoveryCaseRepository cases;
    @Autowired RecoveryActionRepository actions;
    @Autowired RecoveryCollectionRepository collections;
    @Autowired AIAnalysisRepository analyses;
    @Autowired PaymentRepository payments;
    @Autowired CustomerRepository customers;
    @Autowired JwtService jwtService;

    @BeforeEach void clean() {
        collections.deleteAll(); actions.deleteAll(); analyses.deleteAll(); cases.deleteAll(); payments.deleteAll(); customers.deleteAll(); users.deleteAll();
        AuthUser admin = new AuthUser(); admin.setFullName("Test Admin"); admin.setEmail("admin@example.com"); admin.setPasswordHash(encoder.encode("SecureAdmin!234")); admin.setRole(AuthUser.Role.ADMIN); admin.setEnabled(true); users.save(admin);
    }

    @Test void signupCreatesUserAndHashesPasswordAndRejectsDuplicate() throws Exception {
        String body = "{\"fullName\":\"A User\",\"email\":\"User@Example.com\",\"password\":\"SecurePass!234\",\"role\":\"ADMIN\"}";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role", is("USER")))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
        AuthUser saved = users.findByEmail("user@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals("SecurePass!234", saved.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(encoder.matches("SecurePass!234", saved.getPasswordHash()));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }

    @Test void loginWorksAndInvalidPasswordFails() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"admin@example.com\",\"password\":\"SecureAdmin!234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role", is("ADMIN"))).andExpect(jsonPath("$.token").exists());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"admin@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        String token = token("admin@example.com", "SecureAdmin!234", true);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("ADMIN")));
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test void protectedAndAdminEndpointsEnforceAuthenticationAndRole() throws Exception {
        mvc.perform(get("/api/recovery-cases")).andExpect(status().isUnauthorized());
        String userToken = token("user@example.com", "SecurePass!234", false);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken)).andExpect(status().isForbidden());
        String adminToken = token("admin@example.com", "SecureAdmin!234", true);
        mvc.perform(get("/api/admin/users").param("search", "admin@example.com")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].role", is("ADMIN")))
                .andExpect(jsonPath("$[0].assignedPaymentCount").exists())
                .andExpect(jsonPath("$[0].assignedCaseCount").exists())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].token").doesNotExist());
        Long adminId = users.findByEmail("admin@example.com").orElseThrow().getId();
        mvc.perform(get("/api/admin/users/" + adminId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email", is("admin@example.com")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void disabledAccountAndInvalidJwtAreRejected() throws Exception {
        AuthUser user = new AuthUser(); user.setFullName("Disabled"); user.setEmail("disabled@example.com"); user.setPasswordHash(encoder.encode("SecurePass!234")); user.setRole(AuthUser.Role.USER); user.setEnabled(false); users.save(user);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"disabled@example.com\",\"password\":\"SecurePass!234\"}" )).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/recovery-cases").header("Authorization", "Bearer " + jwtService.issue(user))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/recovery-cases").header("Authorization", "Bearer invalid.token.value")).andExpect(status().isUnauthorized());
        String expired = Jwts.builder().subject("admin@example.com").expiration(Date.from(Instant.now().minusSeconds(5)))
                .signWith(Keys.hmacShaKeyFor("test-only-signing-key-for-recoverai-local-tests-32-bytes-min".getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(get("/api/recovery-cases").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test void recoveryAttemptAndStoppedCaseRulesRemainEnforced() throws Exception {
        AuthUser owner = makeUser("rules@example.com");
        Customer customer = new Customer("Rules Customer", "rules-customer@example.com", null); customers.save(customer);
        Payment payment = new Payment(); payment.setAmount(new BigDecimal("100")); payment.setStatus(Payment.PaymentStatus.FAILED); payment.setCustomer(customer); payments.save(payment);
        RecoveryCase recoveryCase = new RecoveryCase(); recoveryCase.setPayment(payment); recoveryCase.setOwner(owner); recoveryCase.setRevenueAtRisk(new BigDecimal("100")); recoveryCase.setRecoveryAttempts(3); cases.save(recoveryCase);
        String userToken = token("rules@example.com", "SecurePass!234", false);
        mvc.perform(post("/api/recovery-actions/for-case/" + recoveryCase.getId()).param("actionType", "SEND_REMINDER").header("Authorization", "Bearer " + userToken)).andExpect(status().isConflict());
        recoveryCase.setStatus(RecoveryCase.CaseStatus.STOPPED); cases.save(recoveryCase);
        mvc.perform(post("/api/recovery-collections/confirm").param("caseId", recoveryCase.getId().toString()).param("amount", "10").param("referenceCode", "test-reference").header("Authorization", "Bearer " + userToken)).andExpect(status().isConflict());
    }

    @Test void userCanCreateCaseOnlyFromPaymentAssignedToThem() throws Exception {
        AuthUser owner = makeUser("payment-owner@example.com"); AuthUser stranger = makeUser("payment-stranger@example.com");
        Customer customer = new Customer("Assigned Customer", "assigned-customer@example.com", null); customers.save(customer);
        Payment payment = new Payment(); payment.setAmount(new BigDecimal("250")); payment.setStatus(Payment.PaymentStatus.FAILED); payment.setCustomer(customer); payment.setOwner(owner); payments.save(payment);
        String ownerToken = token("payment-owner@example.com", "SecurePass!234", false);
        String strangerToken = token("payment-stranger@example.com", "SecurePass!234", false);
        mvc.perform(get("/api/payments/owned-failed").header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(payment.getId()));
        mvc.perform(post("/api/recovery-cases/from-payment/" + payment.getId()).header("Authorization", "Bearer " + strangerToken)).andExpect(status().isNotFound());
        mvc.perform(post("/api/recovery-cases/from-payment/" + payment.getId()).header("Authorization", "Bearer " + ownerToken)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").exists());
        org.junit.jupiter.api.Assertions.assertEquals(owner.getId(), cases.findByPaymentId(payment.getId()).orElseThrow().getOwner().getId());
    }

    @Test void anotherUsersRecoveryCaseIsNotVisibleAndDashboardIsScoped() throws Exception {
        AuthUser owner = makeUser("owner@example.com"); AuthUser stranger = makeUser("stranger@example.com");
        Customer customer = new Customer("Private Customer", "customer@example.com", null); customers.save(customer);
        Payment payment = new Payment(); payment.setAmount(new BigDecimal("500")); payment.setStatus(Payment.PaymentStatus.FAILED); payment.setCustomer(customer); payments.save(payment);
        RecoveryCase recoveryCase = new RecoveryCase(); recoveryCase.setPayment(payment); recoveryCase.setOwner(owner); recoveryCase.setRevenueAtRisk(new BigDecimal("500")); cases.save(recoveryCase);
        String ownerToken = token("owner@example.com", "SecurePass!234", false);
        String strangerToken = token("stranger@example.com", "SecurePass!234", false);
        mvc.perform(get("/api/recovery-cases/" + recoveryCase.getId()).header("Authorization", "Bearer " + strangerToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + strangerToken)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCases", is(0)));
        mvc.perform(get("/api/recovery-cases/" + recoveryCase.getId()).header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());
    }

    private AuthUser makeUser(String email) { AuthUser u = new AuthUser(); u.setFullName("Test User"); u.setEmail(email); u.setPasswordHash(encoder.encode("SecurePass!234")); u.setRole(AuthUser.Role.USER); u.setEnabled(true); return users.save(u); }
    private String token(String email, String password, boolean admin) throws Exception {
        if (!users.existsByEmail(email)) {
            AuthUser u = new AuthUser(); u.setFullName("Test"); u.setEmail(email); u.setPasswordHash(encoder.encode(password)); u.setRole(admin ? AuthUser.Role.ADMIN : AuthUser.Role.USER); u.setEnabled(true); users.save(u);
        }
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"").matcher(response);
        if (!matcher.find()) throw new IllegalStateException("Login did not return a token");
        return matcher.group(1);
    }
}
