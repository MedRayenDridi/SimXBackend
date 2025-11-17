package com.example.ibudgetproject.services.User;

import com.example.ibudgetproject.configurations.EmailTemplateName;
import com.example.ibudgetproject.DTO.User.*;
import com.example.ibudgetproject.entities.User.ConnexionInformation;
import com.example.ibudgetproject.entities.User.TypeAccount;
import com.example.ibudgetproject.entities.User.User;
import com.example.ibudgetproject.repositories.User.ConnexionInfoRepository;
import com.example.ibudgetproject.repositories.User.RoleRepository;
import com.example.ibudgetproject.repositories.User.UserRepository;
import com.example.ibudgetproject.security.JWTService;
import com.example.ibudgetproject.services.User.Interfaces.IConnexionInfoService;
import com.example.ibudgetproject.services.User.Interfaces.IUserService;
import com.example.ibudgetproject.utilities.EncryptionUtility;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserService implements IUserService {
    //rayen--------------------------------------------------------------

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ConnexionInfoRepository connexionInfoRepository;

    @Autowired
    AuthenticationManager authenticationManager;


    @Autowired
    private JWTService jwtService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private IConnexionInfoService cnxInfoService;

    @Value("${abstractapi.api.key}")
    private String apiKey;

    @Value("${abstractapi.api.url}")
    private String apiUrl;

    @Autowired
    private  RestTemplate restTemplate = new RestTemplate();

    private EncryptionUtility encryptor = new EncryptionUtility();

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private static final long tokenExpirationInMinutes = 5;

    //**Get User
    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found!"));
    }

    @Override
    public List<User> getAllUsers() {

        return userRepository.findAll();
    }
    //**Delete User**
    @Override
    public boolean requestDeleteAccount(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setDeletionRequested(true);
            user.setAccountLocked(true);
            user.setAccountEnabled(false);
            userRepository.save(user);
            return true;
        }
        return false;

    }
    @Override
    public List<User> getUsersRequestedDeletion() {
        return userRepository.findByDeletionRequestedTrue();
    }

    @Override
    public boolean deleteUsersByAdmin() {
        List<User> usersToDelete = userRepository.findByDeletionRequestedTrue();
        if (!usersToDelete.isEmpty()) {
            userRepository.deleteAll(usersToDelete);
            return true;
        }
        return false;

    }
    @Override
    public void updateUser(User connectedUser, UpdateUserRequest userDetails) throws Exception {
        if(verifyPassword(connectedUser.getEmail(), userDetails.getCurrentPassword()))
        {
            if(userDetails.getEmail()!=null){
                if (!isEmailValid(userDetails.getEmail()))
                {
                    throw new Exception("Email not valid or doesn't exists");
                }else{
                    connectedUser.setEmail(userDetails.getEmail());
                }
            }
            if(userDetails.getProfession()!=null)
                connectedUser.setProfession(userDetails.getProfession());
            if(userDetails.getPhoneNumber()!=null)
                connectedUser.setPhoneNumber(userDetails.getPhoneNumber());
            if(userDetails.getAiTonePreference()!=null)
                connectedUser.setAiTonePreference(userDetails.getAiTonePreference());
            if(userDetails.getFinancialKnowledgeLevel()!=null)
                connectedUser.setFinancialKnowledgeLevel(userDetails.getFinancialKnowledgeLevel());
            userRepository.save(connectedUser);


        }else{
            throw  new Exception("Uncorrect Password");
        }
    }
    @Override
    public List<User> getUsersRequestedUpdate() {
        return  userRepository.findByUpdateRequestedTrue();
    }

    @Override
    public boolean updateUserByAdmin(Long id) {
        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isPresent()) {
            User user=optionalUser.get();
            if (user.getUpdateRequested())
            {
                if(user.getFirstNameUpdate()!=null) {
                    user.setFirstName(user.getFirstNameUpdate());
                    user.setFirstNameUpdate(null);
                }
                if(user.getLastNameUpdate()!=null) {
                    user.setLastName(user.getLastNameUpdate());
                    user.setLastNameUpdate(null);
                }
                if(user.getDateOfBirthUpdate()!=null) {
                    user.setDateOfBirth(user.getDateOfBirthUpdate());
                    user.setDateOfBirthUpdate(null);
                }
                user.setUpdateRequested(false);
                userRepository.save(user);
                return  true;

            }

        }
        return false;
    }

    @Override
    public boolean requestUpdateAccount(String email,UpdateUserByAdminRequest request) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setUpdateRequested(true);
            if(request.getFirstName()!=null) {
                user.setFirstNameUpdate(request.getFirstName());
            }
            if(request.getLastName()!=null) {
                user.setLastNameUpdate(request.getLastName());
            }
            if(request.getDateOfBirth()!=null) {
                user.setDateOfBirthUpdate(request.getDateOfBirth());
            }
            userRepository.save(user);
            return true;
        }
        return false;
    }

    ////******Verify that the email is valid and exists
    @Override

    public boolean isEmailValid(String email) {
        String url = String.format("%s?api_key=%s&email=%s", apiUrl, apiKey, email);
        EmailValidationResponse response = restTemplate.getForObject(url, EmailValidationResponse.class);

        if (response == null) return false;

        return "DELIVERABLE".equalsIgnoreCase(response.getDeliverability())
                && response.getValidFormat().isValue()
                && response.getMxFound().isValue()
                && response.getSmtpValid().isValue();
    }

    //***Register
    @Override
    public void register(RegisterRequest request, HttpServletRequest req) throws Exception {
        String code = generateActivationCode();
        var userRole = roleRepository.findByName("ROLE_USER_FREMIUM")
                .orElseThrow(() -> new IllegalStateException("ROLE USER was not initiated"));

        if (!isEmailValid(request.getEmail())) {
            throw new Exception("Email not valid or doesn't exists");
        }

        var user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(encoder.encode(request.getPassword()))
                .gender(request.getGender())
                .profession(request.getProfession())
                .dateOfBirth(request.getDateOfBirth())
                .phoneNumber(request.getPhoneNumber())
                .accountLocked(false)
                .accountEnabled(false)
                .deletionRequested(false)
                .role(userRole)
                .activationCode(code)
                .activationCodeExpiry(LocalDateTime.now().plusMinutes(15))
                .accountType(TypeAccount.Fremium)
                .aiTonePreference(request.getAiTonePreference())
                .financialKnowledgeLevel(request.getFinancialKnowledgeLevel())
                .dateOfBirthUpdate(null)
                .firstNameUpdate(null)
                .lastNameUpdate(null)
                .updateRequested(false)
                .build();

        // Save the user
        userRepository.save(user);

        // rayen---------------creation_sim_card_account_ma_kol_user----------------
        //--------------------------------------------------------------------------------

        cnxInfoService.add(user, req, "register");
        sendActivationEmail(user);
    }
    @Override
    public void completProfile(CompleteProfileRequest request, HttpServletRequest req, String email) throws Exception {
        var userRole = roleRepository.findByName("ROLE_USER_FREMIUM")
                .orElseThrow(() -> new IllegalStateException("ROLE USER was not initiated"));
        try{
            var user = User.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(email)
                    .password(encoder.encode(request.getPassword()))
                    .gender(request.getGender())
                    .profession(request.getProfession())
                    .dateOfBirth(request.getDateOfBirth())
                    .phoneNumber(request.getPhoneNumber())
                    .accountLocked(false)
                    .accountEnabled(true)
                    .deletionRequested(false)
                    .role(userRole)
                    .activationCode(null)
                    .activationCodeExpiry(null)
                    .accountType(TypeAccount.Fremium)
                    .financialKnowledgeLevel(request.getFinancialKnowledgeLevel())
                    .aiTonePreference(request.getAiTonePreference())
                    .dateOfBirthUpdate(null)
                    .firstNameUpdate(null)
                    .lastNameUpdate(null)
                    .updateRequested(false)
                    .build();

            userRepository.save(user);
            cnxInfoService.add(user,req,"register");
        }catch (Exception e)
        {
            throw new Exception(("Registration failed "));
        }

    }



    ////***Account Activation
    @Override
    public void activateAccount(ActivationRequest activationRequest) {
        Optional<User> optionalUser = userRepository.findByEmail(activationRequest.getEmail());
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.getActivationCodeExpiry().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("Activation code has expired");
            }
            if (user.getActivationCode().equals(activationRequest.getActivationCode())) {
                user.setAccountEnabled(true);
                user.setActivationCode(null);
                user.setActivationCodeExpiry(null);
                if (user.getAccountLocked())
                    user.setAccountLocked(false);
                user.setFailedAttempts(0);
                userRepository.save(user);
            } else {
                throw new RuntimeException("Invalid activation code");
            }
        } else {
            throw new RuntimeException("User not found");
        }
    }
    @Override

    public void resendActivationCode(String email) throws MessagingException {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        User user = optionalUser.orElseThrow(() -> new RuntimeException("User not found:" + email));
        if (user.isEnabled()) {
            throw new RuntimeException("Account is already verified");
        }
        user.setActivationCode(generateActivationCode());
        user.setActivationCodeExpiry(LocalDateTime.now().plusHours(1));

        if (user.getAccountLocked()) {
            sendRestoreAccountAccessEmail(user);
        } else {
            sendActivationEmail(user);
        }
        userRepository.save(user);

    }
    ///*****LogIn
    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request , HttpServletRequest req) throws Exception {
        Optional<User> optionalUser = userRepository.findByEmail(request.getEmail());

        if (optionalUser.isEmpty()) {
            throw new Exception("User not found");
        }

        User user = optionalUser.get();

        if (!user.isAccountNonLocked()) {
            sendAccountBlockedAlertEmail(user);
            throw new Exception("Your account is locked due to multiple failed login attempts");
        }
        if(!cnxInfoService.verifyConnectionInfo(user,req))
        {
            ConnexionInformation cnxInfo=cnxInfoService.add(user,req,"login");
            cnxInfoService.sendLogInAlertEmail(user.getEmail(),cnxInfo);
            throw new Exception("A login attempt from a different device has been detected");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            if (authentication.isAuthenticated()) {
                user.setFailedAttempts(0);
                userRepository.save(user);
                return AuthenticationResponse.builder()
                        .accessToken(jwtService.generateToken(request.getEmail()))
                        .refreshToken(jwtService.generateRefreshToken(request.getEmail()))
                        .role(user.getRole().getName())
                        .email(user.getEmail())
                        .id(user.getUserId())
                        .build();
            }

        } catch (Exception e) {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() == 3) {
                user.setAccountLocked(true);
                user.setAccountEnabled(false);
            }

            userRepository.save(user);
            throw new Exception("Login failed " + (3 - user.getFailedAttempts()) + " attempts remaining");

        }

        throw new Exception("Invalid credentials");
    }

    ///*****Refresh token
    @Override
    public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        //final String header = request.getHeader("Refresh-Token");
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        final String refreshToken;
        final String userEmail;
        /*if (header == null ) {
            return;
        }else {
            refreshToken = header;
        }*/
        if (authHeader == null ||!authHeader.startsWith("Bearer ")) {
            return;
        }
        refreshToken = authHeader.substring(7);
        userEmail = jwtService.extractUserName(refreshToken);
        if (userEmail != null) {
            var user = this.userRepository.findByEmail(userEmail)
                    .orElseThrow();
            if (jwtService.validateToken(refreshToken, user)) {
                var accessToken = jwtService.generateToken(user.getEmail());
                var authResponse = AuthenticationResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .role(user.getRole().getName())
                        .build();
                new ObjectMapper().writeValue(response.getOutputStream(), authResponse);
            }
        }
    }

    ///****Password
    @Override
    public void changePassword(ChangePasswordRequest request, User user) {

        if (!encoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalStateException("Wrong password");
        }
        if (!request.getNewPassword().equals(request.getConfirmationPassword())) {
            throw new IllegalStateException("New password and confirmation new password are not the same");
        }
        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
    @Override
    public void resetPassword(String token, ResetPasswordRequest request) throws Exception {
        String decryptedData = encryptor.decrypt(token);
        String[] parts = decryptedData.split(":");
        if (parts.length != 2) {
            throw new RuntimeException("Invalid token format");
        }

        String email = parts[0];
        long timestamp = Long.parseLong(parts[1]);

        long currentTime = Instant.now().getEpochSecond();
        long timeDifference = Duration.between(Instant.ofEpochSecond(timestamp), Instant.ofEpochSecond(currentTime)).toMinutes();

        if (timeDifference >tokenExpirationInMinutes) {
            throw new RuntimeException("Token expired");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
    @Override
    public boolean verifyPassword(String email, String password) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isPresent()) {
            String storedPassword = optionalUser.get().getPassword();
            return encoder.matches(password, storedPassword);
        }
        return false;
    }
    ///***Code Gneration
    @Override
    public String generateActivationCode() {

        String characters = "0123456789";
        StringBuilder codeBuilder = new StringBuilder();
        SecureRandom secureRandom = new SecureRandom();

        for (int i = 0; i < 6; i++) {
            int randomIndex = secureRandom.nextInt(characters.length());
            codeBuilder.append(characters.charAt(randomIndex));
        }

        String activationCode = codeBuilder.toString();

        return activationCode;

    }
    //***les Emails
    @Override
    public void sendActivationEmail(User user) throws MessagingException {
        String activationCode = user.getActivationCode();

        emailService.sendEmail(
                user.getEmail(),
                user.fullName(),
                EmailTemplateName.ACTIVATE_ACCOUNT,
                activationCode,
                new ConnexionInformation(),
                null,
                "Account Activation"
        );

    }
    @Override
    public void sendAccountBlockedAlertEmail(User user) throws MessagingException {
        String activationCode = user.getActivationCode();

        emailService.sendEmail(
                user.getEmail(),
                user.fullName(),
                EmailTemplateName.ALERT_BLOCKED_ACCOUNT,
                null,
                new ConnexionInformation(),
                null,
                "Alert : Account Blocked"
        );

    }
    @Override
    public void sendRestoreAccountAccessEmail(User user) throws MessagingException {
        String activationCode = user.getActivationCode();

        emailService.sendEmail(
                user.getEmail(),
                user.fullName(),
                EmailTemplateName.RESTORE_ACCOUNT_ACCESS,
                activationCode,
                new ConnexionInformation(),
                null,
                "Restore Account Access "
        );

    }
    @Override
    public void sendPasswordResetEmail(String email) throws Exception {

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            throw new Exception("User not found");
        }
        User user = userOptional.get();


        long timestamp = Instant.now().getEpochSecond();
        String tokenData = email + ":" + timestamp;

        String encryptedToken = encryptor.encrypt(tokenData);

        String resetLink = "http://localhost:4200/resetPassword?token=" + encryptedToken;

        emailService.sendEmail(
                user.getEmail(),
                user.fullName(),
                EmailTemplateName.RESET_PASSWORD,
                resetLink,
                new ConnexionInformation(),
                null,
                "Reset Password"
        );

    }
    //Teba3 Achref
    public User findUserProfileByJwt(String jwt) throws Exception {
        try {
            // Supprimer le préfixe "Bearer " s'il est présent
            if (jwt != null && jwt.startsWith("Bearer ")) {
                jwt = jwt.substring(7); // Supprime "Bearer "
            }

            // Supprimer les espaces autour du JWT
            jwt = jwt.trim();

            String email = jwtService.extractUserName(jwt); // Extraire l'email
            User user = userRepository.findByEmail(email).orElse(null); // Rechercher l'utilisateur

            if (user == null) {
                throw new Exception("User not found");
            }
            return user;
        } catch (MalformedJwtException e) {
            // Gérer l'exception JWT malformé
            throw new Exception("Malformed JWT: " + e.getMessage());
        }
    }




}