package com.example.ibudgetproject.controllers.User;

import com.example.ibudgetproject.DTO.User.*;
import com.example.ibudgetproject.entities.User.FinancialKnowledgeLevel;
import com.example.ibudgetproject.entities.User.Tone;
import com.example.ibudgetproject.entities.User.User;
//import com.example.ibudgetproject.services.AIService;
import com.example.ibudgetproject.services.User.ChatService;
import com.example.ibudgetproject.services.User.Interfaces.IUserService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private IUserService service;
    @Autowired
    private ChatService chatService;
    @PostMapping("/completeProfile")
    public ResponseEntity<?> completeProfile(@RequestBody @Valid CompleteProfileRequest request, HttpServletRequest req,@RequestParam String email ) throws MessagingException {
        try {
            service.completProfile(request,req,email);
            return ResponseEntity.status(HttpStatus.CREATED).body("Registered with success");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest request, HttpServletRequest req) throws MessagingException {
        try {
            service.register(request,req);
            return ResponseEntity.status(HttpStatus.CREATED).body("Registered with success");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthenticationRequest request, HttpServletRequest req) throws MessagingException {
        try {
            AuthenticationResponse response = service.authenticate(request,req);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/refreshToken")
    public void refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        service.refreshToken(request, response);
    }



    @PostMapping("/activateAccount")
    public ResponseEntity<?> confirm(@RequestBody @Valid ActivationRequest activationRequest
    ) {
        try {
            service.activateAccount(activationRequest);
            return ResponseEntity.ok("Account activated successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/resendActivationCode")
    public ResponseEntity<?> resendVerificationCode (@RequestBody  ResendActivationCodeRequest request)
    {
        try {
            service.resendActivationCode(request.getEmail());
            return ResponseEntity.ok("Activation code sent");
        } catch (RuntimeException | MessagingException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/changePassword")
    public ResponseEntity<?> changePassword(
            @RequestBody @Valid
            ChangePasswordRequest request,
            @AuthenticationPrincipal User connectedUser
    ) {
        try {
            service.changePassword(request, connectedUser);
            return ResponseEntity.ok("Password changed successfully");
        }catch(IllegalStateException e){

            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PatchMapping("/updateUser")
    public ResponseEntity<?> updateUser(@AuthenticationPrincipal User connectedUser
            ,@RequestBody UpdateUserRequest userDetails)
    {
        try {
            service.updateUser(connectedUser,userDetails);
            return ResponseEntity.ok("Information updated with success");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PostMapping("/sendResetPasswordRequest")
    public ResponseEntity<String> sendResetEmail(@RequestBody ResetPasswordEmailRequest request) {
        try {
            service.sendPasswordResetEmail(request.getEmail());
            return ResponseEntity.ok("Password reset link sent to email");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/resetPassword")
    public ResponseEntity<String> resetPassword(@RequestParam String token, @RequestBody @Valid ResetPasswordRequest newPassword) {
        try {
            service.resetPassword(token, newPassword);
            return ResponseEntity.ok("Password has been successfully reset");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/getUserByAdmin/{userId}")
    // @RolesAllowed("ROLE_ADMIN")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        try {
            User user = service.getUserById(userId);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.status(NOT_FOUND).body(e.getMessage());
        }
    }
    @GetMapping("/getAllUsersByAdmin")
    @RolesAllowed("ROLE_ADMIN")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<User> users = service.getAllUsers();
            return ResponseEntity.ok(users);
        } catch (RuntimeException e) {
            return ResponseEntity.status(NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/accountDeletionRequest")
    @RolesAllowed({"ROLE_USER_FREMIUM", "ROLE_USER_PREMIUM"})
    public ResponseEntity<String> requestDeleteAccount(@AuthenticationPrincipal User connectedUser  , @RequestBody DeletionRequest request) {
        if (!service.verifyPassword(connectedUser.getEmail(),request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Password does not match.");
        }
        boolean isRequested = service.requestDeleteAccount(connectedUser.getEmail());
        if (isRequested) {
            return ResponseEntity.ok("Account deletion requested.Your account will be permanently deleted soon till then you can restore access");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Account deletion request Failed");
        }
    }

    @DeleteMapping("/deleteUserByAdmin")
    @RolesAllowed("ROLE_ADMIN")
    public ResponseEntity<String> deleteUserByAdmin() {
        boolean isDeleted = service.deleteUsersByAdmin();
        if (isDeleted) {
            return ResponseEntity.ok("Deleted successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Deletion failed.");
        }
    }

    @GetMapping("/getDeletionRequestsAdmin")
    @RolesAllowed("ROLE_ADMIN")
    public ResponseEntity<?> getDeletionRequests() {
        try {
            List<User> users = service.getUsersRequestedDeletion();
            return ResponseEntity.ok(users);
        }catch(Exception e) {
            return ResponseEntity.status(NOT_FOUND).body(e.getMessage());}
    }



    //**** Update first/last name and date of birth
    @PostMapping("/sendUpdateRequest")
    @RolesAllowed({"ROLE_USER_FREMIUM", "ROLE_USER_PREMIUM"})
    public ResponseEntity<?> sendUpdateRequest(@AuthenticationPrincipal User connectedUser,@RequestBody UpdateUserByAdminRequest request) {

        if (!service.verifyPassword(connectedUser.getEmail(),request.getCurrentPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Password does not match.");
        }
        boolean isRequested = service.requestUpdateAccount(connectedUser.getEmail(),request);
        if (isRequested) {
            return ResponseEntity.ok("Account update request sent.");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Account update request Failed.");
        }
    }

    @GetMapping("/getUpdateRequestsAdmin")
    @RolesAllowed("ROLE_ADMIN")
    public ResponseEntity<?> getUpdateRequests() {
        try {
            List<User> users = service.getUsersRequestedUpdate();
            return ResponseEntity.ok(users);
        }catch(Exception e) {
            return ResponseEntity.status(NOT_FOUND).body(e.getMessage());}
    }

    @PostMapping("/updateUserByAdmin/{userId}")
    @RolesAllowed("ROLE_ADMIN")
    public ResponseEntity<String> updateUserByAdmin(@PathVariable Long userId) {
        boolean isUpdated = service.updateUserByAdmin(userId);
        if (isUpdated) {
            return ResponseEntity.ok("User updated successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Couldn't update user , please check the id");
        }
    }
    @PostMapping("/advice")
    @RolesAllowed({"ROLE_USER_FREMIUM", "ROLE_USER_PREMIUM"})
    public ResponseEntity<String> getFinancialAdvice(@RequestBody ChatRequest request, @AuthenticationPrincipal User connectedUser) {
        try {
            String advice = chatService.generateFinancialAdvice(request.getQuestion(), connectedUser.getAiTonePreference(), connectedUser.getFinancialKnowledgeLevel());
            return ResponseEntity.ok(advice);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error generating financial advice.");
        }

    }

}