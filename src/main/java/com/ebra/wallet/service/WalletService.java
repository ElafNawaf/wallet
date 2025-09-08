package com.ebra.wallet.service;

import com.ebra.wallet.entity.Transaction;
import com.ebra.wallet.entity.TransactionType;
import com.ebra.wallet.entity.User;
import com.ebra.wallet.exception.DuplicateTransactionException;
import com.ebra.wallet.exception.InsufficientBalanceException;
import com.ebra.wallet.exception.UserAlreadyExistsException;
import com.ebra.wallet.exception.UserNotFoundException;
import com.ebra.wallet.repository.TransactionRepository;
import com.ebra.wallet.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class WalletService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Autowired
    public WalletService(UserRepository userRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Creates a new user account with zero balance
     */
    @Transactional
    public User createAccount(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("User with username '" + username + "' already exists");
        }

        User user = new User(username);
        return userRepository.save(user);
    }

    /**
     * Adds balance to a user account
     */
    @Transactional
    public Transaction topUp(String username, BigDecimal amount, String idempotencyKey) {
        // Validate amount precision (2 decimal places max)
        validateAmount(amount);

        // Check for duplicate transaction
        Optional<Transaction> existingTransaction = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTransaction.isPresent()) {
            throw new DuplicateTransactionException("Transaction with idempotency key already exists");
        }

        // Get user with pessimistic lock to prevent concurrent modifications
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        BigDecimal balanceBefore = user.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        // Update user balance
        user.setBalance(balanceAfter);
        userRepository.save(user);

        // Create transaction record
        Transaction transaction = new Transaction(user, TransactionType.TOP_UP, amount,
                balanceBefore, balanceAfter, idempotencyKey);
        return transactionRepository.save(transaction);
    }

    /**
     * Deducts balance from a user account
     */
    @Transactional
    public Transaction charge(String username, BigDecimal amount, String idempotencyKey) {
        // Validate amount precision (2 decimal places max)
        validateAmount(amount);

        // Check for duplicate transaction
        Optional<Transaction> existingTransaction = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTransaction.isPresent()) {
            throw new DuplicateTransactionException("Transaction with idempotency key already exists");
        }

        // Get user with pessimistic lock to prevent concurrent modifications
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        BigDecimal balanceBefore = user.getBalance();

        // Check if user has sufficient balance
        if (balanceBefore.compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance. Current balance: " +
                    balanceBefore + ", attempted charge: " + amount);
        }

        BigDecimal balanceAfter = balanceBefore.subtract(amount);

        // Update user balance
        user.setBalance(balanceAfter);
        userRepository.save(user);

        // Create transaction record
        Transaction transaction = new Transaction(user, TransactionType.CHARGE, amount,
                balanceBefore, balanceAfter, idempotencyKey);
        return transactionRepository.save(transaction);
    }

    /**
     * Gets user by username
     */
    @Transactional(readOnly = true)
    public User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }

    /**
     * Validates that amount has maximum 2 decimal places and is positive
     */
    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        // Ensure precision is at most 2 decimal places
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(scaled) != 0) {
            throw new IllegalArgumentException("Amount cannot have more than 2 decimal places");
        }
    }
}