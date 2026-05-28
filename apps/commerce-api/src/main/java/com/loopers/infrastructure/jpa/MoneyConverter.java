package com.loopers.infrastructure.jpa;

import com.loopers.domain.common.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Money ↔ BIGINT. autoApply로 모든 Money 필드에 자동 적용 (DDL 변경 불요). */
@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, Long> {

    @Override
    public Long convertToDatabaseColumn(Money attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Money convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : Money.of(dbData);
    }
}
