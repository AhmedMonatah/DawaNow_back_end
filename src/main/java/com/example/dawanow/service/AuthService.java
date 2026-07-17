package com.example.dawanow.service;

import com.example.dawanow.dtos.request.*;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.AuthResponse;
import com.example.dawanow.dtos.response.UserResponse;
import com.example.dawanow.entity.*;
import com.example.dawanow.exception.ResourceAlreadyExistsException;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.UserMapper;
import com.example.dawanow.repo.UserRepository;
import com.example.dawanow.repo.UserTokenRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final UserTokenRepository userTokenRepository;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final OtpService otpService;

    private final Map<String, RegisterRequest> pendingRegistrations =
            new ConcurrentHashMap<>();


    public AuthService(AuthenticationManager authenticationManager,OtpService otpService, EmailService emailService, PasswordEncoder passwordEncoder,  JwtService jwtService,  UserMapper userMapper, UserRepository userRepository, UserTokenRepository userTokenRepository) {
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.userTokenRepository = userTokenRepository;
        this.emailService = emailService;
        this.otpService = otpService;
    }

    public ApiResponse<Void> register(RegisterRequest request) {
        if(userRepository.findByEmail(request.email()) != null) {
            throw new ResourceAlreadyExistsException("Email already exists");
        }

        if(userRepository.findByPhoneNumber(request.phoneNumber()) != null) {
            throw new ResourceAlreadyExistsException("Phone Number already exists");
        }

        String otpcode = otpService.generateOtp(request.email());
        emailService.sendOtpEmail(request.email(),request.firstName(),otpcode);

        pendingRegistrations.put(request.email(), request);

        return new ApiResponse<>(true, "Registration initiated successfully. Please verify the OTP sent to your email.", null );
    }

    public ApiResponse<Void> logout(LogoutRequest request) {

        UserToken userToken =
                userTokenRepository.findByRefreshToken(request.refreshToken())
                        .orElseThrow(() ->
                                new ResourceNotFoundException("Refresh token not found"));

        userTokenRepository.delete(userToken);
        return new ApiResponse<>(true, "Logout successful", null);
    }

    public AuthResponse verify(VerifyRequest request) {

        if (!otpService.validateOtp(request.email(), request.otpCode())) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        RegisterRequest registerRequest = pendingRegistrations.get(request.email());

        if (registerRequest == null) {
            throw new IllegalArgumentException("No pending registration found.");
        }

        if(userRepository.findByEmail(registerRequest.email()) != null) {
            throw new ResourceAlreadyExistsException("Email already exists");
        }

        if(userRepository.findByPhoneNumber(registerRequest.phoneNumber()) != null) {
            throw new ResourceAlreadyExistsException("Phone Number already exists");
        }


        String hashedPassword = passwordEncoder.encode(registerRequest.password());



        User user = switch (registerRequest.role()) {

            case CUSTOMER -> new Customer();

            case ADMIN -> new User();

            case PHARMACIST -> new Pharmacist();
        };


        user.setEmail(registerRequest.email());
        user.setPassword(hashedPassword);
        user.setPhoneNumber(registerRequest.phoneNumber());
        user.setFirstName(registerRequest.firstName());
        user.setLastName(registerRequest.lastName());
        user.setHomeAddress(registerRequest.homeAddress());
        user.setDob(registerRequest.dob());
        user.setRole(registerRequest.role());


        user = userRepository.save(user);

        pendingRegistrations.remove(request.email());


        return createAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
//        UserDetails userDetails =
//                (UserDetails) authentication.getPrincipal();

        User user = (User) authentication.getPrincipal();

        return createAuthResponse(user) ;
    }

    public AuthResponse refresh(RefreshTokenRequest request){
        String refreshToken = request.refreshToken();

        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        if (!jwtService.validateJwtToken(refreshToken)) {
          throw new BadCredentialsException("Invalid refresh token");
        }

        UserToken userToken = userTokenRepository
                .findByRefreshToken(request.refreshToken())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Refresh token not found"));



        User user = userToken.getUser();



        //Generate new access token
        String accessToken =
                jwtService.generateAccessToken(user.getUsername(), String.valueOf(user.getRole()));

        // Map user
        UserResponse userResponse =
                userMapper.toResponse(user);

        // Return same refresh token
        return new AuthResponse(
                accessToken,
                request.refreshToken(),
                userResponse
        );

    }

    private AuthResponse createAuthResponse(User user) {

        String accessToken = jwtService.generateAccessToken(user.getUsername(), String.valueOf(user.getRole()));
        String refreshToken = jwtService.generateRefreshToken(user.getUsername(), String.valueOf(user.getRole()));

        UserToken userToken = new UserToken();
        userToken.setUser(user);
        userToken.setRefreshToken(refreshToken);

        Date expiration = jwtService.extractExpiration(refreshToken);

        LocalDateTime expirationDate = expiration.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        userToken.setExpirationDate(expirationDate);
        userTokenRepository.save(userToken);

        UserResponse userResponse = userMapper.toResponse(user);
//        System.out.println("Access expires: " + jwtService.extractExpiration(accessToken));
//        System.out.println("Refresh expires: " + jwtService.extractExpiration(refreshToken));

        return new AuthResponse(
                accessToken,
                refreshToken,
                userResponse
        );
    }
}
