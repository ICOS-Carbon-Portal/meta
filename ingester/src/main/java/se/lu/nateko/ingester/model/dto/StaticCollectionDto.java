package se.lu.nateko.ingester.model.dto;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class StaticCollectionDto {
  private String submitterId;
  private List<URI> members;
  private String title;
  private Optional<String> description;
  private List<String> isNextVersionOf;
  private Optional<DoiDto> preExistingDoi;
  private Optional<String> documentation;
  private Optional<String> coverage;

	public StaticCollectionDto(
		String submitterId,
		List<URI> members,
		String title,
		Optional<String> description,
		List<String> isNextVersionOf,
		Optional<DoiDto> preExistingDoi,
		Optional<String> documentation,
		Optional<String> coverage
	) {
		this.submitterId = submitterId;
		this.members = members;
		this.title = title;
		this.description = description;
		this.isNextVersionOf = isNextVersionOf;
		this.preExistingDoi = preExistingDoi;
		this.documentation = documentation;
		this.coverage = coverage;
	}

	public String getSubmitterId() {
		return submitterId;
	}
	public List<URI> getMembers() {
		return members;
	}
	public String getTitle() {
		return title;
	}
	public Optional<String> getDescription() {
		return description;
	}
	public List<String> getIsNextVersionOf() {
		return isNextVersionOf;
	}
	public Optional<DoiDto> getPreExistingDoi() {
		return preExistingDoi;
	}
	public Optional<String> getDocumentation() {
		return documentation;
	}
	public Optional<String> getCoverage() {
		return coverage;
	}
}
