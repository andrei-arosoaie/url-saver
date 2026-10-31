package com.know.identification.application;

public interface IdentificationProvider {
  String id();

  String model();

  Identification.Result identify(Identification.Request request);
}
