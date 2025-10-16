package se.lu.nateko.ingester.model.dto;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class DataObjectDto {
	private String hashSum;
	private String submitterId;
	private URI objectSpecification;
	private String fileName;
	private SpecificInfoDto specificInfo;
	private List<String> isNextVersionOf;
	private Optional<DoiDto> preExistingDio;
	private List<ReferencesDto> references;

	public DataObjectDto(
		String hashSum,
		String submitterId,
		URI objectSpecification,
		String fileName,
		SpecificInfoDto specificInfo,
		List<String> isNextVersionOf,
		Optional<DoiDto> preExistingDio,
		List<ReferencesDto> references
	) {
		this.hashSum = hashSum;
		this.submitterId = submitterId;
		this.objectSpecification = objectSpecification;
		this.fileName = fileName;
		this.specificInfo = specificInfo;
		this.isNextVersionOf = isNextVersionOf;
		this.preExistingDio = preExistingDio;
		this.references = references;
	}

	public String getHashSum() {
		return hashSum;
	}

	public String getSubmitterId() {
		return submitterId;
	}

	public URI getObjectSpecification() {
		return objectSpecification;
	}

	public String getFileName() {
		return fileName;
	}

	public SpecificInfoDto getSpecificInfo() {
		return specificInfo;
	}

	public List<String> getIsNextVersionOf() {
		return isNextVersionOf;
	}

	public Optional<DoiDto> getPreExistingDio() {
		return preExistingDio;
	}

	public List<ReferencesDto> getReferences() {
		return references;
	}
}
