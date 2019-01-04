package com.howtographql;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LinkFilter {

  private String descriptionContains;
  private String urlContains;

  @JsonProperty("description_contains")
  public String getDescriptionContains() {
    return descriptionContains;
  }

  public void setDescriptionContains(String descriptionContains) {
    this.descriptionContains = descriptionContains;
  }

  @JsonProperty("url_contains")
  public String getUrlContains() {
    return urlContains;
  }

  public void setUrlContains(String urlContains) {
    this.urlContains = urlContains;
  }
}
