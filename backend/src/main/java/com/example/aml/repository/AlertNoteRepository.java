package com.example.aml.repository;

import com.example.aml.domain.AlertNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertNoteRepository extends JpaRepository<AlertNote, Long> {

    List<AlertNote> findByAlertIdOrderByCreatedAtAsc(Long alertId);
}
