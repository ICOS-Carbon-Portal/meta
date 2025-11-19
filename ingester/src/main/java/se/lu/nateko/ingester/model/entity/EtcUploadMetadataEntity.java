package se.lu.nateko.ingester.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity(name = "EtcUploadMetadata")
public class EtcUploadMetadataEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Integer id;
	private String hashSum;
	private String fileName;
	private String station;
	private Integer logger;
	private String dataType;
	private Integer fileId;
	private LocalDateTime acquisitionStart;
	private LocalDateTime acquisitionStop;

	public EtcUploadMetadataEntity() {}

	public EtcUploadMetadataEntity(
		String hashSum,
		String fileName,
		String station,
		Integer logger,
		String dataType,
		Integer fileId,
		LocalDateTime acquisitionStart,
		LocalDateTime acquisitionStop
	) {
		this.hashSum = hashSum;
		this.fileName = fileName;
		this.station = station;
		this.logger = logger;
		this.dataType = dataType;
		this.fileId = fileId;
		this.acquisitionStart = acquisitionStart;
		this.acquisitionStop = acquisitionStop;
	}

	public String getHashSum() {
		return hashSum;
	}

	public String getFileName() {
		return fileName;
	}

	public String getStation() {
		return station;
	}

	public Integer getLogger() {
		return logger;
	}

	public String getDataType() {
		return dataType;
	}

	public Integer getFileId() {
		return fileId;
	}

	public LocalDateTime getAcquisitionStart() {
		return acquisitionStart;
	}

	public LocalDateTime getAcquisitionStop() {
		return acquisitionStop;
	}
}
