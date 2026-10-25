package com.example.aml.api.dto;

import java.time.Instant;

public record NoteDto(Long id, String author, String text, boolean systemEntry, Instant createdAt) {
}
