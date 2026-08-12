package com.example.ecommerce.service;

import com.example.ecommerce.dto.RegisterUserRequestDTO;
import com.example.ecommerce.dto.UserResponseDTO;
import com.example.ecommerce.model.Role;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.RoleRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponseDTO register(RegisterUserRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRoles(java.util.Set.of(customerRole));
        
        return mapToDTO(userRepository.save(user));
    }

    public UserResponseDTO getById(Long id) {
        return mapToDTO(getEntityById(id));
    }
    
    public User getEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id " + id));
    }

    public List<UserResponseDTO> listAll() {
        return userRepository.findAll().stream().map(this::mapToDTO).toList();
    }
    
    private UserResponseDTO mapToDTO(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setCreatedAt(user.getCreatedAt());
        if (user.getRoles() != null) {
            dto.setRoles(user.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet()));
        }
        return dto;
    }
}

