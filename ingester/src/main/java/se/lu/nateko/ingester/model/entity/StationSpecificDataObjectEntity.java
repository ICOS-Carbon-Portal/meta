package se.lu.nateko.ingester.model.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity(name = "StationSpecificDataObject")
public class StationSpecificDataObjectEntity {
	@Id
	private Integer id;
	private String submitterId;
	private String hashSum;
	private String fileName;
	private String specificStation;
	private Instant acquisitionStartDate;
	private Instant acquisitionStopDate;
	private Float specificSamplingHeight;

	public StationSpecificDataObjectEntity() {}

	public StationSpecificDataObjectEntity(
		Integer id,
		String submitterId,
		String hashSum,
		String fileName,
		String specificStation,
		Instant acquisitionStartDate,
		Instant acquisitionStopDate,
		Float specificSamplingHeight
	) {
		this.id = id;
		this.submitterId = submitterId;
		this.hashSum = hashSum;
		this.fileName = fileName;
		this.specificStation = specificStation;
		this.acquisitionStartDate = acquisitionStartDate;
		this.acquisitionStopDate = acquisitionStopDate;
		this.specificSamplingHeight = specificSamplingHeight;
	}

	public Integer getId() {
		return id;
	}

	public String getSubmitterId() {
		return submitterId;
	}

	public String getHashSum() {
		return hashSum;
	}

	public String getFileName() {
		return fileName;
	}

	public String getSpecificStation() {
		return specificStation;
	}

	public Instant getAcquisitionStartDate() {
		return acquisitionStartDate;
	}

	public Instant getAcquisitionStopDate() {
		return acquisitionStopDate;
	}

	public Float getSpecificSamplingHeight() {
		return specificSamplingHeight;
	}
}
