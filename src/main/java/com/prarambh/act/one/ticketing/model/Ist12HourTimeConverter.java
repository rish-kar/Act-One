package com.prarambh.act.one.ticketing.model;

import jakarta.persistence.*;

import java.time.*;
import java.time.format.*;
import java.util.*;

@Converter
public class Ist12HourTimeConverter implements AttributeConverter<LocalTime, String> {

      private static final DateTimeFormatter FORMATTER_WRITE =
              DateTimeFormatter.ofPattern("hh:mm a");

      private static final DateTimeFormatter FORMATTER_READ =
              new DateTimeFormatterBuilder()
                      .parseCaseInsensitive()
                      .appendPattern("hh:mm a")
                      .toFormatter(Locale.ENGLISH);

      @Override
      public String convertToDatabaseColumn(LocalTime attribute) {
            return attribute == null ? null : attribute.format(FORMATTER_WRITE);
      }

      @Override
      public LocalTime convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return null;
            return LocalTime.parse(dbData.trim(), FORMATTER_READ);
      }
}
