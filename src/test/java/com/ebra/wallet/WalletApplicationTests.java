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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private TransactionRepository transactionRepository;

	@InjectMocks
	private WalletService walletService;

	private User testUser;
	private final String TEST_USERNAME = "testuser";
	private final String TEST_IDEMPOTENCY_KEY = "test-key-123";

	@BeforeEach
	void setUp() {
		testUser = new User(TEST_USERNAME);
		testUser.setId(1L);
		testUser.setBalance(new BigDecimal("100.00"));
	}

	@Test
	void createAccount_Success() {
		// Given
		when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(false);
		when(userRepository.save(any(User.class))).thenReturn(testUser);

		// When
		User result = walletService.createAccount(TEST_USERNAME);

		// Then
		assertNotNull(result);
		assertEquals(TEST_USERNAME, result.getUsername());
		assertEquals(BigDecimal.ZERO, result.getBalance());
		verify(userRepository).existsByUsername(TEST_USERNAME);
		verify(userRepository).save(any(User.class));
	}

	@Test
	void createAccount_UserAlreadyExists() {
		// Given
		when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(true);

		// When & Then
		assertThrows(UserAlreadyExistsException.class,
				() -> walletService.createAccount(TEST_USERNAME));
		verify(userRepository).existsByUsername(TEST_USERNAME);
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void topUp_Success() {
		// Given
		BigDecimal amount = new BigDecimal("50.00");
		when(transactionRepository.findByIdempotencyKey(TEST_IDEMPOTENCY_KEY))
				.thenReturn(Optional.empty());
		when(userRepository.findByUsernameForUpdate(TEST_USERNAME))
				.thenReturn(Optional.of(testUser));
		when(userRepository.save(any(User.class))).thenReturn(testUser);

		Transaction expectedTransaction = new Transaction(testUser, TransactionType.TOP_UP,
				amount, testUser.getBalance(), testUser.getBalance().add(amount), TEST_IDEMPOTENCY_KEY);
		when(transactionRepository.save(any(Transaction.class))).thenReturn(expectedTransaction);

		// When
		Transaction result = walletService.topUp(TEST_USERNAME, amount, TEST_IDEMPOTENCY_KEY);

		// Then
		assertNotNull(result);
		assertEquals(TransactionType.TOP_UP, result.getType());
		assertEquals(amount, result.getAmount());
		assertEquals(new BigDecimal("150.00"), testUser.getBalance());
		verify(transactionRepository).findByIdempotencyKey(TEST_IDEMPOTENCY_KEY);
		verify(userRepository).findByUsernameForUpdate(TEST_USERNAME);
		verify(userRepository).save(testUser);
		verify(transactionRepository).save(any(Transaction.class));
	}

	@Test
	void topUp_DuplicateTransaction() {
		// Given
		BigDecimal amount = new BigDecimal("50.00");
		Transaction existingTransaction = new Transaction();
		when(transactionRepository.findByIdempotencyKey(TEST_IDEMPOTENCY_KEY))
				.thenReturn(Optional.of(existingTransaction));

		// When & Then
		assertThrows(DuplicateTransactionException.class,
				() -> walletService.topUp(TEST_USERNAME, amount, TEST_IDEMPOTENCY_KEY));
		verify(transactionRepository).findByIdempotencyKey(TEST_IDEMPOTENCY_KEY);
		verify(userRepository, never()).findByUsernameForUpdate(anyString());
	}

	@Test
	void topUp_UserNotFound() {
		// Given
		BigDecimal amount = new BigDecimal("50.00");
		when(transactionRepository.findByIdempotencyKey(TEST_IDEMPOTENCY_KEY))
				.thenReturn(Optional.empty());
		when(userRepository.findByUsernameForUpdate(TEST_USERNAME))
				.thenReturn(Optional.empty());

		// When & Then
		assertThrows(UserNotFoundException.class,
				() -> walletService.topUp(TEST_USERNAME, amount, TEST_IDEMPOTENCY_KEY));
		verify(userRepository).findByUsernameForUpdate(TEST_USERNAME);
	}

	@Test
	void charge_Success() {
		// Given
		BigDecimal amount = new BigDecimal("30.00");
		when(transactionRepository.findByIdempotencyKey(TEST_IDEMPOTENCY_KEY))
				.thenReturn(Optional.empty());
		when(userRepository.findByUsernameForUpdate(TEST_USERNAME))
				.thenReturn(Optional.of(testUser));
		when(userRepository.save(any(User.class))).thenReturn(testUser);

		Transaction expectedTransaction = new Transaction(testUser, TransactionType.CHARGE,
				amount, testUser.getBalance(), testUser.getBalance().subtract(amount), TEST_IDEMPOTENCY_KEY);
		when(transactionRepository.save(any(Transaction.class))).thenReturn(expectedTransaction);

		// When
		Transaction result = walletService.charge(TEST_USERNAME, amount, TEST_IDEMPOTENCY_KEY);

		// Then
		assertNotNull(result);
		assertEquals(TransactionType.CHARGE, result.getType());
		assertEquals(amount, result.getAmount());
		assertEquals(new BigDecimal("70.00"), testUser.getBalance());
		verify(transactionRepository).findByIdempotencyKey(TEST_IDEMPOTENCY_KEY);
		verify(userRepository).findByUsernameForUpdate(TEST_USERNAME);
		verify(userRepository).save(testUser);
		verify(transactionRepository).save(any(Transaction.class));
	}

	@Test
	void charge_InsufficientBalance() {
		// Given
		BigDecimal amount = new BigDecimal("150.00"); // More than current balance
		when(transactionRepository.findByIdempotencyKey(TEST_IDEMPOTENCY_KEY))
				.thenReturn(Optional.empty());
		when(userRepository.findByUsernameForUpdate(TEST_USERNAME))
				.thenReturn(Optional.of(testUser));

		// When & Then
		assertThrows(InsufficientBalanceException.class,
				() -> walletService.charge(TEST_USERNAME, amount, TEST_IDEMPOTENCY_KEY));
		verify(userRepository).findByUsernameForUpdate(TEST_USERNAME);
		verify(userRepository, never()).save(any(User.class));
		verify(transactionRepository, never()).save(any(Transaction.class));
	}

	@Test
	void getUser_Success() {
		// Given
		when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));

		// When
		User result = walletService.getUser(TEST_USERNAME);

		// Then
		assertNotNull(result);
		assertEquals(TEST_USERNAME, result.getUsername());
		verify(userRepository).findByUsername(TEST_USERNAME);
	}

	@Test
	void getUser_UserNotFound() {
		// Given
		when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

		// When & Then
		assertThrows(UserNotFoundException.class,
				() -> walletService.getUser(TEST_USERNAME));
		verify(userRepository).findByUsername(TEST_USERNAME);
	}

	@Test
	void validateAmount_InvalidPrecision() {
		// Given
		BigDecimal invalidAmount = new BigDecimal("10.123"); // 3 decimal places

		// When & Then
		assertThrows(IllegalArgumentException.class,
				() -> walletService.topUp(TEST_USERNAME, invalidAmount, TEST_IDEMPOTENCY_KEY));
	}

	@Test
	void validateAmount_NegativeAmount() {
		// Given
		BigDecimal negativeAmount = new BigDecimal("-10.00");

		// When & Then
		assertThrows(IllegalArgumentException.class,
				() -> walletService.topUp(TEST_USERNAME, negativeAmount, TEST_IDEMPOTENCY_KEY));
	}
}