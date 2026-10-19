package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.TransactionProofEntity
import com.example.clarity.domain.model.TransactionProof

fun TransactionProofEntity.toDomain() = TransactionProof(id, transactionId, displayName, mimeType,
    sizeBytes, createdAt, verifiedAmountMinor, pendingUpload, unavailable)
