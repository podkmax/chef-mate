package com.chefmate.api;

import com.chefmate.dto.ClientDto;
import com.chefmate.model.User;
import com.chefmate.repo.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client")
@Tag(name = "Client")
public class ClientController {
    private final UserRepository userRepository;

    public ClientController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<ClientDto> listClients() {
        return userRepository.findByRoleIgnoreCase("CLIENT").stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ClientDto toDto(User user) {
        return new ClientDto(user.getId(), user.getName(), user.getTelegramId());
    }
}
