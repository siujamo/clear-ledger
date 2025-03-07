package com.onixbyte.clearledger.service;

import com.mybatisflex.core.paginate.Page;
import com.onixbyte.clearledger.data.entity.Transaction;
import com.onixbyte.clearledger.data.entity.ViewTransaction;
import com.onixbyte.clearledger.data.entity.table.TransactionTableDef;
import com.onixbyte.clearledger.data.request.CreateTransactionRequest;
import com.onixbyte.clearledger.data.request.UpdateTransactionRequest;
import com.onixbyte.clearledger.exception.BizException;
import com.onixbyte.clearledger.security.UserHolder;
import com.onixbyte.clearledger.repository.LedgerRepository;
import com.onixbyte.clearledger.repository.TransactionRepository;
import com.onixbyte.clearledger.repository.UserLedgerRepository;
import com.onixbyte.guid.GuidCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final String cacheTag = "tx";

    private final TransactionRepository transactionRepository;
    private final GuidCreator<String> transactionIdCreator;
    private final DateTimeFormatter datetimeFormatter;
    private final LedgerRepository ledgerRepository;
    private final UserLedgerRepository userLedgerRepository;
    private final SerialService serialService;

    public TransactionService(TransactionRepository transactionRepository,
                              GuidCreator<String> transactionIdCreator,
                              DateTimeFormatter datetimeFormatter,
                              LedgerRepository ledgerRepository, UserLedgerRepository userLedgerRepository, SerialService serialService) {
        this.transactionRepository = transactionRepository;
        this.transactionIdCreator = transactionIdCreator;
        this.datetimeFormatter = datetimeFormatter;
        this.ledgerRepository = ledgerRepository;
        this.userLedgerRepository = userLedgerRepository;
        this.serialService = serialService;
    }

    private void preValidate(String userId, String ledgerId) {
        if (!ledgerRepository.hasLedger(ledgerId)) {
            throw new BizException(HttpStatus.NOT_FOUND, "账本不存在");
        }

        if (!userLedgerRepository.canWriteTransaction(userId, ledgerId)) {
            throw new BizException(HttpStatus.FORBIDDEN, "您没有对该账本的操作权限");
        }
    }

    public Page<ViewTransaction> getTransactionPage(Long ledgerId, Long pageNum, Long size) {
        var offset = (pageNum - 1) * size;
        var transactions = transactionRepository.selectPaginateViewTransactions(ledgerId, offset, size);

        var result = new Page<ViewTransaction>(pageNum, size);
        result.setRecords(transactions);

        var count = transactionRepository.selectCountByCondition(TransactionTableDef.TRANSACTION
                .LEDGER_ID.eq(ledgerId));
        result.setTotalRow(count);

        return result;
    }

    public ViewTransaction createTransaction(CreateTransactionRequest request) {
        var currentUser = UserHolder.getCurrentUser();
        preValidate(currentUser.id(), request.ledgerId());

        var transaction = Transaction.builder()
                .id(transactionIdCreator.nextId())
                .ledgerId(request.ledgerId())
                .userId(currentUser.id())
                .transactionDate(LocalDateTime.parse(request.transactionDate(), datetimeFormatter))
                .description(request.description())
                .createdAt(LocalDateTime.now())
                .build();
        transactionRepository.insert(transaction);
        return transaction.toView(currentUser.username());
    }

    public ViewTransaction updateTransaction(UpdateTransactionRequest request) {
        var currentUser = UserHolder.getCurrentUser();
        preValidate(currentUser.id(), request.ledgerId());

        var transaction = Transaction.builder()
                .id(request.id())
                .ledgerId(request.ledgerId())
                .userId(currentUser.id())
                .transactionDate(LocalDateTime.parse(request.transactionDate(), datetimeFormatter))
                .description(request.description())
                .build();
        // the default behaviour of `update(T)` method from `BaseMapper` will ignore null values
        transactionRepository.update(transaction);
        return transaction.toView(currentUser.username());
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void resetSerial() {
        log.info("Resetting transaction serial.");
        serialService.resetSerial(cacheTag);
        log.info("Transaction serial has been reset to 0.");
    }
}
