package com.darshan.ecommerce.user;

import com.darshan.ecommerce.common.exception.ConflictException;
import com.darshan.ecommerce.common.exception.ResourceNotFoundException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
            throw new ConflictException("Email already registered");
        }
        
        Role customerRole = roleRepository.findByName(Role.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Role " + Role.CUSTOMER + " is not seeded"));

        User user = new User(request.getEmail(), passwordEncoder.encode(request.getPassword()),
                request.getFullName(), Set.of(customerRole));
        return mapToDTO(userRepository.save(user));
    }

    public UserResponseDTO getById(Long id) {
        return userRepository.findWithRolesById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
    }

    public User getEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
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
        dto.setRoles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()));
        return dto;
    }
}

