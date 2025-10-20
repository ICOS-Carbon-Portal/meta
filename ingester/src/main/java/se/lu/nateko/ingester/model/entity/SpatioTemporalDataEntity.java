package se.lu.nateko.ingester.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity(name = "SpatioTemporalDataObject")
public class SpatioTemporalDataEntity {
	@Id
	private Integer id;
	private String submitterId;
	private String hashSum;
	private String fileName;
	private String title;
	private String description;
	private String spatial;
	private String temporal;
	private String forStation;
	private Float samplingHeight;
	private String customLandingPage;

	public SpatioTemporalDataEntity(
		Integer id,
		String submitterId,
		String hashSum,
		String fileName,
		String title,
		String description,
		String spatial,
		String temporal,
		String forStation,
		Float samplingHeight,
		String customLandingPage
	) {
		this.id = id;
		this.submitterId = submitterId;
		this.hashSum = hashSum;
		this.fileName = fileName;
		this.title = title;
		this.description = description;
		this.spatial = spatial;
		this.temporal = temporal;
		this.forStation = forStation;
		this.samplingHeight = samplingHeight;
		this.customLandingPage = customLandingPage;
	}

	public SpatioTemporalDataEntity() {
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

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getSpatial() {
		return spatial;
	}

	public String getTemporal() {
		return temporal;
	}

	public String getForStation() {
		return forStation;
	}

	public Float getSamplingHeight() {
		return samplingHeight;
	}

	public String getCustomLandingPage() {
		return customLandingPage;
	}
}
