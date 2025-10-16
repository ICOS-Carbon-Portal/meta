package se.lu.nateko.ingester.model.dto;

public class DoiDto {
	private String prefix;
	private String suffix;

	public DoiDto(String prefix, String suffix) {
		this.prefix = prefix;
		this.suffix = suffix;
	}

	@Override
	public String toString() {
		return prefix + "/" + suffix;
	}

	public String getPrefix() {
		return prefix;
	}

	public String getSuffix() {
		return suffix;
	}
}
