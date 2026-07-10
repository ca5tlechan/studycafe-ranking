package com.studycafe.ranking.user;

import com.studycafe.ranking.common.exception.UserNotFoundException;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.user.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long userId) {
        return userRepository.findByIdWithSchool(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
