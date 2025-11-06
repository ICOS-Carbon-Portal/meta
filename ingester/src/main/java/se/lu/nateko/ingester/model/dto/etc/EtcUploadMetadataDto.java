package se.lu.nateko.ingester.model.dto.etc;

import java.time.LocalDateTime;

public class EtcUploadMetadataDto {
	private String hashSum;
	private String fileName;
	private StationId station;
	private Integer logger;
	private DataType dataType;
	private Integer fileId;
	private LocalDateTime acquisitionStart;
	private LocalDateTime acquisitionStop;

	public EtcUploadMetadataDto(
		String hashSum,
		String fileName,
		StationId station,
		Integer logger,
		DataType dataType,
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

	public StationId getStation() {
		return station;
	}

	public Integer getLogger() {
		return logger;
	}

	public DataType getDataType() {
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
