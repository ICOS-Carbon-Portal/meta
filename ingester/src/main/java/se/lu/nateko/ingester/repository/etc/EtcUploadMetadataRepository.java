package se.lu.nateko.ingester.repository.etc;

import org.springframework.data.jpa.repository.JpaRepository;

import se.lu.nateko.ingester.model.entity.EtcUploadMetadataEntity;

public interface EtcUploadMetadataRepository extends JpaRepository<EtcUploadMetadataEntity, Integer> {
}
