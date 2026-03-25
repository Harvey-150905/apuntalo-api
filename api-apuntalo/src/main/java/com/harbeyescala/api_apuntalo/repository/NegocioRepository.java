package com.harbeyescala.api_apuntalo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.harbeyescala.api_apuntalo.entity.Negocio;

public interface NegocioRepository extends JpaRepository<Negocio, Long> {
    List<Negocio> findByActivoTrue();
}