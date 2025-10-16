package se.lu.nateko.ingester.model.dto;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class SubmitterProfile {
  private String id;
  private Optional<URI> producingOrganizationClass;
  private Optional<URI> producingOrganization;
  private List<URI> authorizedThemes;
  private List<URI> authorizedProjects;

	public SubmitterProfile(
		String id,
		Optional<URI> producingOrganizationClass,
		Optional<URI> producingOrganization,
		List<URI> authorizedThemes,
		List<URI> authorizedProjects
  ) {
		this.id = id;
		this.producingOrganizationClass = producingOrganizationClass;
		this.producingOrganization = producingOrganization;
		this.authorizedThemes = authorizedThemes;
		this.authorizedProjects = authorizedProjects;
	}

	public String getId() {
		return id;
	}
	public Optional<URI> getProducingOrganizationClass() {
		return producingOrganizationClass;
	}
	public Optional<URI> getProducingOrganization() {
		return producingOrganization;
	}
	public List<URI> getAuthorizedThemes() {
		return authorizedThemes;
	}
	public List<URI> getAuthorizedProjects() {
		return authorizedProjects;
	}
}
