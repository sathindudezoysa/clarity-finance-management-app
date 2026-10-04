package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.SampleEntity
import com.example.clarity.domain.model.SampleModel

fun SampleEntity.toDomain(): SampleModel {
    return SampleModel(
        id = id,
        title = title,
        description = description
    )
}

fun SampleModel.toEntity(): SampleEntity {
    return SampleEntity(
        id = id,
        title = title,
        description = description
    )
}
