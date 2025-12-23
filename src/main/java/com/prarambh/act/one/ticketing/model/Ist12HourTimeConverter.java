package com.prarambh.act.one.ticketing.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Stores a LocalTime in the database as a 12-hour formatted string like "01:45 PM".
 *
 * Note: This is for display/storage preference only. If you need to sort by time later,
 * prefer also storing a proper TIME column.
 */
@Converter
public class Ist12HourTimeConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    @Override
    public String convertToDatabaseColumn(LocalTime attribute) {
        return attribute == null ? null : attribute.format(FORMATTER);
    }

    @Override
    public LocalTime convertToEntityAttribute(String dbData) {
        return (dbData == null || dbData.isBlank()) ? null : LocalTime.parse(dbData, FORMATTER);
    }
}

