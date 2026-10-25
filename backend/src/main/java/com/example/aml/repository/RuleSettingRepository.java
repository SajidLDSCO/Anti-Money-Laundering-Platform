package com.example.aml.repository;

import com.example.aml.domain.RuleSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleSettingRepository extends JpaRepository<RuleSetting, String> {
}
