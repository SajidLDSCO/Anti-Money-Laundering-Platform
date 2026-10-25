package com.example.aml.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code author} is set by the server from the signed-in user; any value sent by the client is ignored. */
public record NoteRequest(String author, @NotBlank @Size(max = 4000) String text) {
}
