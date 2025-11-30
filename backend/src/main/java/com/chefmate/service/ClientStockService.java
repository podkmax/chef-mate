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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ClientStockService {
    private final ClientStockRepository clientStockRepository;
    private final UserRepository userRepository;
    private final BaseProductRepository baseProductRepository;

    @Transactional(readOnly = true)
    public List<ClientStockDto> getClientStock(Long userId) {
        return clientStockRepository.findByUserId(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public ClientStockDto saveStock(Long userId, ClientStockDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        BaseProduct baseProduct = baseProductRepository.findById(dto.baseProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base product not found"));
        ClientStock stock = dto.id() != null
                ? clientStockRepository.findByIdAndUserId(dto.id(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found"))
                : new ClientStock();
        stock.setUser(user);
        stock.setBaseProduct(baseProduct);
        stock.setUnit(baseProduct.getUnit());
        stock.setQty(normalizeQuantity(dto.qty()));
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
        BaseProduct baseProduct = stock.getBaseProduct();
        UUID baseProductId = baseProduct != null ? baseProduct.getId() : null;
        String baseProductName = baseProduct != null ? baseProduct.getName() : null;
        Boolean isFreezable = baseProduct != null ? baseProduct.getIsFreezable() : null;
        return new ClientStockDto(
                stock.getId(),
                baseProductId,
                stock.getQty(),
                stock.getUnit(),
                baseProductName,
                isFreezable);
    }

    private BigDecimal normalizeQuantity(BigDecimal qty) {
        if (qty == null) {
            return BigDecimal.ZERO;
        }
        return qty.max(BigDecimal.ZERO);
    }
}
