package com.example.dawanow.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

public enum DashboardPeriod {
    LAST_DAY {
        @Override
        public LocalDate getStartDate() {
            return LocalDate.now().minusDays(1);
        }

        @Override
        public LocalDateTime getStartDateTime() {
            return LocalDateTime.now().minusDays(1);
        }
    },
    LAST_WEEK {
        @Override
        public LocalDate getStartDate() {
            return LocalDate.now().minusWeeks(1);
        }

        @Override
        public LocalDateTime getStartDateTime() {
            return LocalDateTime.now().minusWeeks(1);
        }
    },
    LAST_MONTH {
        @Override
        public LocalDate getStartDate() {
            return LocalDate.now().minusMonths(1);
        }

        @Override
        public LocalDateTime getStartDateTime() {
            return LocalDateTime.now().minusMonths(1);
        }
    },
    LAST_YEAR {
        @Override
        public LocalDate getStartDate() {
            return LocalDate.now().minusYears(1);
        }

        @Override
        public LocalDateTime getStartDateTime() {
            return LocalDateTime.now().minusYears(1);
        }
    };

    public abstract LocalDate getStartDate();

    public abstract LocalDateTime getStartDateTime();

    public LocalDate getEndDate() {
        return LocalDate.now();
    }

    public LocalDateTime getEndDateTime() {
        return LocalDateTime.now();
    }
}
