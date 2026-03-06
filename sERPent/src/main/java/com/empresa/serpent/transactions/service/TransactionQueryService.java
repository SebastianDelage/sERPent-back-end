package com.empresa.serpent.transactions.service;


import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.transactions.repository.TransactionSpecifications;
import com.empresa.serpent.transactions.web.dto.filter.TransactionFilter;
import com.empresa.serpent.transactions.web.dto.response.TransactionDetailResponse;
import com.empresa.serpent.transactions.web.dto.response.TransactionListResponse;
import com.empresa.serpent.transactions.web.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionQueryService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public Page<TransactionListResponse> search(TransactionFilter filter, Pageable pageable) {
        Specification<TransactionEntity> spec = TransactionSpecifications.fromFilter(filter);

        return transactionRepository
                .findAll(spec, pageable)
                .map(transactionMapper::toListResponse);
    }

    public TransactionDetailResponse getById(Long id) {
        TransactionEntity entity = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));

        return transactionMapper.toDetailResponse(entity);
    }
}
