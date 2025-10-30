package com.chefmate.service;

import com.chefmate.dto.ClientStockDto;
import com.chefmate.model.BaseProduct;
import com.chefmate.model.ClientStock;
import com.chefmate.model.User;
import com.chefmate.repo.BaseProductRepository;
import com.chefmate.repo.ClientStockRepository;
import com.chefmate.repo.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientStockService {
    private final ClientStockRepository clientStockRepository;
    private final UserRepository userRepository;
    private final BaseProductRepository baseProductRepository;

    public ClientStockService(ClientStockRepository clientStockRepository,
                              UserRepository userRepository,
                              BaseProductRepository baseProductRepository) {
        this.clientStockRepository = clientStockRepository;
        this.userRepository = userRepository;
        this.baseProductRepository = baseProductRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientStockDto> getClientStock(Long userId) {
        return clientStockRepository.findByUserId(userId).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ClientStockDto saveStock(Long userId, ClientStockDto dto) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        BaseProduct baseProduct = baseProductRepository.findById(dto.baseProductId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base product not found"));
        ClientStock stock;
        if (dto.id != null) {
            stock = clientStockRepository.findByIdAndUserId(dto.id, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found"));
        } else {
            stock = new ClientStock();
            stock.user = user;
        }
        stock.baseProduct = baseProduct;
        stock.unit = baseProduct.unit;
        stock.qty = normalizeQuantity(dto.qty);
        return toDto(clientStockRepository.save(stock));
    }

    @Transactional(readOnly = true)
    public ClientStockDto findStock(Long userId, UUID baseProductId) {
        return clientStockRepository.findByUserIdAndBaseProductId(userId, baseProductId)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ClientStockDto findStockById(Long userId, UUID stockId) {
        return clientStockRepository.findByIdAndUserId(stockId, userId)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public void deleteStock(Long userId, UUID stockId) {
        ClientStock stock = clientStockRepository.findByIdAndUserId(stockId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found"));
        clientStockRepository.delete(stock);
    }

    private ClientStockDto toDto(ClientStock stock) {
        ClientStockDto dto = new ClientStockDto();
        dto.id = stock.id;
        dto.baseProductId = stock.baseProduct != null ? stock.baseProduct.id : null;
        dto.baseProductName = stock.baseProduct != null ? stock.baseProduct.name : null;
        dto.qty = stock.qty;
        dto.unit = stock.unit;
        dto.isFreezable = stock.baseProduct != null ? stock.baseProduct.isFreezable : null;
        return dto;
    }

    private BigDecimal normalizeQuantity(BigDecimal qty) {
        if (qty == null) {
            return BigDecimal.ZERO;
        }
        return qty.max(BigDecimal.ZERO);
    }
}
