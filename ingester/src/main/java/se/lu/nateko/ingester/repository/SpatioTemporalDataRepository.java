package se.lu.nateko.ingester.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import se.lu.nateko.ingester.model.entity.SpatioTemporalDataEntity;

public interface SpatioTemporalDataRepository extends JpaRepository<SpatioTemporalDataEntity, Integer> {
}
