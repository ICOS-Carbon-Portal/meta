package se.lu.nateko.ingester.model.entity;

import java.sql.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class StationSpecificDataObjectEntity {
	@Id
	private Integer id;
	private String submitterId;
	private String hashSum;
	private String fileName;
	private String specificStation;
	private Date acquisitionStartDate;
	private Date acquisitionStopDate;
	private Double specificSamplingHeight;

	public StationSpecificDataObjectEntity(
		Integer id,
		String submitterId,
		String hashSum,
		String fileName,
		String specificStation,
		Date acquisitionStartDate,
		Date acquisitionStopDate,
		Double specificSamplingHeight
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

	public Date getAcquisitionStartDate() {
		return acquisitionStartDate;
	}

	public Date getAcquisitionStopDate() {
		return acquisitionStopDate;
	}

	public Double getSpecificSamplingHeight() {
		return specificSamplingHeight;
	}
}
