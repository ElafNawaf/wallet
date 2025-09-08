package com.ebra.wallet.controller;

import com.ebra.wallet.dto.ChargeRequest;
import com.ebra.wallet.dto.CreateAccountRequest;
import com.ebra.wallet.dto.TopUpRequest;
import com.ebra.wallet.dto.TransactionResponse;
import com.ebra.wallet.dto.UserResponse;
import com.ebra.wallet.entity.Transaction;
import com.ebra.wallet.entity.User;
import com.ebra.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
@CrossOrigin(origins = "*")
public class WalletController {

    private final WalletService walletService;

    @Autowired
    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * Creates a new user account
     * POST /api/wallet/account
     */
    @PostMapping("/account")
    public ResponseEntity<UserResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        User user = walletService.createAccount(request.getUsername());
        UserResponse response = mapToUserResponse(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Adds balance to a user account
     * POST /api/wallet/topup
     */
    @PostMapping("/topup")
    public ResponseEntity<TransactionResponse> topUp(@Valid @RequestBody TopUpRequest request) {
        Transaction transaction = walletService.topUp(
                request.getUsername(),
                request.getAmount(),
                request.getIdempotencyKey()
        );
        TransactionResponse response = mapToTransactionResponse(transaction);
        return ResponseEntity.ok(response);
    }

    /**
     * Deducts balance from a user account
     * POST /api/wallet/charge
     */
    @PostMapping("/charge")
    public ResponseEntity<TransactionResponse> charge(@Valid @RequestBody ChargeRequest request) {
        Transaction transaction = walletService.charge(
                request.getUsername(),
                request.getAmount(),
                request.getIdempotencyKey()
        );
        TransactionResponse response = mapToTransactionResponse(transaction);
        return ResponseEntity.ok(response);
    }

    /**
     * Gets user account information
     * GET /api/wallet/account/{username}
     */
    @GetMapping("/account/{username}")
    public ResponseEntity<UserResponse> getAccount(@PathVariable String username) {
        User user = walletService.getUser(username);
        UserResponse response = mapToUserResponse(user);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     * GET /api/wallet/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Wallet service is running");
    }

    // Helper methods to map entities to DTOs
    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getBalance(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType().toString(),
                transaction.getAmount(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getIdempotencyKey(),
                transaction.getCreatedAt()
        );
    }
}