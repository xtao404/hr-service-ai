package com.hr.ai.service;

import com.hr.ai.dto.*;
import com.hr.ai.model.entity.KnowledgeDocument;
import com.hr.ai.model.entity.User;
import com.hr.ai.repository.KnowledgeDocumentRepository;
import com.hr.ai.repository.UserRepository;
import com.hr.ai.security.JwtTokenProvider;
import com.hr.ai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = tokenProvider.generateToken(principal);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(principal.getId());
        response.setUsername(principal.getUsername());
        response.setName(principal.getName());
        response.setRole(principal.getRole());
        response.setDepartmentId(principal.getDepartmentId());
        response.setDepartmentName(principal.getDepartmentName());
        return response;
    }

    public UserInfoResponse getCurrentUser(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId()).orElseThrow();
        UserInfoResponse info = new UserInfoResponse();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setName(user.getName());
        info.setRole(user.getRole());
        info.setDepartmentId(user.getDepartmentId());
        info.setDepartmentName(user.getDepartmentName());
        info.setEmployeeId(user.getEmployeeId());
        return info;
    }
}
