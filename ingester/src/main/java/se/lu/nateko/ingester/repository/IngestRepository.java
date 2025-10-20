package se.lu.nateko.ingester.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import se.lu.nateko.ingester.model.entity.StationSpecificDataObjectEntity;

@Repository
public interface IngestRepository extends JpaRepository<StationSpecificDataObjectEntity, Integer> {
}
