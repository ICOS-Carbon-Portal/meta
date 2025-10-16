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
	private Optional<List<String>> isNextVersionOf;
	private Optional<DoiDto> preExistingDio;
	private Optional<List<ReferencesDto>> references;

	public DataObjectDto(
		String hashSum,
		String submitterId,
		URI objectSpecification,
		String fileName,
		SpecificInfoDto specificInfo,
		Optional<List<String>> isNextVersionOf,
		Optional<DoiDto> preExistingDio,
		Optional<List<ReferencesDto>> references
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

	public Optional<List<String>> getIsNextVersionOf() {
		return isNextVersionOf;
	}

	public Optional<DoiDto> getPreExistingDio() {
		return preExistingDio;
	}

	public Optional<List<ReferencesDto>> getReferences() {
		return references;
	}
}
