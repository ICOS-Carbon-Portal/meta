package se.lu.nateko.ingester.model.dto;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class DataProductionDto {
	private URI creator;
	private List<URI> contributors;
	private Optional<URI> hostOrganization;
	private Optional<String> comment;
	private List<String> sources;
	private Optional<String> documentation;

	public DataProductionDto(
		URI creator,
		List<URI> contributors,
		Optional<URI> hostOrganization,
		Optional<String> comment,
		List<String> sources,
		Optional<String> documentation
	) {
		this.creator = creator;
		this.contributors = contributors;
		this.hostOrganization = hostOrganization;
		this.comment = comment;
		this.sources = sources;
		this.documentation = documentation;
	}

	public URI getCreator() {
		return creator;
	}

	public List<URI> getContributors() {
		return contributors;
	}

	public Optional<URI> getHostOrganization() {
		return hostOrganization;
	}

	public Optional<String> getComment() {
		return comment;
	}

	public List<String> getSources() {
		return sources;
	}

	public Optional<String> getDocumentation() {
		return documentation;
	}
}
